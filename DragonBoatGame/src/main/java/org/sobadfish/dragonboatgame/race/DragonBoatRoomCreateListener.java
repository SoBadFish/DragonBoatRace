package org.sobadfish.dragonboatgame.race;

import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import org.sobadfish.dragonboatgame.dlc.IGameRoomDlc;
import org.sobadfish.dragonboatgame.event.GameRoomCreateEvent;

/**
 * 将龙舟竞速规则注入所有房间。
 */
public class DragonBoatRoomCreateListener implements Listener {

    @EventHandler
    public void onRoomCreate(GameRoomCreateEvent event) {
        for (IGameRoomDlc dlc : event.getRoom().getGameRoomDlc()) {
            if (dlc instanceof DragonBoatRaceDlc) {
                return;
            }
        }
        DragonBoatRaceDlc dlc = new DragonBoatRaceDlc();
        event.addDlc(dlc);
        dlc.onEnable(event.getRoom());
    }
}


