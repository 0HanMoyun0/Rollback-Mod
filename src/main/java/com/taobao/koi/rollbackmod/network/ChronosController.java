package com.taobao.koi.rollbackmod.network;

import com.taobao.koi.rollbackmod.compat.TimeClockCompat;
import com.taobao.koi.rollbackmod.config.RollbackConfig;
import com.taobao.koi.rollbackmod.effect.ModMobEffects;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class ChronosController {
    private ChronosController() {
    }

    public static void handleKeyState(ServerPlayer player, boolean active) {
        if (!player.hasEffect(ModMobEffects.CHRONOS.get())) {
            TimeClockCompat.resetPlayer(player);
            return;
        }
        if (!active) {
            TimeClockCompat.resetPlayer(player);
            return;
        }

        if (RollbackConfig.REQUIRE_TIME_CLOCK_MOD.get() && !TimeClockCompat.isDependencyPresent()) {
            player.displayClientMessage(Component.translatable("message.rollbackmod.chronos_no_time_clock"), true);
            return;
        }

        boolean applied = TimeClockCompat.setSlowTime(
                player.getServer(),
                player,
                true,
                RollbackConfig.CHRONOS_SLOW_TIME_FACTOR.get()
        );
        if (!applied) {
            player.displayClientMessage(Component.translatable("message.rollbackmod.chronos_integration_missing"), true);
        }
    }
}
