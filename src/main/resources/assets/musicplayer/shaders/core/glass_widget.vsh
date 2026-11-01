#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

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
