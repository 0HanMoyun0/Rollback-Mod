package com.taobao.koi.rollbackmod.rollback;

import com.taobao.koi.rollbackmod.compat.TimeClockCompat;
import com.taobao.koi.rollbackmod.config.RollbackConfig;
import com.taobao.koi.rollbackmod.effect.ModMobEffects;
import com.taobao.koi.rollbackmod.item.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.NonNullList;

public final class RollbackManager {
    private static boolean rollingBack;

    private RollbackManager() {
    }

    public static boolean isRollingBack() {
        return rollingBack;
    }

    public static boolean hasCheckpoint(MinecraftServer server) {
        return RollbackSavedData.get(server).hasCheckpoint();
    }

    public static void deleteCheckpoint(MinecraftServer server) {
        if (server == null) {
            return;
        }
        RollbackSavedData data = RollbackSavedData.get(server);
        data.clearCheckpoint();
        data.setInitialCheckpointCreated(true);
        BlockRollbackManager.clearChangedBlocks(data);
        MarkManager.clearAllMarks(server, data);
        DayCounterManager.syncToAll(server);
        server.saveEverything(false, true, false);
    }

    public static void ensureInitialCheckpoint(MinecraftServer server) {
        if (!RollbackConfig.SAVE_ON_FIRST_WORLD_LOAD.get()) {
            return;
        }
        RollbackSavedData data = RollbackSavedData.get(server);
        if (!data.isInitialCheckpointCreated()) {
            createCheckpoint(server, "initial_world_load");
            data.setInitialCheckpointCreated(true);
        }
    }

    public static void createCheckpoint(MinecraftServer server, String reason) {
        if (server == null) {
            return;
        }
        RollbackSavedData data = RollbackSavedData.get(server);
        data.setCheckpoint(RollbackSavedData.Checkpoint.capture(server));
        data.clearSelfDestructedPlayers();
        BlockRollbackManager.clearChangedBlocks(data);
        DayCounterManager.onCheckpointCreated(server, data);
    }

    public static boolean rollback(MinecraftServer server, String reason) {
        if (server == null || rollingBack) {
            return false;
        }
        RollbackSavedData data = RollbackSavedData.get(server);
        RollbackSavedData.Checkpoint checkpoint = data.getCheckpoint();
        if (checkpoint == null) {
            return false;
        }

        rollingBack = true;
        try {
            server.getAllLevels().forEach(level -> level.setDayTime(checkpoint.dayTime()));
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                checkpoint.getSnapshot(player.getUUID())
                        .orElseGet(() -> RollbackSavedData.PlayerSnapshot.capture(player))
                        .restore(player, server);
            }

            BlockRollbackManager.restoreChangedBlocks(server, data);
            MarkManager.applyRollbackEffects(server, data);
            clearCoreStates(server, data);

            if (RollbackConfig.DESTROY_ALL_CORES_ON_ROLLBACK.get()) {
                destroyCoreItems(server);
            }

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.displayClientMessage(Component.translatable("message.rollbackmod.rolled_back"), true);
            }
            DayCounterManager.onRollbackFinished(server, data);
            return true;
        } finally {
            rollingBack = false;
            data.setDirty();
        }
    }

    private static void clearCoreStates(MinecraftServer server, RollbackSavedData data) {
        MarkManager.clearAllMarks(server, data);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.removeEffect(ModMobEffects.CHRONOS.get());
            TimeClockCompat.resetPlayer(player);
        }
    }

    private static void destroyCoreItems(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            removeCores(player.getInventory().items);
            removeCores(player.getInventory().armor);
            removeCores(player.getInventory().offhand);
            player.inventoryMenu.broadcastChanges();
            player.containerMenu.broadcastChanges();
        }
    }

    private static void removeCores(NonNullList<ItemStack> items) {
        for (int i = 0; i < items.size(); i++) {
            if (ModItems.isCore(items.get(i))) {
                items.set(i, ItemStack.EMPTY);
            }
        }
    }
}
