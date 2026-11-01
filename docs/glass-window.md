# 液态玻璃窗口框架

`client/gui/window` 提供一套可复用的液态玻璃窗口：定位、尺寸、开场动画、内容控件跟随
全部封装好，新界面只需要声明配置和摆控件。

底层渲染（折射、色散、模糊、投影）在 `client/gui/glass`，本框架只负责"窗口应该在哪、
多大、这一帧长什么样"，不碰着色器。

---

## 快速上手

```java
public final class MyScreen extends GlassWindowScreen {

    public MyScreen() {
        super(new GlassWindow()
                .style(GlassTheme.windowStyle())   // 窗口玻璃材质（见下文"窗口材质"）
                .size(WindowSize.preferred(640, 420, 32))
                .anchor(WindowAnchor.CENTER)
                .opening(WindowAnimations.elasticPop()));
    }

    @Override
    protected void buildContent(WindowBounds content) {
        // 每次构建重挂当前外观的窗口材质，切主题后 rebuildWidgets() 即完成换肤
        window().style(GlassTheme.windowStyle());

        addRenderableWidget(GlassButton.regular(content.x0() + 24, content.y0() + 24, 96,
                Component.literal("确定"), this::onClose));
    }
}
```

## 玻璃控件（macOS/iOS 风格）

`client/gui/glass/widget` 是不依赖原版 Button/Slider 视觉的控件，尺寸、配色、动效按
macOS HIG 缩小到与 GUI 字体匹配的比例。**按钮、开关、滑块圆珠都是真正的液态玻璃**，
采样背景做折射与色散，不是纯色圆角块：

| 控件 | 尺寸 | 说明 |
|---|---|---|
| `GlassButton` | 高 16（大号 22） | 玻璃片，`Kind.PLAIN` 原色、`Kind.PROMINENT` 叠系统蓝 |
| `GlassSlider` | 滑轨 3、圆珠 12 | 圆珠是玻璃珠，hover/拖拽连续放大，值在右侧淡显 |
| `GlassToggle` | 32×18 | iOS 开关，圆点是玻璃珠，开启时底槽渐染系统蓝 |

文字统一用 `GlassTheme.Text` 的缩放比（控件 0.75、标签 0.8、数值 0.7），
通过 `pose` 缩放实现，不改变命中区。

```java
addRenderableWidget(GlassButton.large(x, y, 120, Component.literal("刻录"), this::burn,
        GlassButton.Kind.PROMINENT));
addRenderableWidget(GlassSlider.regular(x, y, 200, volume, v -> setVolume(v), null));
addRenderableWidget(new GlassToggle(x, y, enabled, this::apply));
```

实现要点：

- 按钮与开关继承 `AbstractButton`，其 `extractWidgetRenderState` 只调 `extractContents`，
  可访问性、回车/空格、点击音效全部保留，视觉完全自绘。
- 滑块覆写 `extractWidgetRenderState`，拖拽映射与键盘步进沿用 `AbstractSliderButton`。
- 控件玻璃走 `GlassWidgets.glassBackground` → `GlassPanel.drawTuned`。**关键约束**：
  `GlassStyle` 的参数多半在编译期烘焙进管线，只有折射、边缘色散、中心色散、
  a 位置、反向畸变率、中心缩小这六项逐 draw 经顶点色传入，
  所以动画只能改几何尺寸和这六项，否则每帧重编译会卡死。
- `GlassTheme.BUTTON` / `TOGGLE` / `KNOB` 的 `radius` 都写成 32，而着色器取
  `min(RADIUS, 半宽, 半高)`：小控件上是圆角矩形、圆珠上自动变正圆，
  **一份管线同时覆盖三种形状**，动画改尺寸也不需要重编译。
- `GlassWidgets.roundedFill` 只作为玻璃不可用时的退化路径（SDF 抗锯齿用
  `dFdx/dFdy` 求出的屏幕像素宽度做过渡带宽，固定 0.5px 会在 GUI 缩放时出现锯齿）。

### 动效

`GlassWidgets.Spring` 提供两种收敛：

| 方法 | 曲线 | 用途 |
|---|---|---|
| `tick(active, tau)` | 指数收敛，不过冲 | 亮度、开关染色 |
| `tickSpring(active, tau)` | 二阶，阻尼比 0.62，轻微回弹 | 按下、圆点滑动 |

两者都是帧率无关的，掉帧不会变速。`tau` 见 `GlassTheme.Motion`：
hover 0.055s、按下 0.035s、松开回弹 0.075s、开关滑动 0.075s。

具体动效：

- **hover**：边缘折射加强 45%、色散加强 30%、中心色散加强 40%，像玻璃被照亮。
- **按下**：整体缩到 97%、折射收到 65%，文字压暗 10%，松手回弹到原状。
- **开关**：圆点带过冲滑过行程，底槽染色同时渐染系统蓝；圆点还会沿运动方向被拉长
  （由它自身的 `slide.velocity()` 驱动，点击瞬间拉伸、到位自动复原，不需要额外计时器）。

