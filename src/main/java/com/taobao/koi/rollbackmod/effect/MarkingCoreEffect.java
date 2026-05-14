package com.taobao.koi.rollbackmod.effect;

import com.taobao.koi.rollbackmod.core.CoreType;
import com.taobao.koi.rollbackmod.core.CoreUseResult;
import com.taobao.koi.rollbackmod.core.ICoreEffect;
import com.taobao.koi.rollbackmod.rollback.MarkManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public class MarkingCoreEffect implements ICoreEffect {
    private final CoreType type;

    public MarkingCoreEffect(CoreType type) {
        if (!type.isMarkingCore()) {
            throw new IllegalArgumentException("Core type is not a marking core: " + type);
        }
        this.type = type;
    }

    @Override
    public CoreType type() {
        return type;
    }

    @Override
    public CoreUseResult onUseInhaler(ServerPlayer player, ItemStack inhaler, ItemStack coreStack) {
        return CoreUseResult.fail(Component.translatable("message.rollbackmod.mark_requires_target"));
    }

    @Override
    public CoreUseResult onMarkEntity(ServerPlayer player, LivingEntity target, ItemStack inhaler, ItemStack coreStack) {
        return MarkManager.mark(player, target, type);
    }
}
