# Minecraft Music Player

Minecraft Music Player 是一个面向 Minecraft `1.21.11` 的 Fabric 音乐模组，提供网易云音乐搜索、点歌、播放队列、歌单播放、自定义音乐唱片，以及唱片机 URL 音乐播放功能。

服务端负责命令、队列、搜索、同步、战利品箱随机唱片注入和唱片机控制；客户端负责实际音频下载、播放和唱片机封面渲染。因此这是一个双端协作模组，而不是纯服务端音频模组。

![img](https://cdn.modrinth.com/data/cached_images/5c2f4460b27729217aacf0722ce706ff0c65bf92.png)

![img](https://cdn.modrinth.com/data/cached_images/95b8961a79392a69e9a9bbaff77d1fa2fdc8b433.png)

![img](https://cdn.modrinth.com/data/cached_images/0a5d5463d0b775d10f2e0bd5b352fd10ec48f596.png)

![img](https://cdn.modrinth.com/data/cached_images/b171b9e6cb1764fffcb62ec2e12d890559bdeaf9.png)

![img](https://cdn.modrinth.com/data/cached_images/c02c2173e84bcc6b5d461fd9cb3d55c66ee5f6de.png)

![img](https://cdn.modrinth.com/data/cached_images/503d7f7f1a4b7cacf04b0757cf1ff2bcfd84d7be.png)

![img](https://cdn.modrinth.com/data/cached_images/20d3b12f90f0f836e7a16a8e18359da56fe65d3d.png)





## 功能概览

- 搜索歌曲、作者、歌单和用户
- 查看作者热门歌曲、歌单详情和用户歌单
- 所有主要列表支持分页与可点击导航
- 所有高频条目支持点击查看详情、点歌、刻录或下载
- 共享点歌、待播队列、自动下一首、投票切歌
- 重复点歌自动去重，待播歌曲支持提升为下一首
- 服务端队列预缓存，减少切歌解析等待
- 将歌曲刻录进原版唱片，并放入唱片机播放 URL 音乐
- `/music random` 每次生成 10 首随机热门音乐，可直接点歌和刻录
- 所有带战利品表的容器都可按配置随机生成热门音乐唱片

## 工作方式

- 服务端安装本模组后，负责命令处理、搜索请求、播放队列、战利品箱随机唱片和唱片机同步
- 客户端安装本模组后，负责实际播放音频和渲染唱片机封面
- 只有服务端安装时，命令和队列逻辑可以工作，但客户端听不到声音
- 需要听歌的玩家必须安装客户端模组

推荐部署方式：

1. 服务器安装 `Minecraft Music Player`
2. 所有需要听歌的客户端也安装 `Minecraft Music Player`
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

## 音乐唱片

### 刻录流程

1. 主手持有任意原版唱片
2. 使用 `/music burn song <歌曲ID>`
3. 或在支持的列表页面中点击 `[刻录]`
4. 获得带有歌曲 URL 和元数据的自定义音乐唱片

### 唱片保存的数据

- 歌曲 ID
- 歌曲名
- 作者名
- 作者 ID
- 封面 URL
- 时长
- 可用音源 URL 列表

### 唱片机播放

- 将刻录后的唱片放入唱片机后自动播放其中的 URL 音乐
- 播放会跟随唱片机位置同步，而不是全局背景音乐
- 四个侧面会渲染旋转中的唱片封面效果
- 唱片被弹出、替换、唱片机被破坏、区块卸载或玩家离开可听范围后会停止播放
- “正在生成”的占位唱片不能放入唱片机，使用时不会有任何反应

### 支持的刻录入口

当主手持有可刻录唱片时，这些界面会自动出现 `[刻录]`：

- `/music now`
- `/music queue`
- `/music random`
- `/music search song ...`
- `/music view playlist ...`
- `/music view artist ...`
- `/music view author ...`

## 随机热门音乐

使用：

```text
/music random
```

行为说明：

- 每次生成 10 首随机热门音乐
- 来源是热门歌单分类下的热门歌单，再从歌单中随机抽歌
- 每次生成的列表都不同
- 列表中的每一首歌都支持：
  - 点歌
  - 刻录
  - 查看作者详情
  - 下载当前直链

## 战利品箱随机音乐唱片

本模组支持在所有带战利品表的容器第一次被打开时，按配置随机注入热门音乐唱片。

行为说明：

- 对所有带 loot table 的方块容器和实体容器生效
- 每个容器只会决策一次，不会重复生成
- 首次打开时会先放入“随机音乐唱片生成中”的占位唱片，后台异步生成真正的音乐唱片
- 如果玩家先把占位唱片拿走，生成完成后会自动在玩家背包或当前打开的容器里替换成真正的音乐唱片
- 生成完成后会保留原始唱片底盘类型，不会改变颜色和外观
- 生成的唱片内容来自热门歌单中的随机热门歌曲
- 可由管理员配置是否启用、生成概率和每个容器的数量

## 玩家命令

| 命令 | 说明 |
| --- | --- |
| `/music` | 显示帮助 |
| `/music now` | 查看当前播放，并提供下载入口 |
| `/music queue [page]` | 查看当前待播队列 |
| `/music queue next <歌曲ID>` | 将队列中的歌曲提升为下一首 |
| `/music join` | 加入当前播放 |
| `/music leave` | 退出当前播放 |
| `/music mute once` | 仅停止接收当前歌曲 |
| `/music vote next` | 投票切到下一首 |
| `/music play song <歌曲ID>` | 直接点播单曲 |
| `/music play playlist <歌单ID>` | 切换到歌单播放模式 |
| `/music burn song <歌曲ID>` | 将主手唱片刻录为音乐唱片 |
| `/music random` | 生成 10 首随机热门音乐 |
| `/music random refresh` | 重新生成随机热门音乐列表 |
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
| `/music admin set showLoadingHints <true\|false>` | 是否显示加载提示 |
| `/music admin set useSystemProxy <true\|false>` | 是否启用系统代理自动发现 |
| `/music admin set preferIpv4 <true\|false>` | 是否优先使用 IPv4 |
| `/music admin set proxy <host:port>` | 设置 HTTP 代理 |
| `/music admin set proxy none` | 清除代理 |
| `/music admin set connectTimeoutSeconds <3-60>` | 设置连接超时秒数 |
| `/music admin set readTimeoutSeconds <3-120>` | 设置读取超时秒数 |
| `/music admin set searchLimit <3-20>` | 设置列表分页大小 |
| `/music admin set maxQueueSize <1-200>` | 设置队列上限 |
| `/music admin set playlistQueueLimit <1-100>` | 设置单次导入歌单的最大歌曲数 |
| `/music admin set queueCacheSize <0-20>` | 设置服务端队列预缓存数量 |
| `/music admin set enableLootMusicDiscs <true\|false>` | 是否允许战利品箱生成随机音乐唱片 |
| `/music admin set lootMusicDiscChance <0.0-1.0>` | 设置战利品箱生成随机音乐唱片的概率 |
| `/music admin set lootMusicDiscCount <0-5>` | 设置每个战利品箱最多生成的随机音乐唱片数量 |
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
  "enableLootMusicDiscs": true,
  "lootMusicDiscChance": 0.3,
  "lootMusicDiscCount": 1,
  "voteSkipPercent": 0.6
}
```

## 构建

```powershell
.\gradlew.bat clean build
```

构建产物示例：

```text
minecraft-music-player-2.0.3-fabricmc1.21.11.jar
```

## 发布

### 使用 tag 发布

```bash
git tag v2.0.3
git push origin v2.0.3
```

### 手动发布

在 GitHub 仓库的 `Actions` 页面运行 `release` 工作流，并填写要发布的 tag。

## 说明

- 项目使用 Mojang 官方映射
- 交互基于聊天栏文本和点击操作，不依赖自定义界面
- 客户端播放器当前优先使用可直接播放的 mp3 音源
- 英文文档见 [README_EN.md](README_EN.md)

