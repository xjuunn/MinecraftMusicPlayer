#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:projection.glsl>

#ifndef GLASS_SHADOW
uniform sampler2D Sampler0; // 模糊度为 0 时是原图（1:1），否则是清透层（1/2）
uniform sampler2D Sampler1; // 磨砂层（1/4 分辨率）
uniform sampler2D Sampler2; // 重度层（1/8 分辨率）
#endif

layout(location = 0) in vec2 vPos;
layout(location = 1) in vec2 vLocal;
layout(location = 2) in vec4 vTuning;

layout(location = 0) out vec4 fragColor;

// ── 编译期常量：由 RenderPipeline 的 shader define 注入，一种材质一份管线 ──
const float RADIUS = float(GLASS_RADIUS);

#ifndef GLASS_SHADOW
const vec4 TINT = vec4(GLASS_TINT_R, GLASS_TINT_G, GLASS_TINT_B, GLASS_TINT_A);
const float MAX_LUMA = GLASS_MAX_LUMA;
const float SATURATION = GLASS_SATURATION;
const float HIGHLIGHT = GLASS_HIGHLIGHT;
const float OPACITY = GLASS_OPACITY;
const float BLUR = GLASS_BLUR;
const float EDGE_LIGHT = GLASS_EDGE_LIGHT;
const float WINDOW_TINT = GLASS_WINDOW_TINT;
const vec3 WINDOW_TINT_COLOR = vec3(GLASS_WINDOW_TINT_R, GLASS_WINDOW_TINT_G, GLASS_WINDOW_TINT_B);

// 边缘高光的高斯 σ（GUI 像素）。Apple 未公开具体值，按"贴着轮廓的一圈柔光"标定：
// σ 太大就糊成一整片白，太小就退化成 1px 硬描边。3.0 在 1x 与 2x DPI 下都在合适区间。
const float EDGE_SIGMA = 3.0;
#else
const float SHADOW_SIZE = GLASS_SHADOW_SIZE;
const float SHADOW_ALPHA = GLASS_SHADOW_ALPHA;
const vec2 SHADOW_OFFSET = vec2(GLASS_SHADOW_OFFSET_X, GLASS_SHADOW_OFFSET_Y);
const float SHADOW_PADDING = float(GLASS_SHADOW_PADDING);
#endif

const float PI = 3.14159265;

