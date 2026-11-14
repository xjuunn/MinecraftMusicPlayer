#version 330
#extension GL_ARB_separate_shader_objects : require

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec4 Color;

// 屏幕坐标（GUI 像素，左上原点）
layout(location = 0) out vec2 vPos;
// 面板内归一化坐标：0 在左上角，1 在右下角
layout(location = 1) out vec2 vLocal;
// 四项调参：折射 / 色散 / 中心色散 / 斜面宽度。
// 界面渲染路径拿不到 RenderPass，没法绑定逐 draw 的 uniform；而改 shader define
// 等于重新编译管线。顶点色是格式里现成且未被使用的通道，正好用来逐 draw 传参。
// 编码见 GlassDebug.encode()。
layout(location = 2) out vec4 vTuning;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vPos = Position.xy;
    vLocal = UV0;
    vTuning = Color;
}
