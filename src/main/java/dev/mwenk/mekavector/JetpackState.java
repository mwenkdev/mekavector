package dev.mwenk.mekavector;

import dev.mwenk.mekavector.net.ServerVectorState;
import java.util.function.BooleanSupplier;
import mekanism.common.item.interfaces.IJetpackItem.JetpackMode;
import net.minecraft.world.entity.player.Player;

/**
 * Shared, side-agnostic helper for querying jetpack/vector state. Safe to load on
 * a dedicated server: the only client-specific piece is a plain {@code boolean}
 * ({@link #LOCAL_VECTOR_ACTIVE}) written by the client tick listener, never a
 * client-only class reference.
 */
public final class JetpackState {

    /**
     * The local player's Vector-Thrust input state on the client. Updated every
     * client tick by {@code ClientSetup}; mirrored to the server via a packet.
     */
    public static volatile boolean LOCAL_VECTOR_ACTIVE = false;

    private JetpackState() {
    }

    /**
     * @return whether vector thrust should currently apply for this player.
     * Client side reads the local input flag; server side reads the synced flag.
     */
    public static boolean isVectorActive(Player player) {
        if (player.level().isClientSide) {
            return LOCAL_VECTOR_ACTIVE;
        }
        return ServerVectorState.isActive(player.getUUID());
    }

    /**
     * @return true when this player should be vectoring: the Vector key is engaged,
     * the jetpack is in NORMAL mode, and Space (ascend) is NOT held (Space wins).
     * Used to force Mekanism's DISABLED gate to NORMAL so the jetpack block runs
     * even without Space, letting look-vector thrust, fuel drain, and flames fire.
     */
    public static boolean isVectorEngaged(Player player, JetpackMode primaryMode, BooleanSupplier ascending) {
        return primaryMode == JetpackMode.NORMAL
                && !player.isSpectator()
                && isVectorActive(player)
                && !ascending.getAsBoolean();
    }
}
