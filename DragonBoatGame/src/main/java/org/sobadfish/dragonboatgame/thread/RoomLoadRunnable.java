package org.sobadfish.dragonboatgame.thread;

import cn.nukkit.Server;
import org.sobadfish.dragonboatgame.manager.RoomManager;
import org.sobadfish.dragonboatgame.manager.ThreadManager;
import org.sobadfish.dragonboatgame.manager.TotalManager;
import org.sobadfish.dragonboatgame.player.PlayerInfo;
import org.sobadfish.dragonboatgame.room.GameRoom;


import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class RoomLoadRunnable extends ThreadManager.AbstractGameRunnable {

    public LinkedHashMap<String,Long> time = new LinkedHashMap<>();

    /** 上一帧是否还排在主线程队列里没执行，避免主线程卡顿时任务堆积 */
    private volatile boolean tickQueued;

    @Override
    public GameRoom getRoom() {
        return null;
    }

    @Override
    public String getThreadName() {
        String color = "&a";
        if(isClose){
            color = "&7";
        }
        StringBuilder s = new StringBuilder(color+"房间进程 &7(" + TotalManager.getRoomManager().getRooms().size() + ")\n");
        for(Map.Entry<String,Long> room: time.entrySet()){
            s.append("     &r").append(room.getKey()).append("  &a").append(room.getValue()).append(" ms\n");

        }
        return s.toString();
    }

    @Override
    public void run() {
        if (isClose) {
            ThreadManager.cancel(this);
            return;
        }
        if (TotalManager.isDisabled()) {
            isClose = true;
            return;
        }
        if (tickQueued) {
            return;
        }
        tickQueued = true;
        //本 runnable 由后台线程池驱动，房间/玩家/实体逻辑必须回主线程执行
        ThreadManager.runOnMainThread(() -> {
            try {
                tickRooms();
            } finally {
                tickQueued = false;
            }
        });
    }

    /** 在主线程推进所有房间一帧。 */
    private void tickRooms() {
        try {
            if (isClose || TotalManager.isDisabled()) {
                return;
            }
            List<GameRoom> gameRooms = new CopyOnWriteArrayList<>(TotalManager.getRoomManager().getRooms().values());
            for (GameRoom room : gameRooms) {
                long t1 = System.currentTimeMillis();
                if (TotalManager.getRoomManager().getRoom(room.getRoomConfig().name) == null) {
                    RoomManager.LOCK_GAME.remove(room.getRoomConfig());
                    TotalManager.getRoomManager().getRooms().remove(room.getRoomConfig().name);
                    continue;
                }

                if (room.close || room.worldInfo == null || room.worldInfo.getConfig().getGameWorld() == null) {
                    continue;
                }
                room.onUpdate();
                for (PlayerInfo playerInfo : new ArrayList<>(room.getPlayerInfos())) {
                    if (playerInfo.cancel || playerInfo.isLeave) {
                        playerInfo.removeScoreBoard();

                    } else {
                        playerInfo.onUpdate();
                    }

                }

                if (room.loadTime > 0) {
                    if (!room.getEventControl().hasEvent()) {
                        if(!room.getRoomConfig().infiniteTime || room.getType() != GameRoom.GameType.START){
                            room.loadTime--;
                        }

                    }
                }
                try {
                    if (room.worldInfo != null) {
                        if (!room.worldInfo.isClose()) {
                            Server.getInstance().getScheduler().scheduleTask(TotalManager.getPlugin(),new WorldInfoMasterThread(room,room.worldInfo,TotalManager.getPlugin()));
                        }
                    }
                } catch (Exception ignore) {
                }

                time.put(room.getRoomConfig().name, System.currentTimeMillis() - t1);
            }
        }catch (Exception e){
            e.printStackTrace();
        }

    }
}


