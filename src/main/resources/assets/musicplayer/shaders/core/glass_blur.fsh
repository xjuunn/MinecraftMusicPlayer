#version 150

uniform sampler2D Sampler0;

// x = 源贴图宽，y = 源贴图高
uniform vec2 SamplerSize;
// x = 高斯扩散（texel），y = 1.0 表示纵向模糊
uniform vec2 SpreadVertical;

in vec2 vUv;

out vec4 fragColor;

// 9 抽头高斯拆成 5 次采样：线性过滤让相邻两个抽头一次取到（权重已合并）
const float WEIGHT_CENTER = 0.2270270270;
const float WEIGHT_NEAR = 0.3162162162;
const float WEIGHT_FAR = 0.0702702703;
const float OFFSET_NEAR = 1.3846153846;
const float OFFSET_FAR = 3.2307692308;

void main() {
    vec2 texel = 1.0 / max(SamplerSize, vec2(1.0));

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
