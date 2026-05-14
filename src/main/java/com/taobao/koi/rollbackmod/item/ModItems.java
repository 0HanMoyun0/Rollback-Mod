package com.taobao.koi.rollbackmod.item;

import com.taobao.koi.rollbackmod.RollbackMod;
import com.taobao.koi.rollbackmod.core.CoreType;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, RollbackMod.MOD_ID);

    public static final RegistryObject<Item> INHALER = ITEMS.register("inhaler",
            () -> new InhalerItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    public static final RegistryObject<Item> DISCONNECTION_CORE = registerCore(CoreType.DISCONNECTION);
    public static final RegistryObject<Item> CHRONOS_CORE = registerCore(CoreType.CHRONOS);
    public static final RegistryObject<Item> MOLTING_CORE = registerCore(CoreType.MOLTING);
    public static final RegistryObject<Item> TOWER_CORE = registerCore(CoreType.TOWER);
    public static final RegistryObject<Item> SAND_CORE = registerCore(CoreType.SAND);
    public static final RegistryObject<Item> CAUSALITY_CORE = registerCore(CoreType.CAUSALITY);
    public static final RegistryObject<Item> MYRIAD_CORE = registerCore(CoreType.MYRIAD);
    public static final RegistryObject<Item> STASIS_CORE = registerCore(CoreType.STASIS);

    private ModItems() {
    }

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }

    public static Optional<CoreType> getCoreType(ItemStack stack) {
        if (stack.getItem() instanceof CoreItem coreItem) {
            return Optional.of(coreItem.getCoreType());
        }
        return Optional.empty();
    }

    public static boolean isCore(ItemStack stack) {
        return getCoreType(stack).isPresent();
    }

    public static boolean isRollbackModItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null && RollbackMod.MOD_ID.equals(id.getNamespace());
    }

    private static RegistryObject<Item> registerCore(CoreType type) {
        return ITEMS.register(type.registryName(),
                () -> new CoreItem(type, new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    }
}
