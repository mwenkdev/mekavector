package dev.mwenk.mekavector;

import java.util.function.BooleanSupplier;
import mekanism.common.item.interfaces.IJetpackItem;
import mekanism.common.item.interfaces.IJetpackItem.JetpackMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * §3.2 — Vector thrust. Called in place of Mekanism's stock
 * {@link IJetpackItem#handleJetpackMotion} (via a {@code @Redirect} on both the
 * client and server call sites) so client and server compute identical motion and
 * there is no rubber-banding.
 *
 * <p>When vector is NOT engaged this simply delegates to the stock method, so
 * Normal/Hover behavior is untouched. Mekanism's vertical logic is never frozen.
 */
public final class VectorThrust {

    private VectorThrust() {
    }

    /**
     * @return whether fall distance should be reset (mirrors the stock method's
     * contract, so the caller's {@code resetFallDistance()} branch still fires).
     */
    public static boolean handle(Player player, JetpackMode mode, BooleanSupplier ascendingSupplier) {
        // Sneak-to-pillar: while vectoring, holding shift reverts to stock vertical
        // thrust (pillar straight up). Fall-flying keeps Mekanism's elytra boost.
        if (JetpackState.isVectorActive(player) && !player.isShiftKeyDown() && !player.isFallFlying()) {
            double thrust = Config.vectorThrust();
            Vec3 look = player.getLookAngle();
            Vec3 motion = player.getDeltaMovement();
            // Starting blend (tunable in-game): replace vertical with the look
            // vector's Y thrust, keep existing horizontal, add the look vector's
            // horizontal thrust.
            player.setDeltaMovement(
                    motion.x() + look.x() * thrust,
                    look.y() * thrust,
                    motion.z() + look.z() * thrust
            );
            return true;
        }
        return IJetpackItem.handleJetpackMotion(player, mode, ascendingSupplier);
    }
}
