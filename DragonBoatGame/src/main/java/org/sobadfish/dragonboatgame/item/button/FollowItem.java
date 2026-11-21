package org.sobadfish.dragonboatgame.item.button;

import cn.nukkit.Player;
import cn.nukkit.item.Item;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.utils.TextFormat;
import org.sobadfish.dragonboatgame.item.ICustomItem;
import org.sobadfish.dragonboatgame.manager.TotalManager;
import org.sobadfish.dragonboatgame.panel.DisPlayWindowsFrom;
import org.sobadfish.dragonboatgame.panel.from.button.BaseIButton;
import org.sobadfish.dragonboatgame.panel.items.PlayerItem;
import org.sobadfish.dragonboatgame.player.PlayerInfo;
import org.sobadfish.dragonboatgame.room.GameRoom;

import java.util.ArrayList;
import java.util.List;

/**
 * 跟随玩家的物品栏菜单
 *
 * @author SoBadFish
 * 2022/8/10
 */
public class FollowItem implements ICustomItem {



    @Override
    public void onClick(PlayerInfo info) {
        GameRoom room = info.getGameRoom();
        info.sendMessage(TotalManager.getLanguage().getLanguage("player-chose-teleport-player","选择要传送的玩家"));
        if (room == null){
            return;
        }
        List<BaseIButton> list = new ArrayList<>();
        //手机玩家
        for(PlayerInfo i: room.getLivePlayers()){
            list.add(new BaseIButton(new PlayerItem(i).getGUIButton(info)) {
                @Override
                public void onClick(Player player) {
                    player.teleport(i.getPlayer().getLocation());
                }
            });
        }
        DisPlayWindowsFrom.disPlayerCustomMenu((Player) info.getPlayer()
                , TotalManager.getLanguage().getLanguage("player-from-teleport-player-title","传送玩家"), list);
    }

    @Override
    public boolean canBeUse() {
        return false;
    }

    @Override
    public Item getItem() {
        Item i = Item.get(345);
        i.addEnchantment(Enchantment.getEnchantment(0));
        return i;
    }

    @Override
    public String getName() {
        return "follow";
    }

    @Override
    public String getCustomName() {
        return TextFormat.colorize('&',TotalManager.getLanguage().getLanguage("teleport-player-button","&r&l&e点我传送到玩家"));
    }
}


