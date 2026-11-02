# 音乐浏览窗口：页面框架

浏览窗口（`MusicBrowserScreen`）不是一个"能切模式的界面"，而是一个**页面宿主**。窗口本身
只做三件事：维护返回栈、按栈顶页面需要的外壳摆控件、把交互与动画转给页面。

这样做的直接原因：页面会越来越多（搜索、歌单、歌手、用户、播客、歌词、排行榜……）。如果
继续用"一个控件 + 一个 `Mode` 枚举"的写法，每加一页就要在同一个类里再加一堆 `if`，布局、
数据、网络请求全搅在一起。

---

## 三个角色

| 角色 | 文件 | 职责 |
| --- | --- | --- |
| 宿主 | `browser/MusicBrowserScreen.java` | 返回栈、外壳布局、页面切换动画、点播命令 |
| 页面 | `browser/BrowserPage.java` | 一份数据 + 一个 `MediaListView` |
| 外壳声明 | `browser/PageChrome.java` | 页面需要哪些固定部件 |

页面只通过 `BrowserHost`（`push` / `pop` / `play` / `refreshChrome`）与外界交互，**不碰控件、
不发请求、不发命令**。

### `PageChrome`

| 值 | 部件 | 谁在用 |
| --- | --- | --- |
| `BACK_BAR` | 顶栏：返回按钮 + 页面标题 | 详情页 |
| `SEARCH_BAR` | 搜索行：输入框 + 搜索按钮 | 搜索页 |
| `TABS` | 类型标签栏 | 搜索页 |
| `PAGER` | 底部翻页栏 | 搜索页 |

页面用 `EnumSet<PageChrome>` 声明，宿主按**声明顺序**从上往下摞：顶栏 → 搜索行 → 标签栏 →
列表 → 翻页栏。列表吃掉剩下的全部高度，所以详情页没有搜索行和标签栏时，能滚的区域比搜索页
大得多。

---

## 新增一个页面

```java
public final class RankPage extends BrowserPage {

    private List<MediaItem> items = List.of();

    @Override public Component title() {
        return Component.translatable("musicplayer.browser.rank");
    }

    @Override public EnumSet<PageChrome> chrome() {
        return EnumSet.of(PageChrome.BACK_BAR);          // 只要一个返回顶栏
    }

    @Override public void apply() {                      // 状态 → 列表，必须可重复调用
        view().setHeader(null);
        view().setStatus(Component.empty());
        view().showItems(items, false, false);
    }

    @Override public void onEnter(BrowserHost host) {     // 压栈时拉数据
        service.rank().whenComplete((list, error) ->
                Minecraft.getInstance().execute(() -> {
                    items = list == null ? List.of() : list;
                    apply();
                    view().resetAndReveal();              // 数据到齐才播入场动画
                }));
    }
}
```

跳转只需要一行：`host.push(new RankPage())`（或 `top().host` 之外的地方调 `BrowserHost.push`）。

宿主会自动：清掉旧控件 → 按 `chrome()` 重新摆一遍 → 让新页面从右侧滑入 → 调 `onEnter`。
返回时自动让上一页从左侧滑入，并调 `onResume`（默认只是 `apply()`，**不重新请求**）。

### 必须遵守的两条

1. **状态挂字段，控件每次新建。** `buildContent` 会在窗口缩放、页面切换时重跑，`view()` 之外
   的控件（按钮、标签）都是新对象，靠字段状态重建。
2. **`apply()` 要可重复调用。** 它会在每次布局、每次数据变化后被调；不要在里面发起请求。

### 什么时候播动画

`showItems(...)` **只更新数据**，不动滚动位置。要"回到顶部 + 逐行错峰入场"时，在数据真正换了
之后再调 `resetAndReveal()`。分开的原因：追加（加载更多）如果走同一条路径，列表会弹回顶部。

---

## 列表控件 `MediaListView`

一个控件同时管"头部区块"和"行"，两者**一起滚动**。

| 区块 | 内容 |
| --- | --- |
| 头部 | 封面、标题、副标题、数据小标签、简介（自动折行，最多 3 行） |
| 行 | 序号、封面、标题、副标题、时长等元信息、加入歌单按钮、箭头 |
| 末尾 | "加载更多"行（`hasMore` 时出现） |

头部跟着滚，是为了不把可滚区域压成中间一小条：详情页的封面 + 简介固定占掉一百多像素，
列表只剩两三行，翻起来很难受。

### 行的排版

```
歌曲：[序号][封面][标题        ][时长][＋]
歌单：[序号][封面][标题        ][曲目数][›]
```

