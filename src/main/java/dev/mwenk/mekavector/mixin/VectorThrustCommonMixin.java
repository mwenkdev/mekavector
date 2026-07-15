package dev.mwenk.mekavector.mixin;

import java.util.function.BooleanSupplier;
import dev.mwenk.mekavector.VectorThrust;
import mekanism.common.CommonPlayerTickHandler;
import mekanism.common.item.interfaces.IJetpackItem.JetpackMode;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Server-side call site of {@code IJetpackItem.handleJetpackMotion}
 * ({@code CommonPlayerTickHandler#tickEnd}, which runs on the logical server).
 * Redirecting the invoke lets us substitute vector thrust while preserving stock
 * behavior when vector is off. {@code remap = false} because the target is a
 * Mekanism method whose name is not obfuscated.
 */
@Mixin(value = CommonPlayerTickHandler.class, remap = false)
public class VectorThrustCommonMixin {

    @Redirect(
            method = "tickEnd(Lnet/minecraft/world/entity/player/Player;)V",
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
