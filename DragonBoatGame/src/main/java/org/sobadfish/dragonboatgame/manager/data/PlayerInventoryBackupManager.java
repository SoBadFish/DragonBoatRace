package org.sobadfish.dragonboatgame.manager.data;

import cn.nukkit.Player;
import cn.nukkit.item.Item;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.sobadfish.dragonboatgame.manager.TotalManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 玩家进入小游戏前的背包存档管理器。
 *
 * <p>原来的实现只把玩家背包放在 {@link org.sobadfish.dragonboatgame.player.PlayerInfo} 的内存字段里，
 * 一旦服务器重启（或者插件被重载）内存里的存档就没了，而服务端已经把玩家当前（被小游戏清空过的）
 * 背包写进了 player.dat，于是玩家永久丢失物品。这里把存档落到插件数据目录的
 * {@code player-inventory-backup.json}，玩家下次进入服务器时再还原。</p>
 *
 * <p>同时补齐了原实现漏掉的两个栏位：盔甲和副手。经验、快捷栏选中格也一并还原。</p>
 *
 * @author Sobadfish
 */
public final class PlayerInventoryBackupManager {

    private static final String FILE_NAME = "player-inventory-backup.json";

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private static PlayerInventoryBackupManager instance;

    private final File file;

    private final Map<String, Snapshot> snapshots = new LinkedHashMap<>();

    private PlayerInventoryBackupManager(File file) {
        this.file = file;
        this.load();
    }

    /**
     * 读取（或创建）玩家背包存档管理器。
     *
     * @param dataFolder 插件数据目录
     * @return 管理器实例
     */
    public static PlayerInventoryBackupManager init(File dataFolder) {
        PlayerInventoryBackupManager manager = new PlayerInventoryBackupManager(
                new File(dataFolder, FILE_NAME));
        instance = manager;
        return manager;
    }

    public static PlayerInventoryBackupManager get() {
        return instance;
    }

    /**
     * 备份玩家当前背包（主背包、盔甲、副手、末影箱、经验、选中格）。
     * 每次进入房间都会覆盖旧存档，保证存下来的永远是"进入小游戏之前"的状态。
     *
     * @param player 玩家
     */
    public static void backup(Player player) {
        if (instance != null) {
            instance.capture(player);
        }
    }

    /**
     * 还原玩家背包存档，成功后删除存档，避免重复还原导致刷物品。
     *
     * @param player 玩家
     * @return 是否真的还原了
     */
    public static boolean restore(Player player) {
        return instance != null && instance.restoreSnapshot(player);
    }

    /**
     * 是否存在该玩家的存档。
     *
     * @param playerName 玩家名
     * @return 是否有存档
     */
    public static boolean hasBackup(String playerName) {
        return instance != null && instance.snapshots.containsKey(key(playerName));
    }

    /**
     * 丢弃某个玩家的存档（用于玩家自己清档这类场景）。
     *
     * @param playerName 玩家名
     */
    public static void discard(String playerName) {
        if (instance != null && instance.snapshots.remove(key(playerName)) != null) {
            instance.save();
        }
    }

