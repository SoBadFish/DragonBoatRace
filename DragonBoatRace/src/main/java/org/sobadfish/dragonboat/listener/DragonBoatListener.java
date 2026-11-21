package org.sobadfish.dragonboat.listener;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerInteractEvent;
import cn.nukkit.event.server.DataPacketReceiveEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.Sound;
import cn.nukkit.math.Vector2f;
import cn.nukkit.network.protocol.PlayerAuthInputPacket;
import cn.nukkit.network.protocol.PlayerInputPacket;
import cn.nukkit.network.protocol.types.AuthInputAction;
import org.sobadfish.dragonboat.DragonBoatMainClass;
import org.sobadfish.dragonboat.entity.DragonBoatEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class DragonBoatListener implements Listener {

    /**
     * 骑乘时客户端用 PlayerInputPacket 传载具输入，这是最可靠的来源；
     * 收到后这么多个 tick 内忽略 PlayerAuthInputPacket，避免两路输入互相覆盖。
     */
    private static final int VEHICLE_INPUT_GRACE_TICKS = 4;

    @SuppressWarnings("unused")
    private final DragonBoatMainClass plugin;
    private final Map<String, Integer> vehicleInputTicks = new HashMap<>();

    public DragonBoatListener(DragonBoatMainClass plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDataPacket(DataPacketReceiveEvent event) {
        Player player = event.getPlayer();
        if (!(player.riding instanceof DragonBoatEntity boat)) {
            this.vehicleInputTicks.remove(player.getName());
            return;
        }

        int tick = Server.getInstance().getTick();
        if (event.getPacket() instanceof PlayerInputPacket inputPacket) {
            this.vehicleInputTicks.put(player.getName(), tick);
            boat.updateMoveInput(player, inputPacket.motionX, inputPacket.motionY);
            return;
        }

        if (!(event.getPacket() instanceof PlayerAuthInputPacket authInput)) {
            return;
        }
        Integer vehicleTick = this.vehicleInputTicks.get(player.getName());
        if (vehicleTick != null && tick - vehicleTick <= VEHICLE_INPUT_GRACE_TICKS) {
            return;
        }
        InputState input = this.readAuthInput(authInput);
        if (!input.hasMovement) {
            // 认不出方向就不要覆盖：旧实现这里会把油门清成 0，龙舟就会一顿一顿
            return;
        }
        boat.updateMoveInput(player, input.strafe, input.forward);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != PlayerInteractEvent.Action.RIGHT_CLICK_AIR
                && event.getAction() != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Item item = event.getItem();
        if (item == null || item.isNull()) {
            return;
        }

        String namespaceId = item.getNamespaceId();
        Player player = event.getPlayer();

        switch (namespaceId) {
            case "minecraft:dragon_boost" -> {
                DragonBoatEntity boat = this.getRidingBoat(player);
                if (boat == null) {
                    player.sendMessage("§c只有骑乘龙舟时才能使用加速道具。");
                    event.setCancelled();
                    return;
                }
                boat.activateBoost();
                player.getLevel().addSound(boat, Sound.FIREWORK_LAUNCH, 0.9f, 1.35f);
                player.getLevel().addSound(boat, Sound.RANDOM_ORB, 0.7f, 1.3f);
                player.sendMessage("§b龙舟加速已启动，速度提升 100%，持续 8 秒。");
                this.consumeHeldItem(player);
                event.setCancelled();
            }
            case "minecraft:dragon_magnet" -> {
                if (this.plugin.activateMagnetFor(player)) {
                    this.consumeHeldItem(player);
                    event.setCancelled();
                    return;
                }
                event.setCancelled();
            }
            case "minecraft:dragon_missile" -> {
                if (this.plugin.launchMissileFor(player)) {
                    this.consumeHeldItem(player);
                    event.setCancelled();
                }
            }
            default -> {
            }
        }
    }

    private InputState readAuthInput(PlayerAuthInputPacket inputPacket) {
        InputState state = new InputState();

        Set<AuthInputAction> inputData = inputPacket.getInputData();
        if (this.hasDirectionalInput(inputData)) {
            state.hasMovement = true;
            if (inputData.contains(AuthInputAction.UP) || inputData.contains(AuthInputAction.UP_LEFT) || inputData.contains(AuthInputAction.UP_RIGHT)) {
                state.forward += 1f;
            }
            if (inputData.contains(AuthInputAction.DOWN) || inputData.contains(AuthInputAction.DOWN_LEFT) || inputData.contains(AuthInputAction.DOWN_RIGHT)) {
                state.forward -= 1f;
            }
            if (inputData.contains(AuthInputAction.LEFT) || inputData.contains(AuthInputAction.UP_LEFT) || inputData.contains(AuthInputAction.DOWN_LEFT)) {
                state.strafe += 1f;
            }
            if (inputData.contains(AuthInputAction.RIGHT) || inputData.contains(AuthInputAction.UP_RIGHT) || inputData.contains(AuthInputAction.DOWN_RIGHT)) {
                state.strafe -= 1f;
            }
            return state;
        }

        Vector2f rawMoveVector = inputPacket.getRawMoveVector();
        if (this.hasMeaningfulVector(rawMoveVector)) {
            state.hasMovement = true;
            state.strafe = rawMoveVector.getX();
            state.forward = rawMoveVector.getY();
            return state;
        }

        Vector2f analogMoveVector = inputPacket.getAnalogMoveVector();
        if (this.hasMeaningfulVector(analogMoveVector)) {
            state.hasMovement = true;
            state.strafe = analogMoveVector.getX();
            state.forward = analogMoveVector.getY();
        }
        return state;
    }

    private boolean hasDirectionalInput(Set<AuthInputAction> inputData) {
        if (inputData == null || inputData.isEmpty()) {
            return false;
        }
        return inputData.contains(AuthInputAction.UP)
                || inputData.contains(AuthInputAction.DOWN)
                || inputData.contains(AuthInputAction.LEFT)
                || inputData.contains(AuthInputAction.RIGHT)
                || inputData.contains(AuthInputAction.UP_LEFT)
                || inputData.contains(AuthInputAction.UP_RIGHT)
                || inputData.contains(AuthInputAction.DOWN_LEFT)
                || inputData.contains(AuthInputAction.DOWN_RIGHT);
    }

    private boolean hasMeaningfulVector(Vector2f vector) {
        return vector != null && (Math.abs(vector.getX()) > 0.001f || Math.abs(vector.getY()) > 0.001f);
    }

    private DragonBoatEntity getRidingBoat(Player player) {
        if (player.riding instanceof DragonBoatEntity boat) {
            return boat;
        }
        return null;
    }

    private void consumeHeldItem(Player player) {
        if (player.isCreative()) {
            return;
        }
        player.getInventory().decreaseCount(player.getInventory().getHeldItemIndex());
    }

    private static class InputState {
        private float strafe;
        private float forward;
        private boolean hasMovement;
    }
}
