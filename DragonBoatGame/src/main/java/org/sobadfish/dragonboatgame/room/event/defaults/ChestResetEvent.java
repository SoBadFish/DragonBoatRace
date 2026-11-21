package org.sobadfish.dragonboatgame.room.event.defaults;

import org.sobadfish.dragonboatgame.manager.TotalManager;
import org.sobadfish.dragonboatgame.room.GameRoom;
import org.sobadfish.dragonboatgame.room.config.GameRoomEventConfig;
import org.sobadfish.dragonboatgame.room.event.IGameRoomEvent;

/**
 * @author Sobadfish
 *  2023/1/12
 */
public class ChestResetEvent extends IGameRoomEvent {

    public ChestResetEvent(GameRoomEventConfig.GameRoomEventItem item) {
        super(item);
    }

    @Override
    public void onStart(GameRoom room) {
        room.worldInfo.clickChest.clear();
        room.sendMessage(TotalManager.getLanguage().getLanguage("room-event-chest-reset","&a箱子全部刷新!"));
    }
}


