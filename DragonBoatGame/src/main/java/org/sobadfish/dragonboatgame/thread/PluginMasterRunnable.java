package org.sobadfish.dragonboatgame.thread;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.scheduler.AsyncTask;
import org.sobadfish.dragonboatgame.entity.GameFloatText;
import org.sobadfish.dragonboatgame.event.ReloadWorldEvent;
import org.sobadfish.dragonboatgame.manager.FloatTextManager;
import org.sobadfish.dragonboatgame.manager.ThreadManager;
import org.sobadfish.dragonboatgame.manager.TotalManager;
import org.sobadfish.dragonboatgame.manager.WorldResetManager;
import org.sobadfish.dragonboatgame.room.GameRoom;
import org.sobadfish.dragonboatgame.room.config.WorldInfoConfig;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author Sobadfish
 */
public class PluginMasterRunnable extends ThreadManager.AbstractGameRunnable {

    private long loadTime = 0;

    // 浮空字每隔几次循环统一更新，避免频繁刷新
    private int update = 0;


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
        StringBuilder s = new StringBuilder(color + "插件主进程 浮空字 &7(" +
                FloatTextManager.floatTextList.size() + ") &a" + loadTime + " ms\n");
        for(GameFloatText floatText:FloatTextManager.floatTextList){
            s.append("&r   - ").append(floatText.name).append(" &7pos=(")
                    .append(floatText.getFloorX()).append(":")
                    .append(floatText.getFloorY()).append(":")
                    .append(floatText.getFloorZ()).append(":")
                    .append(floatText.getLevel().getFolderName()).append(")\n");
        }

        return s.toString();
    }

    @Override
    public void run() {
        //浮空字/地图还原这类逻辑会操作实体和发包，必须回主线程执行
        ThreadManager.runOnMainThread(this::tickMaster);
    }

    private void tickMaster() {
        long t1 = System.currentTimeMillis();
        update ++;
        try {
            if (isClose) {
                ThreadManager.cancel(this);
                return;
            }
            if (TotalManager.isDisabled()) {
                isClose = true;
                return;
            }
            for (Player player : new ArrayList<>(Server.getInstance().getOnlinePlayers().values())) {
                for (GameFloatText floatText : new CopyOnWriteArrayList<>(FloatTextManager.floatTextList)) {
                    if (floatText == null) {
                        continue;
                    }

                    if (floatText.isFinalClose) {
                        FloatTextManager.removeFloatText(floatText);
                        continue;
                    }
//                    if (floatText.player.contains(player.getName())) {
//                        if (!player.getLevel().getFolderName().equalsIgnoreCase(floatText.getPosition().getLevel().getFolderName()) || !player.isOnline()) {
//                            if (!floatText.closed) {
//                                RemoveEntityPacket rp = new RemoveEntityPacket();
//                                rp.eid = floatText.getId();
//                                player.dataPacket(rp);
//                            }
//                            floatText.player.remove(player.getName());
//                        }
//                    }

                    if (player.getLevel().getFolderName().equalsIgnoreCase(floatText.getPosition().getLevel().getFolderName())) {
                        if(!floatText.player.contains(player.getName())){
                            floatText.player.add(player.getName());
                        }

                    }
                    if(update > 3){
                        floatText.disPlayers();
                        update = 0;
                    }

                }

            }
            if (WorldResetManager.CURRENT_RESET_ROOM == null && !WorldResetManager.RESET_QUEUE.isEmpty()) {
                Map.Entry<String, String> nextReset = WorldResetManager.RESET_QUEUE.entrySet().iterator().next();
                final String roomName = nextReset.getKey();
                final String levelName = nextReset.getValue();
                final org.sobadfish.dragonboatgame.room.config.GameRoomConfig roomConfig =
                        TotalManager.getRoomManager().getRoomConfig(roomName);
                if (roomConfig == null) {
                    WorldResetManager.RESET_QUEUE.remove(roomName);
                } else {
                    WorldResetManager.CURRENT_RESET_ROOM = roomName;
                    Server.getInstance().getScheduler().scheduleAsyncTask(TotalManager.getPlugin(), new AsyncTask() {
                        @Override
                        public void onRun() {
                            boolean success = false;
                            try {
                                success = WorldInfoConfig.toPathWorld(roomName, levelName, false);
                            } catch (Exception e) {
                                TotalManager.sendMessageToConsole("&c释放房间出现了一个小问题，导致无法正常释放，已将这个房间暂时锁定");
                            }
                            final boolean resetSuccess = success;
                            Server.getInstance().getScheduler().scheduleTask(TotalManager.getPlugin(), () -> {
                                try {
                                    if (resetSuccess) {
                                        TotalManager.sendMessageToConsole("&a" + roomName + " 地图已还原");
                                        Server.getInstance().getPluginManager().callEvent(new ReloadWorldEvent(TotalManager.getPlugin(), roomConfig));
                                    } else {
                                        TotalManager.sendMessageToConsole("&c" + roomName + " 地图还原失败，将在下次轮询时重试");
                                    }
                                } finally {
                                    WorldResetManager.CURRENT_RESET_ROOM = null;
                                }
                            });
                        }
                    });
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        loadTime = System.currentTimeMillis() - t1;
    }


}


