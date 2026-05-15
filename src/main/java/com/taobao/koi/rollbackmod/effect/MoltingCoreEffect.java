package com.taobao.koi.rollbackmod.effect;

import com.taobao.koi.rollbackmod.core.CoreType;
import com.taobao.koi.rollbackmod.core.CoreUseResult;
import com.taobao.koi.rollbackmod.core.ICoreEffect;
import com.taobao.koi.rollbackmod.rollback.RollbackManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public class MoltingCoreEffect implements ICoreEffect {
    @Override
    public CoreType type() {
        return CoreType.MOLTING;
    }

    @Override
    public CoreUseResult onUseInhaler(ServerPlayer player, ItemStack inhaler, ItemStack coreStack) {
        boolean rolledBack = RollbackManager.rollback(player.getServer(), "molting_core");
        return rolledBack
                ? CoreUseResult.success(true, Component.translatable("message.rollbackmod.rolled_back"))
                : CoreUseResult.fail(Component.translatable("message.rollbackmod.no_checkpoint"));
    }
}
