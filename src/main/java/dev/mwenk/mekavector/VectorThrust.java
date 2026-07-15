package dev.mwenk.mekavector;

import java.util.function.BooleanSupplier;
import mekanism.common.item.interfaces.IJetpackItem;
import mekanism.common.item.interfaces.IJetpackItem.JetpackMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * §3.2 — Jetpack motion. Called in place of Mekanism's stock
 * {@link IJetpackItem#handleJetpackMotion} (via a {@code @Redirect} on both the
 * client and server call sites) so client and server compute identical motion and
 * there is no rubber-banding.
 *
 * <p>Replaces Mekanism 10.4.x's stock jetpack motion with the math from Mekanism
 * 1.21.x, which is what this mod exists to backport:
 * <ul>
 *   <li>NORMAL: velocity-proportional horizontal boost + self-limiting vertical.
 *   <li>VECTOR (our V key): thrust along the player's up-axis (out of the back).
 *   <li>HOVER + fall-flying: delegate to stock 10.4.x, untouched.
 * </ul>
 */
public final class VectorThrust {

    private VectorThrust() {
    }

    /**
     * @return whether fall distance should be reset (mirrors the stock method's
     * contract, so the caller's {@code resetFallDistance()} branch still fires).
     */
    public static boolean handle(Player player, JetpackMode mode, BooleanSupplier ascendingSupplier) {
        // Hover stays fully stock (1.21's hover has no horizontal term either, and
        // 10.4.x's vertical hover logic is equivalent). Fall-flying keeps Mekanism's
        // elytra boost rather than freezing another branch.
        if (mode != JetpackMode.NORMAL || player.isFallFlying()) {
            return IJetpackItem.handleJetpackMotion(player, mode, ascendingSupplier);
        }

        double thrust = Config.jetpackThrust();
        Vec3 motion = player.getDeltaMovement();

        if (JetpackState.isVectorActive(player) && !ascendingSupplier.getAsBoolean()) {
            // 1.21 VECTOR. getUpVector = head's up axis: level -> straight up,
            // look down -> forward, look up -> brake. Space (ascending) wins and
            // falls through to the NORMAL branch below.
            Vec3 t = player.getUpVector(1.0F).scale(thrust);
            player.addDeltaMovement(new Vec3(t.x(), t.y() * verticalCoefficient(motion.y()), t.z()));
        } else {
            // 1.21 NORMAL. Horizontal boost is a fraction of CURRENT velocity, so it
            // amplifies what you're already doing instead of injecting raw input.
            double f = Config.forwardBoostFactor();
            player.addDeltaMovement(new Vec3(
                    f * motion.x(),
                    thrust * verticalCoefficient(motion.y()),
                    f * motion.z()));
        }
        return true;   // load-bearing: resets fall distance AND aboveGroundTickCount
    }

    /**
     * 1.21's soft vertical limiter. Full thrust while falling or level; decays
     * exponentially as upward speed builds, so it settles instead of clamping.
     * At thrust=0.15 the climb settles around 0.5-0.6/tick, where the decayed
     * thrust balances gravity and air drag.
     */
    private static double verticalCoefficient(double currentYVelocity) {
        return Math.min(1.0, Math.exp(-currentYVelocity));
    }
}
