package org.sobadfish.dragonboat.entity;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.custom.CustomEntity;
import cn.nukkit.entity.custom.EntityDefinition;
import cn.nukkit.entity.item.EntityVehicle;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.level.Sound;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.particle.BubbleParticle;
import cn.nukkit.level.particle.DustParticle;
import cn.nukkit.level.particle.SplashParticle;
import cn.nukkit.level.particle.WaterParticle;
import cn.nukkit.math.Vector3;
import cn.nukkit.math.Vector3f;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.SetEntityLinkPacket;

/**
 * 龙舟载具实体。
 */
public class DragonBoatEntity extends EntityVehicle implements CustomEntity {

    public static final EntityDefinition DEF_ENTITY = EntityDefinition
            .builder()
            .identifier("dragon:dragon_boat")
            .spawnEgg(true)
            .implementation(DragonBoatEntity.class)
            .build();

    private static final double SPEED_BLOCKS_PER_SECOND = 8.0d;
    private static final double MAX_SPEED = SPEED_BLOCKS_PER_SECOND / 20.0d;
    private static final float YAW_FOLLOW_FACTOR = 0.26f;
    private static final float TURN_STRENGTH = 3.2f;
    private static final double WATER_DRAG = 0.965d;
    private static final double LAND_DRAG = 0.86d;
    private static final double WATER_COAST_DRAG = 0.992d;
    private static final double LAND_COAST_DRAG = 0.93d;
    private static final double DRIFT_COAST_DRAG = 0.999d;
    private static final double WATER_ACCELERATION = 0.032d;
    private static final double LAND_ACCELERATION = 0.014d;
    private static final double WATER_SIDE_DAMPING = 0.70d;
    private static final double LAND_SIDE_DAMPING = 0.42d;
    private static final double WATER_STRAIGHT_SIDE_DAMPING = 0.12d;
    private static final double LAND_STRAIGHT_SIDE_DAMPING = 0.08d;
    private static final double REVERSE_MULTIPLIER = 0.45d;
    private static final double BOOST_MULTIPLIER = 2.0d;
    private static final double DRIFT_TRIGGER_SPEED_RATIO = 0.92d;
    private static final double POWER_UNDERSTEER_FACTOR = 0.22d;
    private static final double DRIFT_EXIT_ASSIST = 0.45d;
    private static final double ACCELERATION_RAMP_FACTOR = 1.1d;
    private static final float STRAIGHTEN_STEER_THRESHOLD = 0.16f;
    private static final float TARGET_YAW_RECENTER_FACTOR = 0.38f;
    private static final double WATER_FORWARD_TRACKING = 0.32d;
    private static final double LAND_FORWARD_TRACKING = 0.48d;
    private static final double WATER_COLLISION_FORWARD_DAMPING = 0.28d;
    private static final double LAND_COLLISION_FORWARD_DAMPING = 0.12d;

    private static final double DRIFT_STREER_THRESHOLD = 0.12d;
    private static final double DRIFT_SUCCESS_MIN_SPEED_RATIO = 0.94d;
    private static final int DRIFT_SUCCESS_MIN_TICKS = 5;
    private static final float INPUT_DEADZONE = 0.05f;
    private static final int INPUT_TIMEOUT_TICKS = 10;
    private static final int BOOST_DURATION_TICKS = 160;
    private static final int DRIFT_REBOOST_WINDOW_TICKS = 20;
    private static final int DRIFT_REBOOST_DURATION_TICKS = 20;
    private static final int MAGNET_DURATION_TICKS = 100;
    private static final double MAGNET_SPEED_MULTIPLIER = 4.0d;
    private static final double MAGNET_ACCELERATION = 0.18d;
    private static final double MAGNET_TARGET_SLOWDOWN_FACTOR = 0.92d;
    private static final double MAGNET_TARGET_MAX_SPEED_FACTOR = 0.65d;
    private static final double DRIFT_REBOOST_BASE_MULTIPLIER = 1.5d;
    private static final double DRIFT_REBOOST_MAX_MULTIPLIER = 2.0d;
    private static final double DRIFT_EXIT_LATERAL_ASSIST = 0.14d;
    private static final double DRIFT_EXIT_OUTER_RETENTION = 0.94d;
    private static final double DRIFT_EXIT_INNER_RETENTION = 0.74d;

