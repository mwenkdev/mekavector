package dev.mwenk.mekavector.mixin;

import java.util.function.BooleanSupplier;
import dev.mwenk.mekavector.VectorThrust;
import mekanism.client.ClientTickHandler;
import mekanism.common.item.interfaces.IJetpackItem.JetpackMode;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Client-side call site of {@code IJetpackItem.handleJetpackMotion}
 * ({@code ClientTickHandler#tickStart}). Must apply the same look-vector motion
 * as the server mixin so the local prediction matches and there is no
 * rubber-banding. {@code remap = false} — Mekanism target, unobfuscated names.
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
}