> 切换开关状态时**不要调 `rebuildWidgets()`**。重建控件会重新执行构造函数里的
> `slide.snap()`，把圆点直接放到终点，过渡动画根本来不及播——这正是之前"点了没动画"的原因。
> 状态由控件自己持有、由 `on` 字段驱动弹簧即可。

---

## 布局组件

`client/gui/glass/layout/GlassLayout` 提供纵向堆栈与横向行，先声明、后统一放置：

```java
GlassLayout.Column column = new GlassLayout.Column().gap(6);
for (Tuning tuning : Tuning.values()) {
    column.fill(20, slot -> addRenderableWidget(new TuningSlider(slot, tuning)));
}
column.fill(GlassTheme.Sizes.CONTROL, slot -> addRenderableWidget(
        GlassButton.regular(slot.x(), slot.y(), slot.width(), label, action)));
column.layout(content);
```

`Column` 支持 `align(START/CENTER/END)`、`add(宽, 高, …)` 与 `fill(高, …)`（铺满）；
`Row` 支持 `grow(高, …)` 占满剩余宽度，`align(START/CENTER/END)` 纵向对齐，适合
「标签 + 控件」并排。每个槽位是 `GlassLayout.Slot(x, y, width, height)`，另带
`right()/bottom()/centerX()/centerY()` 与 `toBounds()`。

---

## 二层玻璃卡片

窗口内的第二层玻璃容器是 `GlassCard`（`client/gui/glass/widget/GlassCard`）。
它既是绘制单元、又是布局容器：负责画玻璃底、可选标题，并把扣掉内边距后的区域
交给内容回调继续布局。

### 左右两栏

```java
GlassCard left = GlassCard.create()
        .title("播放列表")
        .padding(12)
        .content(area -> { /* 用 GlassLayout 在 area 里摆控件 */ });
GlassCard right = GlassCard.create().title("详情").padding(12);

AtomicReference<GlassLayout.Slot> leftSlot = new AtomicReference<>();
AtomicReference<GlassLayout.Slot> rightSlot = new AtomicReference<>();
new GlassLayout.Row()
        .gap(12)
        .add(160, content.height(), leftSlot::set)     // 左栏固定 160
        .grow(content.height(), rightSlot::set)        // 右栏吃掉剩余宽度
        .layout(content);

left.bounds(leftSlot.get());
right.bounds(rightSlot.get());
addRenderableWidget(left);       // 卡片先注册 → 玻璃底在下
addRenderableWidget(right);
left.layoutContent();            // 内容控件后注册 → 叠在玻璃之上
right.layoutContent();
```

### 为什么是「定位 → 注册 → 排内容」三步

`GlassWindowScreen` 在 `addRenderableWidget` 那一刻就把控件当时的坐标记成**动画基准**。
若先注册再定位，基准会停在 `(0,0)`，窗口开场的平移 / 缩放会把卡片拽到左上角。
所以拆成 `bounds(...)`（只定位）和 `layoutContent()`（只排内容）两个方法。
`layout(slot)` 是两者合一的便捷写法，只在卡片已注册时才可直接用。

### 材质

`GlassTheme.CARD` 是专用材质，**不能直接复用窗口材质**：玻璃叠玻璃会把折射重复施加
两次，中心区域糊成一团。卡片把折射与中心收缩收到窗口的四成左右、色散减半。

卡片通过 `GlassPanel.drawTuned` 绘制，用的是 `childLook`（带窗口叠加浓度），
所以它折射的是"世界背景 + 窗口那层浅色叠加"，看起来嵌在窗口里而不是浮在外面。

### 圆角必须与容器同心

卡片贴着窗口边缘时，两者的圆角若不同心，角落处会露出两条错开的弧线，看起来就是
"窗口角和卡片角圆角不一致"。同心条件是：

```
卡片圆角 = 窗口圆角 − 卡片到窗口的边距
```

因为外层圆心在窗口角的 `(半径, 半径)`，内层圆心在卡片的 `(半径, 半径)`；
只有上式成立，两个圆心才落在同一点。

不要硬编码这个值。用 `container(...)` 把容器矩形和半径交给卡片，它会按
**实际几何**四边取最小距离反推：

```java
// 容器矩形由内容区外扩 PADDING 反推回窗口整体
WindowBounds windowBounds = new WindowBounds(
        content.x0() - PADDING, content.y0() - PADDING,
        content.x1() + PADDING, content.y1() + PADDING);
float windowRadius = GlassTheme.windowStyle().radius();

left.container(windowBounds, windowRadius);
right.container(windowBounds, windowRadius);
```

这样以后改 `PADDING` 或窗口圆角都不会错位。取**最小**距离而不是固定边距，
是因为卡片可能被放进任意布局，四边到容器的距离未必相同；取最小可保证无论
卡片贴哪一边都不超出容器圆角。

玻璃与叠加层共用同一个解析后的半径（`style.withRadius(radius)` 派生材质），
否则两者轮廓对不上，会多出一圈色块。

### 底色

`tint` 默认 `0.12`，**刻意不跟随全局透明度配置**：该配置默认是 0，若卡片也乘同一个
系数就会完全没有底色，只剩折射，与窗口表面分不开。传 `tint(0)` 可让它完全透明。

