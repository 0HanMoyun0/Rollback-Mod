package com.taobao.koi.rollbackmod.effect;

import com.taobao.koi.rollbackmod.core.CoreType;
import com.taobao.koi.rollbackmod.core.CoreUseResult;
import com.taobao.koi.rollbackmod.core.ICoreEffect;
import com.taobao.koi.rollbackmod.rollback.RollbackManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public class DisconnectionCoreEffect implements ICoreEffect {
    @Override
    public CoreType type() {
        return CoreType.DISCONNECTION;
    }

    @Override
    public CoreUseResult onUseInhaler(ServerPlayer player, ItemStack inhaler, ItemStack coreStack) {
        RollbackManager.createCheckpoint(player.getServer(), "disconnection_core");
        return CoreUseResult.success(true, Component.translatable("message.rollbackmod.checkpoint_created"));
    }
}
