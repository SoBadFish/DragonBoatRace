package org.sobadfish.dragonboat.manager;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.level.particle.SmokeParticle;
import cn.nukkit.math.Vector3;
import cn.nukkit.scheduler.TaskHandler;
import org.sobadfish.dragonboat.DragonBoatMainClass;
import org.sobadfish.dragonboat.entity.DragonBoatEntity;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class PlayerControlEffectManager {

    private static final Map<String, Integer> FROZEN_PLAYERS = new HashMap<>();
    private static TaskHandler taskHandler;

    private PlayerControlEffectManager() {
    }

    public static void start(DragonBoatMainClass plugin) {
        stop();
        taskHandler = Server.getInstance().getScheduler().scheduleRepeatingTask(plugin, () -> {
            Iterator<Map.Entry<String, Integer>> iterator = FROZEN_PLAYERS.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, Integer> entry = iterator.next();
                Player player = Server.getInstance().getPlayer(entry.getKey());
                if (player == null || !player.isOnline()) {
                    iterator.remove();
                    continue;
                }

                player.setImmobile(true);
                player.setMotion(new Vector3(0, 0, 0));
                if (player.riding != null) {
                    player.riding.setMotion(new Vector3(0, 0, 0));
                    if (player.riding instanceof DragonBoatEntity boat) {
                        boat.activateStun(2);
                    }
                }
                player.getLevel().addParticle(new SmokeParticle(player.add(0, player.getEyeHeight() + 0.3, 0)));

                int ticks = entry.getValue() - 1;
                if (ticks <= 0) {
                    player.setImmobile(false);
                    iterator.remove();
                } else {
                    entry.setValue(ticks);
                }
            }
        }, 1);
    }

    public static void freezePlayer(Player player, int ticks) {
        if (player == null || !player.isOnline()) {
            return;
        }
        FROZEN_PLAYERS.put(player.getName(), Math.max(FROZEN_PLAYERS.getOrDefault(player.getName(), 0), ticks));
        player.setImmobile(true);
        player.setMotion(new Vector3(0, 0, 0));
        if (player.riding instanceof DragonBoatEntity boat) {
            boat.activateStun(ticks);
            boat.setMotion(new Vector3(0, 0, 0));
        }
    }

    public static void stop() {
        if (taskHandler != null) {
            taskHandler.cancel();
            taskHandler = null;
        }

        for (Player player : Server.getInstance().getOnlinePlayers().values()) {
            player.setImmobile(false);
        }
        FROZEN_PLAYERS.clear();
    }
}
