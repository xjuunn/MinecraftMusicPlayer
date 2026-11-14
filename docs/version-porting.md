# 版本适配指南

本文档记录将模组移植到其他 Minecraft 版本时的分支策略、操作步骤与已知版本差异。适配新版本前必须先阅读本文档。

## 分支与工作流

- 每个适配版本使用独立分支，分支名即 Minecraft 版本号（如 `1.19.2`、`1.20.1`、`26.2`、`26.3`）。
- `master` 为最新版本主分支（当前 `26.4-snapshot-8`）。
- 适配新版本时，从**最接近的目标版本分支**切出新分支（例如适配 1.19.2 基于 `1.20.1`，适配 1.20.1 基于 `1.21.11`），而不是直接基于 master。
- 适配操作全部在新分支上完成。
- **默认禁止切换或修改其他分支。** 后续功能更新如需同步到各版本分支，使用 `cherry-pick` 将主分支提交挑选到目标分支，但必须由用户明确要求后才执行。
- **AI 禁止执行 `git commit`。** 修改完成后告知用户改了哪些文件，由用户自行提交。用户询问提交信息时，提供中文提交信息建议（可保留 `feat:` / `fix:` / `docs:` / `adapt:` / `release:` 等英文类型前缀，正文使用中文）。

## 适配步骤

1. 切出新版本分支。
2. 修改版本配置：
   - `gradle.properties`：`minecraft_version`、`fabric_api_version`（及必要时 `loader_version`）。
   - `build.gradle`：Java `release`/`source`/`target` 按目标版本调整（见下表）。
   - `src/main/resources/fabric.mod.json`：通常使用 `${minecraft_version}` 模板变量，无需改动；确认 `depends.java` 与 Java 版本一致。
3. 执行 `.\gradlew.bat clean compileJava` 下载该版本的 loom 映射 jar（首次会失败属正常，目的是生成 `.gradle/loom-cache/minecraftMaven/` 下的映射产物）。
4. 对不确定的 API，用 javap 对照映射 jar 验证，**禁止凭记忆猜测**：

   ```powershell
   $javap = 'C:\Users\<你>\.gradle\jdks\<jdk目录>\bin\javap.exe'
   $jar = 'C:\Space\Projects\MinecraftMusicPlayer\.gradle\loom-cache\minecraftMaven\net\minecraft\minecraft-common-<hash>\<version>-loom.mappings.*\<....>.jar'
   & $javap -classpath $jar net.minecraft.commands.CommandSourceStack
   ```

   常用类：`CommandSourceStack`、`JukeboxBlockEntity`、`ServerAdvancementManager`、`DimensionDataStorage`、`Options`、`ConfirmLinkScreen`、`NativeImage`、`ServerPlayer`。
5. 逐个修复编译错误，直到 `compileJava`、`compileClientJava` 通过。
6. 执行 `.\gradlew.bat clean build` 完整验证（含测试与 remap）。
7. 检查数据包资源的目录名与 JSON 格式是否符合目标版本（见下文）。
8. 告知用户改动摘要，由用户提交。

## 构建与验证

- 提交/完成前至少执行：

  ```powershell
  .\gradlew.bat clean build
  ```

- Windows PowerShell 下中文编译错误可能乱码，可临时切换英文输出：

  ```powershell
  [Console]::OutputEncoding = [System.Text.Encoding]::GetEncoding(936)
  $env:JAVA_TOOL_OPTIONS = '-Duser.language=en -Duser.country=US'
  .\gradlew.bat clean build 2>&1 | Out-File "$env:TEMP\build.txt" -Encoding utf8
  $env:JAVA_TOOL_OPTIONS = $null
  ```

- 映射 jar 位置：`.gradle\loom-cache\minecraftMaven\net\minecraft\minecraft-common-*\<ver>-loom.mappings.*\*.jar`（客户端类在 `minecraft-clientOnly-*`）。

## 版本差异速查（Mojang 官方映射）

### 服务端 / 通用

