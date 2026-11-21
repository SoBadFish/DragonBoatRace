package org.sobadfish.dragonboatgame.room;

import cn.nukkit.command.data.CommandParameter;
import cn.nukkit.level.Position;
import cn.nukkit.level.particle.SmokeParticle;
import cn.nukkit.utils.Config;
import org.sobadfish.dragonboatgame.manager.TotalManager;
import org.sobadfish.dragonboatgame.player.PlayerInfo;
import org.sobadfish.dragonboatgame.player.team.config.TeamInfoConfig;
import org.sobadfish.dragonboatgame.player.team.config.TeamConfig;
import org.sobadfish.dragonboatgame.room.config.GameRoomConfig;
import org.sobadfish.dragonboatgame.room.config.WorldInfoConfig;
import org.sobadfish.dragonboatgame.tools.Utils;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 房间创建器，可以根据自身需求定制化创建房间配置
 * @author SoBadFish
 * 2022/1/13
 */
public class GameRoomCreator {

    private final PlayerInfo creator;

    private boolean isCreate;

    private boolean isRoomCreate;

    public int flag = 1;

    public int setFlag = 1;

    private String roomName = null;

    private int min = 2;

    private GameRoomConfig roomConfig;

    private WorldInfoConfig worldInfoConfig;

    private String startLineFrom;
    private String startLineTo;
    private String finishLineFrom;
    private String finishLineTo;

    private final List<String> itemSpawns = new ArrayList<>();

    public GameRoomCreator(PlayerInfo player) {
        this.creator = player;
    }

    public void onCreatePreset(String value) {
        if (flag != 1) {
            creator.sendForceMessage(TotalManager.getLanguage().getLanguage("go-on-default-create-not-use", "&c你正在进行默认创建，无法使用预设"));
            return;
        }
        switch (setFlag) {
            case 1:
                String name = getRoomName(value);
                creator.sendForceMessage(TotalManager.getLanguage().getLanguage("create-room-template", "&2正在创建 名称为 &r[1] &2的房间模板", name));
                creator.sendForceMessage(TotalManager.getLanguage().getLanguage("create-room-run-command-min-player",
                        "&e继续执行 &r/[1] set &a[最低玩家数]&e 执行下一步操作",
                        TotalManager.COMMAND_ADMIN_NAME));
                roomName = name;
                setFlag++;
                break;
            case 2:
                min = Integer.parseInt(value);
                creator.sendForceMessage(TotalManager.getLanguage().getLanguage("create-room-setting-min-player", "&2设置最低人数 [1]", value));
                creator.sendForceMessage(TotalManager.getLanguage().getLanguage("create-room-run-command-max-player",
                        "&e继续执行 &r/[1] set &2[最大玩家数]&e 执行下一步操作",
                        TotalManager.COMMAND_ADMIN_NAME));
                setFlag++;
                break;
            case 3:
                int max = Integer.parseInt(value);
                roomConfig = GameRoomConfig.createGameRoom(roomName, min, max);
                isRoomCreate = true;
                setFlag = 1;
                creator.sendForceMessage(TotalManager.getLanguage().getLanguage("create-room-setting-max-player", "&2设置最大人数:&b [1]", value));
                creator.sendForceMessage(TotalManager.getLanguage().getLanguage("create-room-default-setting-success", "&a预设完成"));
                break;
            default:
                break;
        }
    }

    public void stopInit() {
        if (setFlag > 1) {
            setFlag = 1;
            creator.sendForceMessage(TotalManager.getLanguage().getLanguage("create-room-default-setting-cancel", "&c终止预设"));
        } else {
            creator.sendForceMessage(TotalManager.getLanguage().getLanguage("create-room-default-setting-cancel-error", "&c无法终止预设"));
        }
    }