- 序号列宽 12，封面 24，右侧内缩 8；
- 元信息右对齐，放在按钮左边；
- **单曲**行尾是"加入歌单"加号（`queuable(item)`），点它 → `Listener.onPlay` → 宿主发
  `/music play song <id>`：有歌在播时这就是**追加到当前队列**，空队列时则是开始播放，
  和聊天栏点播完全同一条路径（权限校验、加载提示、队列规则都走服务端）；
- **歌单**行尾是右箭头，点行 → `Listener.onItemClick` → 打开详情页；歌单的播放入口是详情页
  头部的"播放全部"（`/music play playlist` 是整单替换队列，没法叫"加入歌单"）；
- **单曲行点整行不做任何事**：单曲没有详情页（点开只有一个封面和一行时长），所以列表也不给
  它画右箭头、不给按下回弹，避免"看起来能点、点了没反应"。

### 封面预取

缩略图在 `MediaThumbnails` 里按 URL 缓存，`request(url)` 只发起下载不等结果。列表每帧调
`prefetchCovers()`：把可见区上下各多算 6 行，提前把封面塞进下载队列（单线程、按发起顺序
处理），所以往下滚时下一屏的封面基本已经在路上，不会出现"先占位块、过一会儿才出图"。

每帧调用是安全的：命中缓存或已在下载时只是一次哈希查找。

头部右侧还有一个"播放全部"（`Header.playAll`）：歌单动辄几百首，逐首点加号不现实。
它只对歌单开放——点播命令只有 `song` / `playlist` 两种，给歌手或用户放这个按钮会点了没反应。
按钮跟着头部一起滚动，点击区域与绘制矩形完全一致（`overHeaderAction`）。

### 裁剪：整块列表只开一道口

滚动区里的一切（文字、封面、行底玻璃、徽标、播放按钮）都由
`GlassWidgets.beginClip(...)` / `endClip(...)` 遮挡，行和头部**不需要**各自判断可见性：

```java
GlassWidgets.beginClip(graphics, getX(), getY(), getX() + width, getY() + height);
drawContent(graphics, font);
GlassWidgets.endClip(graphics);
```

原因是玻璃方块是手写 `BlitRenderState` 提交的，原版 `enableScissor` 管不到它；`beginClip`
会额外把矩形压进玻璃自己的裁剪栈，提交时作为 `scissorArea` 交给渲染器。详见
`glass-window.md` 的"裁剪"一节。

### scissor 的四个参数是 `(x0, y0, x1, y1)`

不是 `(x, y, w, h)`。字节码里做的是 `arg3 - arg1`、`arg4 - arg2`，传宽度进去会得到一个
退化的裁剪框——表现就是"列表只显示前两行，第二行还缺一半"。

### 滚动条与键盘

- 滚动条只在滚动时出现：`markScrolling()` 续期 700ms，再用 400ms 淡出。常驻一条竖线会一直
  占着右侧，列表本身的行已经有分隔感，不需要它做视觉锚点；
- 键盘滚动在 `MediaListView.keyPressed` 里：↑↓ 一行、PgUp/PgDn 一屏、Home/End 到顶/到底。
  Home/End 用 `GlassScroll.jumpTo` 滑过去而不是瞬移；
- 宿主在输入框没获得焦点时，把按键先转给列表——列表是窗口里唯一可滚的东西，不必先点一下。

### 顶栏标题只在头部滚出后出现

详情页的标题在头部区块里已经有一份，刚打开就上下各一个会显得重复。`updateTitle()` 每 tick
查一次 `MediaListView.headerScrolledAway()`：头部还看得见就留空，滚过去之后才在顶栏补上，
避免"整页只剩封面和简介、看不出自己在哪一页"。

### 摆控件用 `setRectangle(width, height, x, y)`

`AbstractWidget.setRectangle` 内部是 `setSize(w, h)` + `setPosition(x, y)`，参数顺序是
**先尺寸后位置**。按直觉写成 `(x, y, w, h)` 会把控件摆到右下角。

---

## 页面切换动画

| 动作 | 效果 |
| --- | --- |
| `push` | 新页面从右侧 26px 处滑入 |
| `pop` | 上一页从左侧 26px 处滑入 |

滑动在列表控件内部完成（整块内容 `pose().translate`），所以外壳部件（顶栏、翻页栏）不跟着动，
看起来是"内容换了一页"而不是"整个窗口在晃"。时间轴 180ms，缓动用 `GlassTheme.Motion`。

Esc 键：直接关闭整个窗口（不逐页后退）；返回上一页只走顶栏的返回按钮。

---

## 与窗口动画的关系

页面切换用的是 `relayoutContent()`（见 `glass-window.md`），它只重建控件、不动窗口的
开场/关闭动画时间轴。两者互不干扰：窗口还在播开场动画时按返回，页面照样能滑，窗口的
位移/缩放继续叠加在控件位置上。
