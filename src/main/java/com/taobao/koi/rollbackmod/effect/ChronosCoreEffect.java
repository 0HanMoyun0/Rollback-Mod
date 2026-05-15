package com.taobao.koi.rollbackmod.effect;

import com.taobao.koi.rollbackmod.compat.TimeClockCompat;
import com.taobao.koi.rollbackmod.config.RollbackConfig;
import com.taobao.koi.rollbackmod.core.CoreType;
import com.taobao.koi.rollbackmod.core.CoreUseResult;
import com.taobao.koi.rollbackmod.core.ICoreEffect;
import com.taobao.koi.rollbackmod.rollback.RollbackManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;

public class ChronosCoreEffect implements ICoreEffect {
    @Override
    public CoreType type() {
        return CoreType.CHRONOS;
    }

    @Override
    public CoreUseResult onUseInhaler(ServerPlayer player, ItemStack inhaler, ItemStack coreStack) {
        RollbackManager.createCheckpoint(player.getServer(), "chronos_core");
        int duration = RollbackConfig.CHRONOS_DURATION_TICKS.get();
        player.addEffect(new MobEffectInstance(ModMobEffects.CHRONOS.get(), duration, 0, false, true, true));

        if (RollbackConfig.REQUIRE_TIME_CLOCK_MOD.get() && !TimeClockCompat.isDependencyPresent()) {
            return CoreUseResult.success(true, Component.translatable("message.rollbackmod.chronos_no_time_clock"));
        }
        return CoreUseResult.success(true, Component.translatable("message.rollbackmod.checkpoint_created"));
    }
}