    private Player master;
    private String ownerName;
    private float throttleInput;
    private float steeringInput;
    private float desiredThrottleInput;
    private float desiredSteeringInput;
    private float targetYaw;
    private int boostTicks;
    private int magnetTicks;
    private int stunTicks;
    private Player magnetTarget;
    private int waterSoundCooldown;
    private int inputHoldTicks;
    private int driftReboostWindowTicks;
    private int driftReboostTicks;
    private boolean drifting;
    private int driftTicks;
    private int driftDirectionSign;
    private int driftReboostDirection;
    private double driftPeakSpeedRatio;
    private double driftReboostPower;
    private boolean motionControlEnabled = true;
    private int effectTick;

    public DragonBoatEntity(FullChunk chunk, CompoundTag nbt) {
        this(chunk, nbt, null);
    }

    public DragonBoatEntity(FullChunk chunk, CompoundTag nbt, Player master) {
        super(chunk, nbt);
        this.master = master;
        this.ownerName = master != null ? master.getName() : this.namedTag.getString("ownerName");
        this.targetYaw = this.namedTag.contains("Yaw") ? this.namedTag.getFloat("Yaw") : (float) this.yaw;
    }

    @Override
    protected void initEntity() {
        super.initEntity();
        this.setMaxHealth(40);
        this.setHealth(40);
        this.setScale(1.6f);
        this.setDataFlag(DATA_FLAGS_EXTENDED, DATA_FLAG_COLLIDABLE, true);
        this.setDataFlag(DATA_FLAGS, DATA_FLAG_WASD_CONTROLLED, true);
        this.dataProperties.putBoolean(DATA_IS_BUOYANT, true);
        this.dataProperties.putString(DATA_BUOYANCY_DATA, "{\"apply_gravity\":false,\"base_buoyancy\":1.0,\"big_wave_probability\":0.0,\"big_wave_speed\":0.0,\"drag_down_on_buoyancy_removed\":0.0,\"liquid_blocks\":[\"minecraft:water\",\"minecraft:flowing_water\"],\"simulate_waves\":false}");
    }

    @Override
    public boolean onUpdate(int currentTick) {
        int tickDiff = currentTick - this.lastUpdate;
        if (tickDiff <= 0 && !this.justCreated) {
            return true;
        }
        this.lastUpdate = currentTick;

        boolean hasUpdate = this.entityBaseTick(tickDiff);

        if (this.getHealth() < this.getMaxHealth()) {
            this.setHealth(this.getMaxHealth());
        }
        if (this.isOnFire()) {
            this.setOnFire(0);
            this.fireTicks = 0;
        }
        this.setLastDamageCause(null);

        SurfaceInfo surfaceInfo = this.getSurfaceInfo();
        boolean onSupportedSurface = surfaceInfo.type != SurfaceType.NONE;
        boolean onWater = surfaceInfo.type == SurfaceType.WATER;
        if (onSupportedSurface) {
            this.adjustSurfaceHeight(surfaceInfo);
        }

        if (this.inputHoldTicks > 0) {
            this.inputHoldTicks--;
        } else {
            this.desiredThrottleInput = 0;
            this.desiredSteeringInput = 0;
        }

        this.throttleInput = this.approachInput(this.throttleInput, this.desiredThrottleInput, 0.22f);
        this.steeringInput = this.approachInput(this.steeringInput, this.desiredSteeringInput, 0.45f);
        if (this.motionControlEnabled) {
            this.applyMovement(onSupportedSurface);
            this.applyActiveEffects();
        } else {
            this.throttleInput = 0f;
            this.steeringInput = 0f;
            this.motionX = 0d;
            this.motionZ = 0d;
        }
        // 尾迹粒子/水声每两 tick 发一次：肉眼几乎没区别，网络包量直接减半，高速行驶时更跟手
        if ((this.effectTick++ & 1) == 0) {
            this.spawnSurfaceEffects(surfaceInfo.type);
        }
        double attemptedMotionX = this.motionX;
        double attemptedMotionZ = this.motionZ;
        this.move(this.motionX, this.motionY, this.motionZ);
        this.applyCollisionRecovery(onSupportedSurface, attemptedMotionX, attemptedMotionZ);
        this.updatePassengers();
        this.updateMovement();

        if (this.boostTicks > 0) {
            this.boostTicks--;
        }
        if (this.driftReboostTicks > 0) {
            this.driftReboostTicks--;
        }
        if (this.driftReboostWindowTicks > 0) {
            this.driftReboostWindowTicks--;
        }
        if (this.driftReboostWindowTicks == 0 && this.driftReboostTicks == 0) {
            this.driftReboostDirection = 0;
            this.driftReboostPower = 0;
        }
        if (this.magnetTicks > 0) {
            this.magnetTicks--;
            if (this.magnetTicks == 0) {
                this.magnetTarget = null;
            }
        }
        if (this.stunTicks > 0) {
            this.stunTicks--;
        }
        if (this.waterSoundCooldown > 0) {
            this.waterSoundCooldown--;
        }

        return hasUpdate
                || Math.abs(this.motionX) > 0.0001
                || Math.abs(this.motionY) > 0.0001
                || Math.abs(this.motionZ) > 0.0001;
    }

