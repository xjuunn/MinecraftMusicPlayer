# Minecraft Music Player

Minecraft Music Player 是一个面向 Fabric 服务器与客户端的网易云音乐点歌模组，适配 Minecraft `1.21.11`。

它提供统一的点歌、搜索、播放队列、歌单导入、自动切歌、投票下一首和管理员配置能力。服务器负责管理命令、队列和同步；客户端负责实际音频播放。

## 特性

- 支持网易云音乐歌曲搜索、作者搜索、歌单搜索、用户搜索
- 支持查看作者热门歌曲、查看歌单详情、查看用户歌单
- 支持玩家点歌、导入歌单、查看当前播放与播放队列
- 支持自动播放下一首
- 支持玩家投票切歌
- 支持管理员切歌、停止播放、清空队列、热重载配置
- 支持默认网易云 API 地址，也支持管理员切换为自定义 API 服务
- 搜索和加载过程带有简洁的聊天提示
- 点击聊天消息即可点歌、查看歌单或查看用户歌单

## 运行方式

这个模组不是纯服务端音频模组。

- 服务器安装本模组后，提供命令、队列和同步逻辑
- 客户端安装本模组后，才能真正听到音乐
- 如果客户端没有安装，本地不会播放音乐，但服务器命令仍然存在

建议部署方式：

1. 服务器安装 `Minecraft Music Player`
2. 需要听歌的客户端也安装 `Minecraft Music Player`
3. 服务器或客户端网络可访问一个网易云 API 服务

## 依赖要求

- Minecraft `1.21.11`
- Fabric Loader `0.18.4` 或更高版本
- Fabric API `0.141.3+1.21.11` 或兼容版本
- Java `21`
- 一个可用的 [NeteaseCloudMusicApi](https://github.com/Binaryify/NeteaseCloudMusicApi) 实例，默认地址为 `http://127.0.0.1:3000`

## 安装

### 服务器

将构建产物和 `Fabric API` 放入服务器 `mods` 目录。

### 客户端

将构建产物和 `Fabric API` 放入客户端 `mods` 目录。

### 网易云 API

默认配置使用：

```text
http://127.0.0.1:3000
```

如果你使用反向代理或独立部署的 API 服务，可由管理员通过命令修改。

## 玩家命令

| 命令 | 说明 |
| --- | --- |
| `/music` | 显示帮助 |
| `/music now` | 查看当前播放 |
| `/music queue` | 查看当前队列 |
| `/music join` | 加入当前播放 |
| `/music leave` | 退出当前播放 |
| `/music mute once` | 退出当前歌曲播放 |
| `/music vote next` | 投票切到下一首 |
| `/music play song <歌曲ID>` | 直接点播单曲 |
| `/music play playlist <歌单ID>` | 将歌单加入队列 |
| `/music search song <关键词>` | 搜索歌曲 |
| `/music search artist <关键词>` | 搜索作者 |
| `/music search author <关键词>` | 搜索作者，`artist` 的别名 |
| `/music search playlist <关键词>` | 搜索歌单 |
| `/music search user <关键词>` | 搜索用户 |
| `/music view artist <作者ID>` | 查看作者热门歌曲 |
| `/music view author <作者ID>` | 查看作者热门歌曲，`artist` 的别名 |
| `/music view playlist <歌单ID>` | 查看歌单详情 |
| `/music view user <用户ID>` | 查看用户歌单 |

## 管理员命令

| 命令 | 说明 |
| --- | --- |
| `/music admin reload` | 重新加载配置 |
| `/music admin status` | 查看当前配置状态 |
| `/music admin clearqueue` | 清空待播队列 |
| `/music next` | 立即切到下一首 |
| `/music stop` | 停止当前播放 |
| `/music admin set baseUrl <地址>` | 设置网易云 API 地址，传入 `default` 恢复默认值 |
| `/music admin set allowCustomServer <true\|false>` | 是否允许使用自定义 API 地址 |
| `/music admin set allowSongRequest <true\|false>` | 是否允许玩家点歌 |
| `/music admin set allowPlaylistRequest <true\|false>` | 是否允许玩家导入歌单 |
| `/music admin set autoAdvance <true\|false>` | 是否自动播放下一首 |
| `/music admin set announceQueueChanges <true\|false>` | 是否广播点歌入队消息 |
| `/music admin set showLoadingHints <true\|false>` | 是否显示搜索和加载提示 |
| `/music admin set searchLimit <3-20>` | 设置搜索结果数量 |
| `/music admin set maxQueueSize <1-200>` | 设置播放队列上限 |
| `/music admin set playlistQueueLimit <1-100>` | 设置单次导入歌单时最多入列的歌曲数 |
| `/music admin set voteSkipPercent <0.1-1.0>` | 设置投票切歌所需比例 |

## 配置文件

配置文件路径：

```text
config/minecraft-music-player.json
```

默认配置示例：

```json
{
  "neteaseBaseUrl": "http://127.0.0.1:3000",
  "allowCustomServer": true,
  "allowSongRequest": true,
  "allowPlaylistRequest": true,
  "autoAdvance": true,
  "announceQueueChanges": true,
  "showLoadingHints": true,
  "searchLimit": 8,
  "maxQueueSize": 40,
  "playlistQueueLimit": 20,
  "voteSkipPercent": 0.6
}
```

## 构建

```powershell
.\gradlew.bat clean build
```

构建产物示例：

```text
minecraft-music-player-2.0.0-fabricmc1.21.11.jar
```

## GitHub Release

仓库已支持通过 GitHub Actions 发布版本。

### tag 发布

```bash
git tag v2.0.0
git push origin v2.0.0
```

### 手动发布

在 GitHub 仓库的 `Actions` 页面运行 `release` 工作流，并填写要发布的 tag。

## 说明

- 本模组默认使用 Mojang 官方映射
- 聊天交互为简洁文本按钮，不依赖自定义界面
- 客户端播放器基于 `JLayer`，适合极简场景的在线音频播放

## 英文文档

英文说明见 [README_EN.md](README_EN.md)。