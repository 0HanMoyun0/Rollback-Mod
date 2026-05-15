package com.taobao.koi.rollbackmod.client;

import com.taobao.koi.rollbackmod.network.DayInfoPacket;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.client.event.RenderGuiEvent;

public final class ClientDayHud {
    private static final long ANIMATION_DURATION_MS = 1800L;
    private static boolean showHud;
    private static boolean countdownMode;
    private static int day = 1;
    private static int remainingDays;
    private static long animationStartedAt = -1L;

    private ClientDayHud() {
    }

    public static void update(DayInfoPacket packet) {
        showHud = packet.showHud();
        countdownMode = packet.countdownMode();
        day = packet.day();
        remainingDays = packet.remainingDays();
        if (packet.playAnimation()) {
            animationStartedAt = Util.getMillis();
        }
    }

    public static void render(RenderGuiEvent.Post event) {
        if (animationStartedAt <= 0L) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) {
            return;
        }

        Component text = countdownMode
                ? Component.translatable("hud.rollbackmod.remaining_days", remainingDays)
                : Component.translatable("hud.rollbackmod.day", day);
        GuiGraphics guiGraphics = event.getGuiGraphics();

        if (animationStartedAt > 0L) {
            renderTimeAnimation(event, minecraft, text);
        }
    }

    private static void renderTimeAnimation(RenderGuiEvent.Post event, Minecraft minecraft, Component text) {
        long elapsed = Util.getMillis() - animationStartedAt;
        if (elapsed >= ANIMATION_DURATION_MS) {
            animationStartedAt = -1L;
            return;
        }

        float progress = elapsed / (float) ANIMATION_DURATION_MS;
        int width = event.getWindow().getGuiScaledWidth();
        int height = event.getWindow().getGuiScaledHeight();
        float fadeIn = Mth.clamp(progress / 0.18F, 0.0F, 1.0F);
        float fadeOut = Mth.clamp((1.0F - progress) / 0.25F, 0.0F, 1.0F);
        float visibility = Math.min(fadeIn, fadeOut);
        int alpha = Mth.clamp((int) (245.0F * visibility), 0, 245);
        event.getGuiGraphics().fill(0, 0, width, height, alpha << 24);

        int textWidth = minecraft.font.width(text);
        int color = (Mth.clamp((int) (255.0F * visibility), 0, 255) << 24) | 0xF2F2F2;
        event.getGuiGraphics().pose().pushPose();
        event.getGuiGraphics().pose().translate(width / 2.0F, height / 2.0F, 0.0F);
        event.getGuiGraphics().pose().scale(2.0F, 2.0F, 1.0F);
        event.getGuiGraphics().drawString(minecraft.font, text, -textWidth / 2, -minecraft.font.lineHeight / 2, color, true);
        event.getGuiGraphics().pose().popPose();
    }
}
