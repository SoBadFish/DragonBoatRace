package org.sobadfish.dragonboat.manager;

import cn.nukkit.Player;
import cn.nukkit.item.Item;
import cn.nukkit.level.Sound;
import org.sobadfish.dragonboat.customitem.DragonBoostItem;
import org.sobadfish.dragonboat.customitem.DragonMagnetItem;
import org.sobadfish.dragonboat.customitem.DragonMissileItem;

import java.util.concurrent.ThreadLocalRandom;

public final class RewardManager {

    private RewardManager() {
    }

    public static Item createRandomReward() {
        return switch (ThreadLocalRandom.current().nextInt(3)) {
            case 0 -> new DragonBoostItem();
            case 1 -> new DragonMagnetItem();
            default -> new DragonMissileItem();
        };
    }

    public static void giveRandomReward(Player player) {
        Item reward = createRandomReward();
        player.getInventory().addItem(reward);
        player.getLevel().addSound(player, Sound.RANDOM_ORB, 0.85f, 1.45f);
        player.getLevel().addSound(player, Sound.FIREWORK_TWINKLE, 0.75f, 1.25f);
        player.sendMessage("§e你获得了奖励道具: §6" + reward.getCustomName().replace("§r", ""));
    }
}
