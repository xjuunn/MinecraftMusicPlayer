# 配置系统

模组的所有配置集中在**一个文件**里：

```
config/minecraft-music-player.json
```

由 `MusicPlayerConfigManager` 统一读写，`MusicPlayerConfig` 是数据模型。

---

## 为什么是一个文件

早期是两个独立管理器、两个文件（服务端 `minecraft-music-player.json`、
客户端 `minecraft-music-player-client.json`），各自 `save()` 各自的路径。

合并成单文件的前提是：**单人游戏的内置服务端与客户端在同一个 JVM、同一个类加载器里
运行**，因此共享同一个 `MusicPlayerConfig` 实例和同一个 `MusicPlayerConfigManager`。
不存在"两端各写各的、互相覆盖"的风险。

专用服务器只加载 `src/main` 的类，`client` 分节保持默认值，读入写出都无害；
客户端单机 / 联机时两边看到的是同一份数据。

> 若将来把客户端与服务端拆成两个独立进程（例如改用外部服务端 + 独立客户端调试），
> 这个前提就不成立了，那时必须退回"按端拆分文件"。

## 文件布局

```json
{
  "neteaseBaseUrl": "...",     // 根：服务端 / 双端共用
  "apiMode": "auto",
  "maxQueueSize": 40,
  "...": "...",

  "client": {                  // 客户端本地设置
    "bgmVolume": 0.7,
    "enableNetworkBgm": true
  },

  "glass": {                   // 液态玻璃外观
    "opacity": 0.0,
    "blur": 0.2,
    "shadow": false,
    "refraction": 30.0,
    "dispersion": 0.65,
    "centerDispersion": 6.4,
    "aDistance": 5.0,
    "contraRatio": 0.62,
    "shrink": 7.34
  }
}
```

### 玻璃默认参数

| 参数 | 默认值 | 说明 |
|---|---|---|
| `aDistance` | 5.0 | a 位置（反向段宽），1px 步进 |
| `contraRatio` | 0.62 | 反向畸变率 |
| `refraction` | 30.0 | 正向折射，1px 步进 |
| `shrink` | 7.34 | 中心缩小 |
| `dispersion` | 0.65 | 边缘色散 |
| `centerDispersion` | 6.4 | 中心色散，1px 步进（编码为 6） |
| `opacity` | 0.0 | 背景透明度 0 = 叠加层完全消失 |
| `blur` | 0.2 | 背景模糊 20% |
| `shadow` | false | 背景阴影关闭 |

`GlassStyle.WINDOW` 里烘焙的数值必须与上表**逐项一致**，否则「恢复默认」会让外观突变。
`MusicPlayerConfigSectionTest` 里有一项断言专门守这条。

> `opacity = 0` 时 `overlayAlpha() = 0.92 × 0^1.6 = 0`，叠加层完全不可见，
> 窗口只剩纯折射；这是 iOS clear 变体的观感，与默认开启阴影 / 高不透明度的
> regular 变体相反。

`blur = 0.2` 能被精确量化：`BLUR_STEPS = 10` 时 `round(0.2×10)/10 = 0.2`。
早先取 8 档会把 0.2 量化成 0.25，默认观感与配置值不符。

根上的服务端字段**保持原位**：老版本配置就长这样，搬进子节会让已有用户的配置失效。
新增参数一律分节。

## 新增一个参数

1. 在 `MusicPlayerConfig` 的对应分节里加字段，并给默认值常量：

   ```java
   public static final int DEFAULT_FOO = 42;
   public int foo = DEFAULT_FOO;
   ```

2. 若需要范围约束，加到该分节的 `normalize()`：

   ```java
   @Override
   public void normalize() {
       // ...
       if (foo < 0) { foo = DEFAULT_FOO; }
   }
   ```

3. 若要支持"恢复默认"，加到该分节的 `resetToDefaults()`：

   ```java
   @Override
   public void resetToDefaults() {
       // ...
       foo = DEFAULT_FOO;
   }
   ```

4. 读取处用 `MusicPlayerConfigManager.get().<分节>.<字段>`；写入后调用
   `MusicPlayerConfigManager.markDirty()`（高频，合并落盘）或
   `MusicPlayerConfigManager.save()`（低频，立即落盘）。

改动 `normalize()` / `resetToDefaults()` 时**两边都要加**——只加一处，
新参数就会在"加载时越界"和"恢复默认"这两种情况下表现不一致。

### 新增一整个分节

分节需要实现 `MusicPlayerConfig.Section`（`normalize()` + `resetToDefaults()`），
然后在 `MusicPlayerConfigManager` 里补两处：

- `ensureSections()`——Gson 对旧文件里缺失的字段填默认值，但**显式 `null` 会置空**，
  必须在这里兜住；
- `normalizeAll()`——加载后必定调用一次。

## 写盘策略

| 方法 | 时机 | 用于 |
|---|---|---|
| `save()` | 立即 | 命令、开关、恢复默认 |
| `markDirty()` | 打标记 | 滑块拖动等每帧变化 |
| `tick()` | 每 client tick | 把标记的变更合并落盘（≥500ms 一次） |
| `flushDirty()` | 退出前 | 落盘最后一次未合并的变更 |

滑块拖动每帧都在改，直接 `save()` 会每秒写盘几十次；`markDirty()` + `tick()`
把写盘压到最多每 500ms 一次。

## 恢复默认

| 方法 | 范围 |
|---|---|
| `MusicPlayerConfigManager.resetGlass()` | 只重置 `glass` 分节（9 项玻璃外观） |
| `MusicPlayerConfigManager.resetClient()` | 只重置 `client` 分节 |
| `MusicPlayerConfigManager.resetAll()` | 全部配置，含服务端字段 |