| API | 新版本 | 旧版本（约 1.19.x–1.20.1） |
|---|---|---|
| `CommandSourceStack.sendSuccess` | `sendSuccess(Supplier<Component>, boolean)`（约 1.20.2+） | `sendSuccess(Component, boolean)`，去掉所有 `() ->` 包装 |
| `ItemStack.copyWithCount` | 存在（约 1.20.2+） | `copy()` 后 `setCount(1)` |
| `Entity.level()` / `player.level()` | 方法 | 公开字段 `entity.level` / `player.level` |
| `JukeboxBlockEntity` | `setRecordWithoutPlaying`、`getFirstItem()`（`ContainerSingleItem`） | 仅 `setRecord` / `getRecord` / `playRecord`，无 `ContainerSingleItem` |
| `ServerAdvancementManager` | `getAdvancement(ResourceLocation)`（1.20.1 及 1.19.2 实测均可） | 若编译报错改用 `get(...)` |
| `GameProfile.getName()` | 新 authlib 改为 `name()` | `getName()` |
| `SavedData.save` | 1.20.5+ 需 `save(CompoundTag, HolderLookup.Provider)` | `save(CompoundTag)` |
| `DimensionDataStorage.computeIfAbsent` | `computeIfAbsent(Function, Supplier, String)`（1.19.2、1.20.1 实测一致） | 同左 |
| `Options.chatLinks` / `chatLinksPrompt` | 方法访问器 `.chatLinks().get()`（1.19.2、1.20.1 实测可用） | 若不可用改为公开字段 |
| `Player.drop` | 三参 `drop(ItemStack, boolean, boolean)`（1.19.2 实测可用） | — |
| 权限判断 | `source.hasPermission(2)` | 避免 `Permissions.COMMANDS_ADMIN`（1.21+ 才有） |

### 客户端

| API | 新版本 | 旧版本 |
|---|---|---|
| 动态纹理 | 26.4-snapshot-8+ 移除 `TextureManager.register(Identifier, TextureResources)`，只能用 `registerProvider(id, new ExternalTextureProvider(resources))`；`ExternalTextureProvider.createTexture` 原样返回传入句柄，`releaseTexture` 是空实现，因此 `release(id)` **不再**释放纹理，淘汰/清空时必须自己 `TextureResources.close()`。26.4-snapshot-5～7 用 `TextureResources.from2dImage(Supplier<String>, NativeImage[, GpuSampler])` + `register(id, resources)`，不再调用 `upload()` | `new DynamicTexture(supplier, image)` + `texture.upload()` |
| `com.mojang.math.Axis` | `Axis.YP.rotationDegrees(...)`（1.19.3+） | 1.19.2 用 `com.mojang.math.Vector3f.YP` 等 |
| `ConfirmLinkScreen.confirmLinkNow(url, parent, warn)` | 存在 | 用构造器 `new ConfirmLinkScreen(accepted -> { if (accepted) openUri(...); setScreen(parent); }, url, false)`；**回调必须在 accepted 为 true 时真正打开 URL 并恢复父屏幕** |
| `Util.getPlatform().openUri` | 顶层 `net.minecraft.Util` | 同左 |
| Fabric 网络接收器 | 1.19.x–1.20.x：`(server, player, handler, buf, sender)` + `FriendlyByteBuf`/`PacketByteBufs` | 极新版本使用 `CustomPacketPayload`，按分支现状对齐，不要跨风格混用 |
| `WorldRenderContext` | `matrixStack()` / `consumers()`（fabric-rendering-v1） | 1.19.2 实测可用 |
| `RenderType` / `BufferUploader` | `entityCutoutNoCull(ResourceLocation)`、`setupRenderState`/`clearRenderState`、`drawWithShader` | 1.19.2/1.20.1 实测可用 |
| `VertexConsumer` | 旧式 `vertex...color/uv/overlayCoords/uv2/normal/endVertex` | 1.21.2+ 改为 `addVertex` 风格，按目标版本选 |
| 命令补全 | 26.4-snapshot-8+ 移除 `net.minecraft.commands.SharedSuggestionProvider`，静态方法迁到 `net.minecraft.commands.suggestions.SuggestionContext`（`suggest`/`suggestResource` 签名一致） | `net.minecraft.commands.SharedSuggestionProvider.suggest(...)` |