卡片的圆角与叠加层共用同一套夹取规则（`min(半径, 半宽, 半高)`），与着色器一致，
不会多出一圈形状对不上的色块。

---

## 窗口材质与外观开关

只有一套外观：**永远白色主题、白色文字**，不随背景明暗切换。透明度与模糊作用于
**窗口与全部控件**，投影只作用于窗口：

```java
GlassTheme.opacity();            // 背景不透明度 0..1，0 = 完全透明
GlassTheme.blur();               // 背景模糊度 0..1，0 = 完全没有模糊
GlassTheme.shadowEnabled();      // 窗口底部投影开关
GlassTheme.setBlur(0.0F);        // 对应的 setter 都会 markDirty
```

### 配置持久化

所有玻璃外观参数都存在模组统一配置文件里，重启后保留：

```json
"glass": {
  "opacity": 0.35, "blur": 1.0, "shadow": true,
  "refraction": 32.0, "dispersion": 0.85, "centerDispersion": 4.0,
  "aDistance": 8.0, "contraRatio": 1.2, "shrink": 11.0
}
```

- 读写门面：`GlassDebug`（6 项光学参数）+ `GlassTheme`（透明度 / 模糊 / 阴影）。
- 一键恢复：界面里的「恢复默认并重播动画」调用 `GlassDebug.reset()`，
  等价于 `MusicPlayerConfigManager.resetGlass()`，**只重置这 9 项玻璃外观**，
  不动音量等其他客户端设置，并立即落盘。
- 6 项光学参数走顶点色逐 draw 传入、**不参与管线定义**，所以拖动滑块不会重编译着色器。

> **只有窗口读这些配置。** `GlassDebug.refraction(style)` 这类方法会先判断传入材质
> 是不是 `GlassStyle.WINDOW`：只有窗口采用配置值，按钮 / 开关 / 圆珠仍用各自材质里
> 烘焙的数值。早期是全局静态覆盖、不分材质，把窗口的 `refraction=32` 一并套到按钮上，
> 而按钮短边半尺寸只有 8px，位移达 400% 半高，导致中心重叠、颜色发浑。

边缘高光已按 Apple 文档重新实现（见下文"四条核心原理"），但**不是一圈均匀白框**：
它是贴着轮廓的高斯衰减，并乘法线的方向性调制，上缘明显亮于下缘。早期那三套无差别的
加白处理（`BORDER` 圆环、`rim` 镜面边、`inner` 斜面内发光）确实已全部移除——它们
才是"关掉白框后边缘仍有一条近白细边"的原因。

## 四条核心原理

实现按 [Apple 官方 Liquid Glass 技术总览][apple-lg] 与
[HIG Materials][apple-hig] 归纳的四条原理组织，每条都对应着色器里的一段明确代码：

| 原理 | 着色器实现 | 参数 |
|---|---|---|
| 背景透射与模糊 | 三层降采样贴图连续混合，`C_out = α·C_glass + (1−α)·C_blur` | `BLUR`、`TINT`、`OPACITY` |
| 边缘高光与轮廓 | `H(p) = e^(−d²/2σ²)` × 法线方向性调制 | `EDGE_LIGHT`、`EDGE_SIGMA` |
| 光学响应 | 屏幕空间折射（SDF 梯度当法线扰动 UV）+ 棱镜色散 | `REFRACTION`、`DISPERSION` |
| 动态形变 | Java 侧二阶弹簧（阻尼 0.62 带过冲） | `GlassTheme.Motion` |

[apple-lg]: https://developer.apple.com/documentation/TechnologyOverviews/liquid-glass
[apple-hig]: https://developer.apple.com/design/human-interface-guidelines/materials

### 背景透射与亮度调整

HIG 对 `regular` 变体的定义是"模糊**并调整**背景亮度以维持文字可读性"，所以透射之后
还要做两步，都直接服务于前景白字的对比度：

```glsl
color = max(mix(vec3(luma(color)), color, SATURATION), 0.0);   // vibrancy
float b = luma(color);
if (b > MAX_LUMA) { color = max(vec3(MAX_LUMA) + (color - vec3(b)), 0.0); }
color = mix(color, TINT.rgb, TINT.a);                          // α 混合
```

封顶时**只压亮度、保留彩度**。若按比例整体缩放，通道差会一起被压掉（彩度减半），
而室外画面几乎全程都在封顶，结果就是整体发灰。

HIG 还规定 `clear` 变体在亮背景上要叠 **35% 的暗化层**；本项目只用 `regular` 语义，
由 `MAX_LUMA` 承担同样的可读性职责。

### 边缘高光：方向性，不是白框

```glsl
float edgeH = exp(-(dist * dist) / (2.0 * EDGE_SIGMA * EDGE_SIGMA));
float upward = clamp(-normal.y, 0.0, 1.0);      // GUI 的 y 向下，取负得"朝上"
float directional = 0.35 + 0.65 * upward;
color += vec3(edgeH * directional * EDGE_LIGHT);
```

