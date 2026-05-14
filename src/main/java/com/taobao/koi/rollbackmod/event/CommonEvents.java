package com.taobao.koi.rollbackmod.event;

import com.taobao.koi.rollbackmod.config.RollbackConfig;
import com.taobao.koi.rollbackmod.rollback.BlockRollbackManager;
import com.taobao.koi.rollbackmod.rollback.MarkManager;
import com.taobao.koi.rollbackmod.rollback.DayCounterManager;
import com.taobao.koi.rollbackmod.rollback.RollbackManager;
import com.taobao.koi.rollbackmod.rollback.SelfDestructManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDestroyBlockEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class CommonEvents {
    public static final String SKIP_NEXT_DEATH_ROLLBACK_TAG = "rollbackmod_skip_next_death_rollback";

    private CommonEvents() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RollbackManager.ensureInitialCheckpoint(player.getServer());
            SelfDestructManager.enforce(player);
            DayCounterManager.syncTo(player);
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!hasSelfDestructDeathPending(event)) {
            MarkManager.handleFatalCoreEffects(event);
        }
        if (!event.isCanceled()) {
            if (handleFatalRollback(event)) {
                return;
            }
            MarkManager.handleMarkedTargetHurt(event);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || RollbackManager.isRollingBack()) {
            return;
        }
        if (player.getPersistentData().getBoolean(SKIP_NEXT_DEATH_ROLLBACK_TAG)) {
            player.getPersistentData().remove(SKIP_NEXT_DEATH_ROLLBACK_TAG);
            return;
        }
        if (!RollbackConfig.ENABLE_DEATH_ROLLBACK.get()) {
            return;
        }
        if (!RollbackConfig.ROLLBACK_ALL_PLAYERS_ON_DEATH.get()) {
            return;
        }
        if (!RollbackManager.hasCheckpoint(player.getServer())) {
            return;
        }

        event.setCanceled(true);
        player.setHealth(Math.max(1.0F, player.getHealth()));
        RollbackManager.rollback(player.getServer(), "player_death");
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!event.isCanceled() && event.getLevel() instanceof ServerLevel level) {
            BlockRollbackManager.rememberBlockBeforeChange(level, event.getPos(), event.getState());
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!event.isCanceled() && event.getLevel() instanceof ServerLevel level) {
            BlockRollbackManager.rememberBlockBeforeChange(level, event.getPos(), event.getBlockSnapshot().getReplacedBlock());
        }
    }

    @SubscribeEvent
    public static void onBlockMultiPlace(BlockEvent.EntityMultiPlaceEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        event.getReplacedBlockSnapshots().forEach(snapshot ->
                BlockRollbackManager.rememberBlockBeforeChange(level, snapshot.getPos(), snapshot.getReplacedBlock()));
    }

    @SubscribeEvent
    public static void onFluidPlaceBlock(BlockEvent.FluidPlaceBlockEvent event) {
        if (!event.isCanceled() && event.getLevel() instanceof ServerLevel level) {
            BlockRollbackManager.rememberBlockBeforeChange(level, event.getPos(), event.getOriginalState());
        }
    }

    @SubscribeEvent
    public static void onFarmlandTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (!event.isCanceled() && event.getLevel() instanceof ServerLevel level) {
            BlockRollbackManager.rememberBlockBeforeChange(level, event.getPos(), event.getState());
        }
    }

    @SubscribeEvent
    public static void onLivingDestroyBlock(LivingDestroyBlockEvent event) {
        if (!event.isCanceled() && event.getEntity().level() instanceof ServerLevel level) {
            BlockRollbackManager.rememberBlockBeforeChange(level, event.getPos(), event.getState());
        }
    }

    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof ServerLevel level) {
            for (BlockPos pos : event.getAffectedBlocks()) {
                BlockRollbackManager.rememberBlockBeforeChange(level, pos);
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            MarkManager.tick(event.getServer());
            DayCounterManager.tick(event.getServer());
            event.getServer().getPlayerList().getPlayers().forEach(SelfDestructManager::enforce);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangeGameMode(PlayerEvent.PlayerChangeGameModeEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && SelfDestructManager.isLocked(player)) {
            if (event.getNewGameMode() == GameType.SPECTATOR) {
                return;
            }
            event.setCanceled(true);
            event.setNewGameMode(GameType.SPECTATOR);
            SelfDestructManager.enforce(player);
        }
    }

    private static boolean handleFatalRollback(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || RollbackManager.isRollingBack()) {
            return false;
        }
        if (player.getPersistentData().getBoolean(SKIP_NEXT_DEATH_ROLLBACK_TAG)) {
            return false;
        }
        if (!RollbackConfig.ENABLE_DEATH_ROLLBACK.get() || !RollbackConfig.ROLLBACK_ALL_PLAYERS_ON_DEATH.get()) {
            return false;
        }
        if (!RollbackManager.hasCheckpoint(player.getServer())) {
            return false;
        }
        if (event.getAmount() < player.getHealth() + player.getAbsorptionAmount()) {
            return false;
        }

        event.setCanceled(true);
        player.setHealth(Math.max(1.0F, player.getHealth()));
        RollbackManager.rollback(player.getServer(), "fatal_damage");
        return true;
    }

    private static boolean hasSelfDestructDeathPending(LivingHurtEvent event) {
        return event.getEntity() instanceof ServerPlayer player
                && player.getPersistentData().getBoolean(SKIP_NEXT_DEATH_ROLLBACK_TAG);
    }
}
