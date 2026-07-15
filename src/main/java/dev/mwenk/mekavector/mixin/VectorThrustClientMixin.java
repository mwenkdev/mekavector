package dev.mwenk.mekavector.mixin;

import java.util.function.BooleanSupplier;
import dev.mwenk.mekavector.JetpackState;
import dev.mwenk.mekavector.VectorThrust;
import mekanism.client.ClientTickHandler;
import mekanism.common.item.interfaces.IJetpackItem;
import mekanism.common.item.interfaces.IJetpackItem.JetpackMode;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Client-side call site of {@code IJetpackItem.handleJetpackMotion}
 * ({@code ClientTickHandler#tickStart}). Must apply the same look-vector motion
 * as the server mixin so the local prediction matches and there is no
 * rubber-banding. {@code remap = false} — Mekanism target, unobfuscated names.
 *
 * <p>The client has two gates in front of the motion call: {@code getPlayerJetpackMode}
 * (shared with the server) and a second {@code isJetpackInUse} short-circuit. Both
 * are redirected here so V-only engages vector on the client.
 */
@Mixin(value = ClientTickHandler.class, remap = false)
public class VectorThrustClientMixin {

    @Redirect(
            method = "tickStart()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lmekanism/common/item/interfaces/IJetpackItem;handleJetpackMotion(Lnet/minecraft/world/entity/player/Player;Lmekanism/common/item/interfaces/IJetpackItem$JetpackMode;Ljava/util/function/BooleanSupplier;)Z"
            ),
            remap = false
    )
    private boolean mekavector$vectorThrust(Player player, JetpackMode mode, BooleanSupplier ascendingSupplier) {
        return VectorThrust.handle(player, mode, ascendingSupplier);
    }

    /**
     * Mirror of the server-side gate: force DISABLED → NORMAL when vector is engaged
     * so the jetpack block runs on V-only. See {@code VectorThrustCommonMixin}.
     */
    @Redirect(
            method = "tickStart()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lmekanism/common/item/interfaces/IJetpackItem;getPlayerJetpackMode(Lnet/minecraft/world/entity/player/Player;Lmekanism/common/item/interfaces/IJetpackItem$JetpackMode;Ljava/util/function/BooleanSupplier;)Lmekanism/common/item/interfaces/IJetpackItem$JetpackMode;"
            ),
            remap = false
    )
    private JetpackMode mekavector$modeForVector(Player player, JetpackMode primaryMode, BooleanSupplier ascending) {
        JetpackMode stock = IJetpackItem.getPlayerJetpackMode(player, primaryMode, ascending);
        if (stock == JetpackMode.DISABLED && JetpackState.isVectorEngaged(player, primaryMode, ascending)) {
            return JetpackMode.NORMAL;
        }
        return stock;
    }

    /**
     * Second client gate: {@code tickStart} does {@code if (jetpackInUse && handleJetpackMotion(...))},
     * and {@code isJetpackInUse} returns false for NORMAL unless jump is held, so Java
     * short-circuits and the motion call never happens. Report "in use" when vector is
     * engaged (V, NORMAL, Space not held) so it runs — this also makes flames/sound play.
     * Calling the stock static method here does not recurse: {@code @Redirect} only
     * rewrites the specific invoke inside {@code tickStart}, not this handler's own call.
     */
    @Redirect(
            method = "tickStart()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lmekanism/client/ClientTickHandler;isJetpackInUse(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)Z"
            ),
            remap = false
    )
    private boolean mekavector$inUseForVector(Player player, ItemStack jetpack) {
        boolean stock = ClientTickHandler.isJetpackInUse(player, jetpack);
        if (!stock && !jetpack.isEmpty() && !player.isSpectator()
                && JetpackState.LOCAL_VECTOR_ACTIVE
                && ((IJetpackItem) jetpack.getItem()).getJetpackMode(jetpack) == JetpackMode.NORMAL
                && !Minecraft.getInstance().player.input.jumping) {
            return true;
        }
        return stock;
    }
}
