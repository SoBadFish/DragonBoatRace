# DragonBoatRace 项目指南

## 项目结构

两个独立的 Maven 项目（非父子多模块 reactor）：

| 目录 | 插件名 | 职责 |
|------|--------|------|
| `DragonBoatRace/` | DragonBoat | 核心模型——龙舟实体、导弹、奖励箱、自定义物品 |
| `DragonBoatGame/` | DragonBoatGame | 游戏框架——房间管理、DLC 系统、竞速模式逻辑 |

**依赖关系**：DragonBoatGame 通过 `<systemPath>` 依赖 DragonBoatRace 的 jar。对 DragonBoat 声明 `softdepend`。

## 构建命令

**必须先构建 DragonBoatRace，再构建 DragonBoatGame：**

```bash
cd DragonBoatRace && mvn clean package
cd ..\DragonBoatGame && mvn clean package
```

- 没有测试（`src/test/` 为空，未配置 surefire 插件），不要运行 `mvn test`
- `mvn deploy` 在 DragonBoatGame 中配置为部署到 file 仓库

## 平台

- **Java 17**，Nukkit-MOT 服务器（`cn.nukkit:Nukkit:MOT-SNAPSHOT`）
- `plugin.yml` 中 `api: ["1.0.11","3.0.0"]`
- Nukkit 依赖为 `provided` scope，运行时由服务端提供
- 相关技能文件：`.opencode/skills/nukkit-mot-plugin-dev-skill/SKILL.md`

## 关键架构事实

### DragonBoatGame 的 system-scope 依赖
`lib/` 目录中的 jar（ScoreboardAPI、RsNPC、Tips 等）通过 `pom.xml` 中的 `<scope>system</scope>` 引入。**不要**将它们替换为 Maven 仓库坐标——它们可能是定制版或有特殊构建。

### Maven replacer 插件自动重写 plugin.yml
`DragonBoatGame/pom.xml` 中的 `maven-replacer-plugin` 在 `process-sources` 阶段自动将 `plugin.yml` 的 `version` 替换为 `pom.xml` 的 `<version>`。**不要手动编辑** `plugin.yml` 中的版本号——构建时会被覆盖。

### DLC 扩展系统
游戏逻辑通过 `IGameRoomDlc` 接口以 DLC 形式注入。核心竞速实现是 `DragonBoatGame/.../race/DragonBoatRaceDlc.java`（~920 行），通过 `DragonBoatRoomCreateListener` 在每个房间创建时注入。其他 DLC 通过 `GameRoomDlcManager.register()` 注册。

### 配置驱动设计
`room.yml`、`team.yml`、`shop.yml`、`event.yml`、`items.yml` 中的 YAML 配置与 Java 配置类（`*Config.java`）结构对应。修改配置 schema 时必须同步更新两处。

### 事件系统（event.yml）
内置事件类型：`custom`（自定义）、`effect`（药水效果）、`command`（执行指令）。事件按秒定时触发。`custom` 事件委托给 `roomEventList.yml` 中的事件列表，支持 `while`/`random`/`foreach` 模式。详见 `DragonBoatGame/src/main/resources/readme.txt`。

### 资源包
两个插件都在 `src/main/resources/assets/resource_pack/` 中包含 Minecraft 资源包（实体模型、纹理、物品图标）。修改自定义实体或物品时需同步更新这些资源。

## 入口点

| 入口 | 类 |
|------|-----|
| DragonBoatRace 主类 | `org.sobadfish.dragonboat.DragonBoatMainClass` |
| DragonBoatGame 主类 | `org.sobadfish.dragonboatgame.DragonBoatGameMain` |
| 核心竞速 DLC | `org.sobadfish.dragonboatgame.race.DragonBoatRaceDlc` |
| 总管理器 | `org.sobadfish.dragonboatgame.manager.TotalManager`（onEnable 时初始化所有子系统） |

## 代码约定

- 语言文件：`language.ini`（中文，约 400 行），使用 `§` 颜色代码
- 包名组织：`org.sobadfish.dragonboat(race|game).<module>` 按功能分包（entity/、item/、manager/、panel/、room/ 等）
- 游戏内命令：`/dragonboat`（实体操作）、`/dbg`（玩家模式）、`/dbga`（管理员模式）、`/dbgs`（聊天）
- 无 CI/CD，无 Docker，无 shell 脚本

## 线程与持久化铁律

- `ThreadManager` 里的 runnable（RoomLoad / PluginMaster / Top / RandomJoin）跑在**后台线程池**，禁止在里面直接操作实体、玩家背包、传送、区块加载。一律用 `ThreadManager.runOnMainThread(...)` 回主线程。`DragonBoatRaceDlc.onGameUpdate` 就是由 RoomLoadRunnable 驱动的，之前它跑在后台线程里，导致实体位置和主 tick 抢写。
- 玩家进入房间前的背包由 `PlayerInventoryBackupManager` 持久化到 `plugins/DragonBoatGame/player-inventory-backup.json`（主背包 / 盔甲 / 副手 / 末影箱 + 经验 + 选中格）。还原成功后该条存档会被删除，**不要手动清空这个文件**，否则重启后玩家会丢物品。
- 新增任何「清空玩家背包」的逻辑前，先确认备份已经写好，否则重启后对不上账。
