package com.taobao.koi.rollbackmod.rollback;

import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

public final class BlockRollbackManager {
    private static boolean restoringBlocks;

    private BlockRollbackManager() {
    }

    public static boolean isRestoringBlocks() {
        return restoringBlocks;
    }

    public static void rememberBlockBeforeChange(ServerLevel level, BlockPos pos) {
        rememberBlockBeforeChange(level, pos, level.getBlockState(pos));
    }

    public static void rememberBlockBeforeChange(ServerLevel level, BlockPos pos, BlockState originalState) {
        if (restoringBlocks || !RollbackManager.hasCheckpoint(level.getServer())) {
            return;
        }
        RollbackSavedData.get(level.getServer())
                .rememberChangedBlock(RollbackSavedData.BlockSnapshotRecord.capture(level, pos, originalState));
    }

    public static void restoreChangedBlocks(MinecraftServer server, RollbackSavedData data) {
        restoringBlocks = true;
        try {
            for (RollbackSavedData.BlockSnapshotRecord record : new ArrayList<>(data.getChangedBlocks())) {
                record.restore(server);
            }
            data.clearChangedBlocks();
        } finally {
            restoringBlocks = false;
        }
    }

    public static void clearChangedBlocks(RollbackSavedData data) {
        data.clearChangedBlocks();
    }
}