界面里「恢复默认并重播动画」按钮走的是 `resetGlass()`，不动音量等其他设置。

## 旧文件迁移

加载时自动并入两处历史位置，迁移后旧文件保留不删（用户可自行清理）：

| 旧文件 | 处理 |
|---|---|
| `music-player-config.json` | 读出 `proxy`、`platformBaseUrl`，仅在当前值为空/默认时覆盖 |
| `minecraft-music-player-client.json` | 读出 `glassOpacity` / `glassBlur` / `glassShadow` / `bgmVolume` / `enableNetworkBgm` |

迁移刻意做成"不覆盖用户已改过的值"，避免每次启动都把新配置打回旧值。

## 音乐 URL 音源链

点歌、刻录唱片、随机播放最终都要把「网易云歌曲 ID」换成一条**可播放的音频直链**，
这件事由 `SongUrlResolver` 统一负责，顺序固定为：

| 顺位 | 音源 | 说明 |
|---|---|---|
| 1 | **Qijieya** | `api.qijieya.cn/meting/`，302 跳到网易云 CDN 的 MP3；实测唯一稳定返回**完整时长**（VIP 曲也给整首）的免费源 |
| 2 | **Byfuns** | `api.byfuns.top/1/`，纯文本返回 MP3 直链；偶发 504，故排第二 |
| 3 | **网易云官方直连** | `/api/song/enhance/player/url`；免费曲可达 320k，VIP 曲无登录态时返回空 |
| 4 | **Mycelis（后端代理）** | `{neteaseBaseUrl}/song/url/v1`；VIP 曲可能只给试听，因此只作最后保底 |

规则：

- **每个音源失败后会重试一次**（`SongUrlResolver.PROVIDER_ATTEMPTS = 2`），两次都失败才换下一个；
- **命中即停**：拿到可用直链立刻返回，不再打扰后面的音源（第三方都是免费公共接口，
  串行 + 命中即停能显著减少无谓请求，避免被限流）；
- 整条链有总预算 `CHAIN_BUDGET_MILLIS = 12s`，全部音源都不可达时超时放弃；
- 全部失败时：点歌返回 `musicplayer.song.no_source`（"无法获取可播放的音乐链接"），
  客户端播放侧返回 `musicplayer.playback.no_source` / `all_sources_failed`。

直连模式（`DirectMusicApi`）和后端模式（`NeteaseApiClient`）注册的是**同一条链**，
避免同一首歌在两种模式下可用性不一致。

> 音源站点随时可能失效或改动返回格式。新增/替换音源只需实现 `SongUrlProvider`
> 并在上面两处注册；已移除的 VKEYS（`api.vkeys.cn`）实测对任意歌曲都返回
> `code=500 未找到当前歌曲信息`，故不再参与。

## 歌词时间轴对齐

歌词发在 action bar 上，跟着"当前播放位置"走。这里有个容易踩的坑：

> 服务端发出 `play` 包就按墙钟开始计时，但客户端要**先把整首 MP3 下载完**才开始出声。
> 几 MB 的曲子下载要几秒，这段静默期如果照常推进，歌词就会比声音先跑。

处理办法是让客户端在**真正出声**时回报一条 `started`（`MusicPlaybackReportPayload.started`），
服务端据此对齐时间轴：

| 状态 | 歌词位置 |
|---|---|
| 收到 `play`，还没收到 `started` | **停在 0**，不推进 |
| 收到 `started` 且收到过 `position` | **只信客户端位置**（声卡真实进度 + 上报间隔外推） |
| 一直是没回报的旧客户端 | 超过 `PLAYBACK_START_GRACE_MILLIS = 3s` 宽限期后回退服务端计时 |

判定集中在 `LyricService.resolveElapsedMillis(...)` 这个纯函数里，便于单测覆盖。

几个配套细节：

- **换曲 / seek / resume** 都要把 `clientPlaybackStarted` 复位并清掉旧位置——
  否则新曲会沿用上一条的进度，歌词直接跳到中间；
- **位置上报带脏数据保护**：超过曲长 + 5 秒的位置一律丢弃（旧曲残留或进度重置会产生这种值）；
- **`AudioDevice.getPosition()` 必须加上 seek 起点**：seek / resume 时起始那段是被解码后
  直接丢弃的（没写进声卡），声卡位置从 0 起算，不加回起点歌词会慢一整段；
- **action bar 有显示时限**：原版约 3 秒（60 tick）自动隐藏，所以同一句歌词必须
  周期性保活重发（`OVERLAY_REFRESH_TICKS = 40` ≈ 2 秒），否则会反复"消失—重现"，
  看起来就是在闪。发送判断统一收敛成两点：**内容变了** 或 **保活到期**；
- **曲终必须主动清屏**：歌词变空时要显式发一条空 overlay，否则原版会把最后一句留在
  action bar 上；唱片机非歌单模式播完也要真正停止并从 `activeJukeboxes` 移除；
- **唱片机**同理：`JukeboxPlaybackService.getJukeboxElapsedMillisForPlayer` 没收到真实位置时
  在宽限期内返回 0。

## 相关文件

- `src/main/java/com/junhsiun/musicplayer/config/MusicPlayerConfig.java` — 数据模型
- `src/main/java/com/junhsiun/musicplayer/config/MusicPlayerConfigManager.java` — 读写与迁移
- `src/client/java/com/junhsiun/musicplayer/client/config/MusicPlayerClientConfigManager.java` — 客户端门面（转发）
- `src/client/java/com/junhsiun/musicplayer/client/gui/glass/GlassDebug.java` — 光学参数门面
- `src/client/java/com/junhsiun/musicplayer/client/gui/glass/theme/GlassTheme.java` — 透明度 / 模糊 / 阴影门面
