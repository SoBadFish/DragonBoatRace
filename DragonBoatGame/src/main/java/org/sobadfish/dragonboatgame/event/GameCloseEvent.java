package org.sobadfish.dragonboatgame.event;

import cn.nukkit.plugin.Plugin;
import org.sobadfish.dragonboatgame.room.GameRoom;


/**
 * 房间关闭事件
 * @author SoBadFish
 * 2022/1/15
 */
public class GameCloseEvent extends GameRoomEvent{

    public GameCloseEvent(GameRoom room, Plugin plugin) {
        super(room, plugin);
    }
}