    public boolean onCreateNext() {
        if (setFlag != 1) {
            creator.sendForceMessage(TotalManager.getLanguage().getLanguage("create-room-must-success-setting", "&c请先完成预设"));
            return true;
        }
        switch (flag) {
            case 1:
                if (roomConfig == null) {
                    roomConfig = GameRoomConfig.createGameRoom(getRoomName(TotalManager.getLanguage().getLanguage("create-room=default-room-name", "游戏房间")), 2, 8);
                    isRoomCreate = true;
                    creator.sendForceMessage(TotalManager.getLanguage().getLanguage("create-room-default-success",
                            "&2成功创建一个 名字已经固定为 &r“[1]”&2的游戏房间模板 已设定最低玩家为 &b2&2 最大玩家为 &b8&r",
                            roomConfig.name));
                } else {
                    creator.sendForceMessage(TotalManager.getLanguage().getLanguage("create-room-default-setting-room-success", "&2成功预设房间设置"));
                }
                creator.sendForceMessage(TotalManager.getLanguage().getLanguage("create-room-run-command-game-world",
                        "继续执行/[1] 进行下一步 [进入游戏地图设置]",
                        TotalManager.COMMAND_ADMIN_NAME));
                flag++;
                break;
            case 2:
                worldInfoConfig = WorldInfoConfig.createWorldConfig(creator.getLevel().getFolderName());
                creator.sendForceMessage(TotalManager.getLanguage().getLanguage("create-room-setting-game-world-success", "&2成功设定游戏地图"));
                creator.sendForceMessage(TotalManager.getLanguage().getLanguage("dragonboat-create-room-run-command-game-wait",
                        "&e继续执行 &r/[1] &e进行下一步 &b[设置等待大厅]",
                        TotalManager.COMMAND_ADMIN_NAME));
                flag++;
                break;
            case 3:
                worldInfoConfig.setWaitPosition(creator.getPosition());
                creator.sendForceMessage(TotalManager.getLanguage().getLanguage("create-room-setting-game-wait-success", "&2成功设置等待大厅"));
                creator.sendForceMessage(TotalManager.getLanguage().getLanguage("dragonboat-create-room-run-command-start-line-from",
                        "&e继续执行 &r/[1] &e进行下一步 &b[设置起点线起点]",
                        TotalManager.COMMAND_ADMIN_NAME));
                flag++;
                break;
            case 4:
                startLineFrom = WorldInfoConfig.positionToString(creator.getPosition());
                creator.sendForceMessage(TotalManager.getLanguage().getLanguage("dragonboat-create-room-setting-start-line-from",
                        "&2成功设置起点线起点"));
                creator.sendForceMessage(TotalManager.getLanguage().getLanguage("dragonboat-create-room-run-command-start-line-to",
                        "&e继续执行 &r/[1] &e进行下一步 &b[设置起点线终点]",
                        TotalManager.COMMAND_ADMIN_NAME));
                flag++;
                break;
            case 5:
                startLineTo = WorldInfoConfig.positionToString(creator.getPosition());
                this.previewLine(startLineFrom, startLineTo);
                creator.sendForceMessage(TotalManager.getLanguage().getLanguage("dragonboat-create-room-setting-start-line-success",
                        "&2成功绘制起点线"));
                creator.sendForceMessage(TotalManager.getLanguage().getLanguage("dragonboat-create-room-run-command-finish-line-from",
                        "&e继续执行 &r/[1] &e进行下一步 &b[设置终点线起点]",
                        TotalManager.COMMAND_ADMIN_NAME));
                flag++;
                break;
            case 6:
                finishLineFrom = WorldInfoConfig.positionToString(creator.getPosition());
                creator.sendForceMessage(TotalManager.getLanguage().getLanguage("dragonboat-create-room-setting-finish-line-from",
                        "&2成功设置终点线起点"));
                creator.sendForceMessage(TotalManager.getLanguage().getLanguage("dragonboat-create-room-run-command-finish-line-to",
                        "&e继续执行 &r/[1] &e进行下一步 &b[设置终点线终点]",
                        TotalManager.COMMAND_ADMIN_NAME));
                flag++;
                break;
            case 7:
                finishLineTo = WorldInfoConfig.positionToString(creator.getPosition());
                this.previewLine(finishLineFrom, finishLineTo);
                creator.sendForceMessage(TotalManager.getLanguage().getLanguage("dragonboat-create-room-setting-finish-line-success",
                        "&2成功绘制终点线"));
                creator.sendForceMessage(TotalManager.getLanguage().getLanguage("dragonboat-create-room-run-command-item-spawn",
                        "&e继续执行 &r/[1] &e添加一个道具点，或执行 &r/[1] end &e结束房间设置",
                        TotalManager.COMMAND_ADMIN_NAME));
                flag++;
                break;
            case 8:
                itemSpawns.add(WorldInfoConfig.positionToString(creator.getPosition()));
                creator.sendForceMessage(TotalManager.getLanguage().getLanguage("dragonboat-create-room-setting-item-spawn",
                        "&2成功添加道具点，当前共 [1] 个",
                        String.valueOf(itemSpawns.size())));
                creator.sendForceMessage(TotalManager.getLanguage().getLanguage("dragonboat-create-room-run-command-item-spawn",
                        "&e继续执行 &r/[1] &e添加一个道具点，或执行 &r/[1] end &e结束房间设置",
                        TotalManager.COMMAND_ADMIN_NAME));
                break;
            default:
                break;
        }
        return true;
    }