    public void updateMoveInput(Player player, float strafe, float forward) {
        this.master = player;
        this.ownerName = player.getName();
        float lastForwardInput = this.desiredThrottleInput;
        this.desiredSteeringInput = this.applyDeadzone(this.clamp(strafe, -1f, 1f));
        this.desiredThrottleInput = this.applyDeadzone(this.clamp(forward, -1f, 1f));
        if (lastForwardInput <= 0.05f
                && this.desiredThrottleInput > 0.05f
                && this.driftReboostWindowTicks > 0
                && this.driftReboostDirection != 0) {
            this.driftReboostTicks = DRIFT_REBOOST_DURATION_TICKS;
            this.driftReboostWindowTicks = 0;
        }
        this.inputHoldTicks = INPUT_TIMEOUT_TICKS;
    }

    @Override
    public boolean onInteract(Player player, cn.nukkit.item.Item item, Vector3 clickedPos) {
        if (this.passengers.contains(player)) {
            this.dismountEntity(player);
            return true;
        }

        if (!this.passengers.isEmpty()) {
            player.sendMessage("§c这艘龙舟已经有人骑乘。");
            return true;
        }

        this.mountEntity(player);
        return true;
    }

    @Override
    public boolean mountEntity(Entity entity) {
        if (!this.passengers.isEmpty()) {
            return false;
        }

        boolean mounted = super.mountEntity(entity, SetEntityLinkPacket.TYPE_RIDE);
        if (!mounted) {
            return false;
        }

        this.updatePassengers();
        if (entity instanceof Player player) {
            this.yaw = this.normalizeYaw((float) player.yaw);
            this.targetYaw = (float) this.yaw;
            this.throttleInput = 0;
            this.steeringInput = 0;
            this.desiredThrottleInput = 0;
            this.desiredSteeringInput = 0;
            this.level.addSound(this, Sound.BLOCK_BARREL_OPEN, 0.8f, 1.2f);
            this.level.addSound(this, Sound.RANDOM_SPLASH, 0.7f, 1.15f);
        }
        return true;
    }

    public void alignHeading(float yaw) {
        float normalizedYaw = this.normalizeYaw(yaw);
        this.yaw = normalizedYaw;
        this.targetYaw = normalizedYaw;
        this.setRotation(normalizedYaw, this.pitch);
    }

    @Override
    public boolean dismountEntity(Entity entity, boolean sendLinks) {
        boolean result = super.dismountEntity(entity, sendLinks);
        if (result && entity instanceof Player player) {
            this.level.addSound(this, Sound.BLOCK_BARREL_CLOSE, 0.75f, 1.0f);
        }
        return result;
    }

    @Override
    public void updatePassengers() {
        if (this.passengers.isEmpty()) {
            return;
        }
        Entity passenger = this.passengers.get(0);
        passenger.setSeatPosition(this.getMountedOffset(passenger));
        super.updatePassengerPosition(passenger);
    }

    @Override
    public Vector3f getMountedOffset(Entity entity) {
        return new Vector3f(0, 0.95f, 0);
    }

    @Override
    public boolean isControlling(Entity entity) {
        return entity instanceof Player && !this.passengers.isEmpty() && this.passengers.get(0) == entity;
    }

    public Player getFirstRider() {
        if (this.passengers.isEmpty() || !(this.passengers.get(0) instanceof Player player)) {
            return null;
        }
        return player;
    }

    public void activateBoost() {
        this.boostTicks = BOOST_DURATION_TICKS;
    }

    public void activateMagnet(Player target) {
        this.magnetTarget = target;
        this.magnetTicks = MAGNET_DURATION_TICKS;
    }

    public void activateStun(int ticks) {
        this.stunTicks = Math.max(this.stunTicks, ticks);
    }

