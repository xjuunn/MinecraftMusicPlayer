#version 150

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

// 屏幕坐标（GUI 像素，左上原点）
out vec2 vPos;
// 面板内归一化坐标：0 在左上角，1 在右下角
out vec2 vLocal;
// 四项调参：折射 / 色散 / 中心色散 / 斜面宽度等。
// 界面渲染路径拿不到 per-draw uniform；顶点色是格式里现成且未被使用的通道，
// 正好用来随 draw 传参（编码见 GlassDebug.encode()）。
out vec4 vTuning;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vPos = Position.xy;
    vLocal = UV0;
    vTuning = Color;
}
