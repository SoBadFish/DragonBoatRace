package org.sobadfish.dragonboat;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.custom.EntityManager;
import cn.nukkit.item.Item;
import cn.nukkit.level.Sound;
import cn.nukkit.level.Position;
import cn.nukkit.math.Vector3;
import cn.nukkit.plugin.PluginBase;
import org.sobadfish.dragonboat.customitem.DragonBoostItem;
import org.sobadfish.dragonboat.customitem.DragonMagnetItem;
import org.sobadfish.dragonboat.customitem.DragonMissileItem;
import org.sobadfish.dragonboat.customitem.DragonResetItem;
import org.sobadfish.dragonboat.entity.DragonBoatEntity;
import org.sobadfish.dragonboat.entity.MissileEntity;
import org.sobadfish.dragonboat.entity.RewardBoxEntity;
import org.sobadfish.dragonboat.listener.DragonBoatListener;
import org.sobadfish.dragonboat.manager.PlayerControlEffectManager;

public class DragonBoatMainClass extends PluginBase {

    private static DragonBoatMainClass instance;

    @Override
    public void onEnable() {
        instance = this;

        if (!this.getServer().enableExperimentMode) {
            this.getLogger().warning("服务器未开启实验模式，自定义实体与自定义物品资源可能不会正常工作。");
        }

        EntityManager.get().registerDefinition(DragonBoatEntity.DEF_ENTITY);
        EntityManager.get().registerDefinition(MissileEntity.DEF_ENTITY);
        EntityManager.get().registerDefinition(RewardBoxEntity.DEF_ENTITY);
        Item.registerCustomItem(DragonBoostItem.class, true);
        Item.registerCustomItem(DragonMagnetItem.class, true);
        Item.registerCustomItem(DragonMissileItem.class, true);
        Item.registerCustomItem(DragonResetItem.class, true);
        this.getServer().getPluginManager().registerEvents(new DragonBoatListener(this), this);
        PlayerControlEffectManager.start(this);
        this.getLogger().info("DragonBoatRace 已加载。使用 /dragonboat spawn 生成龙舟，/dragonboat missile 发射导弹，/dragonboat rewardbox 生成奖励方块。");
    }

    @Override
    public void onDisable() {
        PlayerControlEffectManager.stop();
    }

    public static DragonBoatMainClass getInstance() {
        return instance;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!"dragonboat".equalsIgnoreCase(command.getName())) {
            return false;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c该命令只能由玩家执行。");
            return true;
        }

        if (args.length == 0 || "spawn".equalsIgnoreCase(args[0])) {
            return this.spawnBoatFor(player);
        }

        if ("missile".equalsIgnoreCase(args[0])) {
            return this.launchMissileFor(player);
        }

        if ("camera".equalsIgnoreCase(args[0])) {
            player.sendMessage("§e龙舟相机功能已停用。");
            return true;
        }

        if ("rewardbox".equalsIgnoreCase(args[0])) {
            return this.spawnRewardBoxFor(player);
        }

