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
                .style(GlassStyle.WINDOW)
                .size(WindowSize.preferred(640, 420, 32))
                .anchor(WindowAnchor.CENTER)
                .opening(WindowAnimations.elasticPop()));
    }

    @Override
    protected void buildContent(WindowBounds content) {
        addRenderableWidget(Button.builder(Component.literal("确定"), b -> onClose())
                .bounds(content.x0() + 24, content.y0() + 24, 96, 20)
                .build());
    }
}
```

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
- **窗口内容**：继承 `GlassWindowScreen` 实现 `buildContent`。
- **重播开场动画**：直接调用 `replayOpening()`。开场动画只在界面第一次 `init()`（真正
  打开）时播放，之后的 `resize`、`rebuildWidgets()` 不会重播——窗口已经在了，再弹一遍很奇怪。

## 已知限制

**没有淡入淡出。** 玻璃的整体不透明度 `OPACITY` 是编译期 shader define，而顶点色通道
已经被折射 / 色散 / 中心色散 / 斜面四项占满，没有余量传逐帧透明度。当前动画因此是纯
几何的（位移 + 缩放）。

若后续确实需要淡入淡出，可扩展方向：给玻璃管线换一个自定义 `VertexFormat`（加入
`Normal` 或 `UV3` 等浮点属性）并实现自定义的 `GuiElementRenderState`，就能拿到全精度、
多通道的逐 draw 参数。
