# Minecraft Music Player

Minecraft Music Player 是面向 Minecraft `1.21.11` 的 Fabric 音乐模组，提供基于网易云音乐的搜索、点歌、共享播放队列、歌单播放，以及自定义音乐唱片功能。

服务端负责命令、队列、搜索、同步、唱片刻录和唱片机控制；客户端负责实际音频拉取与播放。因此这是一个双端协作模组，而不是纯服务端音频模组。

## 功能概览

- 搜索歌曲、作者、歌单和用户
- 查看作者热门歌曲、歌单详情和用户歌单
- 玩家点歌、歌单播放、共享播放队列、自动下一首
- 玩家投票切歌、重复点歌去重、待播歌曲提升为下一首
- 搜索结果、详情页、队列页统一分页和导航
- 聊天栏交互统一为可点击操作，歌曲、作者、创建者、详情入口都可直接点击
- 当前播放支持可点击下载，自动在浏览器中打开音频直链
- 服务端支持队列预缓存，减少切歌时的等待时间
- 支持将歌曲刻录进原版唱片，并在唱片机中播放 URL 音乐

## 音乐唱片

### 支持的能力

- 使用 `/music burn song <歌曲ID>` 将当前主手唱片刻录为自定义音乐唱片
- 刻录后的唱片会保存：
  - 歌曲 ID
  - 歌曲名
  - 作者名
  - 作者 ID
  - 时长
  - 可用音源 URL 列表
- 唱片名称会显示为 `歌曲名 - 作者`
- 鼠标悬浮到唱片上时，会显示更多元数据
- 将刻录后的唱片放入唱片机后，会自动播放其中保存的 URL 音乐
- 当唱片被弹出、替换、唱片机被破坏、区块卸载或玩家离开可听范围时，会自动停止对应播放

### 支持的原版唱片底材

所有原版 `Music Disc` 均可作为刻录底材使用。

### 刻录入口

当主手持有可刻录唱片时，下列位置会自动显示 `[刻录]` 按钮：

- `/music now`
- `/music search song ...`
- `/music view playlist ...`
- `/music view artist ...`
- `/music view author ...`
- `/music queue`

点击后会直接执行对应的 `/music burn song <歌曲ID>`。

## 工作方式

- 服务端安装本模组后，负责队列逻辑、命令处理、搜索请求、唱片机播放同步和配置管理
- 客户端安装本模组后，负责实际播放音乐和唱片机中的自定义音频
- 只有服务端安装时，命令和队列逻辑可以工作，但客户端听不到声音
- 需要听歌的玩家必须安装客户端模组

推荐部署方式：

1. 服务器安装 `Minecraft Music Player`
2. 需要听歌的客户端也安装 `Minecraft Music Player`
3. 服务器或客户端网络可以访问可用的音乐 API

## 依赖要求

- Minecraft `1.21.11`
- Fabric Loader `0.18.4` 或更高版本
- Fabric API `0.141.3+1.21.11` 或兼容版本
- Java `21`
- 一个可访问的网易云音乐相关 API 服务，默认地址为 `https://mycelis.dpdns.org/`

## 安装

### 服务端

将模组主文件和 `Fabric API` 放入服务器的 `mods` 目录。

### 客户端

将模组主文件和 `Fabric API` 放入客户端的 `mods` 目录。

### 默认 API 地址

默认配置使用：

```text
https://mycelis.dpdns.org/
```

管理员可以通过命令修改地址，也可以恢复到默认值。

## 玩家命令

| 命令 | 说明 |
| --- | --- |
| `/music` | 显示帮助 |
| `/music now` | 查看当前播放，并提供可点击下载入口 |
| `/music queue [page]` | 查看当前队列 |
| `/music queue next <歌曲ID>` | 将队列中的歌曲提到下一首 |
| `/music join` | 加入当前播放 |
| `/music leave` | 退出当前播放 |
| `/music mute once` | 仅停止接收当前歌曲 |
| `/music vote next` | 投票切到下一首 |
| `/music play song <歌曲ID>` | 直接点播单曲 |
| `/music play playlist <歌单ID>` | 切换到歌单播放模式 |
| `/music burn song <歌曲ID>` | 将主手唱片刻录为对应音乐唱片 |
| `/music search song <关键词>` | 搜索歌曲 |
| `/music search song page <页码> <关键词>` | 查看歌曲搜索指定页 |
| `/music search artist <关键词>` | 搜索作者 |
| `/music search artist page <页码> <关键词>` | 查看作者搜索指定页 |
| `/music search author <关键词>` | `artist` 的别名 |
| `/music search playlist <关键词>` | 搜索歌单 |
| `/music search playlist page <页码> <关键词>` | 查看歌单搜索指定页 |
| `/music search user <关键词>` | 搜索用户 |
| `/music search user page <页码> <关键词>` | 查看用户搜索指定页 |
| `/music view artist <作者ID>` | 查看作者热门歌曲 |
| `/music view artist page <页码> <作者ID>` | 查看作者详情指定页 |
| `/music view author <作者ID>` | `artist` 的别名 |
| `/music view playlist <歌单ID>` | 查看歌单详情 |
| `/music view playlist page <页码> <歌单ID>` | 查看歌单详情指定页 |
| `/music view user <用户ID>` | 查看用户歌单 |
| `/music view user page <页码> <用户ID>` | 查看用户歌单指定页 |