        player.sendMessage("§e用法: /dragonboat <spawn|missile|camera|rewardbox>");
        return true;
    }

    private boolean spawnBoatFor(Player player) {
        Position spawnPos = this.findBoatSpawnPosition(player);
        if (spawnPos == null) {
            player.sendMessage("§c请把准星对准 10 格内的水面或冰面，再执行 /dragonboat spawn。");
            return true;
        }

        Entity entity = Entity.createEntity(
                DragonBoatEntity.DEF_ENTITY.getIdentifier(),
                spawnPos.getChunk(),
                Entity.getDefaultNBT(spawnPos).putFloat("Yaw", (float) player.yaw).putFloat("Pitch", 0),
                player
        );

        if (!(entity instanceof DragonBoatEntity boat)) {
            player.sendMessage("§c龙舟生成失败，请检查实验模式与实体注册状态。");
            return true;
        }

        boat.spawnToAll();
        boat.mountEntity(player);
        player.sendMessage("§a龙舟已生成并自动骑乘。");
        return true;
    }

    public boolean launchMissileFor(Player player) {
        Player target = this.findFrontLockTarget(player);
        if (target == null) {
            player.sendMessage("§c前方 15 格内没有可锁定的玩家目标。");
            return false;
        }

        Vector3 direction = player.getDirectionVector();
        Position spawnPos = Position.fromObject(
                player.add(0, player.getEyeHeight() - 0.3, 0).add(direction.multiply(1.6)),
                player.getLevel()
        );

        Entity entity = Entity.createEntity(
                MissileEntity.DEF_ENTITY.getIdentifier(),
                spawnPos.getChunk(),
                Entity.getDefaultNBT(spawnPos).putFloat("Yaw", (float) player.yaw).putFloat("Pitch", (float) player.pitch),
                player,
                target,
                target.add(0, target.getEyeHeight() * 0.6, 0).subtract(spawnPos).normalize()
        );

        if (!(entity instanceof MissileEntity missile)) {
            player.sendMessage("§c导弹生成失败，请检查实体注册状态。");
            return true;
        }

        missile.spawnToAll();
        player.getLevel().addSound(spawnPos, Sound.CROSSBOW_SHOOT, 0.95f, 1.0f);
        player.getLevel().addSound(spawnPos, Sound.ITEM_TRIDENT_THROW, 0.8f, 1.15f);
        player.sendMessage("§a导弹已锁定并发射。");
        return true;
    }

    public boolean activateMagnetFor(Player player) {
        DragonBoatEntity boat = this.getRidingBoat(player);
        if (boat == null) {
            player.sendMessage("§c只有骑乘龙舟时才能使用磁铁道具。");
            return false;
        }

        Player target = this.findFrontLockTarget(player);
        if (target == null) {
            player.sendMessage("§c前方 15 格、左右 3 格内没有可锁定的玩家目标。");
            return false;
        }

        boat.activateMagnet(target);
        player.getLevel().addSound(boat, Sound.BEACON_POWER, 0.8f, 1.1f);
        player.getLevel().addSound(boat, Sound.BUBBLE_UP, 0.6f, 1.35f);
        player.sendMessage("§e磁铁已锁定 " + target.getName() + "，目标会减速，你的龙舟将高速追近。");
        return true;
    }

    private Position findBoatSpawnPosition(Player player) {
        Block target = player.getTargetBlock(10);
        if (this.isBoatSurface(target)) {
            return Position.fromObject(target.add(0.5, 1.0, 0.5), player.getLevel());
        }

        Block ahead = player.getLevel().getBlock(player.add(player.getDirectionVector().multiply(3)));
        if (this.isBoatSurface(ahead)) {
            return Position.fromObject(ahead.add(0.5, 1.0, 0.5), player.getLevel());
        }

        Block belowAhead = player.getLevel().getBlock(player.add(player.getDirectionVector().multiply(3)).down());
        if (this.isBoatSurface(belowAhead)) {
            return Position.fromObject(belowAhead.add(0.5, 1.0, 0.5), player.getLevel());
        }
        return null;
    }

    private boolean spawnRewardBoxFor(Player player) {
        Position spawnPos = this.findBoatSpawnPosition(player);
        if (spawnPos == null) {
            player.sendMessage("§c请把准星对准 10 格内的水面或冰面，再执行 /dragonboat rewardbox。");
            return true;
        }

        Entity entity = Entity.createEntity(
                RewardBoxEntity.DEF_ENTITY.getIdentifier(),
                spawnPos.getChunk(),
                Entity.getDefaultNBT(spawnPos)
                        .putFloat("Yaw", 0)
                        .putFloat("Pitch", 0)
        );

        if (!(entity instanceof RewardBoxEntity rewardBox)) {
            player.sendMessage("§c奖励方块生成失败。");
            return true;
        }

        rewardBox.spawnToAll();
        player.getLevel().addSound(spawnPos, Sound.RANDOM_POP, 0.85f, 1.2f);
        player.getLevel().addSound(spawnPos, Sound.BLOCK_BELL_HIT, 0.55f, 1.5f);
        player.sendMessage("§a奖励方块已生成。");
        return true;
    }

    private boolean isBoatSurface(Block block) {
        if (block == null) {
            return false;
        }
        if (block.isWater()) {
            return true;
        }
        return block.getClass().getSimpleName().toLowerCase().contains("ice");
    }

    private DragonBoatEntity getRidingBoat(Player player) {
        if (player.riding instanceof DragonBoatEntity boat) {
            return boat;
        }
        return null;
    }

    private Player findFrontLockTarget(Player player) {
        Vector3 origin = player.getPosition();
        Vector3 forward = player.getDirectionVector();
        forward = new Vector3(forward.x, 0, forward.z);
        if (forward.lengthSquared() <= 0.0001d) {
            double yaw = Math.toRadians(player.yaw);
            forward = new Vector3(-Math.sin(yaw), 0, Math.cos(yaw));
        } else {
            forward = forward.normalize();
        }
        Vector3 right = new Vector3(forward.z, 0, -forward.x);

        Player best = null;
        double bestForwardDistance = Double.MAX_VALUE;

        for (Player target : this.getServer().getOnlinePlayers().values()) {
            if (target == player || target.level != player.level || !target.isAlive()) {
                continue;
            }

            Vector3 toTarget = target.getPosition().subtract(origin);
            Vector3 horizontal = new Vector3(toTarget.x, 0, toTarget.z);
            double forwardDistance = horizontal.dot(forward);
            if (forwardDistance <= 0 || forwardDistance > 15.0d) {
                continue;
            }

            double lateralOffset = Math.abs(horizontal.dot(right));
            if (lateralOffset > 3.0d) {
                continue;
            }

            if (Math.abs(toTarget.y) > 4.0d) {
                continue;
            }

            if (forwardDistance < bestForwardDistance) {
                bestForwardDistance = forwardDistance;
                best = target;
            }
        }

        return best;
    }
}
