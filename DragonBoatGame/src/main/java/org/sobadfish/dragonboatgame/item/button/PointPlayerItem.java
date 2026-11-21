package org.sobadfish.dragonboatgame.item.button;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemCompass;
import org.sobadfish.dragonboatgame.item.ICustomItem;
import org.sobadfish.dragonboatgame.player.PlayerInfo;
import org.sobadfish.dragonboatgame.room.GameRoom;

import java.util.*;

/**
 * @author Sobadfish
 * 12:52
 */
public class PointPlayerItem implements ICustomItem {
    @Override
    public void onClick(PlayerInfo player) {
        if(player == null){
            return;
        }
        GameRoom room = player.getGameRoom();
        if (room == null) {
            player.sendTip("&c当前不在房间内");
            return;
        }
        PlayerInfo target = null;
        LinkedHashMap<PlayerInfo,Double> dis = new LinkedHashMap<>();
        for(PlayerInfo info1: room.getLivePlayers()){
            if(info1.getGameRoom().isOnlyTeam()){
                if(!info1.equals(player)) {
                    dis.put(info1, player.distance(info1.getPlayer()));
                }
            }else{
                if(!info1.getTeamInfo().equals(player.getTeamInfo())){
                    dis.put(info1,player.distance(info1.getPlayer()));
                }
            }


        }
        List<Map.Entry<PlayerInfo, Double>> list = new ArrayList<>(dis.entrySet());
        list.sort(Comparator.comparingInt(o -> o.getValue().intValue()));
        if(list.size() > 0) {
            target = list.get(0).getKey();

            if (target != null) {
                player.sendTip("&a找到" + target + "\n" + "&c距离: &r" + String.format("%.2f", list.get(0).getValue()) + " 米");

            }
        } else {
            player.sendTip("&e当前没有可追踪的其他玩家");
        }
    }

    @Override
    public boolean canBeUse() {
        return false;
    }

    @Override
    public Item getItem() {
        return new ItemCompass();
    }

    @Override
    public String getName() {
        return "指南针";
    }

    @Override
    public String getCustomName() {
        return null;
    }
}


