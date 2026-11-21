# DragonBoat（龙舟核心模型模块）

> 本模块是 [DragonBoatRace](../README.md) 仓库的一部分，只负责「龙舟 / 导弹 / 奖励箱」这些自定义实体、自定义道具和材质包。
> 房间、竞速规则、指令都在另一个模块 [DragonBoatGame](../DragonBoatGame/README.md) 里。

## 内容

- **三个自定义实体**（`EntityManager` 注册）
  - `dragon:dragon_boat` —— 可骑乘的龙舟，自带冰面 / 水面两套物理；
  - `dragon:missile` —— 锁定前方玩家飞行，命中后冻结对手；
  - `dragon:reward_box` —— 赛道上的道具箱，被撞后消失并定时重生。
- **四个自定义道具**（`Item.registerCustomItem`）
  - `dragon:dragon_boost` 加速、`dragon:dragon_magnet` 磁铁、`dragon:dragon_missile` 导弹、重置位置按钮。
- **输入监听**：`DragonBoatListener` 处理 `PlayerInputPacket` / `PlayerAuthInputPacket`，把 WASD 喂给龙舟。
- **资源包**：`assets/resource_pack/`，含三个实体的 `*.entity.json` / `*.geo.json` 与贴图。

## 龙舟操控参数

| 参数 | 值 |
| --- | --- |
| 最高速度 | 8 格/秒（0.4 格/tick） |
| 加速道具 | 速度 ×2，持续 160 tick（8 秒） |
| 磁铁 | 锁定目标 100 tick（5 秒），自己追近、目标减速 |
| 导弹冻结 | 命中后 60 tick（3 秒） |
| 漂移 | 冰/水面上转向达到阈值且接近满速时触发，出弯可接一段加速 |

## 调试指令

| 指令 | 作用 |
| --- | --- |
| `/dragonboat spawn` | 在准星指向的水面 / 冰面上生成龙舟并骑乘 |
| `/dragonboat missile` | 锁定前方 15 格内的玩家并发射导弹 |
| `/dragonboat rewardbox` | 生成一个奖励方块 |

> 需要 `dragonboat.command` 权限（默认 OP）。

## 资源包与实验模式

- 服务端必须在 `nukkit-mot.yml` 里开启 `enable-experiment-mode: true`，否则自定义实体 / 道具不生效；
- 客户端首次进服要**接受资源包**，不然龙舟会显示成普通船或白块；
- 改模型 / 贴图后记得同步 `manifest.json` 里的 uuid 或版本，避免客户端缓存旧包。

## 构建

本模块是纯模型插件，先构建它再构建 DragonBoatGame：

```bash
mvn clean package
# 产物：target/DragonBoatRace_v1.1.0-SNAPSHOT.jar
```

- Java 17 + **Nukkit-MOT**（`provided` scope，运行时由服务端提供）
- 没有单元测试，`mvn test` 无意义

## 安装

放进 `plugins/DragonBoat/`（插件名是 `DragonBoat`，不是 `DragonBoatRace`），与 `plugins/DragonBoatGame/` 里的框架插件配对使用。
