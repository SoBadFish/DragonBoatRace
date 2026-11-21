package org.sobadfish.dragonboatgame.race;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.command.ConsoleCommandSender;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.Location;
import cn.nukkit.level.Position;
import cn.nukkit.level.particle.SmokeParticle;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.utils.Config;
import org.sobadfish.dragonboat.entity.DragonBoatEntity;
import org.sobadfish.dragonboat.entity.RewardBoxEntity;
import org.sobadfish.dragonboatgame.dlc.IGameEndJudge;
import org.sobadfish.dragonboatgame.dlc.IGamePlayerScoreBoard;
import org.sobadfish.dragonboatgame.dlc.IGameRoomDlc;
import org.sobadfish.dragonboatgame.item.button.ResetBoatItem;
import org.sobadfish.dragonboatgame.manager.ButtonItemManager;
import org.sobadfish.dragonboatgame.manager.FunctionManager;
import org.sobadfish.dragonboatgame.manager.TotalManager;
import org.sobadfish.dragonboatgame.player.PlayerData;
import org.sobadfish.dragonboatgame.player.PlayerInfo;
import org.sobadfish.dragonboatgame.room.GameRoom;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DragonBoatRaceDlc extends IGameRoomDlc implements IGameEndJudge, IGamePlayerScoreBoard {

    private static final double FINISH_LINE_WIDTH = 2.2d;
    private static final double FINISH_DETECTION_EXTRA_RANGE = 5.0d;
    private static final String DISPLAY_LINE = "■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■";
    private static final long PICKUP_RESPAWN_DELAY_MS = 5000L;
    private static final int RESET_ITEM_HOTBAR_SLOT = 8;
    private static final String RESET_ITEM_NAME = "§e重置位置";
    private static final int MIN_TRACK_WIDTH = 5;
    private static final int MAX_TRACK_SEARCH_DISTANCE = 50;
    private static final int ACTIVE_CHUNK_RADIUS = 2;
    private static final int LEAD_CHUNKS = 2;
    private static final int LINE_PARTICLE_INTERVAL = 4;

    private GameRoom room;
    private DragonBoatRaceConfig config;
    private final Map<String, RacePlayerState> playerStateMap = new LinkedHashMap<String, RacePlayerState>();
    private final List<RacePickupEntity> pickupEntities = new ArrayList<RacePickupEntity>();
    private final List<RacePlayerState> lastRanking = new ArrayList<RacePlayerState>();
    private boolean closed = false;
    private boolean lineupReady = false;
    private boolean raceStarted = false;
    private boolean settlementDone = false;
    private long raceStartTime = -1L;
    private long firstFinishTime = -1L;
    private long closeTime = -1L;
    private int lineParticleTick = 0;

    /** 初始化竞速 DLC，并覆盖房间为龙舟竞速所需配置。 */
    @Override
    public void onEnable(GameRoom room) {
        this.room = room;
        this.config = DragonBoatRaceConfig.load(new Config(new File(TotalManager.getDataFolder(),
                "rooms/" + room.getRoomConfig().getName() + "/room.yml"), Config.YAML));
        room.getRoomConfig().pvp = false;
        room.getRoomConfig().deathDrop = false;
        room.getRoomConfig().roundChest = false;
        room.getRoomConfig().enableShop = false;
        room.getRoomConfig().playerHelperConfig.enable = false;
        room.getRoomConfig().playerChoseTeam = false;
        room.getRoomConfig().resetWorld = true;
        room.getRoomConfig().reSpawnTime = -1;
        room.getRoomConfig().reSpawnCount = 0;
        room.getRoomConfig().hasWatch = true;
        room.getRoomConfig().gameInWait = 5;
        for (DragonBoatRaceConfig.ItemSpawnConfig itemSpawnConfig : this.config.getItemSpawns()) {
            this.pickupEntities.add(new RacePickupEntity(itemSpawnConfig));
        }
        this.clearPickupEntities();
    }

    /** 返回 DLC 的唯一标识名。 */
    @Override
    public String getName() {
        return "dragon_boat_race";
    }

    /** 每秒更新竞速流程，包括起跑、进行中与结算前逻辑。 */
    @Override
    public void onGameUpdate(GameRoom room) {
        if (this.closed || this.room == null || this.room != room || room.getType() != GameRoom.GameType.START) {
            return;
        }
        this.ensurePlayerStates();
        this.showLineParticles();
        if (!this.lineupReady) {
            this.prepareLineup();
        }
        this.keepRaceChunksLoaded();
        if (!this.raceStarted) {
            this.holdCountdownLineup();
            this.updateStartCountdownTitle();
            this.updateRanking();
            if (this.allPlayersCanMove()) {
                this.startRace();
            }
            return;
        }
        this.updatePickupEntities();
        this.updateRacePlayers();
        this.updateRanking();
        this.updateClosingCountdownTitle();
        if (!this.settlementDone && this.closeTime > 0L && System.currentTimeMillis() >= this.closeTime) {
            this.settleRace();
        }
    }

    /** 告知房间当前竞速是否已经完成结算。 */
    @Override
    public boolean judgeGameEnd(GameRoom room) {
        return this.settlementDone;
    }

    /** 构建等待阶段的计分板内容。 */
    @Override
    public ArrayList<String> displayPlayerWaitGameScoreBoard(PlayerInfo info) {
        ArrayList<String> lines = new ArrayList<String>();
        lines.add("§b§l龙舟竞速");
        lines.add("§7§m----------------");
        lines.add("§f房间 §7| §b" + this.room.getRoomConfig().getName());
        lines.add("§f人数 §7| §a" + this.room.getPlayerInfos().size() + "§7/§a" + this.room.getRoomConfig().getMaxPlayerSize());
        lines.add("§f状态 §7| §e等待起跑");
        lines.add("§f倒计时 §7| §6" + Math.max(0, info.waitTime) + "s");
        lines.add("§8§m----------------");
        lines.add("§7保持方向，准备冲线");
        return lines;
    }

    /** 构建比赛开始后的实时计分板内容。 */
    @Override
    public ArrayList<String> displayPlayerGameStartScoreBoard(PlayerInfo info) {
        ArrayList<String> lines = new ArrayList<String>();
        RacePlayerState self = this.playerStateMap.get(info.getName());
        lines.add("§b§l龙舟竞速");
        lines.add("§7§m----------------");
        lines.add("§f赛况 §7| " + this.getBoardStageText());
        lines.add("§f名次 §7| " + this.getBoardRankText(self));
        lines.add("§f进度 §7| §b" + this.getProgressPercent(self) + "%");
        if (self != null) {
            lines.add("§f状态 §7| " + this.getBoardStateText(self));
            if (!self.finished && this.raceStarted) {
                String gapText = this.getGapToAhead(self);
                if (gapText != null) {
                    lines.add("§f距前 §7| " + gapText);
                }
            }
        } else {
            lines.add("§f状态 §7| §7载入中");
        }
        if (!this.raceStarted) {
            lines.add("§f起跑 §7| §6" + Math.max(0, info.waitTime) + "s");
        } else if (this.closeTime > 0L) {
            long remainSeconds = Math.max(0L, (this.closeTime - System.currentTimeMillis() + 999L) / 1000L);
            lines.add("§f收尾 §7| §e" + remainSeconds + "s");
        } else {
            lines.add("§f用时 §7| §a" + this.formatTime(System.currentTimeMillis() - this.raceStartTime));
        }
        lines.add("§8§m----------------");
        lines.add("§e实时排名");
        int displaySize = Math.min(Math.min(5, this.config.getRankingBoardSize()), this.lastRanking.size());
        for (int i = 0; i < displaySize; i++) {
            RacePlayerState state = this.lastRanking.get(i);
            lines.add(this.formatRankingLine(i, state));
        }
        if (displaySize == 0) {
            lines.add("§7暂无排名数据");
        }
        return lines;
    }

    /** 计算当前玩家与前一名之间的实时差距。 */
    private String getGapToAhead(RacePlayerState self) {
        int selfIndex = this.lastRanking.indexOf(self);
        if (selfIndex <= 0 || selfIndex >= this.lastRanking.size()) {
            return null;
        }
        RacePlayerState ahead = this.lastRanking.get(selfIndex - 1);
        if (ahead.finished || self.finished) {
            return null;
        }
        double gap = ahead.lastProgress - self.lastProgress;
        if (gap <= 0d) {
            return null;
        }
        return "§e" + ahead.playerInfo.getName() + " §7+" + String.format("%.1f", gap) + "m";
    }

    /** 返回计分板上显示的比赛阶段文本。 */
    private String getBoardStageText() {
        if (!this.raceStarted) {
            return "§e起跑准备";
        }
        if (this.closeTime > 0L) {
            return "§6冲线收尾";
        }
        return "§a竞速进行中";
    }

    /** 返回计分板上显示的当前名次文本。 */
    private String getBoardRankText(RacePlayerState self) {
        if (self == null) {
            return "§7--";
        }
        int rank = this.lastRanking.indexOf(self);
        if (rank < 0) {
            return "§7--";
        }
        return "§e#" + (rank + 1);
    }

    /** 返回计分板上显示的玩家状态文本。 */
    private String getBoardStateText(RacePlayerState state) {
        if (state.finished) {
            return "§a已完赛";
        }
        if (state.unfinished) {
            return "§c未完成";
        }
        if (!this.raceStarted) {
            return "§e待出发";
        }
        return "§b冲刺中";
    }

    /** 根据起终点距离换算玩家的百分比进度。 */
    private int getProgressPercent(RacePlayerState state) {
        if (state == null) {
            return 0;
        }
        if (state.finished) {
            return 100;
        }
        Position startCenter = this.getLineCenter(this.config.getStartLine());
        Position finishCenter = this.getLineCenter(this.config.getFinishLine());
        if (startCenter == null || finishCenter == null) {
            return 0;
        }
        double totalDistance = Math.max(0.001d, startCenter.distance(finishCenter));
        double progress = Math.max(0d, state.lastProgress);
        return Math.max(0, Math.min(99, (int) Math.round((progress / totalDistance) * 100d)));
    }

    /** 格式化排行榜中的单行显示内容。 */
    private String formatRankingLine(int index, RacePlayerState state) {
        String rankText;
        switch (index) {
            case 0:
                rankText = "§6#1";
                break;
            case 1:
                rankText = "§f#2";
                break;
            case 2:
                rankText = "§c#3";
                break;
            default:
                rankText = "§7#" + (index + 1);
                break;
        }
        String result = state.finished ? this.formatTime(state.finishTimeMillis) : (this.getProgressPercent(state) + "%");
        String line = rankText + " §f" + state.playerInfo.getName() + " §7" + result;
        if (index > 0 && this.lastRanking.size() > index) {
            RacePlayerState ahead = this.lastRanking.get(index - 1);
            if (!ahead.finished && !state.finished) {
                double gapBlocks = ahead.lastProgress - state.lastProgress;
                line += " §8+" + String.format("%.1f", Math.max(0d, gapBlocks)) + "m";
            }
        }
        return line;
    }

    /** 关闭 DLC 时清理龙舟、道具和缓存状态。 */
    @Override
    public void onDisable(GameRoom room) {
        this.closed = true;
        this.removeResetItems();
        this.clearAllRewardBoxEntities();
        for (RacePlayerState state : this.playerStateMap.values()) {
            this.releasePlayer(state);
            this.detachBoat(state);
        }
        this.playerStateMap.clear();
        this.lastRanking.clear();
    }

    /** 为房间中的玩家补齐竞速状态对象。 */
    private void ensurePlayerStates() {
        for (PlayerInfo playerInfo : this.room.getPlayerInfos()) {
            if (!this.playerStateMap.containsKey(playerInfo.getName())) {
                this.playerStateMap.put(playerInfo.getName(), new RacePlayerState(playerInfo));
            }
        }
    }

    /** 生成起跑阶段的龙舟并按起点线排位。 */
    private void prepareLineup() {
        List<RacePlayerState> players = new ArrayList<RacePlayerState>(this.playerStateMap.values());
        if (players.isEmpty()) {
            return;
        }
        Vector3 direction = this.getRaceDirection();
        float yaw = this.vectorToYaw(direction);
        for (int i = 0; i < players.size(); i++) {
            RacePlayerState state = players.get(i);
            if (!(state.playerInfo.getPlayer() instanceof Player)) {
                continue;
            }
            state.lineupIndex = this.getCenterOutLineupIndex(i, players.size());
            Position position = this.getLineupPosition(state.lineupIndex, players.size());
            if (position == null || position.level == null) {
                continue;
            }
            this.spawnOrMoveBoat(state, position, yaw);
            state.lineupPosition = position;
        }
        this.resetPickupEntitiesForRound();
        this.lineupReady = true;
    }

    /** 在倒计时期间持续固定龙舟和玩家朝向与位置。 */
    private void holdCountdownLineup() {
        Vector3 raceDirection = this.getRaceDirection();
        float targetYaw = this.vectorToYaw(raceDirection);
        for (RacePlayerState state : this.playerStateMap.values()) {
            if (!(state.playerInfo.getPlayer() instanceof Player player)) {
                continue;
            }
            PlayerInfo.PlayerType t = state.playerInfo.getPlayerType();
            if (t == PlayerInfo.PlayerType.WATCH
                    || t == PlayerInfo.PlayerType.LEAVE
                    || t == PlayerInfo.PlayerType.DEATH
                    || t == PlayerInfo.PlayerType.WAIT_HELP) {
                continue;
            }
            this.ensureResetItem(player);
            if (state.lineupPosition == null) {
                Position lineupPos = this.getOrCreateLineupPosition(state);
                if (lineupPos == null) {
                    continue;
                }
                this.spawnOrMoveBoat(state, lineupPos, targetYaw);
                state.mounted = false;
            }
            DragonBoatEntity boat = state.spawnedBoat;
            if (boat == null || boat.closed) {
                Position lineupPos = state.lineupPosition != null
                        ? state.lineupPosition
                        : this.getOrCreateLineupPosition(state);
                if (lineupPos == null) {
                    continue;
                }
                this.spawnOrMoveBoat(state, lineupPos, targetYaw);
                boat = state.spawnedBoat;
            }
            if (boat != null) {
                if (player.riding != boat) {
                    boat.mountEntity(player);
                }
                if (player.riding == boat) {
                    boat.teleport(new Location(state.lineupPosition.x, state.lineupPosition.y, state.lineupPosition.z,
                            targetYaw, 0, state.lineupPosition.level));
                    boat.setMotion(new Vector3(0, 0, 0));
                    boat.yaw = targetYaw;
                    boat.setRotation(targetYaw, 0);
                }
                if (Math.abs(player.yaw - targetYaw) > 0.5d) {
                    player.setRotation(targetYaw, player.pitch);
                }
                player.setImmobile(true);
                state.forceImmobile = true;
            }
        }
    }

    /** 判断所有参赛玩家是否都结束了起跑倒计时。 */
    private boolean allPlayersCanMove() {
        boolean hasPlayer = false;
        for (RacePlayerState state : this.playerStateMap.values()) {
            if (state.playerInfo.getPlayerType() == PlayerInfo.PlayerType.START) {
                hasPlayer = true;
                if (state.playerInfo.waitTime >= 0) {
                    return false;
                }
            }
        }
        return hasPlayer;
    }

    /** 正式发车并给予统一的起跑推力。 */
    private void startRace() {
        this.raceStarted = true;
        this.raceStartTime = System.currentTimeMillis();
        this.room.sendMessage("§a起跑开始，计时已启动。");
        Vector3 raceDirection = this.getRaceDirection();
        float targetYaw = this.vectorToYaw(raceDirection);
        for (RacePlayerState state : this.playerStateMap.values()) {
            this.releasePlayer(state);
            DragonBoatEntity boat = state.spawnedBoat;
            if (boat != null && !boat.closed) {
                this.loadChunksAround(boat.getPosition(), ACTIVE_CHUNK_RADIUS);
                boat.alignHeading(targetYaw);
                boat.setMotionControlEnabled(true);
                this.pushForward(boat, raceDirection, this.config.getStartPushStrength());
            }
            if (state.playerInfo.getPlayer() instanceof Player) {
                Player p = (Player) state.playerInfo.getPlayer();
                if (Math.abs(p.yaw - targetYaw) > 0.5d) {
                    p.setRotation(targetYaw, p.pitch);
                }
                p.sendTitle("§a出发!", "§f控制龙舟冲向终点", 0, 20, 10);
            }
        }
    }

    /** 比赛进行时只在"跨区块"时预加载前方区块，避免每秒重复强制加载造成主线程卡顿。 */
    private void keepRaceChunksLoaded() {
        Vector3 direction = this.getRaceDirection();
        for (RacePlayerState state : this.playerStateMap.values()) {
            Position focus = null;
            if (state.spawnedBoat != null && !state.spawnedBoat.closed) {
                focus = state.spawnedBoat;
            } else if (state.playerInfo.getPlayer() instanceof Player player) {
                focus = player;
            }
            if (focus == null || focus.level == null) {
                continue;
            }
            int chunkX = focus.getFloorX() >> 4;
            int chunkZ = focus.getFloorZ() >> 4;
            long key = ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
            if (key == state.lastPreloadChunkKey) {
                continue;
            }
            state.lastPreloadChunkKey = key;
            this.preloadChunksAround(focus.level, chunkX, chunkZ, ACTIVE_CHUNK_RADIUS);
            int leadX = chunkX + (int) Math.round(direction.x * LEAD_CHUNKS);
            int leadZ = chunkZ + (int) Math.round(direction.z * LEAD_CHUNKS);
            if (leadX != chunkX || leadZ != chunkZ) {
                this.preloadChunksAround(focus.level, leadX, leadZ, ACTIVE_CHUNK_RADIUS);
            }
        }
    }

    /** 只加载尚未加载的区块，且不生成地形，减少高速移动时的同步读盘卡顿。 */
    private void preloadChunksAround(cn.nukkit.level.Level level, int chunkX, int chunkZ, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int x = chunkX + dx;
                int z = chunkZ + dz;
                if (level.isChunkLoaded(x, z)) {
                    continue;
                }
                level.loadChunk(x, z, false);
            }
        }
    }

    /** 预加载中心点周围的区块，避免实体生成、挂载和高速移动时踩到未加载区块。 */
    private void loadChunksAround(Position center, int radius) {
        if (center == null || center.level == null) {
            return;
        }
        int chunkX = center.getFloorX() >> 4;
        int chunkZ = center.getFloorZ() >> 4;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                center.level.loadChunk(chunkX + dx, chunkZ + dz, true);
            }
        }
    }

    /** 确保玩家快捷栏里始终有一个重置龙舟按钮，优先放空位，避免顶掉玩家手上的道具。 */
    private void ensureResetItem(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (this.hasResetItem(player)) {
            return;
        }
        int slot = -1;
        Item preferred = player.getInventory().getItem(RESET_ITEM_HOTBAR_SLOT);
        if (preferred == null || preferred.isNull()) {
            slot = RESET_ITEM_HOTBAR_SLOT;
        } else {
            for (int i = 0; i < 9; i++) {
                Item item = player.getInventory().getItem(i);
                if (item == null || item.isNull()) {
                    slot = i;
                    break;
                }
            }
        }
        if (slot < 0) {
            // 快捷栏塞满了就先不给，等吃到道具用完腾出空位
            return;
        }
        player.getInventory().setItem(slot, ButtonItemManager.getItem(ResetBoatItem.class));
    }

    /** 判断快捷栏里是否已经有重置按钮。 */
    private boolean hasResetItem(Player player) {
        for (int i = 0; i < 9; i++) {
            if (this.isResetItem(player.getInventory().getItem(i))) {
                return true;
            }
        }
        return false;
    }

    /** 更新比赛中的玩家状态、进度与冲线判定。 */
    private void updateRacePlayers() {
        for (RacePlayerState state : this.playerStateMap.values()) {
            if (!(state.playerInfo.getPlayer() instanceof Player player)) {
                if (state.playerInfo.getPlayerType() == PlayerInfo.PlayerType.LEAVE) {
                    state.unfinished = true;
                    this.detachBoat(state);
                }
                continue;
            }
            if (state.playerInfo.getPlayerType() == PlayerInfo.PlayerType.WATCH
                    || state.playerInfo.getPlayerType() == PlayerInfo.PlayerType.LEAVE
                    || state.playerInfo.getPlayerType() == PlayerInfo.PlayerType.DEATH
                    || state.playerInfo.getPlayerType() == PlayerInfo.PlayerType.WAIT_HELP) {
                this.detachBoat(state);
                continue;
            }
            DragonBoatEntity boat = this.getMountedBoat(state.playerInfo);
            if (state.finished) {
                this.lockFinishedState(state, boat, player);
                continue;
            }
            this.ensureResetItem(player);
            if (boat != null) {
                this.applyTimedEffects(state, boat);
            } else {
                this.releasePlayer(state);
            }
            Position tracked = this.getRankingPosition(state);
            state.lastProgress = this.computeProgress(tracked);
            state.lastDistanceToFinish = this.distanceToFinishLine(tracked);
            if (this.hasReachedFinish(tracked)) {
                this.finishPlayer(state);
            }
        }
    }

    /** 更新场上的奖励箱实体刷新状态。 */
    private void updatePickupEntities() {
        for (RacePickupEntity pickupEntity : this.pickupEntities) {
            pickupEntity.ensureSpawned();
        }
    }

    /** 在新一轮开始时重置所有奖励箱。 */
    private void resetPickupEntitiesForRound() {
        this.clearAllRewardBoxEntities();
        this.updatePickupEntities();
    }

    /** 清理缓存和世界中的全部奖励箱实体。 */
    private void clearAllRewardBoxEntities() {
        this.clearPickupEntities();
        this.clearWorldRewardBoxEntities();
    }

    /** 清理本 DLC 管理的奖励箱缓存对象。 */
    private void clearPickupEntities() {
        for (RacePickupEntity pickupEntity : this.pickupEntities) {
            pickupEntity.close();
        }
    }

    /** 扫描比赛相关世界并移除遗留奖励箱实体。 */
    private void clearWorldRewardBoxEntities() {
        Map<String, cn.nukkit.level.Level> levels = new LinkedHashMap<String, cn.nukkit.level.Level>();
        if (this.room != null
                && this.room.getWorldInfo() != null
                && this.room.getWorldInfo().getConfig() != null
                && this.room.getWorldInfo().getConfig().getGameWorld() != null) {
            cn.nukkit.level.Level gameWorld = this.room.getWorldInfo().getConfig().getGameWorld();
            levels.put(gameWorld.getFolderName(), gameWorld);
        }
        for (DragonBoatRaceConfig.ItemSpawnConfig itemSpawnConfig : this.config.getItemSpawns()) {
            Position position = itemSpawnConfig.getPosition();
            if (position == null || position.level == null) {
                continue;
            }
            levels.put(position.level.getFolderName(), position.level);
        }
        for (cn.nukkit.level.Level level : levels.values()) {
            for (Entity entity : new ArrayList<>(List.of(level.getEntities()))) {
                if (entity instanceof RewardBoxEntity && !entity.isClosed()) {
                    entity.close();
                }
            }
        }
    }

    /** 为所有参赛玩家移除重置龙舟按钮。 */
    private void removeResetItems() {
        for (RacePlayerState state : this.playerStateMap.values()) {
            if (state.playerInfo.getPlayer() instanceof Player) {
                this.removeResetItem((Player) state.playerInfo.getPlayer());
            }
        }
    }

    /** 从指定玩家快捷栏中移除重置龙舟按钮（按钮可能被放到任意格）。 */
    private void removeResetItem(Player player) {
        if (player == null) {
            return;
        }
        for (int i = 0; i < 9; i++) {
            if (this.isResetItem(player.getInventory().getItem(i))) {
                player.getInventory().clear(i);
            }
        }
    }

    /** 判断一个物品是否为竞速专用的重置按钮。 */
    private boolean isResetItem(Item item) {
        return item != null && !item.isNull()
                && item.hasCompoundTag()
                && item.getNamedTag().contains(TotalManager.GAME_NAME)
                && "resetBoat".equals(item.getNamedTag().getString(TotalManager.GAME_NAME));
    }

    /** 刷新当前轮的实时排名缓存。 */
    private void updateRanking() {
        this.lastRanking.clear();
        for (RacePlayerState state : this.playerStateMap.values()) {
            if (this.shouldIncludeInRanking(state)) {
                this.lastRanking.add(state);
            }
        }
        this.lastRanking.sort(new Comparator<RacePlayerState>() {
            /** 排序时优先比较完赛状态，再比较进度与终点距离。 */
            @Override
            public int compare(RacePlayerState left, RacePlayerState right) {
                if (left.finished && right.finished) {
                    return compareLong(left.finishTimeMillis, right.finishTimeMillis);
                }
                if (left.finished) {
                    return -1;
                }
                if (right.finished) {
                    return 1;
                }
                if (left.unfinished && !right.unfinished) {
                    return 1;
                }
                if (!left.unfinished && right.unfinished) {
                    return -1;
                }
                int compareProgress = Double.compare(right.lastProgress, left.lastProgress);
                if (compareProgress != 0) {
                    return compareProgress;
                }
                return Double.compare(left.lastDistanceToFinish, right.lastDistanceToFinish);
            }
        });
    }

    /** 判断一个玩家是否应该进入实时排名。 */
    private boolean shouldIncludeInRanking(RacePlayerState state) {
        if (state == null || state.playerInfo == null) {
            return false;
        }
        PlayerInfo.PlayerType playerType = state.playerInfo.getPlayerType();
        return playerType != PlayerInfo.PlayerType.WATCH
                && playerType != PlayerInfo.PlayerType.LEAVE;
    }

    /** 执行统一结算、广播结果并发放奖励。 */
    private void settleRace() {
        this.settlementDone = true;
        this.clearAllRewardBoxEntities();
        this.removeResetItems();
        for (RacePlayerState state : this.playerStateMap.values()) {
            if (!state.finished) {
                state.unfinished = true;
            }
            this.releasePlayer(state);
            this.detachBoat(state);
        }
        this.updateRanking();
        this.displaySettlementMessage();
        int index = 1;
        for (RacePlayerState state : this.lastRanking) {
            String status = state.finished ? this.formatTime(state.finishTimeMillis) : "未完成";
            this.room.sendMessage("§f#" + index + " " + state.playerInfo.getName() + " §7- §e" + status);
            if (state.finished) {
                this.executeRewards(state, index);
            } else {
                this.executeDnfRewards(state);
            }
            state.playerInfo.addData(PlayerData.DataType.GAME.getName());
            if (index == 1 && state.finished) {
                state.playerInfo.addData(PlayerData.DataType.VICTORY.getName());
            } else if (!state.finished) {
                state.playerInfo.addData(PlayerData.DataType.DEFEAT.getName());
            }
            index++;
        }
        if (this.room.getType() == GameRoom.GameType.START) {
            this.room.end();
        }
    }

    /** 按名次执行配置中的结算奖励命令。 */
    private void executeRewards(RacePlayerState state, int rank) {
        List<String> commands = this.config.getRankingRewards().get(rank);
        if (commands == null) {
            return;
        }
        for (String command : commands) {
            Server.getInstance().dispatchCommand(new ConsoleCommandSender(), this.replacePlaceholders(command, state, rank));
        }
    }

    /** 为未完成比赛的玩家执行 DNF 奖励命令。 */
    private void executeDnfRewards(RacePlayerState state) {
        for (String command : this.config.getDnfRewards()) {
            Server.getInstance().dispatchCommand(new ConsoleCommandSender(), this.replacePlaceholders(command, state, -1));
        }
    }

    /** 替换奖励命令中的玩家、名次和时间占位符。 */
    private String replacePlaceholders(String command, RacePlayerState state, int rank) {
        return command
                .replace("@p", state.playerInfo.getName())
                .replace("[player]", state.playerInfo.getName())
                .replace("[rank]", String.valueOf(rank))
                .replace("[time]", state.finished ? this.formatTime(state.finishTimeMillis) : "DNF");
    }

    /** 处理玩家冲线后的锁定、计时与广播。 */
    private void finishPlayer(RacePlayerState state) {
        if (state.finished) {
            return;
        }
        state.finished = true;
        state.finishTimeMillis = System.currentTimeMillis() - this.raceStartTime;
        state.finishRank = this.countFinishedPlayers();
        Player player = (Player) state.playerInfo.getPlayer();
        this.lockFinishedState(state, this.getMountedBoat(state.playerInfo), player);
        player.sendTitle("§a到达终点", "§f用时 " + this.formatTime(state.finishTimeMillis), 0, 40, 10);
        this.room.sendMessage("§a" + state.playerInfo.getName() + " 以 " + this.formatTime(state.finishTimeMillis) + " 到达终点。");
        if (this.firstFinishTime < 0L) {
            this.firstFinishTime = System.currentTimeMillis();
            this.closeTime = this.firstFinishTime + (this.config.getFirstFinishCountdown() * 1000L);
            this.room.sendMessage("§e首名已冲线，" + this.config.getFirstFinishCountdown() + " 秒后统一结算。");
        }
    }

    /** 在起跑倒计时阶段向玩家发送标题提示。 */
    private void updateStartCountdownTitle() {
        for (RacePlayerState state : this.playerStateMap.values()) {
            if (!(state.playerInfo.getPlayer() instanceof Player)) {
                continue;
            }
            int waitTime = state.playerInfo.waitTime;
            if (waitTime < 0 || waitTime == state.lastStartTitleSecond) {
                continue;
            }
            Player player = (Player) state.playerInfo.getPlayer();
            if (waitTime > 0) {
                player.sendTitle("§e" + waitTime, "§f龙舟已就位，准备起跑", 0, 20, 0);
            } else {
                player.sendTitle("§a出发!", "§f控制龙舟冲向终点", 0, 20, 10);
            }
            state.lastStartTitleSecond = waitTime;
        }
    }

    /** 将结算榜单以居中公告的形式发送给房间。 */
    private void displaySettlementMessage() {
        this.room.sendTipMessage("§a" + DISPLAY_LINE);
        this.room.sendTipMessage(this.centerDisplayLine("§b龙舟竞速"));
        this.room.sendTipMessage(this.centerDisplayLine("§e竞速结算"));
        for (int i = 0; i < this.lastRanking.size(); i++) {
            RacePlayerState state = this.lastRanking.get(i);
            String status = state.finished ? this.formatTime(state.finishTimeMillis) : "未完成";
            this.room.sendTipMessage(this.centerDisplayLine("§f#" + (i + 1) + " §e" + state.playerInfo.getName() + " §f" + status));
        }
        this.room.sendTipMessage("§a" + DISPLAY_LINE);
    }

    /** 将文本按展示宽度居中到提示条中。 */
    private String centerDisplayLine(String message) {
        return FunctionManager.getCentontString(message, DISPLAY_LINE.length());
    }

    /** 在首名冲线后持续更新统一结算倒计时标题。 */
    private void updateClosingCountdownTitle() {
        if (this.closeTime <= 0L || this.settlementDone) {
            return;
        }
        long remainMillis = this.closeTime - System.currentTimeMillis();
        int remainSeconds = (int) Math.max(0L, (remainMillis + 999L) / 1000L);
        for (RacePlayerState state : this.playerStateMap.values()) {
            if (!(state.playerInfo.getPlayer() instanceof Player)) {
                continue;
            }
            if (remainSeconds == state.lastCloseTitleSecond) {
                continue;
            }
            Player player = (Player) state.playerInfo.getPlayer();
            if (state.finished) {
                player.sendTitle("§e收尾倒计时", "§f其余玩家剩余 " + remainSeconds + " 秒", 0, 20, 0);
            } else {
                player.sendTitle("§c" + remainSeconds, "§f到时未冲线将判定未完成", 0, 20, 0);
            }
            state.lastCloseTitleSecond = remainSeconds;
        }
    }

    /** 统计当前已经完赛的玩家数量。 */
    private int countFinishedPlayers() {
        int count = 0;
        for (RacePlayerState state : this.playerStateMap.values()) {
            if (state.finished) {
                count++;
            }
        }
        return count;
    }

    /** 结算龙舟上的临时效果，如眩晕与加速。 */
    private void applyTimedEffects(RacePlayerState state, DragonBoatEntity boat) {
        long now = System.currentTimeMillis();
        if (state.stunEndTime > now) {
            boat.setMotion(new Vector3(0, 0, 0));
            if (state.playerInfo.getPlayer() instanceof Player) {
                ((Player) state.playerInfo.getPlayer()).setImmobile(true);
                state.forceImmobile = true;
            }
            return;
        }
        this.releasePlayer(state);
        if (state.boostEndTime > now) {
            this.pushForward(boat, this.getForwardVector(boat), this.config.getBoostStrength());
        }
    }

    /** 释放被临时锁定移动的玩家。 */
    private void releasePlayer(RacePlayerState state) {
        if (state.forceImmobile && state.playerInfo.getPlayer() instanceof Player) {
            ((Player) state.playerInfo.getPlayer()).setImmobile(false);
            state.forceImmobile = false;
        }
    }

    /** 让玩家与龙舟解绑并关闭本轮龙舟实体。 */
    private void detachBoat(RacePlayerState state) {
        if (state == null) {
            return;
        }
        if (!(state.playerInfo.getPlayer() instanceof Player) || !((Player) state.playerInfo.getPlayer()).isOnline()) {
            this.closeBoat(state);
            return;
        }
        Player player = (Player) state.playerInfo.getPlayer();
        if (state.spawnedBoat != null && !state.spawnedBoat.closed) {
            state.spawnedBoat.setMotionControlEnabled(false);
            if (player.riding == state.spawnedBoat) {
                state.spawnedBoat.dismountEntity(player);
            }
            state.spawnedBoat.setMotion(new Vector3(0, 0, 0));
        }
        this.closeBoat(state);
    }

    /** 在玩家完赛后冻结操作并让龙舟缓慢停下。 */
    private void lockFinishedState(RacePlayerState state, DragonBoatEntity boat, Player player) {
        player.setImmobile(true);
        state.forceImmobile = true;
        if (boat != null && !boat.closed) {
            boat.setMotion(new Vector3(boat.motionX * this.config.getFinishLockSpeed(), 0, boat.motionZ * this.config.getFinishLockSpeed()));
            if (boat.motionX * boat.motionX + boat.motionZ * boat.motionZ < 0.01d) {
                boat.setMotion(new Vector3(0, 0, 0));
            }
        }
    }

    /** 沿指定方向给龙舟追加一段前进速度。 */
    private void pushForward(DragonBoatEntity boat, Vector3 direction, double strength) {
        Vector3 forward = direction;
        if (forward == null || forward.lengthSquared() <= 0.0001d) {
            forward = this.getForwardVector(boat);
        } else {
            forward = forward.normalize();
        }
        Vector3 vector = forward.multiply(strength);
        boat.setMotion(new Vector3(boat.motionX + vector.x, boat.motionY, boat.motionZ + vector.z));
    }

    /** 根据龙舟当前朝向换算前进单位向量。 */
    private Vector3 getForwardVector(DragonBoatEntity boat) {
        double yaw = Math.toRadians(boat.yaw);
        return new Vector3(-Math.sin(yaw), 0, Math.cos(yaw));
    }

    /** 获取用于排名计算的位置，优先使用龙舟位置。 */
    private Position getRankingPosition(RacePlayerState state) {
        if (!(state.playerInfo.getPlayer() instanceof Player)) {
            return null;
        }
        Player player = (Player) state.playerInfo.getPlayer();
        DragonBoatEntity ridingBoat = this.getMountedBoat(state.playerInfo);
        if (ridingBoat != null) {
            return ridingBoat;
        }
        return player;
    }

    /** 计算某个位置沿赛道方向前进了多少距离。 */
    private double computeProgress(Position position) {
        Position startCenter = this.getLineCenter(this.config.getStartLine());
        Vector3 direction = this.getRaceDirection();
        if (position == null || startCenter == null) {
            return 0d;
        }
        return (position.x - startCenter.x) * direction.x + (position.z - startCenter.z) * direction.z;
    }

    /** 计算当前位置到终点线段的最短距离。 */
    private double distanceToFinishLine(Position position) {
        return this.distanceToLineSegment(position, this.config.getFinishLine());
    }

    /** 判断当前位置是否已经进入终点判定范围。 */
    private boolean hasReachedFinish(Position position) {
        if (position == null) {
            return false;
        }
        Position finishCenter = this.getLineCenter(this.config.getFinishLine());
        Vector3 direction = this.getRaceDirection();
        if (finishCenter == null) {
            return false;
        }
        double projection = (position.x - finishCenter.x) * direction.x + (position.z - finishCenter.z) * direction.z;
        double detectionRange = FINISH_LINE_WIDTH + FINISH_DETECTION_EXTRA_RANGE;
        return projection >= -detectionRange && this.distanceToFinishLine(position) <= detectionRange;
    }

    /** 生成龙舟或将已有龙舟移动到指定位置。 */
    private void spawnOrMoveBoat(RacePlayerState state, Position position, float yaw) {
        Player player = (Player) state.playerInfo.getPlayer();
        this.loadChunksAround(position, ACTIVE_CHUNK_RADIUS);
        this.loadChunksAround(player.getPosition(), ACTIVE_CHUNK_RADIUS);
        if (state.spawnedBoat != null && !state.spawnedBoat.closed) {
            state.spawnedBoat.teleport(new Location(position.x, position.y, position.z, yaw, 0, position.level));
            state.spawnedBoat.setMotion(new Vector3(0, 0, 0));
            state.spawnedBoat.setMotionControlEnabled(this.raceStarted);
            if (player.riding != state.spawnedBoat) {
                state.spawnedBoat.mountEntity(player);
                state.mounted = true;
            }
            return;
        }
        CompoundTag nbt = Entity.getDefaultNBT(position);
        nbt.putInt("Variant", this.config.getBoatVariant());
        nbt.putFloat("Yaw", yaw);
        nbt.putFloat("Pitch", 0);
        Entity boatEntity = Entity.createEntity(DragonBoatEntity.DEF_ENTITY.getIdentifier(), position.getChunk(), nbt, player);
        if (!(boatEntity instanceof DragonBoatEntity)) {
            player.sendMessage("§c未找到 DragonBoat 插件中的龙舟实体，请确认插件已加载。");
            return;
        }
        DragonBoatEntity boat = (DragonBoatEntity) boatEntity;
        boat.yaw = yaw;
        boat.spawnToAll();
        boat.setMotionControlEnabled(this.raceStarted);
        boat.mountEntity(player);
        state.mounted = true;
        state.spawnedBoat = boat;
    }

    /** 根据排位索引计算玩家在起点线上的目标位置。 */
    private Position getLineupPosition(int index, int total) {
        DragonBoatRaceConfig.LineConfig lineConfig = this.config.getStartLine();
        if (!lineConfig.isValid()) {
            return this.getFallbackSpawn(index);
        }
        Position from = lineConfig.getFrom();
        Position to = lineConfig.getTo();
        double progress = total <= 1 ? 0.5d : (double) index / (double) (total - 1);
        return new Position(
                from.x + (to.x - from.x) * progress,
                from.y + (to.y - from.y) * progress,
                from.z + (to.z - from.z) * progress,
                from.level
        );
    }

    /** 按从中心向两边展开的规则换算排位索引。 */
    private int getCenterOutLineupIndex(int order, int total) {
        if (total <= 1) {
            return 0;
        }
        if ((total & 1) == 1) {
            int center = total / 2;
            if (order == 0) {
                return center;
            }
            int offset = (order + 1) / 2;
            return (order & 1) == 1 ? center - offset : center + offset;
        }
        int leftCenter = (total / 2) - 1;
        int rightCenter = total / 2;
        int offset = order / 2;
        return (order & 1) == 0 ? leftCenter - offset : rightCenter + offset;
    }

    /** 当起点线配置无效时回退到队伍出生点。 */
    private Position getFallbackSpawn(int index) {
        RacePlayerState state = new ArrayList<RacePlayerState>(this.playerStateMap.values()).get(index);
        if (state.playerInfo.getTeamInfo() != null) {
            Position location = state.playerInfo.getTeamInfo().getSpawnLocation();
            return new Position(location.x, location.y, location.z, location.level);
        }
        return null;
    }

    /** 获取或初始化玩家的固定起跑排位位置。 */
    private Position getOrCreateLineupPosition(RacePlayerState state) {
        int total = this.playerStateMap.size();
        if (state.lineupIndex < 0) {
            List<RacePlayerState> players = new ArrayList<RacePlayerState>(this.playerStateMap.values());
            int order = players.indexOf(state);
            if (order < 0) {
                order = players.size() - 1;
            }
            state.lineupIndex = this.getCenterOutLineupIndex(order, total);
        }
        Position pos = this.getLineupPosition(state.lineupIndex, total);
        if (pos != null) {
            state.lineupPosition = pos;
        }
        return pos;
    }

    /** 在起点线和终点线上显示提示粒子。 */
    private void showLineParticles() {
        //每秒都铺满整条线的粒子会持续占用主线程与网络带宽，降频到每 4 秒一轮
        if ((this.lineParticleTick++ % LINE_PARTICLE_INTERVAL) != 0) {
            return;
        }
        this.spawnLineParticles(this.config.getStartLine());
        this.spawnLineParticles(this.config.getFinishLine());
    }

    /** 沿一条线段生成整排烟雾粒子。 */
    private void spawnLineParticles(DragonBoatRaceConfig.LineConfig line) {
        if (line == null || !line.isValid()) {
            return;
        }
        Position from = line.getFrom();
        Position to = line.getTo();
        double length = from.distance(to);
        int count = Math.max(2, (int) Math.ceil(length / 2.0d));
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

    /** 计算配置线段的中心点位置。 */
    private Position getLineCenter(DragonBoatRaceConfig.LineConfig line) {
        if (line == null || !line.isValid()) {
            return null;
        }
        Position from = line.getFrom();
        Position to = line.getTo();
        return new Position((from.x + to.x) / 2.0d, (from.y + to.y) / 2.0d, (from.z + to.z) / 2.0d, from.level);
    }

    /** 计算从起点中心指向终点中心的赛道方向。 */
    private Vector3 getRaceDirection() {
        Position startCenter = this.getLineCenter(this.config.getStartLine());
        Position finishCenter = this.getLineCenter(this.config.getFinishLine());
        if (startCenter == null || finishCenter == null) {
            return new Vector3(0, 0, 1);
        }
        Vector3 vector = finishCenter.subtract(startCenter);
        vector = new Vector3(vector.x, 0, vector.z);
        if (vector.lengthSquared() <= 0.0001d) {
            return new Vector3(0, 0, 1);
        }
        return vector.normalize();
    }

    /** 将方向向量转换为 Nukkit 使用的 yaw 角度。 */
    private float vectorToYaw(Vector3 vector) {
        return (float) Math.toDegrees(Math.atan2(-vector.x, vector.z));
    }

    /** 计算点到二维线段的最短距离。 */
    private double distanceToLineSegment(Position point, DragonBoatRaceConfig.LineConfig line) {
        if (point == null || line == null || !line.isValid()) {
            return Double.MAX_VALUE;
        }
        Position from = line.getFrom();
        Position to = line.getTo();
        double abx = to.x - from.x;
        double abz = to.z - from.z;
        double apx = point.x - from.x;
        double apz = point.z - from.z;
        double ab2 = (abx * abx) + (abz * abz);
        if (ab2 <= 0.0001d) {
            double dx = point.x - from.x;
            double dz = point.z - from.z;
            return Math.sqrt(dx * dx + dz * dz);
        }
        double t = ((apx * abx) + (apz * abz)) / ab2;
        t = Math.max(0d, Math.min(1d, t));
        double closestX = from.x + (abx * t);
        double closestZ = from.z + (abz * t);
        double dx = point.x - closestX;
        double dz = point.z - closestZ;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** 获取玩家当前骑乘的龙舟实体。 */
    private DragonBoatEntity getMountedBoat(PlayerInfo playerInfo) {
        if (!(playerInfo.getPlayer() instanceof Player)) {
            return null;
        }
        Entity riding = ((Player) playerInfo.getPlayer()).riding;
        if (riding instanceof DragonBoatEntity) {
            return (DragonBoatEntity) riding;
        }
        return null;
    }

    /** 关闭并清空玩家状态里记录的龙舟引用。 */
    private void closeBoat(RacePlayerState state) {
        if (state.spawnedBoat != null && !state.spawnedBoat.closed) {
            state.spawnedBoat.close();
        }
        state.spawnedBoat = null;
        state.mounted = false;
        state.lineupPosition = null;
    }

    /** 返回用于展示的结算状态标签。 */
    private String getStateLabel(RacePlayerState state) {
        if (state.finished) {
            return "已完赛";
        }
        if (state.unfinished) {
            return "未完成";
        }
        return this.raceStarted ? "竞速中" : "待起跑";
    }

    /** 将耗时格式化为分秒十分秒文本，避免在秒级刷新链路里显示伪毫秒。 */
    private String formatTime(long millis) {
        long total = Math.max(0L, millis);
        long minutes = total / 60000L;
        long seconds = (total % 60000L) / 1000L;
        long deciseconds = (total % 1000L) / 100L;
        return String.format("%02d:%02d.%01d", minutes, seconds, deciseconds);
    }

    /** 比较两个 long 值并返回排序结果。 */
    private static int compareLong(long left, long right) {
        if (left == right) {
            return 0;
        }
        return left < right ? -1 : 1;
    }

    /** 处理玩家点击重置龙舟按钮后的逻辑。 */
    public void onResetButtonClick(PlayerInfo playerInfo) {
        if (playerInfo == null || this.room == null || this.room.getType() != GameRoom.GameType.START) {
            return;
        }
        RacePlayerState state = this.playerStateMap.get(playerInfo.getName());
        if (state == null || state.finished) {
            return;
        }
        this.resetPlayerBoat(state);
    }

    /** 在异常受伤时接管位置恢复，避免走公共死亡链。 */
    public boolean recoverPlayerFromAbnormalPosition(PlayerInfo playerInfo, EntityDamageEvent event) {
        if (playerInfo == null || this.room == null || this.closed) {
            return false;
        }
        GameRoom.GameType roomType = this.room.getType();
        if (roomType != GameRoom.GameType.START && roomType != GameRoom.GameType.END) {
            return false;
        }
        if (!(playerInfo.getPlayer() instanceof Player player)) {
            return false;
        }
        if (event == null) {
            return false;
        }
        PlayerInfo.PlayerType playerType = playerInfo.getPlayerType();
        if (playerType == PlayerInfo.PlayerType.WATCH
                || playerType == PlayerInfo.PlayerType.LEAVE
                || playerType == PlayerInfo.PlayerType.DEATH
                || playerType == PlayerInfo.PlayerType.WAIT_HELP) {
            return false;
        }
        DragonBoatEntity ridingBoat = this.getMountedBoat(playerInfo);
        // 只处理“仍在龙舟上”的异常状态，正常下船后的首次受伤不应被当成复位条件。
        if (ridingBoat == null || player.riding != ridingBoat) {
            return false;
        }
        EntityDamageEvent.DamageCause cause = event.getCause();
        boolean abnormalCause = cause == EntityDamageEvent.DamageCause.VOID
                || cause == EntityDamageEvent.DamageCause.SUFFOCATION
                || cause == EntityDamageEvent.DamageCause.FIRE
                || cause == EntityDamageEvent.DamageCause.FIRE_TICK
                || cause == EntityDamageEvent.DamageCause.LAVA
                || cause == EntityDamageEvent.DamageCause.DROWNING;
        boolean lethalDamage = event.getFinalDamage() + 1 > player.getHealth();
        if (!abnormalCause && !lethalDamage) {
            return false;
        }
        if (!abnormalCause
                && cause != EntityDamageEvent.DamageCause.VOID
                && cause != EntityDamageEvent.DamageCause.SUFFOCATION) {
            return false;
        }
        RacePlayerState state = this.playerStateMap.get(playerInfo.getName());
        if (state == null) {
            return false;
        }
        this.releasePlayer(state);
        this.resetPlayerBoat(state);
        return true;
    }

    /** 将玩家的龙舟重置到最近的冰道中心位置。 */
    private void resetPlayerBoat(RacePlayerState state) {
        if (!(state.playerInfo.getPlayer() instanceof Player)) {
            return;
        }
        Player player = (Player) state.playerInfo.getPlayer();
        DragonBoatEntity boat = this.getMountedBoat(state.playerInfo);
        boolean keepMounted = boat != null && !boat.closed && player.riding == boat;
        Position source = boat != null && !boat.closed ? boat : player;
        if (source == null || source.level == null) {
            return;
        }
        Vector3 direction = this.getRaceDirection();
        int floorY = this.resolveTrackFloorY(source.level, source.getFloorX(), source.getFloorY(), source.getFloorZ());
        TrackCenterResult trackResult = this.findTrackCenter(source.level, source.x, source.z, floorY, direction);
        Position trackPos = trackResult == null ? null : trackResult.toPosition(source.level, floorY);
        if (trackPos == null) {
            player.sendMessage("§c未找到附近冰道");
            return;
        }
        this.loadChunksAround(trackPos, ACTIVE_CHUNK_RADIUS);
        this.loadChunksAround(player.getPosition(), ACTIVE_CHUNK_RADIUS);
        if (boat == null) {
            this.spawnOrMoveBoat(state, trackPos, this.vectorToYaw(direction));
            boat = state.spawnedBoat;
        }
        if (boat != null && !boat.closed) {
            boat.teleport(new Location(trackPos.x, trackPos.y, trackPos.z, this.vectorToYaw(direction), 0, trackPos.level));
            boat.setMotion(new Vector3(0, 0, 0));
            // 正常下船后只移动龙舟，不再把玩家强制拉回骑乘状态。
            if (keepMounted && player.riding != boat) {
                boat.mountEntity(player);
            }
        }
        player.sendMessage("§a已重置到最近冰道中心");
    }

    /** 从当前位置附近推断冰道所在的地面高度。 */
    private int resolveTrackFloorY(cn.nukkit.level.Level level, int blockX, int approxY, int blockZ) {
        int startY = Math.max(1, approxY + 1);
        int minY = Math.max(0, approxY - 3);

        for (int y = startY; y >= minY; y--) {
            if (this.isIceBlock(level.getBlock(blockX, y, blockZ))) {
                return y;
            }
        }
        return Math.max(0, approxY - 1);
    }

    /** 在当前位置附近搜索最近的有效冰道中心。 */
    private TrackCenterResult findTrackCenter(cn.nukkit.level.Level level, double x, double z, int floorY, Vector3 direction) {
        double perpX = -direction.z;
        double perpZ = direction.x;
        int anchorX = (int) Math.floor(x);
        int anchorZ = (int) Math.floor(z);
        TrackCenterResult currentTrack = this.findTrackContaining(level, anchorX, anchorZ, floorY, perpX, perpZ);
        if (currentTrack != null) {
            return currentTrack;
        }
        for (int distance = 1; distance <= MAX_TRACK_SEARCH_DISTANCE; distance++) {
            for (int side = -1; side <= 1; side += 2) {
                int edgeX = anchorX + (int) Math.round(perpX * distance * side);
                int edgeZ = anchorZ + (int) Math.round(perpZ * distance * side);
                TrackCenterResult result = this.findTrackFromEdge(level, edgeX, edgeZ, floorY, perpX * side, perpZ * side);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    /** 若当前位置就在冰道上，则直接求出该冰道的中心。 */
    private TrackCenterResult findTrackContaining(cn.nukkit.level.Level level, int blockX, int blockZ, int floorY, double perpX, double perpZ) {
        if (!this.isIceBlock(level.getBlock(blockX, floorY, blockZ))) {
            return null;
        }
        int positiveSteps = this.countConsecutiveIce(level, blockX, blockZ, floorY, perpX, perpZ);
        int negativeSteps = this.countConsecutiveIce(level, blockX, blockZ, floorY, -perpX, -perpZ);
        int width = positiveSteps + negativeSteps + 1;
        if (width < MIN_TRACK_WIDTH) {
            return null;
        }
        double centerX = blockX + ((positiveSteps - negativeSteps) * perpX) / 2.0d + 0.5d;
        double centerZ = blockZ + ((positiveSteps - negativeSteps) * perpZ) / 2.0d + 0.5d;
        return new TrackCenterResult(centerX, centerZ);
    }

    /** 从冰道边缘朝单侧扫描并计算整条冰道中心。 */
    private TrackCenterResult findTrackFromEdge(cn.nukkit.level.Level level, int startX, int startZ, int floorY, double scanX, double scanZ) {
        if (!this.isIceBlock(level.getBlock(startX, floorY, startZ))) {
            return null;
        }
        int width = this.countConsecutiveIce(level, startX, startZ, floorY, scanX, scanZ) + 1;
        if (width < MIN_TRACK_WIDTH) {
            return null;
        }
        double centerX = startX + scanX * (width - 1) / 2.0d + 0.5d;
        double centerZ = startZ + scanZ * (width - 1) / 2.0d + 0.5d;
        return new TrackCenterResult(centerX, centerZ);
    }

    /** 统计某个方向上连续冰块的长度。 */
    private int countConsecutiveIce(cn.nukkit.level.Level level, int startX, int startZ, int floorY, double stepX, double stepZ) {
        int steps = 0;
        while (steps < MAX_TRACK_SEARCH_DISTANCE) {
            int nextX = startX + (int) Math.round(stepX * (steps + 1));
            int nextZ = startZ + (int) Math.round(stepZ * (steps + 1));
            if (!this.isIceBlock(level.getBlock(nextX, floorY, nextZ))) {
                break;
            }
            steps++;
        }
        return steps;
    }

    /** 根据名称判断一个方块是否可视为冰道方块。 */
    private boolean isIceBlock(Block block) {
        if (block == null) {
            return false;
        }
        String displayName = block.getName().toLowerCase();
        if (displayName.contains("ice") || displayName.contains("冰")) {
            return true;
        }
        String name = block.getClass().getSimpleName().toLowerCase();
        return name.contains("ice");
    }

    private final class RacePickupEntity {
        private final DragonBoatRaceConfig.ItemSpawnConfig itemSpawnConfig;
        private Entity entity;
        private long nextRespawnTime = 0L;

        /** 创建一个奖励箱刷新点状态对象。 */
        private RacePickupEntity(DragonBoatRaceConfig.ItemSpawnConfig itemSpawnConfig) {
            this.itemSpawnConfig = itemSpawnConfig;
        }

        /** 确保奖励箱在需要时生成，并处理拾取后的重生计时。 */
        private void ensureSpawned() {
            if (this.entity != null && !this.entity.closed) {
                return;
            }
            if (this.entity != null) {
                if (this.entity instanceof RewardBoxEntity && ((RewardBoxEntity) this.entity).isCollected()) {
                    this.nextRespawnTime = System.currentTimeMillis() + PICKUP_RESPAWN_DELAY_MS;
                }
                this.entity = null;
            }
            if (System.currentTimeMillis() < this.nextRespawnTime) {
                return;
            }
            Position position = this.itemSpawnConfig.getPosition();
            if (position == null || position.getChunk() == null) {
                return;
            }
            CompoundTag nbt = Entity.getDefaultNBT(position.add(0, 0.9d, 0));
            Entity rewardBoxEntity = Entity.createEntity(RewardBoxEntity.DEF_ENTITY.getIdentifier(), position.getChunk(), nbt);
            if (rewardBoxEntity instanceof RewardBoxEntity) {
                rewardBoxEntity.setMotion(new Vector3(0, 0, 0));
                rewardBoxEntity.spawnToAll();
                this.entity = rewardBoxEntity;
            }
        }

        /** 标记奖励箱已被消耗并开始重生倒计时。 */
        private void consume() {
            if (this.entity != null && !this.entity.closed) {
                this.entity.close();
            }
            this.entity = null;
            this.nextRespawnTime = System.currentTimeMillis() + PICKUP_RESPAWN_DELAY_MS;
        }

        /** 关闭奖励箱实体并重置内部状态。 */
        private void close() {
            if (this.entity != null && !this.entity.closed) {
                this.entity.close();
            }
            this.entity = null;
            this.nextRespawnTime = 0L;
        }
    }

    private static final class RacePlayerState {
        private final PlayerInfo playerInfo;
        private DragonBoatEntity spawnedBoat;
        private Position lineupPosition;
        private boolean finished = false;
        private boolean mounted = false;
        private boolean unfinished = false;
        private boolean forceImmobile = false;
        private int finishRank = -1;
        private long finishTimeMillis = -1L;
        private long boostEndTime = -1L;
        private long stunEndTime = -1L;
        private double lastProgress = 0d;
        private double lastDistanceToFinish = Double.MAX_VALUE;
        private int lastStartTitleSecond = Integer.MIN_VALUE;
        private int lastCloseTitleSecond = Integer.MIN_VALUE;
        private int lineupIndex = -1;
        private long lastPreloadChunkKey = Long.MIN_VALUE;

        /** 为参赛玩家创建一份竞速运行时状态。 */
        private RacePlayerState(PlayerInfo playerInfo) {
            this.playerInfo = playerInfo;
        }
    }

    private static final class TrackCenterResult {
        private final double centerX;
        private final double centerZ;

        /** 记录一次冰道中心搜索结果。 */
        private TrackCenterResult(double centerX, double centerZ) {
            this.centerX = centerX;
            this.centerZ = centerZ;
        }

        /** 将冰道中心结果转换成可直接传送的世界坐标。 */
        private Position toPosition(cn.nukkit.level.Level level, int floorY) {
            return new Position(this.centerX, floorY + 1.0d, this.centerZ, level);
        }
    }
}
