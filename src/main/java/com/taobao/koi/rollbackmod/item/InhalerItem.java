package com.taobao.koi.rollbackmod.item;

import com.taobao.koi.rollbackmod.core.CoreEffectRegistry;
import com.taobao.koi.rollbackmod.core.CoreType;
import com.taobao.koi.rollbackmod.core.CoreUseResult;
import com.taobao.koi.rollbackmod.core.ICoreEffect;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class InhalerItem extends Item {
    public InhalerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack inhaler = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResultHolder.pass(inhaler);
        }
        if (level.isClientSide()) {
            return InteractionResultHolder.sidedSuccess(inhaler, true);
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.pass(inhaler);
        }

        ItemStack coreStack = player.getOffhandItem();
        Optional<CoreType> coreType = ModItems.getCoreType(coreStack);
        if (coreType.isEmpty()) {
            serverPlayer.displayClientMessage(Component.translatable("message.rollbackmod.no_offhand_core"), true);
            return InteractionResultHolder.fail(inhaler);
        }

        ICoreEffect effect = CoreEffectRegistry.get(coreType.get());
        if (effect == null) {
            return InteractionResultHolder.fail(inhaler);
        }

        CoreUseResult result = effect.onUseInhaler(serverPlayer, inhaler, coreStack);
        finishCoreUse(serverPlayer, coreStack, result);
        return result.success() ? InteractionResultHolder.success(inhaler) : InteractionResultHolder.fail(inhaler);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (player.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }

        ItemStack coreStack = player.getOffhandItem();
        Optional<CoreType> coreType = ModItems.getCoreType(coreStack);
        if (coreType.isEmpty()) {
            serverPlayer.displayClientMessage(Component.translatable("message.rollbackmod.no_offhand_core"), true);
            return InteractionResult.FAIL;
        }

        ICoreEffect effect = CoreEffectRegistry.get(coreType.get());
        if (effect == null) {
            return InteractionResult.FAIL;
        }

        CoreUseResult result = effect.onMarkEntity(serverPlayer, target, stack, coreStack);
        finishCoreUse(serverPlayer, coreStack, result);
        return result.success() ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }

    private static void finishCoreUse(ServerPlayer player, ItemStack coreStack, CoreUseResult result) {
        if (result.message() != null) {
            player.displayClientMessage(result.message(), true);
        }
        if (result.success() && result.consumeCore() && !player.getAbilities().instabuild) {
            coreStack.shrink(1);
        }
    }
}
