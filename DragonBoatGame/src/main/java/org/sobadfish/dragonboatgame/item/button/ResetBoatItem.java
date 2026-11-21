package org.sobadfish.dragonboatgame.item.button;

import cn.nukkit.item.Item;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.utils.TextFormat;
import org.sobadfish.dragonboatgame.dlc.IGameRoomDlc;
import org.sobadfish.dragonboatgame.item.ICustomItem;
import org.sobadfish.dragonboatgame.manager.TotalManager;
import org.sobadfish.dragonboatgame.player.PlayerInfo;
import org.sobadfish.dragonboatgame.race.DragonBoatRaceDlc;
import org.sobadfish.dragonboatgame.room.GameRoom;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ResetBoatItem implements ICustomItem {

    private static final long DOUBLE_CLICK_CONFIRM_WINDOW_MS = 2000L;
    private static final Map<String, Long> LAST_CLICK_TIME = new ConcurrentHashMap<String, Long>();

    @Override
    public void onClick(PlayerInfo playerInfo) {
        if (playerInfo == null) {
            return;
        }
        GameRoom room = playerInfo.getGameRoom();
        if (room == null) {
            return;
        }
        long now = System.currentTimeMillis();
        String playerName = playerInfo.getName();
        Long lastClickTime = LAST_CLICK_TIME.get(playerName);
        if (lastClickTime == null || now - lastClickTime > DOUBLE_CLICK_CONFIRM_WINDOW_MS) {
            LAST_CLICK_TIME.put(playerName, now);
            playerInfo.sendMessage("§e请在 2 秒内再次点击重置位置");
            return;
        }
        LAST_CLICK_TIME.remove(playerName);
        for (IGameRoomDlc dlc : room.getGameRoomDlc()) {
            if (dlc instanceof DragonBoatRaceDlc) {
                ((DragonBoatRaceDlc) dlc).onResetButtonClick(playerInfo);
                return;
            }
        }
    }

    @Override
    public boolean canBeUse() {
        return false;
    }

    @Override
    public Item getItem() {
        Item item = Item.get(345);
        item.addEnchantment(Enchantment.getEnchantment(0));
        return item;
    }

    @Override
    public String getName() {
        return "resetBoat";
    }

    @Override
    public String getCustomName() {
        return TextFormat.colorize('&', "&r&l&e重置龙舟位置");
    }
}
