#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:projection.glsl>

#ifndef GLASS_SHADOW
uniform sampler2D Sampler0; // 清透层（较大模糊半径的一半分辨率）
uniform sampler2D Sampler1; // 磨砂层（更小分辨率、更大模糊半径）
#endif

layout(location = 0) in vec2 vPos;
layout(location = 1) in vec2 vLocal;
layout(location = 2) in vec4 vTuning;

layout(location = 0) out vec4 fragColor;

// 编译期常量：由 RenderPipeline 的 shader define 注入，一种材质一份管线
const float RADIUS = float(GLASS_RADIUS);

#ifndef GLASS_SHADOW
const vec4 TINT = vec4(GLASS_TINT_R, GLASS_TINT_G, GLASS_TINT_B, GLASS_TINT_A);
const float MAX_LUMA = GLASS_MAX_LUMA;
const float SATURATION = GLASS_SATURATION;
const float FROST = GLASS_FROST;
const float HIGHLIGHT = GLASS_HIGHLIGHT;
const float OPACITY = GLASS_OPACITY;
#else
const float SHADOW_SIZE = GLASS_SHADOW_SIZE;
const float SHADOW_ALPHA = GLASS_SHADOW_ALPHA;
const vec2 SHADOW_OFFSET = vec2(GLASS_SHADOW_OFFSET_X, GLASS_SHADOW_OFFSET_Y);
const float SHADOW_PADDING = float(GLASS_SHADOW_PADDING);
#endif

float sdRoundBox(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

float luma(vec3 color) {
    return dot(color, vec3(0.2126, 0.7152, 0.0722));
}

#ifndef GLASS_SHADOW
vec3 backdrop(vec2 uv) {
    // 钳制到内侧，避免边缘采样到无关内容
    uv = clamp(uv, vec2(0.001), vec2(0.999));
    vec3 soft = texture(Sampler0, uv).rgb;
    vec3 frost = texture(Sampler1, uv).rgb;
    return mix(soft, frost, FROST);
}
#endif

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
    // 投影的四边形是按 GlassShadow.padding() 向外扩张过的，而 size 是从顶点导数反推的
    // 四边形尺寸，所以阴影分支拿到的是"扩张后"的尺寸。必须减掉这段 padding，
    // 否则 SDF 描述的是扩张后的框，投影会变成一圈离开面板、带圆角的暗环——
    // 窗口贴近屏幕边缘时，这圈暗环正好压在屏幕四角上。
    vec2 panelHalf = halfSize;
#ifdef GLASS_SHADOW
    panelHalf = max(halfSize - vec2(SHADOW_PADDING), vec2(0.5));
#endif
    float radius = min(RADIUS, min(panelHalf.x, panelHalf.y));
    float d = sdRoundBox(p, panelHalf, radius);
    float aa = max(fwidth(d), 1e-4);
    float mask = 1.0 - smoothstep(-aa, 0.0, d);

#ifdef GLASS_SHADOW
    // 与参考实现的 shape.frag 一致：投影轮廓取面板矩形，按偏移平移后做柔和衰减。
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

    // 折射 / 色散 / 中心色散 / 斜面宽度逐 draw 由顶点色传入，可以在运行时连续调节而
    // 不重编译管线。顶点色是 RGBA8_UNORM，每通道 8 位（1/255），精度足够调试。
    // 必须声明成函数内局部变量：全局变量的初始化器要求是常量表达式，而 vTuning 不是。
    // 编码见 GlassDebug.encode()：r = 折射/64，g = 色散，b = 中心色散/32，a = 斜面/64
    float REFRACTION = vTuning.r * 64.0;
    float DISPERSION = vTuning.g;
    float CENTER_DISPERSION = vTuning.b * 32.0;
    float BEVEL = max(vTuning.a * 64.0, 0.001);

    // 圆角矩形 SDF 的梯度就是向外的法线，用来做边缘弯折
    const float e = 0.5;
    vec2 gradient = vec2(
        sdRoundBox(p + vec2(e, 0.0), halfSize, radius) - sdRoundBox(p - vec2(e, 0.0), halfSize, radius),
        sdRoundBox(p + vec2(0.0, e), halfSize, radius) - sdRoundBox(p - vec2(0.0, e), halfSize, radius)
    );
    vec2 normal = gradient / max(length(gradient), 1e-5);

    float t = clamp(-d / max(BEVEL, 0.001), 0.0, 1.0); // 0 = 贴边，1 = 离开斜面
    float edge = 1.0 - t;
    float bend = edge * edge;                         // 中间平、边缘陡的折射曲线
    vec2 lens = -normal * bend * REFRACTION;          // 形状畸变：GUI 像素，向面板内部偏移

    // 色散位移由两部分相加：
    //   1) 边缘棱镜色散——乘在折射位移上，所以只在斜面范围内可见，超出就归零；
    //   2) 面板内横向色差——从面板中心沿径向线性增长（中心为 0），越靠外越强。
    // 第 2 项是真实镜头都有的性质，它让面板中部也有颜色分离，而不是只有边上一圈。
    vec2 radial = p / max(halfSize, vec2(1.0));       // 每个分量都在 -1..1
    vec2 spread = lens * DISPERSION + radial * CENTER_DISPERSION;

    // 底色贴图是 GL 语义：v = 0 在世界底部，而 GUI 的 y = 0 在顶部
    vec2 baseUv = vec2(vPos.x / guiSize.x, 1.0 - vPos.y / guiSize.y);
    vec2 lensUv = lens / guiSize;
    vec2 spreadUv = spread / guiSize;

    vec3 color;
    color.r = backdrop(baseUv + lensUv + spreadUv).r;
    color.g = backdrop(baseUv + lensUv).g;
    color.b = backdrop(baseUv + lensUv - spreadUv).b;

    // 饱和度提升 + 亮度封顶：保证雪地 / 天空前面文字依旧可读。
    // 封顶时只压缩亮度、保留原有彩度：按比例缩放会把通道差一起压掉（彩度减半），
    // 而室外画面几乎全程都在封顶，结果就是整体发灰。这里改成把亮度落到 MAX_LUMA、
    // 色度差原样保留——色度差的 luma 恒为 0，所以总亮度正好是 MAX_LUMA。
    color = max(mix(vec3(luma(color)), color, SATURATION), 0.0);
    float brightness = luma(color);
    if (brightness > MAX_LUMA) {
        color = max(vec3(MAX_LUMA) + (color - vec3(brightness)), 0.0);
    }
    color = mix(color, TINT.rgb, TINT.a);

    // 顶部柔光
    float fy = (p.y + halfSize.y) / max(size.y, 1.0);
    color += vec3(0.035 * (1.0 - fy) * HIGHLIGHT);

    // 斜面内侧的内发光
    float inner = pow(edge, 4.0) * 0.14;

    // 边缘镜面高光：左上打光，正面强、背面弱
    const vec2 LIGHT_DIR = vec2(-0.7307, -0.6828); // normalize(vec2(-0.55, -0.83))
    float facing = dot(normal, LIGHT_DIR);
    float lightAmount = max(facing, 0.0) + 0.35 * max(-facing, 0.0);
    float rim = 1.0 - smoothstep(0.0, 1.4, -d);
    color += vec3(rim * (0.10 + 0.75 * lightAmount) + inner * (0.25 + 0.75 * lightAmount)) * HIGHLIGHT;

    fragColor = vec4(clamp(color, 0.0, 1.0), mask * OPACITY);
#endif
}
