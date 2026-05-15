package com.taobao.koi.rollbackmod.rollback;

import com.taobao.koi.rollbackmod.config.RollbackConfig;
import com.taobao.koi.rollbackmod.core.CoreType;
import com.taobao.koi.rollbackmod.core.CoreUseResult;
import com.taobao.koi.rollbackmod.item.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

public final class MarkManager {
    private MarkManager() {
    }

    public static CoreUseResult mark(ServerPlayer player, LivingEntity target, CoreType type) {
        if (!(target.level() instanceof ServerLevel targetLevel)) {
            return CoreUseResult.fail(Component.translatable("message.rollbackmod.mark_failed"));
        }
        if (target instanceof Player || target.getUUID().equals(player.getUUID())) {
            return CoreUseResult.fail(Component.translatable("message.rollbackmod.invalid_mark_target"));
        }

        RollbackSavedData data = RollbackSavedData.get(player.getServer());
        if (RollbackConfig.ONLY_ONE_MARK_PER_PLAYER.get() && data.getMark(player.getUUID()).isPresent()) {
            return CoreUseResult.fail(Component.translatable("message.rollbackmod.already_marked"));
        }

        boolean originalNoAi = target instanceof Mob mob && mob.isNoAi();
        boolean originalGlowing = target.hasGlowingTag();
        target.setGlowingTag(true);
        long expiresAt = 0L;
        if (type == CoreType.STASIS) {
            expiresAt = player.serverLevel().getGameTime() + RollbackConfig.STASIS_DURATION_TICKS.get();
            if (target instanceof Mob mob) {
                mob.setNoAi(true);
            }
        }

        data.putMark(new RollbackSavedData.MarkRecord(
                player.getUUID(),
                type,
                target.getUUID(),
                targetLevel.dimension().location().toString(),
                expiresAt,
                originalNoAi,
                originalGlowing
        ));
        return CoreUseResult.success(true, Component.translatable("message.rollbackmod.target_marked"));
    }

    public static void tick(MinecraftServer server) {
        RollbackSavedData data = RollbackSavedData.get(server);
        long now = server.overworld().getGameTime();
        List<RollbackSavedData.MarkRecord> records = new ArrayList<>(data.getMarks());
        for (RollbackSavedData.MarkRecord record : records) {
            Optional<LivingEntity> target = findLivingTarget(server, record);
            if (target.isEmpty() && RollbackConfig.MARK_LOST_WHEN_TARGET_DEAD.get()) {
                restoreMarkedTargetState(server, record);
                data.removeMark(record.playerId());
                continue;
            }
            if (record.coreType() == CoreType.STASIS && record.expiresAtGameTime() > 0L && now >= record.expiresAtGameTime()) {
                restoreMarkedTargetState(server, record);
                data.removeMark(record.playerId());
            }
        }
    }

    public static void applyRollbackEffects(MinecraftServer server, RollbackSavedData data) {
        List<RollbackSavedData.MarkRecord> records = new ArrayList<>(data.getMarks());
        for (RollbackSavedData.MarkRecord record : records) {
            if (record.coreType() != CoreType.SAND) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(record.playerId());
            Optional<LivingEntity> target = findLivingTarget(server, record);
            if (player == null || target.isEmpty()) {
                continue;
            }
            moveLivingToLevel(target.get(), player.serverLevel(), player.getX() + 0.75D, player.getY(), player.getZ() + 0.75D, player.getYRot(), player.getXRot())
                    .ifPresent(movedTarget -> data.putMark(record.withTarget(
                            movedTarget.getUUID(),
                            movedTarget.level().dimension().location().toString()
                    )));
        }
    }

    public static void clearAllMarks(MinecraftServer server, RollbackSavedData data) {
        for (RollbackSavedData.MarkRecord record : new ArrayList<>(data.getMarks())) {
            restoreMarkedTargetState(server, record);
        }
        data.clearMarks();
    }

    public static void handleFatalCoreEffects(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.isCanceled()) {
            return;
        }
        if (!wouldBeFatal(player, event.getAmount())) {
            return;
        }

        RollbackSavedData data = RollbackSavedData.get(player.getServer());
        Optional<RollbackSavedData.MarkRecord> record = data.getMark(player.getUUID());
        if (record.isEmpty()) {
            return;
        }

