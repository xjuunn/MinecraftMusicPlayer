#version 330
#extension GL_ARB_separate_shader_objects : require

// 全屏三角形/四边形：顶点直接就是 NDC，不需要投影矩阵
layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;

layout(location = 0) out vec2 vUv;

void main() {
    gl_Position = vec4(Position.xy, 0.0, 1.0);
    vUv = UV0;
}
