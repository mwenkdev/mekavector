package dev.mwenk.mekavector;

import dev.mwenk.mekavector.net.ServerVectorState;
import mekanism.common.item.interfaces.IJetpackItem;
import mekanism.common.item.interfaces.IJetpackItem.JetpackMode;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

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
     * @return true if the entity is wearing a usable jetpack whose selected mode
     * is not DISABLED. This is the gate for the horizontal forward boost.
     */
    public static boolean isJetpackActive(LivingEntity entity) {
        ItemStack active = IJetpackItem.getActiveJetpack(entity);
        if (active.isEmpty()) {
            return false;
        }
        ItemStack primary = IJetpackItem.getPrimaryJetpack(entity);
        if (primary.isEmpty() || !(primary.getItem() instanceof IJetpackItem jetpack)) {
            return false;
        }
        return jetpack.getJetpackMode(primary) != JetpackMode.DISABLED;
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
}