### 命令树必须可序列化（26.4-snapshot-8+）

26.4-snapshot-8 新增 `net.minecraft.commands.SuggestionBox` / `ParseBox` / `SerializableArgument`。`Commands` 构造时会调用 `SuggestionBox.fromFullTree(dispatcher)` **遍历整棵命令树**做转换校验，任何节点不合规都会抛 `UnsupportedOperationException`，表现为**创建新世界/启动服务器即崩溃**（与具体命令是否被执行无关）。

三个约束，缺一即崩：

| 位置 | 崩溃信息 | 必须改成 |
|---|---|---|
| `.requires(predicate)` | `Unsupported predicate: ...use PermissionProviderCheck instead` | `Commands.hasPermission(CommandPermissionLevels.LEVEL_*)`，它返回 `PermissionProviderCheck` |
| `Commands.argument(name, type)` | `Invalid argument type: string()` / `word()` / `integer()` | MC 包装类 `StringArgument.word()`、`IntegerArgument.integer()`、`DoubleArgument.doubleArg()`、`BooleanArgument.bool()`；`EntityArgument` 天然合规 |
| `.suggests(provider)` | 与上同类，裸 lambda 不可序列化 | `SuggestLiteralString.suggestLiteral("a", "b")`；复杂补全需实现 `SerializableCustomSuggestion` |

要点：

- `CommandBox.convertNodePermissionCheck` 只接受 `PermissionProviderCheck`，裸 lambda（即使内部只是查权限）一律拒绝。
- `PermissionProviderCheck` 只能承载 `PermissionCheck`，而 `PermissionCheckTypes` 只注册了 `always_pass` 与 `require` 两种，**没有 AND/OR/NOT 组合**。像「玩家 **或** 管理员」这种复合条件无法表达，只能退化为 `LEVEL_ALL`（所有人可见）后把分流逻辑放进 `executes` 里判断，或直接改成单一权限级别。
- `StringArgument` / `IntegerArgument` 等的 `word()` / `string()` / `integer()` / `getString()` / `getInteger()` 返回的仍是 brigadier `ArgumentType`，取值方法签名与原写法一致，所以调用方取证代码不用动，只换静态方法名。
- 判定某个分支是否受影响：查目标版本 jar 里是否存在 `net/minecraft/commands/SuggestionBox.class`。26.4-snapshot-7 及更早、1.21.x、26.1～26.3 都没有该类，**不需要改**。

#### 这是 mod 不合规，不是原版 bug

遇到这类崩溃时容易误判成"原版校验过严"。实测证据表明是**我们自己的命令树不合规**，必须修，不能因为"是原版的问题"而放过：

- `SerializableArgumentTypes` 注册了 **61 种**可序列化参数类型，`string` / `integer` / `double` / `bool` 全在内。这是 MC 为"命令树可序列化下发"做的系统性改造，不是疏漏。
- 扫描 `net/minecraft/server/commands/**` 与 `gametest/**` 全部原版命令类，**没有任何一处**把裸 `ArgumentType` 传给 `Commands.argument(...)`；原版 `.requires` 一律走 `Commands.hasPermission(...)`。原版命令树 100% 合规，`fromFullTree` 能顺利跑完，所以原版建世界不会崩。
- 唯一扫描命中 `StringArgumentType` 的 `DataPackCommand`，实际只是调用 `StringArgumentType.escapeIfRequired(String)` 这个字符串转义工具方法，没有当作参数类型使用，不构成反例。
- 异常信息直接带我方类名，可作为归属依据：`Unsupported predicate: com.junhsiun.musicplayer.command.MusicCommands$$Lambda/...`
- 危害等级：`fromFullTree` 遍历整棵树，**一个不合规节点就让整个 `Commands` 构造失败**，表现为玩家无法创建任何世界、服务器完全起不来，而不是"某条命令不可用"。