    public void setMotionControlEnabled(boolean enabled) {
        this.motionControlEnabled = enabled;
        if (!enabled) {
            this.throttleInput = 0f;
            this.steeringInput = 0f;
            this.desiredThrottleInput = 0f;
            this.desiredSteeringInput = 0f;
            this.motionX = 0d;
            this.motionZ = 0d;
        }
    }

    public boolean isMotionControlEnabled() {
        return this.motionControlEnabled;
    }

    @Override
    public void close() {
        super.close();
    }

    @Override
    public boolean attack(EntityDamageEvent source) {
        if (source == null) {
            return false;
        }
        String causeName = source.getCause() == null ? "" : source.getCause().name();
        if (causeName.contains("CACTUS")
                || causeName.contains("LAVA")
                || causeName.contains("FIRE")
                || causeName.contains("CONTACT")
                || causeName.contains("HOT_FLOOR")
                || causeName.contains("EXPLOSION")) {
            this.setHealth(this.getMaxHealth());
            this.setLastDamageCause(null);
            return false;
        }
        return super.attack(source);
    }

    @Override
    public void saveNBT() {
        super.saveNBT();
        if (this.ownerName != null && !this.ownerName.isEmpty()) {
            this.namedTag.putString("ownerName", this.ownerName);
        }
    }

    @Override
    public float getWidth() {
        return 0.78f;
    }

    @Override
    public float getHeight() {
        return 1.3f;
    }



    @Override
    public EntityDefinition getEntityDefinition() {
        return DEF_ENTITY;
    }

    @Override
    public int getNetworkId() {
        return DEF_ENTITY.getRuntimeId();
    }

    @Override
    public float getGravity() {
        return 0.04f;
    }