## 管理员命令

| 命令 | 说明 |
| --- | --- |
| `/music admin reload` | 重新加载配置 |
| `/music admin status` | 查看当前配置状态 |
| `/music admin clearqueue` | 清空待播队列 |
| `/music next` | 立即切到下一首 |
| `/music stop` | 停止播放 |
| `/music admin set baseUrl <地址>` | 设置音乐 API 地址，传入 `default` 恢复默认值 |
| `/music admin set allowCustomServer <true\|false>` | 是否允许使用自定义 API 地址 |
| `/music admin set allowSongRequest <true\|false>` | 是否允许玩家点歌 |
| `/music admin set allowPlaylistRequest <true\|false>` | 是否允许玩家导入歌单 |
| `/music admin set autoAdvance <true\|false>` | 是否自动播放下一首 |
| `/music admin set announceQueueChanges <true\|false>` | 是否广播队列变化 |
| `/music admin set showLoadingHints <true\|false>` | 是否显示搜索和解析提示 |
| `/music admin set useSystemProxy <true\|false>` | 是否启用系统代理自动发现 |
| `/music admin set preferIpv4 <true\|false>` | 是否优先使用 IPv4 |
| `/music admin set proxy <host:port>` | 设置 HTTP 代理 |
| `/music admin set proxy none` | 清除代理 |
| `/music admin set connectTimeoutSeconds <3-60>` | 设置连接超时秒数 |
| `/music admin set readTimeoutSeconds <3-120>` | 设置读取超时秒数 |
| `/music admin set searchLimit <3-20>` | 设置每页搜索结果数量 |
| `/music admin set maxQueueSize <1-200>` | 设置队列上限 |
| `/music admin set playlistQueueLimit <1-100>` | 设置单次导入歌单的最大歌曲数 |
| `/music admin set queueCacheSize <0-20>` | 设置服务端队列预缓存数量 |
| `/music admin set voteSkipPercent <0.1-1.0>` | 设置投票切歌所需比例 |

## 配置文件

配置文件路径：

```text
config/minecraft-music-player.json
```

默认配置示例：

```json
{
  "neteaseBaseUrl": "https://mycelis.dpdns.org/",
  "proxy": "",
  "useSystemProxy": true,
  "preferIpv4": true,
  "allowCustomServer": true,
  "allowSongRequest": true,
  "allowPlaylistRequest": true,
  "autoAdvance": true,
  "announceQueueChanges": true,
  "showLoadingHints": true,
  "connectTimeoutSeconds": 10,
  "readTimeoutSeconds": 20,
  "searchLimit": 8,
  "maxQueueSize": 40,
  "playlistQueueLimit": 20,
  "queueCacheSize": 3,
  "voteSkipPercent": 0.6
}
```

## 队列预缓存

- 服务端会自动预解析待播队列前几首歌
- 默认预缓存 `3` 首
- 切歌时会优先复用缓存结果
- 可通过 `/music admin set queueCacheSize <0-20>` 调整
- 设置为 `0` 时关闭队列预缓存

## 音源与回退策略

为了提高可用性，模组会按顺序尝试多个播放源：

1. 优先尝试更适合完整播放 VIP 歌曲的第三方可用音源
2. 再尝试网易云 API 返回的可用 mp3 地址
3. 最后回退到网易云外链兜底

同时会过滤明显的试听链路，例如：

- `musicrep-ts`
- `jd-musicrep-ts`

这可以减少部分 VIP 曲目只能播放 30 秒的问题。

## 构建

```powershell
.\gradlew.bat clean build
```

构建产物示例：

```text
minecraft-music-player-2.0.2-fabricmc1.21.11.jar
```

## 发布

仓库支持通过 GitHub Actions 进行版本发布。

### 使用 tag 发布

```bash
git tag v2.0.2
git push origin v2.0.2
```

### 手动发布

在 GitHub 仓库的 `Actions` 页面运行 `release` 工作流，并填写要发布的 tag。

## 说明

- 项目使用 Mojang 官方映射
- 交互基于聊天栏文本和点击操作，不依赖自定义界面
- 客户端播放器当前优先使用可直接播放的 mp3 音源
- 英文文档见 [README_EN.md](README_EN.md)