`σ = 3px` 时半高宽 3.55px，是柔和的一圈光而不是 1px 描边。方向性来自表面法线——
上缘接收上方光源的反射最强，实测上/下缘亮度比 **2.86 : 1**。这是液态玻璃最标志性的
"空间边界"感：边缘比内部亮，且亮得有方向。

早期用 4 次额外 SDF 求差分法线，现在改成**解析梯度**，省掉 4 次 SDF 求值：

```glsl
vec2 q = abs(p) - b + r;
if (q.x > 0.0 && q.y > 0.0) return sign(p) * q / length(q);   // 圆角区
return q.x > q.y ? vec2(sign(p).x, 0.0) : vec2(0.0, sign(p).y); // 直边带
```

这条规则对形状内外都成立，因为它只依赖 SDF 自身分量的符号。实测在 7 组典型尺寸
（含 400×300 窗口、7×7 正圆、16×8 胶囊）上，与数值梯度的最大偏差仅出现在角点接缝
的零宽线上，其余全部小于 0.001。

### 性能

| 优化 | 效果 |
|---|---|
| 解析梯度 | 每个像素省 4 次 SDF 求值 |
| 静态模糊分支 | `blur=0` 时采样数 9 → 3；三层混合原本每次色散采样都要采满三张图 |
| 无色散快路径 | 三通道采样点相同时只采一次，9 → 3 |
| 管线档位复用 | 模糊 8 档、窗口浓度 32 档，参数走顶点色不重编译 |


### 模糊度：四层 + 原图

`GlassBackdrop` 每帧产出四张背景层：

| 层 | 分辨率 | 处理 | 用途 |
|---|---|---|---|
| `sharp` | 1:1 | 纯拷贝 | 模糊度 = 0，逐像素还原 |
| `soft` | 1/2 | 1 次可分离模糊 | 低模糊 |
| `frost` | 1/4 | 2 次 | 中模糊 |
| `heavy` | 1/8 | 3 次 | 高模糊 |

着色器在 soft → frost → heavy 之间按 `BLUR` 连续插值，所以档位之间没有硬跳。
模糊度为 0 时 `Sampler0` 直接换成 `sharp`，是真正的「完全没有模糊，只剩折射畸变」；
若仍用 1/2 层，画面已经过 2×2 平均，算不上零模糊。

### 模糊度为什么是编译期常量

界面渲染路径拿不到 `RenderPass`，无法绑定逐 draw 的 uniform；而顶点格式只有一路 color，
7 个光学参数已经把它占满，没有通道再传模糊度。

所以模糊度量化成有限档位（`GlassLook`，8 档），同档共用一份管线。拖滑块时最多编译
八九份，之后全部命中缓存——这是在不重编译每帧的前提下能做到的上限。

### 子控件把窗口当作背景

按钮、滑块、开关在视觉上都位于窗口玻璃**之内**。如果它们直接采样世界背景，看到的就是
裸的世界画面，会像浮在窗口外面。所以子控件采样后还要再合成一层「窗口叠加层」：

```glsl
color = mix(color, WINDOW_TINT_COLOR, WINDOW_TINT);   // 窗口自身 WINDOW_TINT = 0
```

`WINDOW_TINT` 来自 `GlassLook.windowTint`，由 `GlassPanel.setLook` 从
`GlassTheme.overlayAlpha()` 同步过来，所以拖动透明度滑块时窗口和控件一起变。

三个必须守住的细节：

- **合成位置要在最后**，和窗口真实的绘制顺序一致——窗口的 `overlayFill` 是在玻璃全部处理完
  （饱和度、亮度封顶、tint、顶部柔光）之后才盖上去的。若把合成提前到封顶之前，
  明亮或高饱和区域会出现一圈可见色差。
- **控件的 `overlayFill` 不能再叠整份窗口浓度**，否则是双重染色、控件会比窗口白一大截。
  着色器已经负责底色，那一层只保留 hover / press 的微小增量。
- **量化档数要细**：`TINT_STEPS = 32`。这一项决定控件底色的"白"程度，实测 8 档在
  `opacity = 0.35` 时最坏差 11.9/255（肉眼可见），32 档降到 3.9/255，已无法分辨。
  档数多也不会频繁重编译——它只随透明度滑块变化，不是逐帧量。

叠加层颜色通过 `GLASS_WINDOW_TINT_R/G/B` 三个 define 从 `GlassTheme.overlayColorRgb()`
注入，和 Java 侧同源，不会因为改了颜色而漏掉另一处。

### 六个光学参数怎么塞进一路顶点色

顶点色是 `RGBA8_UNORM`，也就是 4 字节 / 32 位。6 个参数按位域分配：

| 字段 | 位 | 宽度 | 范围 |
|---|---|---|---|
| 折射 | 0..5 | 6 | 1px 步进 → 0..63 px |
| a 位置 | 6..11 | 6 | 1px 步进 → 0..63 px |
| 反向畸变率 | 12..16 | 5 | /31×2 → 0..2 |
| 中心缩小 | 17..21 | 5 | /31×24 → 0..24 px |
| 边缘色散 | 22..26 | 5 | /31 → 0..1 |
| 中心色散 | 27..31 | 5 | 1px 步进 → 0..31 px |

