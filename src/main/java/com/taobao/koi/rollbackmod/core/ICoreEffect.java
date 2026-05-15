package com.taobao.koi.rollbackmod.core;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public interface ICoreEffect {
    CoreType type();

    CoreUseResult onUseInhaler(ServerPlayer player, ItemStack inhaler, ItemStack coreStack);

    default CoreUseResult onMarkEntity(ServerPlayer player, LivingEntity target, ItemStack inhaler, ItemStack coreStack) {
        return CoreUseResult.fail(net.minecraft.network.chat.Component.translatable("message.rollbackmod.core_cannot_mark"));
    }
}
