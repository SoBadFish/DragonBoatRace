# DragonBoatGame（小游戏框架 + 龙舟竞速）

> 本模块是 [DragonBoatRace](../README.md) 仓库的一部分，负责「房间 / 队伍 / 事件 / DLC」这套小游戏框架，以及龙舟竞速规则的具体实现。
> 安装、玩家指令、房间怎么配，看 [根目录 README](../README.md)；框架前身的原始开发文档见 [README_GameDemo.md](README_GameDemo.md)。

## 这个模块做什么

- **房间框架**：`RoomManager` 管理房间配置与实例，`GameRoom` 负责等待 → 开始 → 结算 → 关闭的完整生命周期；
- **竞速规则**：`DragonBoatRaceDlc` 实现起跑排位、计时、实时排名、冲线判定、统一结算、道具与重置按钮；
- **玩家数据**：`PlayerInfo`（游戏内状态）与 `PlayerData`（跨局统计），以及进房间前的**背包持久化存档**；
- **交互层**：`/dbg` 房间 GUI、`/dbga` 管理指令、`/dbgs` 房间喊话、计分板、箱子 / 商店面板；
- **扩展点**：`IGameRoomDlc` 把玩法以 DLC 形式挂到房间上，内置按秒触发的定时事件（`event.yml`）。

## 目录速查

| 包 | 作用 |
| --- | --- |
| `manager/` | 总管理器、房间管理、线程调度、语言、菜单、数据文件读写 |
| `manager/data/` | 玩家数据 / 排行榜 / tag / **`PlayerInventoryBackupManager`（背包存档）** |
| `room/` | `GameRoom`、地图信息、世界还原、浮空字 |
| `room/config/` | `room.yml` / `team.yml` / `items.yml` / `shop.yml` / `event.yml` 的映射类 |
| `player/` | `PlayerInfo`、`PlayerData`、计分板数据、队伍信息 |
| `race/` | **龙舟竞速**：`DragonBoatRaceDlc`、`DragonBoatRaceConfig`、房间创建监听 |
| `dlc/` | `IGameRoomDlc` 及 `IGameEndJudge` / `IGamePlayerScoreBoard` 等接口 |
| `item/` | 自定义按钮物品（退出房间、选队、重置龙舟…） |
| `panel/` | 箱子 / 商店 / 房间选择等假箱子界面 |
| `command/` | 玩家、管理、喊话三条指令 |
| `thread/` | 后台调度的 runnable（房间帧、浮空字、匹配、排行榜） |

## 入口类

| 入口 | 类 |
| --- | --- |
| 插件主类 | `org.sobadfish.dragonboatgame.DragonBoatGameMain` |
| 总管理器 | `org.sobadfish.dragonboatgame.manager.TotalManager`（onEnable 时初始化所有子系统） |
| 房间管理器 | `org.sobadfish.dragonboatgame.manager.RoomManager` |
| 房间实例 | `org.sobadfish.dragonboatgame.room.GameRoom` |
| 竞速核心 | `org.sobadfish.dragonboatgame.race.DragonBoatRaceDlc` |
| 背包存档 | `org.sobadfish.dragonboatgame.manager.data.PlayerInventoryBackupManager` |

## 构建

本模块通过 `pom.xml` 的 `<systemPath>` 依赖另一个模块的构建产物，**必须先构建 DragonBoatRace**：

```bash
cd ..\DragonBoatRace && mvn clean package
cd ..\DragonBoatGame  && mvn clean package
```

- 产物：`target/DragonBoatGame_v1.0.0.jar`
- `lib/` 下的 ScoreboardAPI、RsNPC、Tips、Sakura、AutoUpData 是**定制版 jar**（`<scope>system</scope>`），不要换成仓库坐标，克隆后也别删；
- `plugin.yml` 的版本号由 `maven-replacer-plugin` 在 `process-sources` 阶段自动改写，别手动改；
- 项目没有单元测试，`mvn test` 无意义。

## 运行环境

- Java 17 + **Nukkit-MOT**（`api: 1.0.11 / 3.0.0`）
- `softdepend`：`ScoreboardPlugin`（计分板）、`Tips`（屏幕文本）、`DragonBoat`（龙舟实体）
- 服务端需开启 `enable-experiment-mode: true`，否则自定义实体无法生成

## 线程模型（改代码前必看）

`ThreadManager` 里的 runnable（`RoomLoadRunnable`、`PluginMasterRunnable`、`TopRunnable`、`RandomJoinRunnable`）跑在**后台线程池**，而房间帧、玩家传送、实体生成、背包修改、发包都必须在主线程执行，所以这些任务统一通过 `ThreadManager.runOnMainThread(...)` 派发回主线程。

历史坑：早期版本直接在后台线程里调 `room.onUpdate()`，实体坐标和主 tick 抢着改，玩家看到的高速行驶「回拉 / 一顿一顿」就是这么来的。

也正因如此，**不要用 `/reload` 重载插件**（会留下悬挂的调度任务），请用 `stop` 重启。

## 数据文件

| 文件 | 说明 |
| --- | --- |
| `config.yml` | 主配置：标题、指令名、经验、加入菜单、`save-playerInventory` |
| `rooms/<房间名>/room.yml` | 单房间配置，含 `dragon-boat-game` 竞速段 |
| `team.yml` | 队伍、皮肤、初始装备、出生点 |
| `items.yml` / `shop.yml` | 随机箱子物品池 / 商店菜单 |
| `event.yml` + `roomEventList.yml` | 按秒触发的定时事件 |
| `lang/language.ini` | 全部界面文本 |
| `player.json` / `top.json` / `tag.json` | 玩家数据、排行榜、tag |
| `player-inventory-backup.json` | **玩家进房间前的背包存档，勿删** |

## 来源

框架最初来自 [GameDemo](https://github.com/SoBadFish/GameDemo)（作者 Sobadfish），本仓库在其基础上实现了龙舟竞速玩法，并修复了线程调度与背包持久化的问题。
