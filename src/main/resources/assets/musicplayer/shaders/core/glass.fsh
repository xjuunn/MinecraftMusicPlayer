#version 150

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

uniform float GlassRadius;
uniform vec4 GlassTint;
uniform float GlassMaxLuma;
uniform float GlassSaturation;
uniform float GlassHighlight;
uniform float GlassEdgeLight;
uniform float GlassOpacity;
uniform float GlassBlur;
uniform float GlassWindowTint;
uniform vec3 GlassWindowTintColor;
uniform float GlassShadowMode;
uniform float GlassShadowSize;
uniform float GlassShadowAlpha;
uniform vec2 GlassShadowOffset;
uniform float GlassShadowPadding;

in vec2 vPos;
in vec2 vLocal;
in vec4 vTuning;

out vec4 fragColor;

// —— 边缘高光的高斯 σ（GUI 像素）。σ 太大就糊成一整片白，太小就退化成 1px 硬描边。
const float EDGE_SIGMA = 3.0;
const float PI = 3.14159265;

float sdRoundBox(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

float luma(vec3 color) {
    return dot(color, vec3(0.2126, 0.7152, 0.0722));
}

// —— 随 draw 光学参数（32 位位域）——
//   [0..5]   折射      6 位，1px 步进 → 0..63 px
//   [6..11]  a 位置    6 位，1px 步进 → 0..63 px
//   [12..16] 反向倍率  5 位，/31×2 = 0..2
//   [17..21] 中心缩小  5 位，/31×24 = 0..24 px
//   [22..26] 边缘色散  5 位，/31 = 0..1
//   [27..31] 中心色散  5 位，1px 步进 → 0..31 px
struct Tuning {
    float refraction;
    float aDist;
    float contra;
    float shrink;
    float dispersion;
    float centerDisp;
};

Tuning unpackTuning(vec4 c) {
    // 必须用 uint 运算：最高位在有符号 int 下是符号位，右移会补符号位、取错位域。
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

// 背景透射与模糊：GlassBlur 是运行时 uniform，用连续 mix 而非编译期分支
vec3 backdrop(vec2 uv) {
    uv = clamp(uv, vec2(0.001), vec2(0.999));
    vec3 soft = texture(Sampler0, uv).rgb;
    vec3 frost = texture(Sampler1, uv).rgb;
    vec3 heavy = texture(Sampler2, uv).rgb;
    // 三层连续混合：GlassBlur = 0 时只剩纯降采样，1 时是重度模糊
    vec3 mid = mix(soft, frost, clamp(GlassBlur * 2.0, 0.0, 1.0));
    return mix(mid, heavy, clamp(GlassBlur * 2.0 - 1.0, 0.0, 1.0));
}

// 圆角矩形的解析梯度
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
    // 两者的偏导数之比就是面板的像素尺寸。
    // GUI 的 y 向下而 dFdy 沿窗口 y 向上，两个导数同为负号，必须都取绝对值。
    vec2 size = vec2(
        abs(dFdx(vPos.x)) / max(abs(dFdx(vLocal.x)), 1e-5),
        abs(dFdy(vPos.y)) / max(abs(dFdy(vLocal.y)), 1e-5)
    );
    size = max(size, vec2(1.0));

    vec2 halfSize = size * 0.5;
    vec2 p = (vLocal - 0.5) * size;

    // GUI 尺寸从 ProjMat 反推（正交投影）
    vec2 guiSize = vec2(2.0 / ProjMat[0][0], -2.0 / ProjMat[1][1]);
    if (guiSize.x < 1.0 || guiSize.y < 1.0) {
        guiSize = vec2(1920.0, 1080.0);
    }

    bool shadowMode = GlassShadowMode > 0.5;

    // 面板自身的 SDF 与覆盖度：玻璃和投影共用
    vec2 panelHalf = shadowMode ? max(halfSize - vec2(GlassShadowPadding), vec2(0.5)) : halfSize;
    float radius = min(GlassRadius, min(panelHalf.x, panelHalf.y));
    float d = sdRoundBox(p, panelHalf, radius);
    float aa = max(fwidth(d), 1e-4);
    float mask = 1.0 - smoothstep(-aa, 0.0, d);

    if (shadowMode) {
        // 投影轮廓取面板矩形，按偏移平移后做柔和衰减；
        // 再乘 (1 - mask) 抹掉面板自身覆盖的区域。
        vec2 shadowLocal = p - GlassShadowOffset;
        float shadowDistance = sdRoundBox(shadowLocal, panelHalf, radius);
        float shadowAlpha = (1.0 - smoothstep(0.0, GlassShadowSize, shadowDistance)) * GlassShadowAlpha;
        fragColor = vec4(0.0, 0.0, 0.0, clamp(shadowAlpha, 0.0, 1.0) * (1.0 - mask));
        return;
    }

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

    // 圆角矩形 SDF 的解析梯度 = 向外的法线
    vec2 normal = sdRoundBoxGradient(p, halfSize, radius);

    // 单调递增斜坡：折射与缩小共用一条 coreRamp，从 a 处的 0 平滑爬到中心的 1
    float shortHalf = max(min(halfSize.x, halfSize.y), 1.0);
    aPos = min(aPos, shortHalf * 0.5);
    float coreEnd = max(shortHalf, aPos + 1.0);
    float s = clamp((dist - aPos) / max(coreEnd - aPos, 0.001), 0.0, 1.0);
    float coreRamp = s * s * (3.0 - 2.0 * s);

    // 位移预算：任何面板都不许位移超过自身短边半尺寸的一定比例
    const float DISPLACEMENT_BUDGET = 0.55;
    float nominal = max(max(REFRACTION, SHRINK), 0.001);
    float budget = shortHalf * DISPLACEMENT_BUDGET;
    float dispScale = min(1.0, budget / nominal);
    REFRACTION *= dispScale;
    SHRINK *= dispScale;

    // 法线只在近边有意义：窗口固定收在圆角半径以内
    float windowWidth = clamp(radius * 0.8, aPos * 1.5, min(halfSize.x, halfSize.y) * 0.5);
    float normalWindow = 1.0 - smoothstep(0.0, 1.0, clamp(dist / max(windowWidth, 1.0), 0.0, 1.0));
    // 零向量兜底：y = 0 或 x = 0 时 sign 为 0，梯度会塌成 (0,0)
    normal = (dot(normal, normal) < 1e-6) ? vec2(0.0, 1.0) : normal;

    // 反向段：边框 → a，把背景向外推
    float bend;
    if (dist < aPos) {
        float u = dist / aPos;
        bend = -CONTRA * pow(1.0 - u, 1.6) * REFRACTION + coreRamp * REFRACTION;
    } else {
        bend = coreRamp * REFRACTION;
    }

    // 折射（沿法线）与缩小（沿径向）叠加，各自乘不同的窗口
    vec2 shrinkOffset = (p / max(halfSize, vec2(1.0))) * (SHRINK * coreRamp);
    vec2 lens = normal * (bend * normalWindow) + shrinkOffset;

    vec2 radial = p / max(halfSize, vec2(1.0));

    // 光学色散：棱镜色散乘在总位移上；面板内横向色差沿径向线性增长
    vec2 spread = lens * DISPERSION + radial * CENTER_DISPERSION;

    // 底色贴图是 GL 语义：v = 0 在世界底部，GUI 的 y = 0 在顶部
    vec2 baseUv = vec2(vPos.x / guiSize.x, 1.0 - vPos.y / guiSize.y);

    vec3 color;
    if (DISPERSION <= 0.0 && CENTER_DISPERSION <= 0.0) {
        color = backdrop(baseUv + lens / guiSize);
    } else {
        vec2 lensUv = lens / guiSize;
        vec2 spreadUv = spread / guiSize;
        color.r = backdrop(baseUv + lensUv + spreadUv).r;
        color.g = backdrop(baseUv + lensUv).g;
        color.b = backdrop(baseUv + lensUv - spreadUv).b;
    }

    // 饱和度提升 + 亮度封顶
    color = max(mix(vec3(luma(color)), color, GlassSaturation), 0.0);
    float brightness = luma(color);
    if (brightness > GlassMaxLuma) {
        color = max(vec3(GlassMaxLuma) + (color - vec3(brightness)), 0.0);
    }
    color = mix(color, GlassTint.rgb, GlassTint.a);

    // 边缘高光：H(p) = e^(-d²/2σ²)，乘法线的方向性调剂
    float edgeH = exp(-(dist * dist) / (2.0 * EDGE_SIGMA * EDGE_SIGMA));
    float upward = clamp(-normal.y, 0.0, 1.0);
    float directional = 0.35 + 0.65 * upward;
    color += vec3(edgeH * directional * GlassEdgeLight);

    // 顶部柔光
    float fy = (p.y + halfSize.y) / max(size.y, 1.0);
    color += vec3(0.035 * (1.0 - fy) * GlassHighlight);

    // 把所在的窗口当作背景（子控件用）
    color = mix(color, GlassWindowTintColor, GlassWindowTint);

    fragColor = vec4(clamp(color, 0.0, 1.0), mask * GlassOpacity);
}