打包在 `GlassDebug.pack()`，解包在 `glass.fsh` 的 `unpackTuning()`。两处必须同步改。

**6 个字段刚好用满 32 位**（全满 = `0xFFFFFFFF`）。移除 `b` 位置后省出的位用来提精度：
`a 位置` 从 2px 步进提到 **1px**、`中心色散` 从 2px 步进提到 **1px**。
默认值因此可以精确编码：`aDistance = 5` 不再被四舍五入成 6，`centerDispersion = 6.4` 编码为 6。

剩余误差只来自位宽本身，无法再压（界面路径拿不到逐 draw uniform）：

```
contraRatio  0.62  → 0.6452   (步进 0.0645)
shrink       7.34  → 6.9677   (步进 0.774)
dispersion   0.65  → 0.6452   (步进 0.0323)
```

> 两个易错点：Java 侧必须用 `long` 打包（最高位左移会把 int 的符号位一起带进来，
> 污染后面所有字段）；GLSL 侧必须用 `uint` 解包（有符号 int 的右移是算术右移，会补符号位）。

### 为什么染色走叠加层而不是 tintA

`tintA` 同样是**编译期**烘焙进管线的，直接改它每拖一格都会编译出新管线（卡顿 + 泄漏着色器）。
所以 `GlassTheme.BUTTON` / `TOGGLE` / `KNOB` 的 `tintA` 全部固定为 **0**，玻璃只做纯折射；
染色改由 `GlassWidgets` 在玻璃**之上**叠一层顶点色圆角填充（`overlayAlpha()`），
顶点色逐 draw 传入，拖动滑块不触发任何重编译，窗口和控件因此天然同步。

叠加层不透明度是 `0.92 * opacity^1.6`：低端几乎不变，拉满时接近实色的浅色面板。

> 注意：自「子控件把窗口当作背景」之后，**控件的这一层叠加只剩很小的交互增量**，
> 不再是整份 `overlayAlpha()`。否则着色器里合成了窗口浓度、这里又叠一份，就是双重染色。

### 文字颜色固定为白色

```java
GlassTheme.TEXT;            // 0xFFFFFFFF
GlassTheme.TEXT_SECONDARY;  // 0xE6FFFFFF，次级文字
```

早期版本会按背景亮度在深色/浅色之间自动反色，那套逻辑（含 `GlassBackdrop` 的
GP﻿U 亮度探针）已经整个删除——探针每帧都要做一次显存读回，现在不再需要了。

强调蓝 `#007AFF` 同样固定。

### 边缘畸变：反向段 + 一条单调过渡

面板从边框到中心只有**一个**可调边界 `a`：

```
边框 ────── a ──────────────────────── 中心
  └─反向─┘   └──── 单调递增的折射与缩小 ────┘
```

```glsl
float dist = max(-d, 0.0);                      // 到边框的距离
float aPos = min(max(A_DIST, 0.001), shortHalf * 0.5);   // 随面板缩放
float shortHalf = max(min(halfSize.x, halfSize.y), 1.0);
float coreEnd = max(shortHalf, aPos + 1.0);
float s = clamp((dist - aPos) / max(coreEnd - aPos, 0.001), 0.0, 1.0);
float coreRamp = s * s * (3.0 - 2.0 * s);       // smoothstep，两端零斜率

// 位移预算：绝对像素参数在小面板上必须收敛
float nominal = max(max(REFRACTION, SHRINK), 0.001);
float dispScale = min(1.0, shortHalf * 0.55 / nominal);
REFRACTION *= dispScale;
SHRINK *= dispScale;

float bend;
if (dist < aPos) {
    // 边框 → a：反向，贴边最强，到 a 归零
    bend = -CONTRA * pow(1.0 - dist / aPos, 1.6) * REFRACTION + coreRamp * REFRACTION;
} else {
    bend = coreRamp * REFRACTION;
}
vec2 lens = normal * (bend * normalWindow)
          + (p / max(halfSize, vec2(1.0))) * (SHRINK * coreRamp);
```

#### 小面板必须收敛：按钮中心重叠的成因

折射与缩小都是**绝对像素**参数，照搬到小控件上会失控。按钮高 16px（短边半尺寸 8），
若沿用窗口预设 `REFRACTION=32`，贴边采样点会外移 **32px = 半高的 400%**，被推到按钮外
的背景里；上下两边同时这样推，中心区域就被两侧的外部背景覆盖 —— 表现为**中心重叠、颜色发浑**。

两处修正：

1. **位移预算**：`dispScale` 把 `REFRACTION` 与 `SHRINK` 等比压到短边半尺寸的 55% 以内。
   基数取 `max(REFRACTION, SHRINK)` 而非两者之和 —— 折射在贴边处达峰值、缩小在中心达峰值，
   **峰值位置不同，不会叠加**。这个 scale 对每个面板是常数，不随位置变化，不引入新断裂。
2. **按各轴归一化**：缩小项写成 `(p / halfSize) * SHRINK`，不能写成 `p * (SHRINK / shortHalf)`。
   后者用短边归一化会把长轴位移放大 `bx / shortHalf` 倍（按钮 100×16 就是 6.25 倍）。

