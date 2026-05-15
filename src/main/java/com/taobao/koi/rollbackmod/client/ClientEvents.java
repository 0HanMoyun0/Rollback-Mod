package com.taobao.koi.rollbackmod.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.taobao.koi.rollbackmod.config.RollbackConfig;
import com.taobao.koi.rollbackmod.effect.ModMobEffects;
import com.taobao.koi.rollbackmod.network.ChronosKeyPacket;
import com.taobao.koi.rollbackmod.network.ModNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

public final class ClientEvents {
    private static final KeyMapping CHRONOS_SLOW_KEY = new KeyMapping(
            "key.rollbackmod.chronos_slow",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.rollbackmod"
    );

    private static boolean sentActive;
    private static boolean toggledActive;

    private ClientEvents() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(ClientEvents::onRegisterKeyMappings);
        MinecraftForge.EVENT_BUS.register(ClientEvents.class);
    }

    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(CHRONOS_SLOW_KEY);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        boolean hasChronos = minecraft.player.hasEffect(ModMobEffects.CHRONOS.get());
        if (!hasChronos) {
            toggledActive = false;
            sendIfChanged(false);
            return;
        }

        if (RollbackConfig.isChronosToggleMode()) {
            while (CHRONOS_SLOW_KEY.consumeClick()) {
                toggledActive = !toggledActive;
                sendIfChanged(toggledActive);
            }
        } else {
            sendIfChanged(CHRONOS_SLOW_KEY.isDown());
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        ClientDayHud.render(event);
    }

    private static void sendIfChanged(boolean active) {
        if (sentActive == active) {
            return;
        }
        sentActive = active;
        ModNetworking.CHANNEL.sendToServer(new ChronosKeyPacket(active));
    }
}
