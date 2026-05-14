package com.taobao.koi.rollbackmod.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public record ChronosKeyPacket(boolean active) {
    public static void encode(ChronosKeyPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.active);
    }

    public static ChronosKeyPacket decode(FriendlyByteBuf buffer) {
        return new ChronosKeyPacket(buffer.readBoolean());
    }

    public static void handle(ChronosKeyPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender != null) {
                ChronosController.handleKeyState(sender, packet.active());
            }
        });
        context.setPacketHandled(true);
    }
}
