package org.sobadfish.dragonboatgame.item.button;

import cn.nukkit.Player;
import cn.nukkit.form.element.ElementButton;
import cn.nukkit.form.element.ElementButtonImageData;
import cn.nukkit.form.window.FormWindowSimple;
import cn.nukkit.item.Item;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.utils.TextFormat;
import org.sobadfish.dragonboatgame.item.ICustomItem;
import org.sobadfish.dragonboatgame.item.ItemIDSunName;
import org.sobadfish.dragonboatgame.manager.TotalManager;
import org.sobadfish.dragonboatgame.player.PlayerInfo;
import org.sobadfish.dragonboatgame.player.team.TeamInfo;

import org.sobadfish.dragonboatgame.room.GameRoom;

/**
 * 玩家选择队伍物品
 * @author SoBadFish
 * 2022/1/3
 */
public class TeamChoseItem implements ICustomItem {


    @Override
    public void onClick(PlayerInfo player) {
        if(player.getPlayer() instanceof Player) {
            GameRoom room = player.getGameRoom();
            if(room != null) {
                int usePlayer = room.roomConfig.maxPlayerSize;
                int teamSize = room.getTeamInfos().size();
                FormWindowSimple simple = new FormWindowSimple(TotalManager.getLanguage().getLanguage("player-chose-team", "请选择队伍"), "");
                for (TeamInfo teamInfoConfig : room.getTeamInfos()) {
                    Item wool = teamInfoConfig.getTeamConfig().getTeamConfig().getBlockWoolColor();


                    int max;
                    if(teamInfoConfig.getTeamConfig().getTeamConfig().maxPlayer > 0){
                        max = teamInfoConfig.getTeamConfig().getTeamConfig().maxPlayer;
                        usePlayer -= max;
                        teamSize--;
                    }else{
                        max = (usePlayer / teamSize);
                    }

                    simple.addButton(new ElementButton(TextFormat.colorize('&', teamInfoConfig + " &r" + teamInfoConfig.getTeamPlayers().size() + " / " +max ),
                            new ElementButtonImageData("path",
                                    ItemIDSunName.getIDByPath(wool.getId(),wool.getDamage()))));
                }
                ((Player)player.getPlayer()).showFormWindow(simple, 102);
            }
        }
    }

    @Override
    public boolean canBeUse() {
        return false;
    }

    @Override
    public Item getItem() {
        Item i = Item.get(69);
        i.addEnchantment(Enchantment.getEnchantment(0));
        return i;
    }


    @Override
    public String getName() {
        return "teamChose";
    }

    @Override
    public String getCustomName() {
        return TextFormat.colorize('&',TotalManager.getLanguage().getLanguage("chose-team-button",
                "&r&l&e点我选择队伍"));
    }
}