    private boolean restoreSnapshot(Player player) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        String key = key(player.getName());
        Snapshot snapshot = this.snapshots.get(key);
        if (snapshot == null) {
            return false;
        }
        try {
            this.apply(player, snapshot);
        } catch (Throwable throwable) {
            TotalManager.sendMessageToConsole(TotalManager.getLanguage().getLanguage(
                    "player-inventory-restore-error",
                    "&c还原玩家 [1] 的背包存档失败，存档已保留: [2]",
                    player.getName(), String.valueOf(throwable)));
            return false;
        }
        this.snapshots.remove(key);
        this.save();
        return true;
    }

    private void capture(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        Snapshot snapshot = new Snapshot();
        snapshot.ensureMaps();
        snapshot.updatedAt = System.currentTimeMillis();
        snapshot.heldSlot = player.getInventory().getHeldItemSlot();
        snapshot.experience = Math.max(0, player.getExperience());
        snapshot.experienceLevel = Math.max(0, player.getExperienceLevel());

        encode(snapshot.items, player.getInventory().getContents());
        encode(snapshot.enderChest, player.getEnderChestInventory().getContents());
        encode(snapshot.offhand, player.getOffhandInventory().getContents());

        Item[] armor = player.getInventory().getArmorContents();
        if (armor != null) {
            for (int index = 0; index < armor.length; index++) {
                String encoded = encodeItem(armor[index]);
                if (encoded != null) {
                    snapshot.armor.put(String.valueOf(index), encoded);
                }
            }
        }

        this.snapshots.put(key(player.getName()), snapshot);
        this.save();
    }

    private void apply(Player player, Snapshot snapshot) {
        player.getInventory().clearAll();
        safeSetMain(player, snapshot.items);
        safeSetArmor(player, snapshot.armor);
        safeSetOffhand(player, snapshot.offhand);
        safeSetEnderChest(player, snapshot.enderChest);

        int held = snapshot.heldSlot;
        if (held < 0 || held > 8) {
            held = 0;
        }
        player.getInventory().setHeldItemSlot(held);
        player.setExperience(Math.max(0, snapshot.experience), Math.max(0, snapshot.experienceLevel));
    }

    /** 单个栏位还原失败时不要影响其它栏位。 */
    private static void safeSetMain(Player player, Map<String, String> encoded) {
        try {
            player.getInventory().setContents(decodeMap(encoded));
        } catch (Throwable ignore) {
            // 交给上层记录
        }
    }

    private static void safeSetArmor(Player player, Map<String, String> encoded) {
        try {
            player.getInventory().setArmorContents(decodeArmor(encoded));
        } catch (Throwable ignore) {
            // 交给上层记录
        }
    }

    private static void safeSetOffhand(Player player, Map<String, String> encoded) {
        try {
            player.getOffhandInventory().setContents(decodeMap(encoded));
        } catch (Throwable ignore) {
            // 交给上层记录
        }
    }

    private static void safeSetEnderChest(Player player, Map<String, String> encoded) {
        try {
            player.getEnderChestInventory().setContents(decodeMap(encoded));
        } catch (Throwable ignore) {
            // 交给上层记录
        }
    }

    private static Map<Integer, Item> decodeMap(Map<String, String> encoded) {
        Map<Integer, Item> items = new LinkedHashMap<>();
        if (encoded == null || encoded.isEmpty()) {
            return items;
        }
        for (Map.Entry<String, String> entry : encoded.entrySet()) {
            int slot;
            try {
                slot = Integer.parseInt(entry.getKey());
            } catch (NumberFormatException ignore) {
                continue;
            }
            Item item = decodeItem(entry.getValue());
            if (item != null && !item.isNull()) {
                items.put(slot, item);
            }
        }
        return items;
    }

    private static Item[] decodeArmor(Map<String, String> encoded) {
        Item[] armor = new Item[4];
        for (int index = 0; index < armor.length; index++) {
            armor[index] = Item.get(0);
        }
        if (encoded == null) {
            return armor;
        }
        for (Map.Entry<String, String> entry : encoded.entrySet()) {
            int slot;
            try {
                slot = Integer.parseInt(entry.getKey());
            } catch (NumberFormatException ignore) {
                continue;
            }
            if (slot < 0 || slot >= armor.length) {
                continue;
            }
            Item item = decodeItem(entry.getValue());
            if (item != null) {
                armor[slot] = item;
            }
        }
        return armor;
    }

    private static void encode(Map<String, String> target, Map<Integer, Item> contents) {
        if (contents == null) {
            return;
        }
        for (Map.Entry<Integer, Item> entry : contents.entrySet()) {
            String encoded = encodeItem(entry.getValue());
            if (encoded != null) {
                target.put(String.valueOf(entry.getKey()), encoded);
            }
        }
    }

    /**
     * 把一个物品编码成 {@code id|damage|count|base64(nbt)|namespaceId} 字符串。
     * 不能只存 id 和数量，否则附魔、改名、NBT 全部会丢。
     */
    private static String encodeItem(Item item) {
        if (item == null || item.isNull() || item.getCount() <= 0) {
            return null;
        }
        String tag = "";
        try {
            if (item.hasCompoundTag()) {
                byte[] bytes = item.getCompoundTag();
                if (bytes != null && bytes.length > 0) {
                    tag = Base64.getEncoder().encodeToString(bytes);
                }
            }
        } catch (Throwable ignore) {
            tag = "";
        }
        String namespace = "";
        try {
            namespace = item.getNamespaceId();
        } catch (Throwable ignore) {
            namespace = "";
        }
        if (namespace == null) {
            namespace = "";
        }
        return item.getId() + "|" + item.getDamage() + "|" + item.getCount() + "|" + tag + "|" + namespace;
    }

    private static Item decodeItem(String encoded) {
        try {
            return decodeItemUnsafe(encoded);
        } catch (Throwable ignore) {
            return null;
        }
    }

    private static Item decodeItemUnsafe(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return null;
        }
        String[] parts = encoded.split("\\|", -1);
        if (parts.length < 3) {
            return null;
        }
        int id = parseInt(parts[0], 0);
        int damage = parseInt(parts[1], 0);
        int count = Math.max(1, parseInt(parts[2], 1));
        byte[] tag = null;
        if (parts.length > 3 && !parts[3].isEmpty()) {
            try {
                tag = Base64.getDecoder().decode(parts[3]);
            } catch (Throwable ignore) {
                tag = null;
            }
        }
        String namespace = parts.length > 4 ? parts[4] : "";

        Item item = null;
        try {
            item = Item.get(id, damage, count, tag);
        } catch (Throwable ignore) {
            item = null;
        }
        // 自定义物品的数值 id 在重启后可能发生变化，命名空间对不上时改用命名空间重建
        if (!namespace.isEmpty() && (item == null || item.isNull() || !namespace.equalsIgnoreCase(namespaceOf(item)))) {
            Supplier<Item> supplier = Item.NAMESPACED_ID_ITEM.get(namespace);
            if (supplier != null) {
                try {
                    Item rebuilt = supplier.get();
                    if (rebuilt != null && !rebuilt.isNull()) {
                        item = rebuilt;
                    }
                } catch (Throwable ignore) {
                    // 保持原 id 解析结果
                }
            }
        }
        if (item == null || item.isNull()) {
            return null;
        }
        try {
            item.setDamage(damage);
            item.setCount(count);
            if (tag != null) {
                item.setCompoundTag(tag);
            }
        } catch (Throwable ignore) {
            // 数量/NBT 写入失败时至少保留基础物品
        }
        return item;
    }

    private static String namespaceOf(Item item) {
        if (item == null) {
            return "";
        }
        try {
            String namespace = item.getNamespaceId();
            return namespace == null ? "" : namespace;
        } catch (Throwable ignore) {
            return "";
        }
    }

    private static int parseInt(String value, int def) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Throwable ignore) {
            return def;
        }
    }

    private static String key(String playerName) {
        return playerName == null ? "" : playerName.toLowerCase(Locale.ROOT);
    }

    private void load() {
        if (!this.file.exists()) {
            return;
        }
        try (Reader reader = new InputStreamReader(new FileInputStream(this.file), StandardCharsets.UTF_8)) {
            JsonElement rootElement = JsonParser.parseReader(reader);
            if (rootElement == null || !rootElement.isJsonObject()) {
                return;
            }
            JsonObject players = rootElement.getAsJsonObject().getAsJsonObject("players");
            if (players == null) {
                return;
            }
            for (Map.Entry<String, JsonElement> entry : players.entrySet()) {
                if (entry.getValue() == null || !entry.getValue().isJsonObject()) {
                    continue;
                }
                try {
                    Snapshot snapshot = GSON.fromJson(entry.getValue(), Snapshot.class);
                    if (snapshot != null) {
                        snapshot.ensureMaps();
                        this.snapshots.put(key(entry.getKey()), snapshot);
                    }
                } catch (Throwable ignore) {
                    // 单条存档损坏不影响其它玩家
                }
            }
            TotalManager.sendMessageToConsole(TotalManager.getLanguage().getLanguage(
                    "player-inventory-backup-load",
                    "&a已载入 [1] 份玩家背包存档", String.valueOf(this.snapshots.size())));
        } catch (Throwable throwable) {
            TotalManager.sendMessageToConsole(TotalManager.getLanguage().getLanguage(
                    "player-inventory-backup-load-error",
                    "&c读取玩家背包存档失败: [1]", String.valueOf(throwable)));
        }
    }

    private void save() {
        JsonObject players = new JsonObject();
        for (Map.Entry<String, Snapshot> entry : this.snapshots.entrySet()) {
            players.add(entry.getKey(), GSON.toJsonTree(entry.getValue()));
        }
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.addProperty("updated-at", System.currentTimeMillis());
        root.add("players", players);

        File parent = this.file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            TotalManager.sendMessageToConsole(TotalManager.getLanguage().getLanguage(
                    "plugin-room-folder-create-error", "&c创建文件夹 rooms失败"));
        }
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(this.file), StandardCharsets.UTF_8)) {
            writer.write(GSON.toJson(root));
            writer.flush();
        } catch (Throwable throwable) {
            TotalManager.sendMessageToConsole(TotalManager.getLanguage().getLanguage(
                    "player-inventory-backup-save-error",
                    "&c保存玩家背包存档失败: [1]", String.valueOf(throwable)));
        }
    }

    /**
     * 单个玩家的背包快照。字段名会直接写进 json，改动时保持兼容。
     */
    public static final class Snapshot {
        private long updatedAt;
        private int heldSlot;
        private int experience;
        private int experienceLevel;
        private Map<String, String> items;
        private Map<String, String> armor;
        private Map<String, String> offhand;
        private Map<String, String> enderChest;

        private void ensureMaps() {
            if (this.items == null) {
                this.items = new LinkedHashMap<>();
            }
            if (this.armor == null) {
                this.armor = new LinkedHashMap<>();
            }
            if (this.offhand == null) {
                this.offhand = new LinkedHashMap<>();
            }
            if (this.enderChest == null) {
                this.enderChest = new LinkedHashMap<>();
            }
        }
    }
}
