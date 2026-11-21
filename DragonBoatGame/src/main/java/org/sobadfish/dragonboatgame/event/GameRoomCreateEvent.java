package org.sobadfish.dragonboatgame.event;

import cn.nukkit.plugin.Plugin;
import org.sobadfish.dragonboatgame.dlc.IGameRoomDlc;
import org.sobadfish.dragonboatgame.room.GameRoom;

/**
 * @author Sobadfish
 *  2023/2/21
 */
public class GameRoomCreateEvent extends GameRoomEvent{

    public GameRoomCreateEvent(GameRoom room, Plugin plugin) {
        super(room, plugin);
    }

    public void addDlc(IGameRoomDlc dlc){
        getRoom().getGameRoomDlc().add(dlc);
    }
}


