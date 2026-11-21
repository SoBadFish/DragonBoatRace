package org.sobadfish.dragonboat.entity;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.custom.CustomEntity;
import cn.nukkit.entity.custom.EntityDefinition;
import cn.nukkit.level.Sound;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.particle.GenericParticle;
import cn.nukkit.level.particle.Particle;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import org.sobadfish.dragonboat.manager.RewardManager;

/**
 * 水面悬浮奖励方块实体。
 */
public class RewardBoxEntity extends Entity implements CustomEntity {

    private static final float ROTATION_SPEED = 1.5f;

    public static final EntityDefinition DEF_ENTITY = EntityDefinition
            .builder()
            .identifier("dragon:reward_box")
            .spawnEgg(false)
            .implementation(RewardBoxEntity.class)
            .build();

    private double baseY;
    private boolean collected;

    public RewardBoxEntity(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
        this.baseY = this.y;
    }

    @Override
    protected void initEntity() {
        super.initEntity();
        this.setScale(0.8f);
        this.setMaxHealth(1);
        this.setHealth(1);
        this.baseY = this.y;
        this.setRotation(0, 0);
    }

    @Override
    public boolean onUpdate(int currentTick) {
        int tickDiff = currentTick - this.lastUpdate;
        if (tickDiff <= 0 && !this.justCreated) {
            return true;
        }
        this.lastUpdate = currentTick;

        boolean hasUpdate = this.entityBaseTick(tickDiff);
        double waterSurface = this.findWaterSurface();
        if (waterSurface != Double.NEGATIVE_INFINITY) {
            this.baseY = waterSurface + 0.9d;
        }

        this.setRotation((this.yaw + ROTATION_SPEED) % 360f, 0);
        double bobOffset = Math.sin(this.age / 8.0d) * 0.08d;
        this.setPosition(new Vector3(this.x, this.baseY + bobOffset, this.z));
        this.updateMovement();

        for (Entity entity : this.level.getNearbyEntities(this.boundingBox.grow(0.35, 0.35, 0.35), this)) {
            if (entity instanceof DragonBoatEntity boat) {
                Player rider = boat.getFirstRider();
                if (rider != null && rider.isOnline()) {
                    this.collect(rider);
                    return false;
                }
            }
        }

        return hasUpdate || true;
    }

    @Override
    public void saveNBT() {
        super.saveNBT();
    }

    @Override
    public float getWidth() {
        return 0.8f;
    }

    @Override
    public float getHeight() {
        return 0.8f;
    }

    @Override
    public float getGravity() {
        return 0f;
    }

    @Override
    public EntityDefinition getEntityDefinition() {
        return DEF_ENTITY;
    }

    @Override
    public int getNetworkId() {
        return DEF_ENTITY.getRuntimeId();
    }

    public boolean isCollected() {
        return this.collected;
    }

    private double findWaterSurface() {
        Block here = this.level.getBlock(this);
        if (here.isWater()) {
            return here.y + 1;
        }

        Block below = this.level.getBlock(this.down());
        if (below.isWater()) {
            return below.y + 1;
        }
        return Double.NEGATIVE_INFINITY;
    }

    private void collect(Player player) {
        if (this.collected || this.closed) {
            return;
        }
        this.collected = true;
        this.spawnCollectEffects();
        RewardManager.giveRandomReward(player);
        this.despawnFromAll();
        this.close();
    }

    public void pullToward(Vector3 target, double strength) {
        Vector3 delta = target.subtract(this);
        double distance = delta.length();
        if (distance < 0.05d) {
            return;
        }

        Vector3 move = delta.normalize().multiply(Math.min(strength, distance * 0.35d));
        this.x += move.x;
        this.z += move.z;
        this.baseY += move.y * 0.18d;
        this.updateMovement();
    }

    private void spawnCollectEffects() {
        for (int i = 0; i < 12; i++) {
            double angle = (Math.PI * 2 * i) / 12d;
            double radius = 0.45d + (i % 2) * 0.12d;
            Vector3 point = this.add(Math.cos(angle) * radius, 0.35d + (i % 3) * 0.08d, Math.sin(angle) * radius);
            this.level.addParticle(new GenericParticle(point, Particle.TYPE_FIREWORKS_SPARK, 0));
        }
        this.level.addParticle(new GenericParticle(this.add(0, 0.55d, 0), Particle.TYPE_FIREWORKS_SPARK, 0));
        this.level.addSound(this, Sound.FIREWORK_BLAST);
    }

    @Override
    public void close() {
        this.despawnFromAll();
        super.close();
    }
}