        if (record.get().coreType() == CoreType.CAUSALITY) {
            handleCausalityFatal(event, player, data, record.get());
        } else if (record.get().coreType() == CoreType.MYRIAD) {
            handleMyriadFatal(event, player, data, record.get());
        }
    }

    public static void handleMarkedTargetHurt(LivingHurtEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level) || event.isCanceled()) {
            return;
        }
        LivingEntity target = event.getEntity();
        MinecraftServer server = level.getServer();
        RollbackSavedData data = RollbackSavedData.get(server);
        for (RollbackSavedData.MarkRecord record : new ArrayList<>(data.getMarks())) {
            if (record.coreType() == CoreType.MYRIAD && record.targetId().equals(target.getUUID())) {
                ServerPlayer player = server.getPlayerList().getPlayer(record.playerId());
                if (player != null) {
                    moveRandomHotbarItemToOffhand(player);
                }
            }
        }
    }

    private static void handleCausalityFatal(LivingHurtEvent event, ServerPlayer player, RollbackSavedData data, RollbackSavedData.MarkRecord record) {
        Optional<LivingEntity> target = findLivingTarget(player.getServer(), record);
        if (target.isEmpty()) {
            data.removeMark(player.getUUID());
            return;
        }

        event.setCanceled(true);
        LivingEntity livingTarget = target.get();
        ServerLevel playerLevel = player.serverLevel();
        double playerX = player.getX();
        double playerY = player.getY();
        double playerZ = player.getZ();
        float playerYRot = player.getYRot();
        float playerXRot = player.getXRot();
        ServerLevel targetLevel = (ServerLevel) livingTarget.level();
        double targetX = livingTarget.getX();
        double targetY = livingTarget.getY();
        double targetZ = livingTarget.getZ();
        float targetYRot = livingTarget.getYRot();
        float targetXRot = livingTarget.getXRot();

        Optional<LivingEntity> movedTarget = moveLivingToLevel(livingTarget, playerLevel, playerX, playerY, playerZ, playerYRot, playerXRot);
        if (movedTarget.isPresent()) {
            player.teleportTo(targetLevel, targetX, targetY, targetZ, targetYRot, targetXRot);
            movedTarget.get().hurt(player.damageSources().magic(), player.getMaxHealth());
        }

        data.removeMark(player.getUUID());
        player.displayClientMessage(Component.translatable("message.rollbackmod.core_triggered"), true);
    }

    private static void handleMyriadFatal(LivingHurtEvent event, ServerPlayer player, RollbackSavedData data, RollbackSavedData.MarkRecord record) {
        boolean destroyedWeapon = destroyOneWeapon(player);
        if (!destroyedWeapon && RollbackConfig.MYRIAD_REQUIRES_WEAPON_TO_PREVENT_DEATH.get()) {
            data.removeMark(record.playerId());
            return;
        }

        event.setCanceled(true);
        player.setHealth(Math.min(player.getMaxHealth(), Math.max(1.0F, player.getHealth()) + 8.0F));
        player.inventoryMenu.broadcastChanges();
        player.containerMenu.broadcastChanges();
        data.removeMark(record.playerId());
        player.displayClientMessage(Component.translatable("message.rollbackmod.core_triggered"), true);
    }

    private static boolean wouldBeFatal(ServerPlayer player, float amount) {
        return amount >= player.getHealth() + player.getAbsorptionAmount();
    }

    private static void moveRandomHotbarItemToOffhand(ServerPlayer player) {
        List<Integer> candidates = new ArrayList<>();
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().items.get(slot);
            if (!stack.isEmpty()) {
                candidates.add(slot);
            }
        }
        if (candidates.isEmpty()) {
            return;
        }
        int selected = candidates.get(player.getRandom().nextInt(candidates.size()));
        ItemStack hotbarStack = player.getInventory().items.get(selected);
        ItemStack offhandStack = player.getInventory().offhand.get(0);
        player.getInventory().items.set(selected, offhandStack);
        player.getInventory().offhand.set(0, hotbarStack);
        player.inventoryMenu.broadcastChanges();
        player.containerMenu.broadcastChanges();
    }

    private static boolean destroyOneWeapon(ServerPlayer player) {
        for (ItemStack stack : player.getInventory().items) {
            if (isWeapon(stack)) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }

    private static boolean isWeapon(ItemStack stack) {
        return !stack.isEmpty()
                && (stack.getItem() instanceof SwordItem
                || stack.getItem() instanceof AxeItem
                || stack.getItem() instanceof BowItem
                || stack.getItem() instanceof CrossbowItem
                || stack.getItem() instanceof TridentItem);
    }

    private static Optional<LivingEntity> findLivingTarget(MinecraftServer server, RollbackSavedData.MarkRecord record) {
        Optional<Entity> entity = findEntity(server, record);
        if (entity.isPresent() && entity.get() instanceof LivingEntity living && living.isAlive()) {
            return Optional.of(living);
        }
        return Optional.empty();
    }

    private static Optional<Entity> findEntity(MinecraftServer server, RollbackSavedData.MarkRecord record) {
        Optional<ServerLevel> level = RollbackSavedData.resolveLevel(server, record.targetDimension());
        if (level.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(level.get().getEntity(record.targetId()));
    }

    private static void restoreMarkedTargetState(MinecraftServer server, RollbackSavedData.MarkRecord record) {
        findEntity(server, record).ifPresent(entity -> {
            entity.setGlowingTag(record.originalGlowing());
            if (entity instanceof Mob mob && mob.isAlive()) {
                if (record.coreType() == CoreType.STASIS) {
                    mob.setNoAi(record.originalNoAi());
                }
            }
        });
    }

    private static Optional<LivingEntity> moveLivingToLevel(LivingEntity living, ServerLevel level, double x, double y, double z, float yRot, float xRot) {
        Entity moved = living;
        if (living.level() != level) {
            moved = living.changeDimension(level);
            if (moved == null) {
                return Optional.empty();
            }
        }
        if (moved instanceof LivingEntity movedLiving) {
            movedLiving.teleportTo(x, y, z);
            movedLiving.setYRot(yRot);
            movedLiving.setXRot(xRot);
            movedLiving.setYHeadRot(yRot);
            return Optional.of(movedLiving);
        }
        return Optional.empty();
    }
}
