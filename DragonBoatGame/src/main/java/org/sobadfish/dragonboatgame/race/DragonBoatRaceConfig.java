package org.sobadfish.dragonboatgame.race;

import cn.nukkit.item.Item;
import cn.nukkit.level.Position;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.utils.Config;
import org.sobadfish.dragonboatgame.room.config.WorldInfoConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class DragonBoatRaceConfig {

    public static final String CONFIG_NODE = "dragon-boat-game";

    private LineConfig startLine = new LineConfig("", "");
    private LineConfig finishLine = new LineConfig("", "");
    private int firstFinishCountdown = 10;
    private int rankingBoardSize = 8;
    private int itemPickupDistance = 3;
    private int boostDurationTicks = 80;
    private int magnetDurationTicks = 80;
    private int missileStunTicks = 60;
    private double startPushStrength = 0.35d;
    private double boostStrength = 0.18d;
    private double magnetStrength = 0.12d;
    private double missileRange = 18d;
    private double finishLockSpeed = 0.15d;
    private int boatVariant = 0;
    private final List<ItemSpawnConfig> itemSpawns = new ArrayList<ItemSpawnConfig>();
    private final Map<Integer, List<String>> rankingRewards = new LinkedHashMap<Integer, List<String>>();
    private final List<String> dnfRewards = new ArrayList<String>();

    public LineConfig getStartLine() {
        return startLine;
    }

    public LineConfig getFinishLine() {
        return finishLine;
    }

    public int getFirstFinishCountdown() {
        return firstFinishCountdown;
    }

    public int getRankingBoardSize() {
        return rankingBoardSize;
    }

    public int getItemPickupDistance() {
        return itemPickupDistance;
    }

    public int getBoostDurationTicks() {
        return boostDurationTicks;
    }

    public int getMagnetDurationTicks() {
        return magnetDurationTicks;
    }

    public int getMissileStunTicks() {
        return missileStunTicks;
    }

    public double getStartPushStrength() {
        return startPushStrength;
    }

    public double getBoostStrength() {
        return boostStrength;
    }

    public double getMagnetStrength() {
        return magnetStrength;
    }

    public double getMissileRange() {
        return missileRange;
    }

    public double getFinishLockSpeed() {
        return finishLockSpeed;
    }

    public int getBoatVariant() {
        return boatVariant;
    }

    public List<ItemSpawnConfig> getItemSpawns() {
        return itemSpawns;
    }

    public Map<Integer, List<String>> getRankingRewards() {
        return rankingRewards;
    }

    public List<String> getDnfRewards() {
        return dnfRewards;
    }

    public static DragonBoatRaceConfig load(Config roomConfig) {
        DragonBoatRaceConfig config = new DragonBoatRaceConfig();
        Object node = roomConfig.get(CONFIG_NODE);
        if (!(node instanceof Map)) {
            return config;
        }

        Map<?, ?> root = (Map<?, ?>) node;
        config.startLine = LineConfig.load(root.get("start-line"));
        config.finishLine = LineConfig.load(root.get("finish-line"));
        config.firstFinishCountdown = intValue(root.get("first-finish-countdown"), config.firstFinishCountdown);
        config.rankingBoardSize = intValue(root.get("ranking-board-size"), config.rankingBoardSize);
        config.itemPickupDistance = intValue(root.get("item-pickup-distance"), config.itemPickupDistance);
        config.boostDurationTicks = intValue(root.get("boost-duration-ticks"), config.boostDurationTicks);
        config.magnetDurationTicks = intValue(root.get("magnet-duration-ticks"), config.magnetDurationTicks);
        config.missileStunTicks = intValue(root.get("missile-stun-ticks"), config.missileStunTicks);
        config.startPushStrength = doubleValue(root.get("start-push-strength"), config.startPushStrength);
        config.boostStrength = doubleValue(root.get("boost-strength"), config.boostStrength);
        config.magnetStrength = doubleValue(root.get("magnet-strength"), config.magnetStrength);
        config.missileRange = doubleValue(root.get("missile-range"), config.missileRange);
        config.finishLockSpeed = doubleValue(root.get("finish-lock-speed"), config.finishLockSpeed);
        config.boatVariant = intValue(root.get("boat-variant"), config.boatVariant);

        Object spawnNode = root.get("item-spawns");
        if (spawnNode instanceof List) {
            for (Object value : (List<?>) spawnNode) {
                ItemSpawnConfig itemSpawnConfig = ItemSpawnConfig.load(value);
                if (itemSpawnConfig != null) {
                    config.itemSpawns.add(itemSpawnConfig);
                }
            }
        }

        Object rewardsNode = root.get("ranking-rewards");
        if (rewardsNode instanceof Map) {
            Map<?, ?> rewardMap = (Map<?, ?>) rewardsNode;
            for (Map.Entry<?, ?> entry : rewardMap.entrySet()) {
                try {
                    int rank = Integer.parseInt(String.valueOf(entry.getKey()));
                    List<String> commands = toStringList(entry.getValue());
                    if (!commands.isEmpty()) {
                        config.rankingRewards.put(rank, commands);
                    }
                } catch (Exception ignore) {
                }
            }
        }

        config.dnfRewards.addAll(toStringList(root.get("dnf-rewards")));
        return config;
    }

    public static final class LineConfig {
        private final String from;
        private final String to;

        private LineConfig(String from, String to) {
            this.from = from;
            this.to = to;
        }

        public Position getFrom() {
            return WorldInfoConfig.getPositionByString(from);
        }

        public Position getTo() {
            return WorldInfoConfig.getPositionByString(to);
        }

        public boolean isValid() {
            Position fromPos = getFrom();
            Position toPos = getTo();
            return fromPos != null && toPos != null && fromPos.level != null && toPos.level != null;
        }

        public static LineConfig load(Object node) {
            if (!(node instanceof Map)) {
                return new LineConfig("", "");
            }
            Map<?, ?> map = (Map<?, ?>) node;
            return new LineConfig(stringValue(map.get("from"), ""), stringValue(map.get("to"), ""));
        }
    }

    public static final class ItemSpawnConfig {
        private final String position;
        private final int respawnSeconds;

        private ItemSpawnConfig(String position, int respawnSeconds) {
            this.position = position;
            this.respawnSeconds = respawnSeconds;
        }

        public Position getPosition() {
            return WorldInfoConfig.getPositionByString(position);
        }

        public int getRespawnSeconds() {
            return respawnSeconds;
        }

        public Item createDisplayItem() {
            Item item = Item.fromString("54:0:1");
            item.setCustomName("§6竞速道具");
            item.setLore(new String[]{"§7驶过后获得随机干扰道具"});
            return item;
        }

        public DragonBoatPowerType randomPowerType() {
            DragonBoatPowerType[] values = DragonBoatPowerType.values();
            return values[ThreadLocalRandom.current().nextInt(values.length)];
        }

        public static ItemSpawnConfig load(Object value) {
            if (value instanceof String) {
                return new ItemSpawnConfig(String.valueOf(value), 30);
            }
            if (!(value instanceof Map)) {
                return null;
            }
            Map<?, ?> map = (Map<?, ?>) value;
            String position = stringValue(map.get("position"), "");
            if ("".equals(position)) {
                return null;
            }
            int respawn = intValue(map.get("respawn"), 30);
            return new ItemSpawnConfig(position, Math.max(1, respawn));
        }
    }

    public enum DragonBoatPowerType {
        BOOST("boost", "353:0:1", "§b龙舟加速", "§7右键获得短时冲刺"),
        MAGNET("magnet", "345:0:1", "§e龙舟磁铁", "§7右键锁定前方目标并高速吸附"),
        MISSILE("missile", "369:0:1", "§c龙舟导弹", "§7右键冻结前方目标");

        private final String id;
        private final String itemString;
        private final String displayName;
        private final String lore;

        DragonBoatPowerType(String id, String itemString, String displayName, String lore) {
            this.id = id;
            this.itemString = itemString;
            this.displayName = displayName;
            this.lore = lore;
        }

        public Item createItem(String tagName) {
            Item item = Item.fromString(itemString);
            item.setCustomName(displayName);
            item.setLore(new String[]{lore});
            CompoundTag tag = item.hasCompoundTag() ? item.getNamedTag() : new CompoundTag();
            tag.putString(tagName, id);
            item.setNamedTag(tag);
            return item;
        }

        public static DragonBoatPowerType fromId(String id) {
            for (DragonBoatPowerType value : values()) {
                if (value.id.equalsIgnoreCase(id)) {
                    return value;
                }
            }
            return null;
        }
    }

    private static List<String> toStringList(Object value) {
        List<String> result = new ArrayList<String>();
        if (value instanceof List) {
            for (Object obj : (List<?>) value) {
                if (obj != null) {
                    result.add(String.valueOf(obj));
                }
            }
        }
        return result;
    }

    private static String stringValue(Object value, String def) {
        return value == null ? def : String.valueOf(value);
    }

    private static int intValue(Object value, int def) {
        try {
            return value == null ? def : Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return def;
        }
    }

    private static double doubleValue(Object value, double def) {
        try {
            return value == null ? def : Double.parseDouble(String.valueOf(value));
        } catch (Exception e) {
            return def;
        }
    }
}
