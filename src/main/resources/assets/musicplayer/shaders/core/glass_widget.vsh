#version 150

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

// 屏幕坐标（GUI 像素，左上原点）
out vec2 vPos;
// 控件内归一化坐标：0 在左上角，1 在右下角
out vec2 vLocal;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vPos = Position.xy;
    vLocal = UV0;
}
