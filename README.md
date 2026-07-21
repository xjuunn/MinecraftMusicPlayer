# Minecraft Music Player

[![中文](https://img.shields.io/badge/README-中文-blue)](README.md) [![English](https://img.shields.io/badge/README-English-orange)](README_EN.md)

Minecraft Music Player 是一个 Fabric 双端音乐模组，提供网易云音乐搜索、点播、共享队列、歌单/播客分批播放、音乐唱片刻录与唱片机播放功能。

服务端负责命令、队列、同步与唱片机控制；客户端负责音频播放。双端协作，非纯服务端模组。

[Demo](https://www.youtube.com/watch?v=4kOa9Ak62Bk)

## 功能概览

- 搜索歌曲、作者、歌单、播客(电台)、用户
- 点播单曲，自动加入队列或立即播放
- 歌单/播客播放模式：后台分批加载，完整播完整个歌单无需手动操作
- **播客(电台)支持**：查看播客详情、节目列表，播放单个节目或整个播客
- **播放顺序**：正序 / 倒序 / 随机，适用于歌单和播客
- 双队列系统：单点队列（高优）优先播放，歌单队列（低优）自动补充
- 投票跳过 / 点歌人直跳 / 管理员直跳
- **进度控制**：快进 / 快退 / 暂停 / 继续，支持可视化进度条
- 歌曲名/播客名点击在浏览器中打开
- **链接识别**：自动解析 `music.163.com` 的单曲、歌单、播客链接
- 刻录自定义音乐唱片，放入唱片机播放 URL 音乐
- 唱片机音乐可随距离移动自动切换（玩家离开唱片机自动切回全局，靠近时恢复唱片机）
- 实时歌词显示（全局 / 唱片机），支持每玩家独立开关
- 战利品箱随机音乐唱片
- 所有列表支持分页与可点击导航

## 依赖要求

- Minecraft `26.2`
- Fabric Loader `0.19.3` 或更高
- Fabric API `0.154.2+26.2` 或兼容版本
- Java `25`
- 可访问的网易云音乐 API 服务，默认 `https://mycelis.dpdns.org/`

## 安装

服务端和需要听歌的客户端均需安装本模组及 Fabric API。

## 玩家命令

### 播放控制

| 命令 | 说明 |
| --- | --- |
| `/music now` | 查看当前播放、进度条、暂停状态 |
| `/music seek <秒数>` | 快进/快退（正数快进，负数快退） |
| `/music skip` | 跳过当前歌曲（点歌人/管理员直跳，其他投票） |
| `/music mute once` | 暂时静音当前歌曲 |

### 点播

| 命令 | 说明 |
| --- | --- |
| `/music play song <ID>` | 点播单曲 |
| `/music play playlist <ID>` | 加载歌单播放模式 |
| `/music play program <ID>` | 播放播客的单个节目 |
| `/music play radio <ID>` | 播放整个播客（所有节目入列） |

### 队列管理

| 命令 | 说明 |
| --- | --- |
| `/music queue` | 查看单点队列 |
| `/music queue promote <ID>` | 提升到下一首 |
| `/music queue remove <ID>` | 从队列移除 |

### 歌单与播客

| 命令 | 说明 |
| --- | --- |
| `/music playlist` | 查看歌单状态与播放顺序 |
| `/music playlist list` | 查看已加载的歌单曲目 |
| `/music playlist stop` | 停止歌单模式 |
| `/music playlist order` | 查看当前播放顺序 |
| `/music playlist order sequential` | 正序播放 |
| `/music playlist order reverse` | 倒序播放 |
| `/music playlist order shuffle` | 随机播放 |
| `/music radio` | 播客中心 |
| `/music radio hot [页数]` | 热门播客 |
| `/music radio categories` | 播客分类 |

### 搜索

| 命令 | 说明 |
| --- | --- |
| `/music search song <关键词>` | 搜索歌曲 |
| `/music search artist <关键词>` | 搜索作者 |
| `/music search playlist <关键词>` | 搜索歌单 |
| `/music search radio <关键词>` | 搜索播客 |
| `/music search user <关键词>` | 搜索用户 |

### 查看详情

| 命令 | 说明 |
| --- | --- |
| `/music view playlist <ID>` | 查看歌单详情 |
| `/music view artist <ID>` | 查看作者详情 |
| `/music view user <ID>` | 查看用户歌单 |
| `/music view radio <ID>` | 查看播客详情与节目列表 |
| `/music view program <ID>` | 查看节目详情 |
| `/music view <URL>` | 自动识别 `music.163.com` 链接（支持 `/song`、`/playlist`、`/djradio`、`/dj`） |

### 其他

| 命令 | 说明 |
| --- | --- |
| `/music random` | 随机 10 首热门音乐 |
| `/music burn song <ID>` | 刻录唱片 |
| `/music join` | 加入当前播放 |
| `/music leave` | 退出当前播放 |
| `/music lyrics` | 切换实时歌词显示 |
| `/music lyrics on/off` | 开启/关闭歌词 |
| `/music lyrics status` | 查看歌词状态 |
| `/music help [子命令]` | 查看帮助 |

## 管理员命令

| 命令 | 说明 |
| --- | --- |
| `/music skip` | 直接跳过 |
| `/music stop` | 完全停止播放 |
| `/music pause` | 暂停播放 |
| `/music resume` | 继续播放 |
| `/music queue clear` | 清空单点队列 |
| `/music config reload` | 重载配置并清空音源缓存 |
| `/music config status` | 查看配置 |
| `/music config clearqueue` | 清空队列 |

配置项通过 `/music config set <键> <值>` 修改，支持项同配置文件。

## 配置文件

`config/minecraft-music-player.json`，默认值见文件。

## 构建

```powershell
.\gradlew.bat clean build
```
