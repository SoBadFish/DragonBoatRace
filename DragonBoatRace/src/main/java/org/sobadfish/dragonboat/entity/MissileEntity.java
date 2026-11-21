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
import cn.nukkit.level.particle.SmokeParticle;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import org.sobadfish.dragonboat.manager.PlayerControlEffectManager;

/**
 * 直线飞行导弹实体。
 */
public class MissileEntity extends Entity implements CustomEntity {

    public static final EntityDefinition DEF_ENTITY = EntityDefinition
            .builder()
            .identifier("dragon:missile")
            .spawnEgg(false)
            .implementation(MissileEntity.class)
            .build();

    private static final double SPEED_PER_TICK = 0.38d;
    private static final int MAX_LIFETIME = 80;
    private static final int FREEZE_DURATION_TICKS = 60;

    private Entity owner;
    private Player target;
    private Vector3 direction;

    public MissileEntity(FullChunk chunk, CompoundTag nbt) {
        this(chunk, nbt, null, null, new Vector3(0, 0, 1));
    }

    public MissileEntity(FullChunk chunk, CompoundTag nbt, Entity owner, Player target, Vector3 direction) {
        super(chunk, nbt);
        this.owner = owner;
        this.target = target;
        this.direction = direction == null ? new Vector3(0, 0, 1) : direction.normalize();
        this.setScale(0.5f);
        this.syncRotationToDirection();
    }

    @Override
    protected void initEntity() {
        super.initEntity();
        this.setMaxHealth(1);
        this.setHealth(1);
        this.setDataFlag(DATA_FLAGS_EXTENDED, DATA_FLAG_COLLIDABLE, true);
    }

    @Override
    public boolean onUpdate(int currentTick) {
        int tickDiff = currentTick - this.lastUpdate;
        if (tickDiff <= 0 && !this.justCreated) {
            return true;
        }
        this.lastUpdate = currentTick;

        boolean hasUpdate = this.entityBaseTick(tickDiff);

        if (this.target != null && (!this.target.isOnline() || !this.target.isAlive() || this.target.level != this.level)) {
            this.explode(null);
            return false;
        }

        if (this.target != null) {
            Vector3 seek = this.target.add(0, this.target.getEyeHeight() * 0.6, 0).subtract(this);
            if (seek.lengthSquared() > 0.001d) {
                this.direction = seek.normalize();
            }
        }

        this.motionX = this.direction.x * SPEED_PER_TICK;
        this.motionY = this.direction.y * SPEED_PER_TICK;
        this.motionZ = this.direction.z * SPEED_PER_TICK;

        this.move(this.motionX, this.motionY, this.motionZ);
        this.updateMovement();
        this.syncRotationToDirection();
        this.spawnTrail();

        if (this.age > MAX_LIFETIME || this.isCollided || this.hitSolidBlock()) {
            this.explode(null);
            return false;
        }

        for (Entity entity : this.level.getNearbyEntities(this.boundingBox.grow(0.45, 0.45, 0.45), this)) {
            if (!entity.isAlive() || entity == this.owner || entity == this.riding || entity == this) {
                continue;
            }

            if (this.target != null && entity != this.target) {
                continue;
            }

            this.explode(entity instanceof Player player ? player : null);
            return false;
        }

        if (this.target != null && this.distanceSquared(this.target) <= 1.2d * 1.2d) {
            this.explode(this.target);
            return false;
        }

        return hasUpdate || true;
    }

    @Override
    public void saveNBT() {
        super.saveNBT();
    }

    @Override
    public float getWidth() {
        return 0.5f;
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

    private void spawnTrail() {
        Vector3 tail = this.subtract(this.direction.multiply(0.6));
        for (int i = 0; i < 6; i++) {
            double spread = 0.18d;
            Vector3 offset = new Vector3(
                    (Math.random() - 0.5d) * spread,
                    (Math.random() - 0.5d) * spread,
                    (Math.random() - 0.5d) * spread
            );
            this.level.addParticle(new SmokeParticle(tail.add(offset)));
        }
    }

    private boolean hitSolidBlock() {
        Block block = this.level.getBlock(this);
        return !block.isAir() && !block.isWater();
    }

    private void explode(Player hitPlayer) {
        for (int i = 0; i < 8; i++) {
            double angle = (Math.PI * 2 * i) / 8d;
            double radius = 0.5d + (i % 3) * 0.15d;
            Vector3 point = this.add(Math.cos(angle) * radius, 0.3d + (i % 2) * 0.2d, Math.sin(angle) * radius);
            this.level.addParticle(new GenericParticle(point, Particle.TYPE_FIREWORKS_SPARK, 0));
        }
        this.level.addSound(this, Sound.FIREWORK_LARGE_BLAST, 1.0f, 0.92f);
        this.level.addSound(this, Sound.ITEM_TRIDENT_HIT, 0.8f, 0.95f);

        Player actualTarget = hitPlayer != null ? hitPlayer : this.target;
        if (actualTarget != null && actualTarget.isOnline() && actualTarget.isAlive()) {
            PlayerControlEffectManager.freezePlayer(actualTarget, FREEZE_DURATION_TICKS);
            this.level.addSound(actualTarget, Sound.RANDOM_ANVIL_LAND, 0.55f, 1.45f);
            actualTarget.sendMessage("§c你被导弹命中，无法行动 3 秒。");
        }

        if (this.owner instanceof Player player && player.isOnline()) {
            player.sendMessage(actualTarget != null ? "§7导弹命中目标并触发爆炸。" : "§7导弹已爆炸。");
        }
        this.close();
    }

    private void syncRotationToDirection() {
        double x = this.direction.x;
        double y = this.direction.y;
        double z = this.direction.z;
        double xz = Math.sqrt(x * x + z * z);
        this.yaw = (float) Math.toDegrees(Math.atan2(-x, z));
        this.pitch = (float) Math.toDegrees(-Math.atan2(y, xz));
    }
}
