package com.taobao.koi.rollbackmod.rollback;

import com.taobao.koi.rollbackmod.config.RollbackConfig;
import com.taobao.koi.rollbackmod.network.DayInfoPacket;
import com.taobao.koi.rollbackmod.network.ModNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

public final class DayCounterManager {
    private static final long TICKS_PER_DAY = 24000L;

    private DayCounterManager() {
    }

    public static long currentDay(MinecraftServer server) {
        return Math.max(1L, Math.floorDiv(server.overworld().getDayTime(), TICKS_PER_DAY) + 1L);
    }

    public static void ensureInitialized(MinecraftServer server, RollbackSavedData data) {
        if (data.getCountdownStartDay() <= 0L) {
            data.setCountdownStartDay(currentDay(server));
        }
        if (data.getLastAnnouncedDay() <= 0L) {
            data.setLastAnnouncedDay(currentDay(server));
        }
    }

    public static void onCheckpointCreated(MinecraftServer server, RollbackSavedData data) {
        long day = currentDay(server);
        data.setCountdownStartDay(day);
        data.setLastAnnouncedDay(day);
        data.setCountdownExpired(false);
        syncToAll(server);
    }

    public static void onRollbackFinished(MinecraftServer server, RollbackSavedData data) {
        data.setLastAnnouncedDay(currentDay(server));
        data.setCountdownExpired(false);
        syncToAll(server, true);
    }

    public static void tick(MinecraftServer server) {
        RollbackSavedData data = RollbackSavedData.get(server);
        ensureInitialized(server, data);

        if (server.overworld().getGameTime() % 20L == 0L) {
            long day = currentDay(server);
            if (day > data.getLastAnnouncedDay()) {
                data.setLastAnnouncedDay(day);
                syncToAll(server, true);
            } else {
                syncToAll(server);
            }
            checkCountdownExpiry(server, data);
        }
    }

    public static void syncTo(ServerPlayer player) {
        ModNetworking.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                buildPacket(player.getServer())
        );
    }

    public static void syncToAll(MinecraftServer server) {
        syncToAll(server, false);
    }

    public static void syncToAll(MinecraftServer server, boolean playAnimation) {
        DayInfoPacket packet = buildPacket(server, playAnimation);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ModNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
        }
    }

    private static DayInfoPacket buildPacket(MinecraftServer server) {
        return buildPacket(server, false);
    }

    private static DayInfoPacket buildPacket(MinecraftServer server, boolean playAnimation) {
        RollbackSavedData data = RollbackSavedData.get(server);
        ensureInitialized(server, data);
        long day = currentDay(server);
        int remainingDays = remainingDays(day, data);
        return new DayInfoPacket(
                RollbackConfig.SHOW_DAY_HUD.get(),
                RollbackConfig.COUNTDOWN_MODE.get(),
                safeInt(day),
                remainingDays,
                playAnimation && RollbackConfig.SHOW_DAY_TRANSITION.get()
        );
    }

    private static int remainingDays(long day, RollbackSavedData data) {
        long elapsed = Math.max(0L, day - data.getCountdownStartDay());
        long remaining = Math.max(0L, RollbackConfig.COUNTDOWN_DAYS.get() - elapsed);
        return safeInt(remaining);
    }

    private static void checkCountdownExpiry(MinecraftServer server, RollbackSavedData data) {
        if (!RollbackConfig.COUNTDOWN_MODE.get() || data.isCountdownExpired()) {
            return;
        }
        if (remainingDays(currentDay(server), data) > 0) {
            return;
        }
        if (server.getPlayerList().getPlayers().isEmpty()) {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.displayClientMessage(Component.translatable("message.rollbackmod.countdown_expired"), true);
        }
        data.setCountdownExpired(true);
        SelfDestructManager.destroyAll(server, SelfDestructManager.Cause.FATE_EXHAUSTED);
    }

    private static int safeInt(long value) {
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, value));
    }
}