    private void applyMovement(boolean onWater) {
        if (this.stunTicks > 0) {
            this.motionX *= 0.35d;
            this.motionZ *= 0.35d;
            this.motionY = onWater ? 0 : this.motionY;
            this.throttleInput = 0;
            this.steeringInput = 0;
            return;
        }

        Vector3 previousDirection = this.getHorizontalDirection();
        Vector3 previousSide = new Vector3(-previousDirection.z, 0, previousDirection.x);
        double boostMultiplier = 1.0d;
        if (this.boostTicks > 0) {
            boostMultiplier = BOOST_MULTIPLIER;
        } else if (this.driftReboostTicks > 0) {
            boostMultiplier = this.lerp(DRIFT_REBOOST_BASE_MULTIPLIER, DRIFT_REBOOST_MAX_MULTIPLIER, this.driftReboostPower);
        }
        double activeMaxSpeed = MAX_SPEED * boostMultiplier;
        double targetSpeed = this.throttleInput >= 0 ? activeMaxSpeed * this.throttleInput : activeMaxSpeed * this.throttleInput * REVERSE_MULTIPLIER;
        double currentForwardSpeed = this.motionX * previousDirection.x + this.motionZ * previousDirection.z;
        double currentSideSpeed = this.motionX * previousSide.x + this.motionZ * previousSide.z;
        boolean hasForwardThrottle = this.throttleInput > 0.05f;
        boolean hasThrottle = Math.abs(this.throttleInput) >= 0.05f;
        double speedRatio = Math.min(1.0d, Math.abs(currentForwardSpeed) / Math.max(activeMaxSpeed, 0.001d));

        if (hasForwardThrottle && currentForwardSpeed < 0) {
            currentForwardSpeed *= 0.2d;
            if (currentForwardSpeed > -0.003d) {
                currentForwardSpeed = 0;
            }
        }

        if (Math.abs(this.steeringInput) > 0.01f) {
            double turnResponse = 0.18d + speedRatio * 0.82d;
            turnResponse *= 1.0d - Math.max(0f, this.throttleInput) * speedRatio * POWER_UNDERSTEER_FACTOR;
            if (!hasThrottle && Math.abs(currentForwardSpeed) < activeMaxSpeed * 0.12d) {
                turnResponse *= 0.45d;
            }
            float steerDelta = (float) (this.steeringInput * TURN_STRENGTH * turnResponse);
            this.targetYaw = this.normalizeYaw(this.targetYaw - steerDelta);
        } else {
            float yawDelta = this.wrapDegrees((float) this.yaw - this.targetYaw);
            this.targetYaw = this.normalizeYaw(this.targetYaw + yawDelta * TARGET_YAW_RECENTER_FACTOR);
        }

        this.targetYaw = this.normalizeYaw(this.targetYaw);
        this.yaw = this.normalizeYaw((float) (this.yaw + this.wrapDegrees((float) (this.wrapYawToReference(this.targetYaw, (float) this.yaw) - this.yaw)) * YAW_FOLLOW_FACTOR));

        Vector3 direction = this.getHorizontalDirection();
        Vector3 side = new Vector3(-direction.z, 0, direction.x);

        if (hasThrottle) {
            double speedProgress = Math.min(1.0d, Math.abs(currentForwardSpeed) / Math.max(activeMaxSpeed, 0.001d));
            double accelerationRamp = 0.55d + speedProgress * ACCELERATION_RAMP_FACTOR;
            double acceleration = (onWater ? WATER_ACCELERATION : LAND_ACCELERATION)
                    * Math.abs(this.throttleInput)
                    * accelerationRamp;
            if (hasForwardThrottle && currentForwardSpeed < 0) {
                currentForwardSpeed = this.moveToward(currentForwardSpeed, 0, acceleration * 2.4d);
            }
            currentForwardSpeed = this.moveToward(currentForwardSpeed, targetSpeed, acceleration);
        } else {
            double coastDrag = onWater ? WATER_COAST_DRAG : LAND_COAST_DRAG;
            if (onWater && Math.abs(this.steeringInput) > DRIFT_STREER_THRESHOLD) {
                coastDrag = Math.max(coastDrag, DRIFT_COAST_DRAG);
            }
            currentForwardSpeed *= coastDrag;
        }

        double steerAmount = Math.min(1.0d, Math.abs(this.steeringInput));
        double straightSideDamping = onWater ? WATER_STRAIGHT_SIDE_DAMPING : LAND_STRAIGHT_SIDE_DAMPING;
        double turningSideDamping = onWater ? WATER_SIDE_DAMPING : LAND_SIDE_DAMPING;
        double sideDamping = this.lerp(straightSideDamping, turningSideDamping, steerAmount);
        currentSideSpeed *= sideDamping;
        if (hasForwardThrottle) {
            double trackingStrength = onWater ? WATER_FORWARD_TRACKING : LAND_FORWARD_TRACKING;
            double straightFactor = 1.0d - Math.min(1.0d, steerAmount / STRAIGHTEN_STEER_THRESHOLD);
            if (straightFactor > 0) {
                currentSideSpeed *= Math.max(0.0d, 1.0d - trackingStrength * straightFactor * this.throttleInput);
            }
        }

        double horizontalSpeed = Math.sqrt(currentForwardSpeed * currentForwardSpeed + currentSideSpeed * currentSideSpeed);

        boolean driftActive = onWater
                && Math.abs(this.steeringInput) > DRIFT_STREER_THRESHOLD
                && horizontalSpeed > activeMaxSpeed * DRIFT_TRIGGER_SPEED_RATIO;
        if (driftActive) {
            this.driftTicks++;
            this.driftDirectionSign = this.steeringInput > 0 ? 1 : -1;
            this.driftPeakSpeedRatio = Math.max(this.driftPeakSpeedRatio, driftScaleFor(horizontalSpeed, activeMaxSpeed));
            double driftScale = Math.min(1.0d, horizontalSpeed / Math.max(activeMaxSpeed, 0.001d));
            double powerDrift = 0.82d + Math.max(0f, this.throttleInput) * 0.30d;
            double drift = this.steeringInput * horizontalSpeed * (0.03d + 0.05d * driftScale) * powerDrift;
            currentSideSpeed += drift;
            currentForwardSpeed *= 0.9995d - Math.min(0.0015d, steerAmount * 0.0015d);
        }
        if (this.drifting && !driftActive) {
            this.finishDrift();
        }
        if (driftActive) {
            this.driftReboostWindowTicks = 0;
        }
        this.drifting = driftActive;

        if (!hasThrottle && currentForwardSpeed < 0) {
            currentForwardSpeed *= onWater ? 0.82d : 0.55d;
        }

        if (hasForwardThrottle && currentForwardSpeed < 0) {
            currentForwardSpeed = 0;
        }

        if (Math.abs(this.steeringInput) < 0.05f) {
            currentSideSpeed *= DRIFT_EXIT_ASSIST;
        } else if (Math.abs(this.steeringInput) < 0.2f) {
            currentSideSpeed *= 0.75d;
        }
        if (this.driftReboostTicks > 0 && this.driftReboostDirection != 0) {
            double exitBlend = (double) this.driftReboostTicks / (double) DRIFT_REBOOST_DURATION_TICKS;
            currentForwardSpeed = Math.max(currentForwardSpeed, activeMaxSpeed * (0.72d + this.driftReboostPower * 0.12d) * exitBlend);
            currentSideSpeed += this.driftReboostDirection * activeMaxSpeed * DRIFT_EXIT_LATERAL_ASSIST * (0.45d + this.driftReboostPower * 0.55d) * exitBlend;
            if (currentSideSpeed * this.driftReboostDirection > 0) {
                currentSideSpeed *= this.lerp(DRIFT_EXIT_OUTER_RETENTION, 0.985d, this.driftReboostPower);
            } else {
                currentSideSpeed *= this.lerp(DRIFT_EXIT_INNER_RETENTION, 0.88d, this.driftReboostPower);
            }
        }

        if (!onWater) {
            this.motionY -= this.getGravity() * 0.35f;
        }

        this.motionX = direction.x * currentForwardSpeed + side.x * currentSideSpeed;
        this.motionZ = direction.z * currentForwardSpeed + side.z * currentSideSpeed;

        if (!hasThrottle && Math.abs(currentForwardSpeed) < 0.0025d && Math.abs(currentSideSpeed) < 0.0015d) {
            this.motionX = 0;
            this.motionZ = 0;
        }

        this.limitHorizontalSpeed(activeMaxSpeed);
    }

