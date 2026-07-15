# AGENTS.md

## 仓库定位

这是一个 Fabric 双端音乐播放模组项目。

- 服务端负责命令、队列、同步与管理配置
- 客户端负责接收播放控制并执行本地音频播放
- 默认适配 Minecraft `26.2`、Java `25`、Mojang 官方映射

## 通用规则

- 如果不确定修改是否必要或如何修改，先问用户，不擅自猜测。
- 只修改实现目标所必需的文件，禁止修改无关代码。
- 当需要了解 API 返回字段含义时，允许自动请求接口获取数据，但不能猜字段的意思，应该询问用户。

## API 测试 ID

### 歌单 ID
- `2953901152`（269 首）
- `6804054976`
- `3229040573`

### 音乐 ID
- `3347088459`
- `33856275`
- `1940308971`

### 用户 ID
- `1732443319`

## API 端点参考

- 歌单详情: `/playlist/detail?id={id}` → `playlist.trackCount`（总曲目数）
- 歌单曲目: `/playlist/track/all?id={id}&limit={n}&offset={offset}` → 分页获取所有曲目
- 音乐详情: `/song/detail?ids={id}` → `songs[].id`, `songs[].name`, `songs[].ar[].id`, `songs[].ar[].name`

## 开发约束

- 默认使用 UTF-8 无 BOM 编码
- 文档默认中文，补充独立英文文档
- 命令、配置项和聊天提示应保持简洁、一致、可直接操作
- 任何管理员配置都必须能通过命令完成，并同步写入配置文件
- 运行时依赖必须随构建产物一并打包，避免服务器或客户端缺类
- 优先兼容专用服务器场景，但要明确客户端是否必须安装

## 代码结构

- `src/main/java`: 服务端与共用逻辑
- `src/client/java`: 客户端播放逻辑
- `config`: 配置读写与默认值
- `platform`: 第三方音乐平台 API 适配
- `service`: 队列、播放流程与业务状态
- `command`: Brigadier 命令注册与交互输出
- `model`: 平台数据模型与视图模型
- `network`: 自定义网络载荷
- `util`: 轻量消息与格式化工具

## 提交要求

- 每完成一个明确任务就提交一次
- 提交信息使用简洁英文动词前缀，例如 `feat:`、`fix:`、`docs:`、`release:`
- 不要把无关修改混入同一次提交

## 验证要求

提交前至少执行：

```powershell
.\gradlew.bat clean build
```

如果增加 GitHub Actions 或发布流程，还应确保：

- `build.yml` 使用 Java 25
- `release.yml` 支持基于 git tag 的正式发布
- Release 只上传当前版本的 remap 主 jar，避免带上旧产物
