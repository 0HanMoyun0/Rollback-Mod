package com.taobao.koi.rollbackmod.compat;

import com.taobao.koi.rollbackmod.config.RollbackConfig;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;

public final class TimeClockCompat {
    private static final double VANILLA_TICKRATE = 20.0D;
    private static final String[] FALLBACK_MOD_IDS = {
            "timeclock",
            "time_clock",
            "time_stop_clock",
            "timestopclock"
    };
    private static final Set<UUID> ACTIVE_CHRONOS_PLAYERS = new HashSet<>();

    private TimeClockCompat() {
    }

    public static boolean isDependencyPresent() {
        String configuredId = RollbackConfig.TIME_CLOCK_MOD_ID.get().toLowerCase(Locale.ROOT);
        if (!configuredId.isBlank() && ModList.get().isLoaded(configuredId)) {
            return true;
        }
        for (String fallbackId : FALLBACK_MOD_IDS) {
            if (ModList.get().isLoaded(fallbackId)) {
                return true;
            }
        }
        return false;
    }

    public static boolean setSlowTime(MinecraftServer server, ServerPlayer player, boolean active, double factor) {
        if (server == null || !isDependencyPresent()) {
            return false;
        }
        if (active) {
            ACTIVE_CHRONOS_PLAYERS.add(player.getUUID());
        } else {
            ACTIVE_CHRONOS_PLAYERS.remove(player.getUUID());
        }

        double targetTickrate = ACTIVE_CHRONOS_PLAYERS.isEmpty()
                ? VANILLA_TICKRATE
                : Math.max(1.0D, VANILLA_TICKRATE * factor);
        return runTimeClockCommand(server, targetTickrate);
    }

    public static void resetPlayer(ServerPlayer player) {
        if (isDependencyPresent()) {
            setSlowTime(player.getServer(), player, false, 1.0D);
        }
    }

    private static boolean runTimeClockCommand(MinecraftServer server, double tickrate) {
        String command = String.format(Locale.ROOT, "timeclock tickrate %.4f", tickrate);
        try {
            server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack().withPermission(4).withSuppressedOutput(),
                    command
            );
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
