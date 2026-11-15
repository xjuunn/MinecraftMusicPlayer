#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D Sampler0;

// x = 高斯扩散（texel），y = 1.0 表示纵向模糊。
// 26.x 用 GLASS_BLUR_VERTICAL / GLASS_BLUR_SPREAD 两个 define 拆成两份管线，
// 1.20.1 单管线只能走运行时分支，由 Java 侧每次 draw 前写入。
uniform vec2 SpreadVertical;

layout(location = 0) in vec2 vUv;

layout(location = 0) out vec4 fragColor;

// 9 抽头高斯拆成 5 次采样：线性过滤让相邻两个抽头一次取到（权重已合并）
const float WEIGHT_CENTER = 0.2270270270;
const float WEIGHT_NEAR = 0.3162162162;
const float WEIGHT_FAR = 0.0702702703;
const float OFFSET_NEAR = 1.3846153846;
const float OFFSET_FAR = 3.2307692308;

void main() {
    vec2 texel = 1.0 / vec2(textureSize(Sampler0, 0));

    // spread = 0 时五次采样重合，权重和为 1，等价于一次双线性降采样
    vec2 dir = SpreadVertical.y > 0.5
            ? vec2(0.0, texel.y) * SpreadVertical.x
            : vec2(texel.x, 0.0) * SpreadVertical.x;

    vec3 color = texture(Sampler0, vUv).rgb * WEIGHT_CENTER;
    color += texture(Sampler0, vUv + dir * OFFSET_NEAR).rgb * WEIGHT_NEAR;
    color += texture(Sampler0, vUv - dir * OFFSET_NEAR).rgb * WEIGHT_NEAR;
    color += texture(Sampler0, vUv + dir * OFFSET_FAR).rgb * WEIGHT_FAR;
    color += texture(Sampler0, vUv - dir * OFFSET_FAR).rgb * WEIGHT_FAR;

    fragColor = vec4(color, 1.0);
}