float sdRoundBox(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

float luma(vec3 color) {
    return dot(color, vec3(0.2126, 0.7152, 0.0722));
}

#ifndef GLASS_SHADOW
// ── 逐 draw 光学参数：32 位位域 ────────────────────────────────────
// 界面渲染路径拿不到 RenderPass，无法绑定逐 draw uniform；顶点格式只有一路 color，
// 即 4 个 RGBA8 通道共 32 位。
//
// 移除 b 位置后空出 5 位，直接用来提升两个原本精度最差的字段：
//
//   [0..5]   折射      6 位，1px 步进 → 0..63 px
//   [6..11]  a 位置    6 位，1px 步进 → 0..63 px
//   [12..16] 反向倍率  5 位，/31×2 = 0..2
//   [17..21] 中心缩小  5 位，/31×24 = 0..24 px
//   [22..26] 边缘色散  5 位，/31 = 0..1
//   [27..31] 中心色散  5 位，1px 步进 → 0..31 px
//
// 刚好用满 32 位。过去 a 位置是 2px 步进、中心色散挤在 4 位里，
// 5px 会被编码成 6px、6.4px 被编码成 6px；移除 b 位置后省出的位
// 刚好把这两项提成 1px 步进。
//
// 边缘高光强度是材质级常量，直接走 shader define，不占位域。
struct Tuning {
    float refraction;
    float aDist;
    float contra;
    float shrink;
    float dispersion;
    float centerDisp;
};

Tuning unpackTuning(vec4 c) {
    // 必须用 uint 运算：最高位在有符号 int 下是符号位，右移会补符号位、取位域就错了。
    uvec4 bytes = uvec4(round(clamp(c, 0.0, 1.0) * 255.0));
    uint packed = bytes.x | (bytes.y << 8u) | (bytes.z << 16u) | (bytes.w << 24u);
    Tuning t;
    t.refraction = float(packed & 63u);
    t.aDist      = float((packed >> 6u) & 63u);
    t.contra     = float((packed >> 12u) & 31u) / 31.0 * 2.0;
    t.shrink     = float((packed >> 17u) & 31u) / 31.0 * 24.0;
    t.dispersion = float((packed >> 22u) & 31u) / 31.0;
    t.centerDisp = float((packed >> 27u) & 31u);
    return t;
}

// ── 原理一：背景透射与模糊 ────────────────────────────────────────
// 直接在 UV 处取混合后的背景。
//
// 性能要点：色散要把同一段代码跑三遍（R/G/B 各一个采样点），如果每次都把
// 三张贴图全采一遍就是 9 次采样。这里按 BLUR 的<b>编译期</b>取值做静态分支，
// 每个档位只保留真正需要的采样：
//   BLUR = 0        → 只采 Sampler0（原图，锐利）
//   BLUR ≤ 0.5      → Sampler0 + Sampler1 两层
//   BLUR >  0.5     → 三层全采
// BLUR 是 shader define 注入的常量，GLSL 编译器会把整个分支折掉，
// 所以这不是运行时分支，而是三份互斥的代码。
vec3 backdrop(vec2 uv) {
    uv = clamp(uv, vec2(0.001), vec2(0.999));
#if GLASS_BLUR_LEVEL == 0
    return texture(Sampler0, uv).rgb;
#elif GLASS_BLUR_LEVEL == 1
    vec3 soft = texture(Sampler0, uv).rgb;
    vec3 frost = texture(Sampler1, uv).rgb;
    return mix(soft, frost, clamp(BLUR * 2.0, 0.0, 1.0));
#else
    vec3 soft = texture(Sampler0, uv).rgb;
    vec3 frost = texture(Sampler1, uv).rgb;
    vec3 heavy = texture(Sampler2, uv).rgb;
    // 三层连续混合：BLUR = 0 时只剩纯降采样，1 时是重度模糊
    vec3 mid = mix(soft, frost, clamp(BLUR * 2.0, 0.0, 1.0));
    return mix(mid, heavy, clamp(BLUR * 2.0 - 1.0, 0.0, 1.0));
#endif
}
#endif

// ── 圆角矩形的解析梯度 ────────────────────────────────────────────
// 早期用 4 次额外 SDF 求差分法线，这里改成解析解，省掉 4 次 SDF 求值。
//
// 记 q = |p| − b + r（就是 SDF 内部用的那个量）：
//   q 两轴都 > 0  → 在圆角区，法线沿角点径向 normalize(sign(p) * q)
//   否则          → 落在直边带（或形状内部），法线是被钳住那一轴的方向，
//                   用 q.x > q.y 判断哪条边更近
//
// 这个规则对形状内外都成立，因为它只依赖 SDF 自己的分量符号，
// 不需要额外区分"内部 / 外部"。早期版本分三种情况、还先判 a.y < 0，
// 结果把 a.x >= 0 且 a.y < 0 的左右直边带整段错判成上下直边，
// 法线在侧边上转 90°，边缘高光与折射方向全错。
vec2 sdRoundBoxGradient(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + r;
    vec2 s = sign(p);
    if (q.x > 0.0 && q.y > 0.0) {
        return s * q / length(q);
    }
    return q.x > q.y ? vec2(s.x, 0.0) : vec2(0.0, s.y);
}

void main() {
    // 面板尺寸：UV 在四边形内是 0..1 的线性插值，位置是 GUI 像素，
    // 两者的偏导数之比就是面板的像素尺寸（正交投影下是仿射的，结果精确）。
    // 注意 GUI 的 y 向下而 dFdy 沿窗口 y 向上，两个导数同为负号，必须都取绝对值。
    vec2 size = vec2(
        abs(dFdx(vPos.x)) / max(abs(dFdx(vLocal.x)), 1e-5),
        abs(dFdy(vPos.y)) / max(abs(dFdy(vLocal.y)), 1e-5)
    );
    size = max(size, vec2(1.0));

    vec2 halfSize = size * 0.5;
    vec2 p = (vLocal - 0.5) * size;

    vec2 guiSize = vec2(2.0 / ProjMat[0][0], -2.0 / ProjMat[1][1]);
    if (guiSize.x < 1.0 || guiSize.y < 1.0) {
        guiSize = vec2(1920.0, 1080.0);
    }

    // 面板自身的 SDF 与覆盖度：玻璃和投影共用，保证两者边界严格互补。
    vec2 panelHalf = halfSize;
#ifdef GLASS_SHADOW
    panelHalf = max(halfSize - vec2(SHADOW_PADDING), vec2(0.5));
#endif
    float radius = min(RADIUS, min(panelHalf.x, panelHalf.y));
    float d = sdRoundBox(p, panelHalf, radius);
    float aa = max(fwidth(d), 1e-4);
    float mask = 1.0 - smoothstep(-aa, 0.0, d);

#ifdef GLASS_SHADOW
    // 投影轮廓取面板矩形，按偏移平移后做柔和衰减。
    // 再乘 (1 - mask) 抹掉面板自身覆盖的区域：26.x 的界面元素是按管线排序后合批绘制的，
    // 投影和玻璃面板谁先画并不由提交顺序决定，一旦投影排在后面就会整块压暗玻璃。
    vec2 shadowLocal = p - SHADOW_OFFSET;
    float shadowDistance = sdRoundBox(shadowLocal, panelHalf, radius);
    float shadowAlpha = (1.0 - smoothstep(0.0, SHADOW_SIZE, shadowDistance)) * SHADOW_ALPHA;
    fragColor = vec4(0.0, 0.0, 0.0, clamp(shadowAlpha, 0.0, 1.0) * (1.0 - mask));
    return;
#else
    if (mask <= 0.0) {
        discard;
    }

    Tuning tuning = unpackTuning(vTuning);
    float REFRACTION = tuning.refraction;
    float DISPERSION = tuning.dispersion;
    float CENTER_DISPERSION = tuning.centerDisp;
    float A_DIST = tuning.aDist;
    float CONTRA = tuning.contra;
    float SHRINK = tuning.shrink;

    float dist = max(-d, 0.0);                 // 到边框的距离
    float aPos = max(A_DIST, 0.001);

    // 圆角矩形 SDF 的解析梯度 = 向外的法线，供边缘弯折与高光方向性调制使用
    vec2 normal = sdRoundBoxGradient(p, halfSize, radius);

    // ── 原理二：边缘畸变（反向段 + 一条单调过渡）──────────────────────
    //
    //   边框 ────── a ──────────────────────── 中心
    //     └─反向─┘   └──── 单调递增的折射与缩小 ────┘
    //
    // 只有一个可调边界 a，a 以内<b>没有</b>中间峰值。
    //
    // 为什么不能用钟形（曾用 sin/sin²）：钟形的峰值落在区间中点，越过峰值后
    // 强度一路衰减、到中心归零。于是"调高折射"只是把那个鼓包抬高，鼓包峰值处
    // 变成折射与缩小的分界——正是能看到突变点的位置。
    // 而真实厚玻璃是<b>整块</b>在当透镜，越靠中心越均匀，折射不该在中心消失。
    //
    // 改用单调递增的斜坡：折射与缩小共用同一条 coreRamp，从 a 处的 0 平滑爬到
    // 中心的 1，全程单调、无内部极值，因此不存在任何分界点。
    float shortHalf = max(min(halfSize.x, halfSize.y), 1.0);
    // a 也必须随面板缩放：按钮只有 16px 高（短边半尺寸 8），而 a 默认给到 8，
    // 此时 a 正好等于短边，核心区整段塌缩、曲线退化成一条竖线。
    aPos = min(aPos, shortHalf * 0.5);
    float coreEnd = max(shortHalf, aPos + 1.0);
    float s = clamp((dist - aPos) / max(coreEnd - aPos, 0.001), 0.0, 1.0);

    // smoothstep：两端斜率都为 0，在 a 处与反向段（同样斜率 0）自然衔接，
    // 在中心处收平。单调且只有端点极值 —— 结构上不可能再产生突变点。
    float coreRamp = s * s * (3.0 - 2.0 * s);

    // ── 位移预算：任何面板都不许位移超过自身短边半尺寸的一定比例 ────────
    //
    // 折射与缩小都是"绝对像素"参数，在小面板上会失控。按钮高 16px
    // （短边半尺寸 8）时，单纯照搬窗口预设的 REFRACTION=32 会让贴边采样点
    // 外移 32px —— 是半高的 400%，采样点被推到按钮外的背景里；
    // 上下两边同时这样推，中心区域就被两侧的外部背景覆盖，
    // 表现为中心重叠、颜色发浑。
    //
    // 基数取 max(REFRACTION, SHRINK) 而不是两者之和：折射在贴边处达到峰值、
    // 缩小在中心达到峰值，<b>峰值位置不同</b>，并不会叠加。
    //
    // 这个缩放对每个面板是常数，不随位置变化，因此不会引入任何新的断裂。
    // 正常配置的控件（按钮默认 REF=3、开关 REF=3、圆珠 REF=2）scale 恒为 1，
    // 完全不受影响；只有被调试面板全局覆盖值放大的小控件才会自动收敛。
    const float DISPLACEMENT_BUDGET = 0.55;
    float nominal = max(max(REFRACTION, SHRINK), 0.001);
    float budget = shortHalf * DISPLACEMENT_BUDGET;
    float dispScale = min(1.0, budget / nominal);
    REFRACTION *= dispScale;
    SHRINK *= dispScale;

    // ── 法线只在近边有意义 ────────────────────────────────────────────
    //
    // 这是必须的修正，不是优化。SDF 梯度是"到最近边的外法线"，
    // 但它在<b>面板内部</b>是不连续的（SDF 的中轴 / medial axis）：
    //
    //   · q.x 与 q.y 都变成负数后，只靠比较两者大小来选轴，会在
    //     |x| − |y| = b.x − b.y 这条对角线上从"轴向 X"翻成"轴向 Y"；
    //   · sign(p) 在 p 的每个分量过零时翻号，法线在 y = 0 处由 −Y 跳成 +Y；
    //     且恰在轴上 sign = 0，梯度退化成零向量；
    //   · 圆角区的中轴是<b>圆角圆心</b>（dist = radius），那里 q.x = q.y，
    //     两种取法都"对"，梯度同样跳变。
    //
    // 前两条合起来在画面正中画出 X 形纹；第三条在四个圆角内侧留一圈亮斑。
    // 早期折射只在近边有值、到中心归零，所以这些都被掩盖了；
    // 现在中心折射是满幅，缺陷才暴露出来。
    //
    // 根治办法不是加宽窗口去遮，而是<b>根本不把法线用到那么深</b>：
    // 物理上整块厚玻璃真正弯光的只有贴着轮廓的一圈，中部是平面。
    // 所以窗口固定收在圆角半径以内，且留足余量 —— 实测窗宽 44px 时
    // 圆角圆心仍在窗内、跳变残留 100%；收到 24px 后跳变随步长同比减半，
    // 即完全平滑。
    float windowWidth = clamp(radius * 0.8, aPos * 1.5, min(halfSize.x, halfSize.y) * 0.5);
    float normalWindow = 1.0 - smoothstep(0.0, 1.0, clamp(dist / max(windowWidth, 1.0), 0.0, 1.0));
    // 零向量兜底：y = 0 或 x = 0 时 sign 为 0，梯度会塌成 (0,0)
    normal = (dot(normal, normal) < 1e-6) ? vec2(0.0, 1.0) : normal;

    // 反向段：边框 → a，把背景向外推。贴边最强，到 a 处值与斜率都归零。
    float bend;
    if (dist < aPos) {
        float u = dist / aPos;
        bend = -CONTRA * pow(1.0 - u, 1.6) * REFRACTION + coreRamp * REFRACTION;
    } else {
        bend = coreRamp * REFRACTION;
    }

    // 折射（沿法线）+ 缩小（沿径向）叠加。
    //
    // 两项乘的是<b>不同</b>的窗口，这是关键：
    //   · 折射乘 normalWindow —— 它依赖 SDF 法线，只在近边有意义；
    //   · 缩小乘 coreRamp     —— 它只用 p 这个径向方向，在中部同样成立。
    // 若两项都乘 coreRamp，近边和中部的强弱就被绑死在一起，没法分别调；
    // 若折射不乘窗口，中部的退化法线就会画出 X 形纹。
    //
    // 缩小还必须<b>按各轴自己的半尺寸</b>归一化，不能整体除以 shortHalf：
    // 那样写出 p * (SHRINK / shortHalf)，短边归一化会把长轴方向的位移放大
    // bx / shortHalf 倍。按钮 100×16 就是 6.25 倍 —— 中心行离上下边才 8px，
    // 整行都落在近边、coreRamp 不为 0，径向位移瞬间把采样点推出面板，
    // 表现为中心区域重叠、颜色发浑。
    //
    // 改成 p / halfSize * SHRINK 之后，每个轴上的缩放比例都严格是
    // SHRINK × coreRamp / 该轴半尺寸 ≤ SHRINK / 该轴半尺寸，长轴不再被放大。
    // 两个方向用同一组参数、比例一致，宽面板上的圆也不会被拉成椭圆。
    vec2 shrinkOffset = (p / max(halfSize, vec2(1.0))) * (SHRINK * coreRamp);

    vec2 lens = normal * (bend * normalWindow) + shrinkOffset;

    vec2 radial = p / max(halfSize, vec2(1.0));       // 每个分量都在 -1..1

    // ── 原理三：光学响应（色散）────────────────────────────────────
    // 两部分相加：棱镜色散乘在总位移上；面板内横向色差沿径向线性增长。
    vec2 spread = lens * DISPERSION + radial * CENTER_DISPERSION;

    // 底色贴图是 GL 语义：v = 0 在世界底部，而 GUI 的 y = 0 在顶部
    vec2 baseUv = vec2(vPos.x / guiSize.x, 1.0 - vPos.y / guiSize.y);

    vec3 color;
    if (DISPERSION <= 0.0 && CENTER_DISPERSION <= 0.0) {
        // 无色散时三通道采样点相同，一次采样即可（快路径）
        color = backdrop(baseUv + lens / guiSize);
    } else {
        vec2 lensUv = lens / guiSize;
        vec2 spreadUv = spread / guiSize;
        color.r = backdrop(baseUv + lensUv + spreadUv).r;
        color.g = backdrop(baseUv + lensUv).g;
        color.b = backdrop(baseUv + lensUv - spreadUv).b;
    }

    // ── 原理一（续）：vibrancy 与亮度调整 ──────────────────────────
    // 饱和度提升 + 亮度封顶：保证雪地 / 天空前面文字依旧可读。
    // 封顶时只压缩亮度、保留原有彩度，否则室外画面会整体发灰。
    color = max(mix(vec3(luma(color)), color, SATURATION), 0.0);
    float brightness = luma(color);
    if (brightness > MAX_LUMA) {
        color = max(vec3(MAX_LUMA) + (color - vec3(brightness)), 0.0);
    }
    // C_out = α·C_glass + (1−α)·C_blur
    color = mix(color, TINT.rgb, TINT.a);

    // ── 原理二：边缘高光与轮廓 ─────────────────────────────────────
    // H(p) = e^(−d²/2σ²)，d 是到轮廓的距离。
    // 关键在于它<b>不是一圈均匀白框</b>：再乘法线的方向性调制，
    // 上缘（法线朝上）接收来自上方光源的反射最强，下缘几乎为零。
    // 这正是"空间边界"——边缘比内部更亮，且亮得有方向。
    float edgeH = exp(-(dist * dist) / (2.0 * EDGE_SIGMA * EDGE_SIGMA));
    // 法线 y 分量在 GUI 里向下为正，取负得到"朝上"的权重
    float upward = clamp(-normal.y, 0.0, 1.0);
    float directional = 0.35 + 0.65 * upward;
    color += vec3(edgeH * directional * EDGE_LIGHT);

    // 顶部柔光：整块面板自上而下的极淡渐变
    float fy = (p.y + halfSize.y) / max(size.y, 1.0);
    color += vec3(0.035 * (1.0 - fy) * HIGHLIGHT);

    // ── 把所在的窗口当作背景（子控件用）──────────────────────────────
    // 按钮、滑块、开关在视觉上都位于窗口玻璃之内，它们折射的应该是
    // "世界背景 + 窗口那层浅色叠加"，而不是裸的世界画面。
    // 合成位置必须和窗口真实的绘制顺序一致：窗口的叠加层是在玻璃全部处理完之后
    // 才盖上去的，所以这里也放在最后做同样的 alpha 混合。
    color = mix(color, WINDOW_TINT_COLOR, WINDOW_TINT);

    fragColor = vec4(clamp(color, 0.0, 1.0), mask * OPACITY);
#endif
}