实测（位移占短边半尺寸比例，应 ≤55%）：

```
面板            scale   贴边折射        中心缩小
WINDOW 300x200  1.00    32.0px =  32%   11.0px = 11%
SIDEBAR         1.00    16.0px =  27%    7.0px = 12%
BUTTON 默认      1.00     3.0px =  38%    3.5px = 44%
BUTTON 被覆盖    0.14     4.4px =  55%    1.5px = 19%
TOGGLE          1.00     3.0px =  33%    3.5px = 39%
KNOB            1.00     2.0px =  33%    2.5px = 42%
```

正常配置的控件 `scale` 恒为 1，**完全不受影响**；只有被调试面板全局覆盖值放大的小控件
才会自动收敛。

> `GlassDebug` 的 6 个覆盖值是**全局静态字段**，不区分 style。在调试面板里为窗口调出的
> `REFRACTION` 会同时套到按钮、开关、圆珠上，这正是本问题的触发场景。

#### 为什么不能用钟形曲线

早期用 `sin(u·π)`、后来用 `sin²(s·π)` 做"正向折射段"，两者都是**钟形**：峰值落在区间
中点，越过峰值后强度一路上衰减、到中心归零。这带来一个无法靠调参解决的缺陷：

> 把折射调高只是把那个鼓包抬高，鼓包峰值处就成了折射与缩小的分界，
> 于是能看到一个**突变点**。

真实厚玻璃是**整块**在当透镜，越靠中心越均匀，折射不该在中心消失。所以改成
**单调递增的 `smoothstep`**：折射与缩小共用同一条 `coreRamp`，从 `a` 处的 0 平滑爬到
中心的 1，**全程单调、没有内部极值**——结构上不可能再产生分界点。

这也顺带删掉了两样东西：`b` 位置参数，以及为了压制 `b` 处斜率而存在的
`(1−u)²` 权重和 `smoothstep(0.55, 1.0, u)` 这个魔法阈值。

实测（`a=8, REFRACTION=32, CONTRA=1.2, SHRINK=11, 短边半尺寸=100`）：

```
dist     0.0    4.0   7.99    8.0    20.0    40.0    60.0    80.0    99.0   100.0
bend   -38.4  -12.7  -0.001  0.000   1.49    8.92   19.11   28.12   31.99   32.00
ramp     0.0    0.0    0.0    0.0   0.047   0.279   0.597   0.879   1.000   1.000
```

- 单调递增，**无内部峰值**（这就是"没有突变点"的保证）。
- `a` 处跳变 **0.000022**，两侧斜率都归零。
- 中心处折射 = **32px 满幅**，不再归零。

参数：

- `a` 位置（`A_DIST`，0..62px，2px 步进）——反向段的宽度。
- `CONTRA`（反向畸变率，0..2）——`0` 不反向，`1` 与正向等幅反号，`2` 更强。
  贴边段用 `pow(..., 1.6)` 而不是线性，靠边处变化更陡，对应"反向畸变率更高"。
- `REFRACTION`（正向折射，0..63px）——中心处达到的满幅强度。

三个参数都走顶点色位域，拖动时**不会触发管线重编译**，可以连续调。

#### 法线必须限制在近边，否则中心会画出 X 形纹

折射沿 `normal`（SDF 解析梯度）方向偏移。但这个梯度**在面板内部是不连续的**
（SDF 的中轴 / medial axis），有三处奇点：

| 位置 | 现象 |
|---|---|
| `\|x\| − \|y\| = b.x − b.y` | 选轴从"轴向 X"翻成"轴向 Y" |
| `x = 0` 或 `y = 0` | `sign(p)` 过零翻号；轴线上 `sign = 0`，梯度塌成零向量 |
| `dist = radius`（圆角圆心） | `q.x = q.y`，两种取法都成立，梯度跳变 |

前两条在画面正中合起来就是 **X 形纹**，第三条在四个圆角内侧留一圈亮斑。

早期折射到中心归零，这些都被掩盖了；折射改成中心满幅后立刻暴露。
根治办法**不是加宽窗口去遮**（实测窗宽 44px 时圆角圆心仍在窗内，跳变残留 100%），
而是根本不把法线用到那么深——物理上整块厚玻璃真正弯光的只有贴着轮廓的一圈，
中部是平面：

```glsl
float windowWidth = clamp(radius * 0.8, aPos * 1.5, min(halfSize.x, halfSize.y) * 0.5);
float normalWindow = 1.0 - smoothstep(0.0, 1.0, clamp(dist / max(windowWidth, 1.0), 0.0, 1.0));
normal = (dot(normal, normal) < 1e-6) ? vec2(0.0, 1.0) : normal;   // 兜住零向量
```

**窗宽必须严格小于 `radius`**，否则圆角圆心进窗，奇点重新暴露。
用步长缩放法验证（真不连续则跳幅不随步长缩小）：

```
窗宽 44px : 步长 0.25→2.4282,  0.125→2.4219  (100%)  仍有不连续
窗宽 24px : 步长 0.25→1.8051,  0.125→0.9007  ( 50%)  平滑
```

