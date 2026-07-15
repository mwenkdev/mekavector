package dev.mwenk.mekavector.mixin;

import java.util.function.BooleanSupplier;
import dev.mwenk.mekavector.JetpackState;
import dev.mwenk.mekavector.VectorThrust;
import mekanism.common.CommonPlayerTickHandler;
import mekanism.common.item.interfaces.IJetpackItem;
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

    /**
     * Mekanism gates the whole jetpack block on {@code getPlayerJetpackMode}, which
     * returns DISABLED for NORMAL unless Space is held — so V-only would never reach
     * {@code handleJetpackMotion}. Force DISABLED → NORMAL when vector is engaged so
     * the block runs (thrust + fuel drain). Never override a mode Mekanism resolved.
     */
    @Redirect(
            method = "tickEnd(Lnet/minecraft/world/entity/player/Player;)V",
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
}
