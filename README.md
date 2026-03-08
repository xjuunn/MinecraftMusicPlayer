# Minecraft Music Player

Minecraft Music Player 是一个面向 Minecraft `1.21.11` 的 Fabric 音乐模组，提供基于网易云音乐的共享播放队列、搜索、点歌和播放同步能力。

服务端负责命令、队列、搜索、同步和配置管理；客户端负责实际的音频拉取和播放。因此这是一个双端协作的模组，而不是纯服务端音频模组。

## 功能概览

- 搜索歌曲、作者、歌单和用户
- 查看作者热门歌曲、歌单详情和用户歌单
- 玩家点歌、歌单入队、查看当前播放和播放列表
- 搜索结果分页，并带可点击的 `上一页` / `下一页` 按钮
- 自动播放下一首
- 玩家投票切歌
- 多音源回退，单个音源失败时自动尝试后备源
- 针对部分 VIP 歌曲优先使用可完整播放的第三方音源，避免 30 秒试听链路
- 管理员可配置 API 地址、代理、系统代理、IPv4 优先、超时、队列大小和搜索数量等参数
- 搜索、解析和远程加载阶段提供简洁的聊天提示
- 聊天栏支持点击操作，便于点歌、翻页和查看详情

## 工作方式

- 服务端安装本模组后，负责队列逻辑、命令处理和同步广播
- 客户端安装本模组后，负责实际播放音乐
- 只有服务端安装时，命令和同步可以工作，但客户端听不到声音
- 需要听歌的玩家必须安装客户端模组

推荐部署方式：

1. 服务器安装 `Minecraft Music Player`
2. 需要听歌的客户端也安装 `Minecraft Music Player`
3. 服务器或客户端网络可访问可用的音乐 API 地址

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
| `/music now` | 查看当前播放 |
| `/music queue` | 查看当前队列 |
| `/music join` | 加入当前播放 |
| `/music leave` | 退出当前播放 |
| `/music mute once` | 仅停止接收当前歌曲 |
| `/music vote next` | 投票切到下一首 |
| `/music play song <歌曲ID>` | 直接点播单曲 |
| `/music play playlist <歌单ID>` | 将歌单加入队列 |
| `/music search song <关键词>` | 搜索歌曲 |
| `/music search song page <页码> <关键词>` | 查看歌曲搜索指定页 |
| `/music search artist <关键词>` | 搜索作者 |
| `/music search artist page <页码> <关键词>` | 查看作者搜索指定页 |
| `/music search author <关键词>` | `artist` 的别名 |
| `/music search author page <页码> <关键词>` | `artist` 分页搜索的别名 |
| `/music search playlist <关键词>` | 搜索歌单 |
| `/music search playlist page <页码> <关键词>` | 查看歌单搜索指定页 |
| `/music search user <关键词>` | 搜索用户 |
| `/music search user page <页码> <关键词>` | 查看用户搜索指定页 |
| `/music view artist <作者ID>` | 查看作者热门歌曲 |
| `/music view author <作者ID>` | `artist` 的别名 |
| `/music view playlist <歌单ID>` | 查看歌单详情 |
| `/music view user <用户ID>` | 查看用户歌单 |

搜索结果底部会显示：

- 可点击的 `上一页`
- 可点击的 `下一页`
- 当前页数

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
  "voteSkipPercent": 0.6
}
```

## 音源与回退策略

为了提高可用性，模组会按顺序尝试多个播放源。

当前策略：

1. 优先尝试更适合完整播放 VIP 歌曲的第三方可用音源
2. 再尝试网易云 API 返回的可用 mp3 地址
3. 最后才回退到网易云外链兜底

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
minecraft-music-player-2.0.0-fabricmc1.21.11.jar
```

## 发布

仓库支持通过 GitHub Actions 进行版本发布。

### 使用 tag 发布

```bash
git tag v2.0.0
git push origin v2.0.0
```

### 手动发布

在 GitHub 仓库的 `Actions` 页面运行 `release` 工作流，并填写要发布的 tag。

## 说明

- 项目使用 Mojang 官方映射
- 交互基于聊天栏文本和点击操作，不依赖自定义界面
- 客户端播放器当前优先使用可直接播放的 mp3 音源
- 英文文档见 [README_EN.md](README_EN.md)
