package com.taobao.koi.rollbackmod.item;

import com.taobao.koi.rollbackmod.RollbackMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, RollbackMod.MOD_ID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.rollbackmod"))
            .icon(() -> new ItemStack(ModItems.INHALER.get()))
            .displayItems((parameters, output) -> {
                output.accept(ModItems.INHALER.get());
                output.accept(ModItems.DISCONNECTION_CORE.get());
                output.accept(ModItems.CHRONOS_CORE.get());
                output.accept(ModItems.MOLTING_CORE.get());
                output.accept(ModItems.TOWER_CORE.get());
                output.accept(ModItems.SAND_CORE.get());
                output.accept(ModItems.CAUSALITY_CORE.get());
                output.accept(ModItems.MYRIAD_CORE.get());
                output.accept(ModItems.STASIS_CORE.get());
            })
            .build());

    private ModCreativeTabs() {
    }

    public static void register(IEventBus eventBus) {
        TABS.register(eventBus);
    }
}