    private void applyCollisionRecovery(boolean onWater, double attemptedMotionX, double attemptedMotionZ) {
        if (!this.isCollidedHorizontally) {
            return;
        }

        Vector3 direction = this.getHorizontalDirection();
        double attemptedForwardSpeed = attemptedMotionX * direction.x + attemptedMotionZ * direction.z;
        double forwardDamping = onWater ? WATER_COLLISION_FORWARD_DAMPING : LAND_COLLISION_FORWARD_DAMPING;

        if (attemptedForwardSpeed > 0) {
            attemptedForwardSpeed *= forwardDamping;
        } else {
            attemptedForwardSpeed *= 0.1d;
        }

        this.motionX = direction.x * attemptedForwardSpeed;
        this.motionZ = direction.z * attemptedForwardSpeed;
        this.targetYaw = (float) this.yaw;
        this.steeringInput *= 0.35f;
    }

    private void finishDrift() {
        boolean successfulDrift = this.driftTicks >= DRIFT_SUCCESS_MIN_TICKS
                && this.driftPeakSpeedRatio >= DRIFT_SUCCESS_MIN_SPEED_RATIO
                && this.driftDirectionSign != 0;
        if (successfulDrift) {
            this.driftReboostWindowTicks = DRIFT_REBOOST_WINDOW_TICKS;
            this.driftReboostDirection = this.driftDirectionSign;
            this.driftReboostPower = this.clampDouble(
                    (this.driftPeakSpeedRatio - DRIFT_SUCCESS_MIN_SPEED_RATIO)
                            / Math.max(0.001d, 1.0d - DRIFT_SUCCESS_MIN_SPEED_RATIO),
                    0d, 1d
            );
        } else {
            this.driftReboostWindowTicks = 0;
            this.driftReboostDirection = 0;
            this.driftReboostPower = 0;
        }
        this.driftTicks = 0;
        this.driftPeakSpeedRatio = 0d;
        this.driftDirectionSign = 0;
    }

    private double driftScaleFor(double horizontalSpeed, double activeMaxSpeed) {
        return Math.min(1.0d, horizontalSpeed / Math.max(activeMaxSpeed, 0.001d));
    }

    private void adjustSurfaceHeight(SurfaceInfo surfaceInfo) {
        double targetY = surfaceInfo.height + (surfaceInfo.type == SurfaceType.WATER ? 0.12d : 0.02d);
        double diff = targetY - this.y;
        if (surfaceInfo.type == SurfaceType.WATER) {
            double buoyancy = this.clamp((float) (diff * 0.18d), -0.06f, 0.06f);
            this.motionY = this.motionY * 0.65d + buoyancy;
            if (Math.abs(diff) < 0.03d) {
                this.motionY *= 0.45d;
            }
            if (Math.abs(diff) < 0.008d && Math.abs(this.motionY) < 0.01d) {
                this.motionY = 0;
            }
            return;
        }
        this.motionY = this.motionY * 0.35d + this.clamp((float) (diff * 0.35d), -0.08f, 0.08f);
        if (Math.abs(diff) < 0.01d && Math.abs(this.motionY) < 0.02d) {
            this.motionY = 0;
        }
    }

