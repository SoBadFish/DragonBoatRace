package org.sobadfish.dragonboatgame.event;


import cn.nukkit.event.Cancellable;
import cn.nukkit.plugin.Plugin;
import org.sobadfish.dragonboatgame.room.GameRoom;

/**
 * 房间游戏开始事�?
 * @author SoBadFish
 * 2022/1/15
 */
public class GameRoomStartEvent extends GameRoomEvent implements Cancellable {

    public GameRoomStartEvent(GameRoom room, Plugin plugin) {
        super(room, plugin);
    }
}


