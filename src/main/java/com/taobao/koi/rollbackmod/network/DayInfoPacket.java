package com.taobao.koi.rollbackmod.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

public record DayInfoPacket(boolean showHud, boolean countdownMode, int day, int remainingDays, boolean playAnimation) {
    public static void encode(DayInfoPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.showHud);
        buffer.writeBoolean(packet.countdownMode);
        buffer.writeVarInt(packet.day);
        buffer.writeVarInt(packet.remainingDays);
        buffer.writeBoolean(packet.playAnimation);
    }

    public static DayInfoPacket decode(FriendlyByteBuf buffer) {
        return new DayInfoPacket(
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readBoolean()
        );
    }

    public static void handle(DayInfoPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            com.taobao.koi.rollbackmod.client.ClientDayHud.update(packet);
        }));
        context.setPacketHandled(true);
    }
}
