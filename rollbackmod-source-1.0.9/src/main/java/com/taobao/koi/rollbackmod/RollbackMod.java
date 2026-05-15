package com.taobao.koi.rollbackmod;

import com.taobao.koi.rollbackmod.config.RollbackConfig;
import com.taobao.koi.rollbackmod.core.CoreEffectRegistry;
import com.taobao.koi.rollbackmod.effect.ModMobEffects;
import com.taobao.koi.rollbackmod.event.CommonEvents;
import com.taobao.koi.rollbackmod.item.ModCreativeTabs;
import com.taobao.koi.rollbackmod.item.ModItems;
import com.taobao.koi.rollbackmod.network.ModNetworking;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.eventbus.api.IEventBus;

@Mod(RollbackMod.MOD_ID)
public class RollbackMod {
    public static final String MOD_ID = "rollbackmod";

    public RollbackMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModItems.register(modBus);
        ModMobEffects.register(modBus);
        ModCreativeTabs.register(modBus);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, RollbackConfig.SPEC);
        modBus.addListener(this::commonSetup);

        CoreEffectRegistry.bootstrap();
        MinecraftForge.EVENT_BUS.register(CommonEvents.class);
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> ClientBootstrap::register);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ModNetworking::register);
    }

    private static final class ClientBootstrap {
        private static void register() {
            com.taobao.koi.rollbackmod.client.ClientEvents.register(FMLJavaModLoadingContext.get().getModEventBus());
        }
    }
}
