package com.taobao.koi.rollbackmod.effect;

import com.taobao.koi.rollbackmod.RollbackMod;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMobEffects {
    public static final DeferredRegister<MobEffect> MOB_EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, RollbackMod.MOD_ID);

    public static final RegistryObject<MobEffect> CHRONOS = MOB_EFFECTS.register("chronos", ChronosMobEffect::new);

    private ModMobEffects() {
    }

    public static void register(IEventBus eventBus) {
        MOB_EFFECTS.register(eventBus);
    }
}
