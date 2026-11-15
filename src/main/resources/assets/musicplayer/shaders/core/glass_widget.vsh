#version 330
#extension GL_ARB_separate_shader_objects : require

// 1.20.1 没有 dynamictransforms.glsl，这两个矩阵由原版作为全局 uniform 提供，
// 在本版本必须自行声明（1.21.1 起才有对应的 include 可用）。
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec4 Color;

// 屏幕坐标（GUI 像素，左上原点）
layout(location = 0) out vec2 vPos;
// 控件内归一化坐标：0 在左上角，1 在右下角
layout(location = 1) out vec2 vLocal;
// 填充色（RGBA 逐 draw 传入）
layout(location = 2) out vec4 vColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vPos = Position.xy;
    vLocal = UV0;
    vColor = Color;
}