各预设的 `windowWidth` 均小于自身 `radius`：WINDOW 30→24、SIDEBAR 22→17.6、
SELECTION 12→9.6、BUTTON 32→4、TOGGLE 24→4.5、KNOB 18→3.5。

> 关键：折射项乘 `normalWindow`，缩小项乘 `coreRamp`，**两项乘不同的窗口**。
> 折射依赖法线、只在近边有意义；缩小只用 `p` 的径向方向、中部同样成立。
> 若两项都乘 `coreRamp`，近边与中部强弱被绑死，没法分别调。

### 中心缩小：与折射共用同一条斜坡

`a` 以内除了折射，还叠一个径向位移——一整块厚玻璃是**整块**都在当透镜。
采样点向外偏移 ⇒ 读到更靠外的背景 ⇒ 背景整体**缩小**（放大镜的反向）。

它和折射**共用同一个 `coreRamp`**，所以两者的强弱比例恒定，调任何一个都不会让另一个
出现分界。`SHRINK` 的单位是 GUI 像素：面板短边半尺寸处的采样点向外偏移这么多，
观感与面板大小无关，默认 11px 对应等效缩放约 0.90。

三个要点：

- **`dist` 是"到边框的距离"，中心区是它的最大值。** 这一条最容易搞反：
  写成 `1.0 - smoothstep(...)` 会让整个中心区恒为 0，滑块怎么调都没有任何变化。
- **必须按各轴自己的半尺寸归一化**：写成 `(p / halfSize) * SHRINK`。
  若写成 `p * (SHRINK / shortHalf)`，短边归一化会把长轴位移放大 `bx / shortHalf` 倍
  （按钮 100×16 放大 6.25 倍），中心区域被推到面板外，颜色发浑。
- **必须与 `p` 成正比、两轴用同一组参数**，不能用 `normalize(p) * 固定像素`，
  否则横向和纵向缩放比不同，宽面板上背景里的圆会被拉成椭圆。

`coreEnd` 用 `max(shortHalf, aPos + 1.0)` 兜底，保证 `a` 接近甚至超过半尺寸时
分母不会退化成 0。

## 打开界面

玻璃界面打开前要预编译管线并抓第一份背景，且**不能用 `setScreenAndShow`**——它会同步强制
渲染一帧，而这一帧会跳过世界提取（`GameRenderer.extract(deltaTracker, false)`），主渲染目标
只剩清屏黑色，玻璃采样到的就是整屏黑，表现为"打开窗口闪一帧黑屏"。正确写法：

```java
MyScreen screen = new MyScreen();
GlassPanel.prewarm(screen.window().style());   // 在渲染循环之外完成全部重资源准备
Minecraft.getInstance().gui.setScreen(screen); // 不强制渲染，下一帧世界照常绘制
```

---

## 三层几何模型

框架刻意把"窗口在哪"拆成三个互不耦合的步骤，任何一层都可以单独替换：

| 层 | 职责 | 相关类型 |
|---|---|---|
| 1. 基准矩形 | 屏幕尺寸 + 尺寸策略 + 锚点 + 偏移 → `WindowBounds` | `WindowSize`、`WindowAnchor` |
| 2. 变换 | 时间 → `WindowTransform`（位移 + 绕心缩放） | `WindowAnimation` |
| 3. 合成 | `transform.apply(baseBounds)` → 这一帧要画的矩形 | `WindowTransform` |

`WindowTransform` 是整个动画系统的通用货币：不管什么效果，最终都归结为"某一时刻的一个
位移 + 缩放"。

---

## 动画系统

`WindowAnimation` 是**纯函数**：`(已播放毫秒) → WindowTransform`。它没有状态，因此可以
任意时刻重新求值——跳帧、暂停、重播都不会错位。

### 内置动画

| 工厂方法 | 效果 |
|---|---|
| `pop(时长, 起始缩放, 缓动)` | 从缩小状态弹到正常大小。配 `Easing.EASE_OUT_BACK` 会冲过头再回落 |
| `bounce(时长, 幅度, 次数, 轴)` | 沿轴来回弹跳后归位。**次数 = 2 就是"弹两下"** |
| `stretch(时长, 幅度, 次数, 轴)` | 弹性拉伸形变：两轴缩放反向振荡，果冻感 |
| `slide(时长, 起点X, 起点Y, 缓动)` | 从偏移位置滑入 |
| `slideAndBounce(...)` | 滑入 + 弹跳 |
| `riseFromBottom()` | **默认开场**：从下方 110px 快速升起 + 上下弹两下 + 纵向拉伸 |
| `elasticPop()` | 原地弹出 + 轻微拉伸 + 水平弹两下（不位移） |

`riseFromBottom()` 是目前 `GlassWindow` 的默认开场动画，三段并行叠加：
`slide`（340ms，立方缓出）负责快速升起，`bounce`（620ms，±14px）负责上下弹两下，
`stretch`（560ms，±4.5%）负责纵向拉伸。三段各自独立，参数在
`WindowAnimations` 顶部的常量区，改数值即可调手感。

