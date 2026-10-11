# 版本适配指南

本文档记录将模组移植到其他 Minecraft 版本时的分支策略、操作步骤与已知版本差异。适配新版本前必须先阅读本文档。

## 分支与工作流

- 每个适配版本使用独立分支，分支名即 Minecraft 版本号（如 `1.19.2`、`1.20.1`、`26.2`、`26.3`）。
- `master` 为最新版本主分支（当前 `26.3`）。
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
| `JukeboxBlockEntity` | `setRecordWithoutPlaying`、`getFirstItem()`（`ContainerSingleItem`） | 仅 `setRecord` / `getRecord` / `playRecord`，无 `ContainerSingleItem`；1.18.2 同左（类在 `block.entity` 包，无 `playRecord` 时用 `setRecord`） |
| `ServerAdvancementManager` | `getAdvancement(ResourceLocation)`（1.20.1、1.19.2、1.18.2 实测均可） | 若编译报错改用 `get(...)` |
| `GameProfile.getName()` | 新 authlib 改为 `name()` | `getName()` |
| `SavedData.save` | 1.20.5+ 需 `save(CompoundTag, HolderLookup.Provider)` | `save(CompoundTag)` |
| `DimensionDataStorage.computeIfAbsent` | `computeIfAbsent(Function, Supplier, String)`（1.19.2、1.20.1 实测一致） | 同左 |
| `Options.chatLinks` / `chatLinksPrompt` | 方法访问器 `.chatLinks().get()`（1.19.2、1.20.1 实测可用） | 1.18.2 为公开布尔字段 `client.options.chatLinks`（无 `.get()`） |
| `Player.drop` | 三参 `drop(ItemStack, boolean, boolean)`（1.19.2、1.18.2 实测可用） | — |
| 权限判断 | `source.hasPermission(2)` | 避免 `Permissions.COMMANDS_ADMIN`（1.21+ 才有） |
| `Player.sendSystemMessage` | 存在（1.19+） | 1.18.2 用 `displayClientMessage(component, false)`（false=聊天栏，true=action bar） |
| `PlayerList.broadcastSystemMessage` | 存在（1.19+） | 1.18.2 用 `broadcastMessage(Component, ChatType.CHAT, UUID)` |
| `net.minecraft.util.RandomSource` | 存在（1.19+） | 1.18.2 用 `java.util.Random`（`Level.random`、`getRandom()` 均返回 `java.util.Random`） |
| `ContainerEntity`（矿车容器） | 存在（1.19+） | 1.18.2 用 `AbstractMinecartContainer`，战利品用 `unpackLootTable(Player)` |
| `Component.literal` / `translatable` / `empty` | 静态工厂方法（1.19+） | 1.18.2 用 `new TextComponent(...)` / `new TranslatableComponent(key, args...)` / `new TextComponent("")` |
| `Component.getContents()` | 返回 contents 对象（如 `TranslatableContents.getKey()`） | 1.18.2 返回 `String`；判断翻译组件用 `instanceof TranslatableComponent` + `getKey()` |
| `CommandBuildContext` | 存在（1.19+） | 1.18.2 无此类；Fabric 命令用 v1 `CommandRegistrationCallback`（回调签名 `(dispatcher, dedicated)`），去掉 `CommandBuildContext` 与 `Commands.CommandSelection` 参数 |

### 客户端

| API | 新版本 | 旧版本 |
|---|---|---|
| `com.mojang.math.Axis` | `Axis.YP.rotationDegrees(...)`（1.19.3+） | 1.19.2 用 `com.mojang.math.Vector3f.YP` 等 |
| `ConfirmLinkScreen.confirmLinkNow(url, parent, warn)` | 存在 | 用构造器 `new ConfirmLinkScreen(accepted -> { if (accepted) openUri(...); setScreen(parent); }, url, false)`；**回调必须在 accepted 为 true 时真正打开 URL 并恢复父屏幕** |
| `Util.getPlatform().openUri` | 顶层 `net.minecraft.Util` | 同左 |
| Fabric 网络接收器 | 1.19.x–1.20.x：`(server, player, handler, buf, sender)` + `FriendlyByteBuf`/`PacketByteBufs` | 极新版本使用 `CustomPacketPayload`，按分支现状对齐，不要跨风格混用 |
| `WorldRenderContext` | `matrixStack()` / `consumers()`（fabric-rendering-v1） | 1.19.2 实测可用 |
| `RenderType` / `BufferUploader` | `entityCutoutNoCull(ResourceLocation)`、`setupRenderState`/`clearRenderState`、`drawWithShader` | 1.19.2/1.20.1 实测可用；1.18.2 的 `BufferBuilder.end()` 返回 `void`（无 `RenderedBuffer`），绘制用 `Tesselator.getInstance().end()` 或 `BufferUploader.end(builder)` |
| `VertexConsumer` | 旧式 `vertex...color/uv/overlayCoords/uv2/normal/endVertex` | 1.21.2+ 改为 `addVertex` 风格，按目标版本选 |

### Mixin

- 低版本可能缺少高版本依赖的事件/mixin 目标（如 `LevelEventHandler` 播唱片 LevelEvent 1010）。服务器侧若改用 `setRecordWithoutPlaying`/`setRecord`（不触发原版唱片事件与声音），对应 LevelEvent mixin 可整体删除，并同步精简 `mixins.json`。
- 每删一个 mixin，确认没有其他代码路径依赖它。

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
| `MUSIC_DISC_PIGSTEP` | 1.16 |
| `MUSIC_DISC_OTHERSIDE` | 1.18 |
| `MUSIC_DISC_5` | 1.19 |
| `MUSIC_DISC_RELIC` | 1.20 |

- 1.19.2 需从集合中剔除 `RELIC` 与 `5`。
- 1.18.2 已含 `PIGSTEP` 与 `OTHERSIDE`（javap 实测），仍需剔除 `RELIC` 与 `5`（代码继承 1.19.2 分支时已满足）。

## 已完成版本分支参考

| 分支 | Minecraft | Java | Fabric API（示例） |
|---|---|---|---|
| `master` | 26.3 | 25 | 随分支更新 |
| `26.2` | 26.2 | 25 | — |
| `26.1` | 26.1 | 25 | — |
| `1.21.11` | 1.21.11 | 21+ | — |
| `1.20.1` | 1.20.1 | 17 | `0.92.x+1.20.1` |
| `1.19.2` | 1.19.2 | 17 | `0.77.x+1.19.2` |
| `1.18.2` | 1.18.2 | 17 | `0.77.0+1.18.2` |

- `fabric_api_version` 以 [fabricmc.net/develop](https://fabricmc.net/develop) 查询为准，解析失败（TLS/404）时重试或换邻近版本。
- loader 版本注意：fabric-loader 0.15+ 需要 Java 21 运行时；低版本 MC 常见 Java 17 环境，如需兼容运行时可考虑 pin 到 0.14.x（仅在用户要求时调整）。
