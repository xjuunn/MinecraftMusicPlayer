# Minecraft Music Player

[![中文](https://img.shields.io/badge/README-中文-blue)](README.md) [![English](https://img.shields.io/badge/README-English-orange)](README_EN.md)

Minecraft Music Player 是一个 Fabric 双端音乐模组，提供网易云音乐搜索、点播、共享队列、歌单分批播放、音乐唱片刻录与唱片机播放功能。

服务端负责命令、队列、同步与唱片机控制；客户端负责音频播放。双端协作，非纯服务端模组。

[Demo](https://www.youtube.com/watch?v=4kOa9Ak62Bk)

## 功能概览

- 搜索歌曲、作者、歌单、用户
- 点播单曲，自动加入队列或立即播放
- 歌单播放模式：后台分批加载，完整播完整个歌单无需手动操作
- 双队列系统：单点队列（高优）优先播放，歌单队列（低优）自动补充
- 投票跳过 / 点歌人直跳 / 管理员直跳
- 歌曲名点击在浏览器中打开
- 刻录自定义音乐唱片，放入唱片机播放 URL 音乐
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

| 命令 | 说明 |
| --- | --- |
| `/music now` | 查看当前播放与进度 |
| `/music play song <ID>` | 点播单曲 |
| `/music play playlist <ID>` | 加载歌单播放模式 |
| `/music skip` | 跳过当前歌曲（点歌人/管理员直跳，其他投票） |
| `/music queue` | 查看单点队列 |
| `/music queue promote <ID>` | 提升到下一首 |
| `/music queue remove <ID>` | 从队列移除 |
| `/music playlist` | 查看歌单状态 |
| `/music playlist list` | 查看已加载的歌单曲目 |
| `/music playlist stop` | 停止歌单模式 |
| `/music search song <关键词>` | 搜索歌曲 |
| `/music search artist <关键词>` | 搜索作者 |
| `/music search playlist <关键词>` | 搜索歌单 |
| `/music search user <关键词>` | 搜索用户 |
| `/music view playlist <ID>` | 查看歌单详情，`<ID> page <N>` 指定页 |
| `/music view artist <ID>` | 查看作者详情，`<ID> page <N>` 指定页 |
| `/music view user <ID>` | 查看用户歌单，`<ID> page <N>` 指定页 |
| `/music random` | 随机 10 首热门音乐 |
| `/music burn song <ID>` | 刻录唱片 |
| `/music join` | 加入当前播放 |
| `/music leave` | 退出当前播放 |
| `/music mute once` | 暂时静音 |
| `/music help [子命令]` | 查看帮助 |

## 管理员命令

| 命令 | 说明 |
| --- | --- |
| `/music skip` | 直接跳过 |
| `/music stop` | 完全停止播放 |
| `/music queue clear` | 清空单点队列 |
| `/music admin reload` | 重载配置 |
| `/music admin status` | 查看配置 |

配置项通过 `/music admin set <键> <值>` 修改，支持项同配置文件。

## 配置文件

`config/minecraft-music-player.json`，默认值见文件。

## 构建

```powershell
.\gradlew.bat clean build
```
