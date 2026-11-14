#version 330
#extension GL_ARB_separate_shader_objects : require

#moj_import <minecraft:projection.glsl>

layout(location = 0) in vec2 vPos;
layout(location = 1) in vec2 vLocal;
layout(location = 2) in vec4 vColor;

layout(location = 0) out vec4 fragColor;

const vec3 FILL = vec3(GLASS_WIDGET_FILL_R, GLASS_WIDGET_FILL_G, GLASS_WIDGET_FILL_B);
const vec3 BORDER = vec3(GLASS_WIDGET_BORDER_R, GLASS_WIDGET_BORDER_G, GLASS_WIDGET_BORDER_B);
const float RADIUS = float(GLASS_WIDGET_RADIUS);
const float GLOSS = GLASS_WIDGET_GLOSS;
const float BORDER_ALPHA = GLASS_WIDGET_BORDER_A;

float sdRoundBox(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

void main() {
    // GUI 的 y 向下、dFdy 向上，导数取绝对值即可得到像素尺寸
    vec2 size = vec2(
        abs(dFdx(vPos.x)) / max(abs(dFdx(vLocal.x)), 1e-5),
        abs(dFdy(vPos.y)) / max(abs(dFdy(vLocal.y)), 1e-5)
    );
    size = max(size, vec2(1.0));

    vec2 halfSize = size * 0.5;
    vec2 p = (vLocal - 0.5) * size;
    float radius = min(RADIUS, min(halfSize.x, halfSize.y));

    float dist = sdRoundBox(p, halfSize, radius);
    // 屏幕空间中 SDF 每前进一个像素的变化量：抗锯齿与描边宽度的唯一正确依据
    float aa = max(length(vec2(dFdx(dist), dFdy(dist))), 1e-4);

    float fillMask = 1.0 - smoothstep(-aa, aa, dist);
    // 贴边内描边，恒定 1 像素
    float borderMask = 1.0 - smoothstep(0.35 * aa, 0.65 * aa, abs(dist + 0.5 * aa));

    // 顶部柔光：macOS 控件的微妙纵向渐变
    float grad = clamp((p.y + halfSize.y) / max(size.y, 1.0), 0.0, 1.0);
    vec3 fill = FILL + vec3(GLOSS * (1.0 - grad));
    vec3 rgb = mix(fill, BORDER, borderMask);
    // 描边极淡，只作为边缘收口的暗示
    float alpha = max(fillMask, borderMask * BORDER_ALPHA * 1.6) * vColor.a;
    fragColor = vec4(rgb, alpha);
}
