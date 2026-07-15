package dev.mwenk.mekavector.net;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client → server: the local player's Vector-Thrust key state changed. Sent only
 * on change (debounced client-side), so this is very low traffic.
 */
public record VectorKeyPacket(boolean active) {

    public static void encode(VectorKeyPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.active);
    }

    public static VectorKeyPacket decode(FriendlyByteBuf buf) {
        return new VectorKeyPacket(buf.readBoolean());
    }

    public static void handle(VectorKeyPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender != null) {
                ServerVectorState.set(sender.getUUID(), msg.active);
            }
        });
        context.setPacketHandled(true);
    }
}
