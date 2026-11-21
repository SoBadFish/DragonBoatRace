package org.sobadfish.dragonboatgame.panel;

import cn.nukkit.Player;
import org.sobadfish.dragonboatgame.panel.from.GameFrom;
import org.sobadfish.dragonboatgame.panel.from.button.BaseIButton;
import org.sobadfish.dragonboatgame.tools.Utils;


import java.util.LinkedHashMap;
import java.util.List;

/**
 * GUI菜单
 * @author SoBadFish
 * 2022/1/11
 */
public class DisPlayWindowsFrom {

    public static int FROM_ID = 155;

    public static int FROM_MAX_ID = 1054780;

    public static LinkedHashMap<String, GameFrom> FROM = new LinkedHashMap<>();


    public static void disPlayerCustomMenu(Player player, String tag, List<BaseIButton> from){
        GameFrom gf = new GameFrom(tag,"",getId());
        gf.setBaseIButtons(from);
        FROM.put(player.getName(), gf);
        gf.disPlay(player);
    }

    public static void disPlayFrom(Player player, GameFrom gameFrom){
        FROM.put(player.getName(), gameFrom);
        gameFrom.disPlay(player);
    }


    public static int getId(){
        return Utils.rand(FROM_ID,FROM_MAX_ID);
    }

    public static int getId(int min,int max){
        return Utils.rand(min,max);
    }

}