    private SurfaceInfo getSurfaceInfo() {
        int baseY = this.getFloorY();
        Block centerBlock = this.level.getBlock(new Vector3(this.x, baseY, this.z));
        if (centerBlock.isWater()) {
            return new SurfaceInfo(SurfaceType.WATER, centerBlock.y + 1);
        }
        if (this.isIceBlock(centerBlock)) {
            return new SurfaceInfo(SurfaceType.ICE, centerBlock.y + 1);
        }

        Block blockBelow = this.level.getBlock(new Vector3(this.x, baseY - 1, this.z));
        if (blockBelow.isWater()) {
            return new SurfaceInfo(SurfaceType.WATER, blockBelow.y + 1);
        }
        if (this.isIceBlock(blockBelow)) {
            return new SurfaceInfo(SurfaceType.ICE, blockBelow.y + 1);
        }
        return new SurfaceInfo(SurfaceType.NONE, Double.NEGATIVE_INFINITY);
    }

    private boolean isIceBlock(Block block) {
        if (block == null) {
            return false;
        }
        String simpleName = block.getClass().getSimpleName().toLowerCase();
        return simpleName.contains("ice");
    }

    private void limitHorizontalSpeed(double maxSpeed) {
        double speed = this.getHorizontalSpeed();
        if (speed <= maxSpeed || speed == 0) {
            return;
        }

        double scale = maxSpeed / speed;
        this.motionX *= scale;
        this.motionZ *= scale;
    }

    private double getHorizontalSpeed() {
        return Math.sqrt(this.motionX * this.motionX + this.motionZ * this.motionZ);
    }

    private void applyActiveEffects() {
        if (this.magnetTicks <= 0) {
            return;
        }

        Player rider = this.getFirstRider();
        if (rider == null) {
            return;
        }

        Player target = this.magnetTarget;
        if (target == null || !this.isValidMagnetTarget(rider, target)) {
            this.magnetTarget = null;
            return;
        }

        Vector3 toTarget = target.subtract(this.x, this.y, this.z);
        Vector3 horizontal = new Vector3(toTarget.x, 0, toTarget.z);
        if (horizontal.lengthSquared() < 0.001d) {
            return;
        }

        Vector3 direction = horizontal.normalize();
        this.targetYaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
        double magnetMaxSpeed = MAX_SPEED * MAGNET_SPEED_MULTIPLIER;
        double desiredSpeed = Math.min(magnetMaxSpeed, Math.max(this.getHorizontalSpeed() + MAGNET_ACCELERATION, MAX_SPEED * 1.6d));
        this.motionX = this.motionX * 0.35d + direction.x * desiredSpeed * 0.65d;
        this.motionZ = this.motionZ * 0.35d + direction.z * desiredSpeed * 0.65d;
        this.limitHorizontalSpeed(magnetMaxSpeed);

        DragonBoatEntity targetBoat = this.getMountedBoat(target);
        if (targetBoat != null && targetBoat != this) {
            targetBoat.applyMagnetSlowdown();
        }
    }

    private boolean isValidMagnetTarget(Player rider, Player target) {
        return target != null
                && target != rider
                && target.isOnline()
                && target.isAlive()
                && target.level == rider.level;
    }

    private DragonBoatEntity getMountedBoat(Player player) {
        if (player != null && player.riding instanceof DragonBoatEntity boat && !boat.closed) {
            return boat;
        }
        return null;
    }

    private void applyMagnetSlowdown() {
        this.motionX *= MAGNET_TARGET_SLOWDOWN_FACTOR;
        this.motionZ *= MAGNET_TARGET_SLOWDOWN_FACTOR;
        this.limitHorizontalSpeed(MAX_SPEED * MAGNET_TARGET_MAX_SPEED_FACTOR);
    }

