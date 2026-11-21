package org.sobadfish.dragonboatgame.item;

import cn.nukkit.item.Item;
import org.sobadfish.dragonboatgame.player.PlayerInfo;

/**
 * @author Sobadfish
 * 2023/2/7
 */
public interface ICustomItem {

    /**
     * 被点击触�?
     * @param player 游戏内玩�?
     * */
    void onClick(PlayerInfo player);

    /**
     * 触发点击事件后是否消耗此物品
     * @return 是否被消�?
     * */
    boolean canBeUse();

    /**
     * 获取物品实体�?
     * @return 物品
     * */
    Item getItem();

    /**
     * 通过这个方法可根据名称获取物品实体类
     * @return 物品的名�?
     * */
    String getName();

    /**
     * 显示的名�?
     * @return 自定义显示的名称
     * */
    String getCustomName();

}


