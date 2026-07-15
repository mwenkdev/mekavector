package dev.mwenk.mekavector.net;

import dev.mwenk.mekavector.MekaVector;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * SimpleChannel registration. A single client→server packet ({@link VectorKeyPacket})
 * carries the Vector-Thrust key state so the server applies the same look-vector
 * motion as the client and there is no rubber-banding.
 *
 * <p>Uses the classic {@code NetworkRegistry.ChannelBuilder} API that Mekanism
 * 10.4.x itself uses on this Forge build, so it is guaranteed available.
 */
public final class Network {

    private static final String PROTOCOL_VERSION = "1";

    private static SimpleChannel CHANNEL;

    private Network() {
    }

    public static void register() {
        CHANNEL = NetworkRegistry.ChannelBuilder
                .named(new ResourceLocation(MekaVector.MOD_ID, "main"))
                // Vector thrust degrades gracefully to stock jetpack behavior, so a
                // vanilla/mismatched peer is acceptable rather than a connection error.
                .clientAcceptedVersions(v -> true)
                .serverAcceptedVersions(v -> true)
                .networkProtocolVersion(() -> PROTOCOL_VERSION)
                .simpleChannel();

        CHANNEL.registerMessage(0, VectorKeyPacket.class,
                VectorKeyPacket::encode,
                VectorKeyPacket::decode,
                VectorKeyPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    /** Client-side: notify the server that our Vector-Thrust key state changed. */
    public static void sendVectorKey(boolean active) {
        if (CHANNEL != null) {
            CHANNEL.sendToServer(new VectorKeyPacket(active));
        }
    }
}