    public void goBackStep() {
        if (setFlag > 1) {
            setFlag--;
            creator.sendForceMessage(TotalManager.getLanguage().getLanguage("create-goBack-setting", "&a成功回退预设的设置"));
            return;
        }
        if (flag <= 1) {
            creator.sendForceMessage(TotalManager.getLanguage().getLanguage("create-goBack-error", "&c当前没有可以回退的步骤"));
            return;
        }
        switch (flag) {
            case 8:
                if (!itemSpawns.isEmpty()) {
                    itemSpawns.remove(itemSpawns.size() - 1);
                    creator.sendForceMessage(TotalManager.getLanguage().getLanguage("dragonboat-create-room-go-back-item-spawn",
                            "&a成功回退最后一个道具点"));
                    return;
                }
                finishLineTo = null;
                break;
            case 7:
                finishLineFrom = null;
                break;
            case 6:
                startLineTo = null;
                break;
            case 5:
                startLineFrom = null;
                break;
            default:
                break;
        }
        flag--;
        creator.sendForceMessage(TotalManager.getLanguage().getLanguage("dragonboat-create-room-go-back-step",
                "&a成功回退房间设置步骤"));
    }

    public boolean finishRoomSetting() {
        if (flag < 8 || roomConfig == null || worldInfoConfig == null || startLineFrom == null || startLineTo == null
                || finishLineFrom == null || finishLineTo == null) {
            creator.sendForceMessage(TotalManager.getLanguage().getLanguage("dragonboat-create-room-end-error",
                    "&c起点线、终点线还没有设置完成，无法结束房间设置"));
            return false;
        }
        this.applyRaceRoomData();
        flag = 1;
        isCreate = true;
        creator.sendForceMessage(TotalManager.getLanguage().getLanguage("create-room-setting-success",
                "&a游戏房间创建完成 &c(重启生效配置)"));
        return true;
    }

    private void applyRaceRoomData() {
        roomConfig.setWorldInfo(worldInfoConfig);
        roomConfig.pvp = false;
        roomConfig.deathDrop = false;
        roomConfig.roundChest = false;
        roomConfig.enableShop = false;
        roomConfig.playerChoseTeam = false;
        roomConfig.hasWatch = true;
        roomConfig.resetWorld = true;
        roomConfig.reSpawnTime = -1;
        roomConfig.reSpawnCount = 0;
        roomConfig.gameInWait = 5;
        roomConfig.playerHelperConfig.enable = false;
        roomConfig.gameStartMessage = new ArrayList<String>();
        roomConfig.gameStartMessage.add("&a■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■");
        roomConfig.gameStartMessage.add("&b龙舟竞速");
        roomConfig.gameStartMessage.add("&e");
        roomConfig.gameStartMessage.add("&e倒计时内自动排船，起跑后冲向终点");
        roomConfig.gameStartMessage.add("&e");
        roomConfig.gameStartMessage.add("&a■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■");
        roomConfig.setTeamConfigs(this.buildSingleTeamConfig());
    }

    private ArrayList<TeamInfoConfig> buildSingleTeamConfig() {
        ArrayList<TeamInfoConfig> teamInfoConfigs = new ArrayList<>();
        if (roomConfig.getTeamCfg().isEmpty()) {
            return teamInfoConfigs;
        }
        TeamConfig teamConfig = roomConfig.getTeamCfg().values().iterator().next();
        ArrayList<String> positions = new ArrayList<>();
        positions.add(this.getLineCenterPositionString(startLineFrom, startLineTo));
        teamInfoConfigs.add(new TeamInfoConfig(teamConfig, positions));
        return teamInfoConfigs;
    }

    private String getLineCenterPositionString(String fromString, String toString) {
        Position from = WorldInfoConfig.getPositionByString(fromString);
        Position to = WorldInfoConfig.getPositionByString(toString);
        Position center = new Position(
                (from.getFloorX() + to.getFloorX()) / 2.0d,
                (from.getFloorY() + to.getFloorY()) / 2.0d,
                (from.getFloorZ() + to.getFloorZ()) / 2.0d,
                from.level
        );
        return WorldInfoConfig.positionToString(center);
    }

    private void previewLine(String fromString, String toString) {
        Position from = WorldInfoConfig.getPositionByString(fromString);
        Position to = WorldInfoConfig.getPositionByString(toString);
        if (from == null || to == null || from.level == null) {
            return;
        }
        double length = from.distance(to);
        int count = Math.max(2, (int) Math.ceil(length));
        for (int i = 0; i <= count; i++) {
            double progress = (double) i / (double) count;
            Position point = new Position(
                    from.x + (to.x - from.x) * progress,
                    from.y + (to.y - from.y) * progress,
                    from.z + (to.z - from.z) * progress,
                    from.level
            );
            point.level.addParticle(new SmokeParticle(point.add(0, 1.1d, 0)));
        }
    }