### Mixin

- 低版本可能缺少高版本依赖的事件/mixin 目标（如 `LevelEventHandler` 播唱片 LevelEvent 1010）。服务器侧若改用 `setRecordWithoutPlaying`/`setRecord`（不触发原版唱片事件与声音），对应 LevelEvent mixin 可整体删除，并同步精简 `mixins.json`。
- 每删一个 mixin，确认没有其他代码路径依赖它。

### 快照版本（snapshot）

适配快照版本时注意两点：

1. `minecraft_version` 填快照全名（如 `26.4-snapshot-5`），用于 Loom 拉取映射；但 `fabric.mod.json` 的 `minecraft` 依赖不能直接写 `~26.4-snapshot-5`。Fabric Loader 会把游戏版本规范化为 `26.4-alpha.5` 形式，`~26.4-snapshot-5` 解析后不匹配，服务器启动会报 `HARD_DEP_INCOMPATIBLE`。因此 `gradle.properties` 另设 `minecraft_dependency=~26.4-`，由 `build.gradle` 的 `processResources` 展开注入。
2. Loom 版本要求会随快照提高（如 1.19.x 需要 Gradle 9.8+），升级时同步改 `gradle/wrapper/gradle-wrapper.properties` 的 `distributionUrl`。

## 数据包 / 资源格式差异

### Advancement（成就）

| 项目 | 1.20.5+（24w21a 起） | ≤1.20.4 |
|---|---|---|
| 目录名 | `data/<ns>/advancement/`（单数） | `data/<ns>/advancements/`（复数） |
| 图标键 | `"icon": { "id": "minecraft:torch" }` | `"icon": { "item": "minecraft:torch" }` |

用错格式的表现：advancement 定义静默加载失败，运行时 `getAdvancement(...)` 返回 null，成就不发放（服务端日志有 warn）。

同类目录改名单数的还有：`recipes`→`recipe`、`loot_tables`→`loot_table`、`functions`→`function`、`structures`→`structure`、`predicates`→`predicate`、`item_modifiers`→`item_modifier`。

### 物品可用性（音乐唱片）

`SUPPORTED_DISCS` 必须只包含目标版本实际存在的唱片：

| 物品 | 加入版本 |
|---|---|
| `MUSIC_DISC_PIGSTEP` | 1.19 |
| `MUSIC_DISC_OTHERSIDE` | 1.19 |
| `MUSIC_DISC_RELIC` | 1.19.3 |
| `MUSIC_DISC_5` | 1.20.5 |

1.19.2 需从集合中剔除 `RELIC` 与 `5`。

## 已完成版本分支参考

| 分支 | Minecraft | Java | Fabric API（示例） |
|---|---|---|---|
| `master` | 26.4-snapshot-8 | 25 | `0.166.2+26.4` |
| `26.4-snapshot-8` | 26.4-snapshot-8 | 25 | `0.166.2+26.4` |
| `26.4-snapshot-7` | 26.4-snapshot-7 | 25 | `0.165.4+26.4` |
| `26.4-snapshot-5` | 26.4-snapshot-5 | 25 | — |
| `26.3` | 26.3 | 25 | — |
| `26.2` | 26.2 | 25 | — |
| `26.1` | 26.1 | 25 | — |
| `1.21.11` | 1.21.11 | 21+ | — |
| `1.20.1` | 1.20.1 | 17 | `0.92.x+1.20.1` |
| `1.19.2` | 1.19.2 | 17 | `0.77.x+1.19.2` |

- `fabric_api_version` 以 [fabricmc.net/develop](https://fabricmc.net/develop) 查询为准，解析失败（TLS/404）时重试或换邻近版本。
- loader 版本注意：fabric-loader 0.15+ 需要 Java 21 运行时；低版本 MC 常见 Java 17 环境，如需兼容运行时可考虑 pin 到 0.14.x（仅在用户要求时调整）。