    private void spawnSurfaceEffects(SurfaceType surfaceType) {
        double speed = this.getHorizontalSpeed();
        if (surfaceType == SurfaceType.NONE || speed < 0.025d) {
            return;
        }

        Vector3 forward = this.getHorizontalDirection();
        Vector3 side = new Vector3(-forward.z, 0, forward.x);
        Vector3 stern = this.add(0, 0.08d, 0).subtract(forward.multiply(1.35d));
        double intensity = Math.min(1.0d, speed / Math.max(MAX_SPEED, 0.001d));

        if (surfaceType == SurfaceType.ICE) {
            this.spawnIceEffects(stern, side, forward, intensity);
            return;
        }

        int splashCount = 3 + (int) Math.round(intensity * 3);

        for (int i = 0; i < splashCount; i++) {
            double sternSpread = 1.1d + intensity * 0.4d;
            double sideSpread = 0.95d + intensity * 0.3d;
            Vector3 wakePos = stern
                    .add(side.multiply((Math.random() - 0.5d) * sideSpread))
                    .add(forward.multiply((Math.random() - 0.5d) * sternSpread))
                    .add(0, Math.random() * 0.18d, 0);
            this.level.addParticle(new WaterParticle(wakePos));
            this.level.addParticle(new SplashParticle(wakePos));
        }

        for (int dir : new int[]{-1, 1}) {
            Vector3 hullSide = this.add(0, 0.12d, 0).add(side.multiply(1.25d * dir));
            for (int i = 0; i < 1 + (int) Math.round(intensity * 2); i++) {
                Vector3 splashPos = hullSide
                        .add(forward.multiply((Math.random() - 0.5d) * 1.3d))
                        .add(side.multiply((Math.random() - 0.5d) * 0.25d))
                        .add(0, Math.random() * 0.22d, 0);
                this.level.addParticle(new SplashParticle(splashPos));
            }
        }

        if (intensity > 0.45d) {
            for (int i = 0; i < 2; i++) {
                Vector3 bubblePos = stern
                        .add(side.multiply((Math.random() - 0.5d) * 0.8d))
                        .add(0, -0.1d, 0);
                this.level.addParticle(new BubbleParticle(bubblePos));
            }
        }

        if (this.waterSoundCooldown <= 0) {
            float volume = (float) Math.min(1.1d, 0.45d + intensity * 0.55d);
            float pitch = (float) Math.max(0.8d, 1.25d - intensity * 0.2d);
            this.level.addSound(this, Sound.RANDOM_SPLASH, volume, pitch);
            if (intensity > 0.55d) {
                this.level.addSound(stern, Sound.BUCKET_EMPTY_WATER, 0.35f + (float) intensity * 0.25f, 1.55f);
            }
            this.waterSoundCooldown = Math.max(4, 9 - (int) Math.round(intensity * 4));
        }
    }

    private void spawnIceEffects(Vector3 stern, Vector3 side, Vector3 forward, double intensity) {
        int smokeCount = 3 + (int) Math.round(intensity * 3);
        for (int i = 0; i < smokeCount; i++) {
            Vector3 smokePos = stern
                    .add(side.multiply((Math.random() - 0.5d) * (0.85d + intensity * 0.25d)))
                    .add(forward.multiply((Math.random() - 0.5d) * 0.95d))
                    .add(0, 0.08d + Math.random() * 0.18d, 0);
            this.level.addParticle(new DustParticle(smokePos, 64, 170, 255, 180));
        }
    }

    private Vector3 getHorizontalDirection() {
        double rad = Math.toRadians(this.yaw);
        double x = -Math.sin(rad);
        double z = Math.cos(rad);
        return new Vector3(x, 0, z).normalize();
    }

    private float wrapDegrees(float value) {
        float wrapped = value % 360f;
        if (wrapped >= 180f) {
            wrapped -= 360f;
        }
        if (wrapped < -180f) {
            wrapped += 360f;
        }
        return wrapped;
    }

    private float normalizeYaw(float value) {
        float normalized = value % 360f;
        if (normalized < 0) {
            normalized += 360f;
        }
        return normalized;
    }

    private float applyDeadzone(float value) {
        return Math.abs(value) < INPUT_DEADZONE ? 0f : value;
    }

    private float approachInput(float current, float target, float factor) {
        float next = current + (target - current) * factor;
        return Math.abs(next) < 0.01f && Math.abs(target) < 0.01f ? 0f : next;
    }

    private float wrapYawToReference(float yaw, float reference) {
        return reference + this.wrapDegrees(yaw - reference);
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private double lerp(double from, double to, double factor) {
        return from + (to - from) * factor;
    }

    private double moveToward(double current, double target, double maxDelta) {
        if (current < target) {
            return Math.min(target, current + maxDelta);
        }
        if (current > target) {
            return Math.max(target, current - maxDelta);
        }
        return target;
    }

    private double clampDouble(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private enum SurfaceType {
        NONE,
        WATER,
        ICE
    }

    private static final class SurfaceInfo {
        private final SurfaceType type;
        private final double height;

        private SurfaceInfo(SurfaceType type, double height) {
            this.type = type;
            this.height = height;
        }
    }
}