    public String getRoomName(String name) {
        return getRoomName(name, 0);
    }

    private String getRoomName(String name, int num) {
        String rn = name;
        if (num > 0) {
            rn += "(" + num + ")";
        }
        if (TotalManager.getRoomManager().hasRoom(rn)) {
            return getRoomName(name, ++num);
        }
        return rn;
    }

    public boolean createRoom() {
        if (isCreate) {
            this.applyRaceRoomData();
            roomConfig.save();
            this.saveRaceRoomConfig();
            Utils.addDescription(new File(TotalManager.getDataFolder() + "/rooms/" + roomConfig.getName() + "/room.yml"),
                    TotalManager.getLanguage().roomDescription, true);
            creator.sendForceMessage(TotalManager.getLanguage().getLanguage("create-room-success", "&a游戏已创建"));
            return true;
        } else {
            creator.sendForceMessage(TotalManager.getLanguage().getLanguage("create-room-error", "&c游戏未创建"));
        }
        return false;
    }

    private void saveRaceRoomConfig() {
        Config config = new Config(TotalManager.getDataFolder() + "/rooms/" + roomConfig.getName() + "/room.yml", Config.YAML);
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        Map<String, Object> startLine = new LinkedHashMap<String, Object>();
        startLine.put("from", startLineFrom);
        startLine.put("to", startLineTo);
        Map<String, Object> finishLine = new LinkedHashMap<String, Object>();
        finishLine.put("from", finishLineFrom);
        finishLine.put("to", finishLineTo);
        List<Map<String, Object>> itemSpawnMaps = new ArrayList<Map<String, Object>>();
        for (String itemSpawn : itemSpawns) {
            Map<String, Object> itemMap = new LinkedHashMap<String, Object>();
            itemMap.put("position", itemSpawn);
            itemMap.put("respawn", 8);
            itemSpawnMaps.add(itemMap);
        }
        root.put("start-line", startLine);
        root.put("finish-line", finishLine);
        root.put("item-spawns", itemSpawnMaps);
        root.put("first-finish-countdown", 10);
        root.put("ranking-board-size", 7);
        root.put("start-push-strength", 0.35d);
        root.put("item-pickup-distance", 3);
        root.put("boost-duration-ticks", 80);
        root.put("magnet-duration-ticks", 80);
        root.put("missile-stun-ticks", 60);
        root.put("boost-strength", 0.17d);
        root.put("magnet-strength", 0.12d);
        root.put("missile-range", 18d);
        root.put("finish-lock-speed", 0.15d);
        root.put("boat-variant", 0);
        config.set("dragon-boat-game", root);
        config.save();
    }

    public GameRoomConfig getRoomConfig() {
        if (isRoomCreate) {
            return roomConfig;
        }
        return null;
    }

    public PlayerInfo getCreator() {
        return creator;
    }

    public CommandParameter[] getSetCommandParameters() {
        switch (setFlag) {
            case 1:
                return new CommandParameter[]{
                        new CommandParameter("set", false, new String[]{"set"}),
                        new CommandParameter("房间名称", CommandParameter.ARG_TYPE_RAW_TEXT, false)
                };
            case 2:
                return new CommandParameter[]{
                        new CommandParameter("set", false, new String[]{"set"}),
                        new CommandParameter("最低玩家数", false, this.getMinPlayerSuggestions())
                };
            case 3:
                return new CommandParameter[]{
                        new CommandParameter("set", false, new String[]{"set"}),
                        new CommandParameter("最大玩家数", false, this.getMaxPlayerSuggestions())
                };
            default:
                return defaultSetCommandParameters();
        }
    }

    public static CommandParameter[] defaultSetCommandParameters() {
        return new CommandParameter[]{
                new CommandParameter("set", false, new String[]{"set"}),
                new CommandParameter("配置值", CommandParameter.ARG_TYPE_RAW_TEXT, false)
        };
    }

    private String[] getMinPlayerSuggestions() {
        return new String[]{"2", "4", "6", "8"};
    }

    private String[] getMaxPlayerSuggestions() {
        LinkedHashSet<String> values = new LinkedHashSet<String>();
        int base = Math.max(2, min);
        values.add(String.valueOf(base));
        values.add(String.valueOf(Math.max(base, 4)));
        values.add(String.valueOf(Math.max(base, 8)));
        values.add(String.valueOf(Math.max(base, 16)));
        return values.toArray(new String[0]);
    }
}