作用轴 `WindowAnimations.Axis` 有 `HORIZONTAL`（左右）、`VERTICAL`（上下）、`BOTH`。

### 组合

```java
// 并行：位移相加、缩放相乘，时长取最长
WindowAnimations.together(popA, bounceB);
// 或链式
popA.and(bounceB);

// 顺序播放
slideIn.then(settleDown);
// 或 slideIn.then(settleDown)
```

`WindowAnimation` 还提供 `delayed(毫秒)`、`scaled(倍率)`、`eased(缓动)` 三个修饰方法。

> 弹跳和拉伸都用了阻尼正弦，**起点和终点精确落在单位变换上**，所以并行叠加时互不干扰。

### 自定义

不需要继承，直接传 lambda：

```java
WindowAnimation custom = new WindowAnimation(400L, elapsed -> {
    float t = Math.min(1.0F, elapsed / 400.0F);
    float scale = 1.0F + (float) Math.sin(t * Math.PI) * 0.08F;
    return new WindowTransform(0.0F, 0.0F, scale, scale);
});
```

缓动函数是 `Easing` 接口，自带 `LINEAR`、`EASE_OUT_QUAD`、`EASE_OUT_CUBIC`、
`EASE_IN_OUT_CUBIC`、`EASE_OUT_BACK`、`EASE_OUT_ELASTIC`；需要别的直接写 lambda。
缓动的返回值**允许越界**（大于 1 或小于 0），这正是回弹的来源。

---

## 尺寸与位置

`WindowSize` 是函数式接口 `(屏宽, 屏高) → Dimensions`，内置：

| 工厂 | 含义 |
|---|---|
| `fixed(w, h)` | 固定尺寸 |
| `preferred(w, h, margin)` | 首选尺寸，四周至少留 margin，装不下就收缩 |
| `fill(marginX, marginY)` | 铺满屏幕并留边距 |
| `fraction(fw, fh)` | 按屏幕比例 |

还有 `cappedAt(maxW, maxH)` 用来在已有策略上再设上限。自定义直接写 lambda 即可。

`WindowAnchor` 是九宫格停靠，两个分量是"剩余空间的使用比例"（0 贴边、0.5 居中、1 贴另一
边），默认 `CENTER`。相对锚点的微调用 `GlassWindow.offset(x, y)`；默认会把窗口夹进屏幕，
需要放到屏幕外就 `clampToScreen(false)`。

---

## 内容控件

在 `buildContent(WindowBounds content)` 里用 `addRenderableWidget` 添加控件，坐标以
传入的内容区为参照。内容区 = 基准矩形四边内缩 `contentPadding(x, y)`。

**控件会自动跟随窗口动画**：基类记录控件被添加时的坐标作为基准位置，每帧施加与窗口
完全相同的变换（平移 + 绕心缩放）。

绘制顺序上，玻璃先提交、控件后提交。`GuiRenderState` 是分层结构，`addGuiElement` 会把新
元素放进所有"边界相交"元素的上层，所以控件必定浮在玻璃之上——与 `RenderPipeline` 的排序
键无关。

---

## 扩展点

- **新动画效果**：优先用 `together` / `sequence` 组合现有动画；组合不出来再直接构造
  `WindowAnimation`。
- **新尺寸策略**：`WindowSize` 是函数式接口，写 lambda。
- **新停靠方式**：`WindowAnchor` 是枚举，按需扩充。
- **窗口内容**：继承 `GlassWindowScreen` 实现 `buildContent`，用 `GlassLayout` 组织。
- **新玻璃控件**：继承 `AbstractButton`（覆写 `extractContents`）或 `AbstractSliderButton`
  （覆写 `extractWidgetRenderState`）。玻璃底用 `GlassWidgets.glassBackground`，
  它在失败时返回 `false`，此时退回 `GlassWidgets.roundedFill`；文字用
  `GlassWidgets.text/centeredText/rightText` 并传 `GlassTheme.Text` 的缩放比，
  颜色取 `GlassTheme.TEXT` / `TEXT_SECONDARY` / `ACCENT`。
  交互态用 `GlassWidgets.Spring`，按下回弹用 `tickSpring`。
- **重播开场动画**：直接调用 `replayOpening()`。开场动画只在界面第一次 `init()`（真正
  打开）时播放，之后的 `resize`、`rebuildWidgets()` 不会重播——窗口已经在了，再弹一遍很奇怪。

## 已知限制

**没有淡入淡出。** 玻璃的整体不透明度 `OPACITY` 是编译期 shader define，而顶点色通道
已经被六项光学参数（折射 / a 位置 / 反向畸变率 / 中心缩小 / 边缘色散 / 中心色散）
按位域占满，没有余量传逐帧透明度。当前动画因此是纯几何的（位移 + 缩放）。

若后续确实需要淡入淡出，可扩展方向：给玻璃管线换一个自定义 `VertexFormat`（加入
`Normal` 或 `UV3` 等浮点属性）并实现自定义的 `GuiElementRenderState`，就能拿到全精度、
多通道的逐 draw 参数。
