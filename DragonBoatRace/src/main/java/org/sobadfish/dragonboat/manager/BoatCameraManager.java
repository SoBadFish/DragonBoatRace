package org.sobadfish.dragonboat.manager;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.math.Vector2f;
import cn.nukkit.math.Vector3;
import cn.nukkit.math.Vector3f;
import cn.nukkit.network.protocol.CameraInstructionPacket;
import cn.nukkit.network.protocol.types.camera.CameraEase;
import cn.nukkit.network.protocol.types.camera.CameraSetInstruction;
import cn.nukkit.scheduler.TaskHandler;
import cn.nukkit.utils.CameraPresetManager;
import org.cloudburstmc.protocol.common.util.OptionalBoolean;
import org.sobadfish.dragonboat.DragonBoatMainClass;
import org.sobadfish.dragonboat.entity.DragonBoatEntity;

import java.util.HashMap;
import java.util.Map;

public final class BoatCameraManager {

    private static final Map<String, TaskHandler> CAMERA_TASKS = new HashMap<>();
    private static final Map<String, CameraState> CAMERA_STATES = new HashMap<>();
    private static final double CAMERA_DISTANCE = 6.0d;
    private static final double CAMERA_HEIGHT = 3.0d;
    private static final double CAMERA_POSITION_DAMPING = 0.26d;
    private static final double CAMERA_FORWARD_PREDICTION = 0.10d;
    private static final double CAMERA_ANCHOR_DAMPING = 0.24d;
    private static final double CAMERA_FOCUS_DAMPING = 0.30d;
    private static final float CAMERA_YAW_TRAIL_DAMPING = 0.16f;
    private static final float CAMERA_ROTATION_DAMPING = 0.28f;

    private BoatCameraManager() {
    }

    public static void startCamera(Player player) {
        stopCamera(player);
    }

    public static void stopCamera(Player player) {
        TaskHandler handler = CAMERA_TASKS.remove(player.getName());
        CAMERA_STATES.remove(player.getName());
        if (handler != null) {
            handler.cancel();
        }

        if (!player.isOnline()) {
            return;
        }

        CameraInstructionPacket clearPacket = new CameraInstructionPacket();
        clearPacket.setClear(OptionalBoolean.of(true));
        player.dataPacket(clearPacket);

        CameraInstructionPacket defaultPacket = new CameraInstructionPacket();
        CameraSetInstruction resetInstruction = new CameraSetInstruction();
        resetInstruction.setPreset(CameraPresetManager.getCameraPresetDefinitions().getDefinition(CameraPresetManager.FIRST_PERSON.getRuntimeId()));
        defaultPacket.setSetInstruction(resetInstruction);
        player.dataPacket(defaultPacket);

        player.dataPacket(new CameraInstructionPacket());
    }

    public static void stopAll() {
        for (Player player : Server.getInstance().getOnlinePlayers().values()) {
            stopCamera(player);
        }
        CAMERA_TASKS.clear();
        CAMERA_STATES.clear();
    }

    private static void sendCamera(Player player, boolean immediate) {
        if (!(player.riding instanceof DragonBoatEntity boat)) {
            stopCamera(player);
            return;
        }

        double boatYaw = boat.yaw;
        Vector3 boatBase = new Vector3(boat.x, boat.y, boat.z);
        Vector3 predictedOffset = new Vector3(boat.motionX, 0, boat.motionZ).multiply(CAMERA_FORWARD_PREDICTION);
        Vector3 cameraAnchor = boatBase.add(predictedOffset);
        Vector3 focusTarget = boatBase.add(0, 1.1d, 0);
        CameraState state = CAMERA_STATES.get(player.getName());
        if (immediate || state == null) {
            Vector3 forward = getHorizontalDirection(boatYaw);
            Vector3 desiredPos = cameraAnchor.add(0, CAMERA_HEIGHT, 0).subtract(forward.multiply(CAMERA_DISTANCE));
            Vector2f desiredRot = calculateLookRotation(desiredPos, focusTarget);
            state = new CameraState(desiredPos, cameraAnchor, focusTarget, (float) boatYaw, desiredRot.getY(), desiredRot.getX());
            CAMERA_STATES.put(player.getName(), state);
        } else {
            state.anchor = lerp(state.anchor, cameraAnchor, CAMERA_ANCHOR_DAMPING);
            state.focus = lerp(state.focus, focusTarget, CAMERA_FOCUS_DAMPING);
            state.trailingYaw = lerpAngle(state.trailingYaw, (float) boatYaw, CAMERA_YAW_TRAIL_DAMPING);
            Vector3 trailingForward = getHorizontalDirection(state.trailingYaw);
            Vector3 desiredPos = state.anchor.add(0, CAMERA_HEIGHT, 0).subtract(trailingForward.multiply(CAMERA_DISTANCE));
            state.position = lerp(state.position, desiredPos, CAMERA_POSITION_DAMPING);
            Vector2f desiredRot = calculateLookRotation(state.position, state.focus);
            state.yaw = lerpAngle(state.yaw, desiredRot.getY(), CAMERA_ROTATION_DAMPING);
            state.pitch = lerpAngle(state.pitch, desiredRot.getX(), CAMERA_ROTATION_DAMPING);
        }

        CameraInstructionPacket packet = new CameraInstructionPacket();
        CameraSetInstruction instruction = new CameraSetInstruction();
        instruction.setPreset(CameraPresetManager.getCameraPresetDefinitions().getDefinition(CameraPresetManager.FREE.getRuntimeId()));
        instruction.setPos(new Vector3f((float) state.position.x, (float) state.position.y, (float) state.position.z));
        instruction.setRot(new Vector2f(state.pitch, state.yaw));
        if (!immediate) {
            instruction.setEase(new CameraSetInstruction.EaseData(CameraEase.LINEAR, 0.04f));
        }
        packet.setSetInstruction(instruction);
        player.dataPacket(packet);
    }

    private static Vector3 getHorizontalDirection(double yaw) {
        double rad = Math.toRadians(yaw);
        return new Vector3(-Math.sin(rad), 0, Math.cos(rad)).normalize();
    }

    private static Vector3 lerp(Vector3 from, Vector3 to, double factor) {
        return new Vector3(
                from.x + (to.x - from.x) * factor,
                from.y + (to.y - from.y) * factor,
                from.z + (to.z - from.z) * factor
        );
    }

    private static Vector2f calculateLookRotation(Vector3 cameraPos, Vector3 targetPos) {
        Vector3 delta = targetPos.subtract(cameraPos);
        double horizontalDistance = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, Math.max(horizontalDistance, 0.0001d)));
        return new Vector2f(pitch, yaw);
    }

    private static float lerpAngle(float from, float to, float factor) {
        float diff = wrapDegrees(to - from);
        return from + diff * factor;
    }

    private static float wrapDegrees(float value) {
        float wrapped = value % 360f;
        if (wrapped >= 180f) {
            wrapped -= 360f;
        }
        if (wrapped < -180f) {
            wrapped += 360f;
        }
        return wrapped;
    }

    private static final class CameraState {
        private Vector3 position;
        private Vector3 anchor;
        private Vector3 focus;
        private float trailingYaw;
        private float yaw;
        private float pitch;

        private CameraState(Vector3 position, Vector3 anchor, Vector3 focus, float trailingYaw, float yaw, float pitch) {
            this.position = position;
            this.anchor = anchor;
            this.focus = focus;
            this.trailingYaw = trailingYaw;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }
}
