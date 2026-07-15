package dev.mwenk.mekavector.mixin;

import dev.mwenk.mekavector.Config;
import dev.mwenk.mekavector.JetpackState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * §3.1 — Horizontal forward boost. Pure-vanilla injection site (after
 * {@code travel()} in {@code aiStep}); only reads Mekanism statics to know a
 * jetpack is active. Driven by the player's movement input ({@code zza}/{@code xxa}),
 * which is valid wherever the entity is locally controlled — so no packet needed.
 */
@Mixin(LivingEntity.class)
public abstract class HorizontalBoostMixin {

    @Shadow
    public float zza;

    @Shadow
    public float xxa;

    @Inject(
            method = "aiStep",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;travel(Lnet/minecraft/world/phys/Vec3;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void mekavector$forwardBoost(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof Player player)) {
            return;
        }

        double forwardBoost = Config.forwardSpeedBoost();
        double strafeBoost = Config.strafeSpeedBoost();
        if (forwardBoost == 0.0D && strafeBoost == 0.0D) {
            return;
        }

        // Only where movement input is authoritative for this instance, and only
        // in a state where the jetpack is actually pushing us through the air.
        if (!(player.isEffectiveAi() || player.isControlledByLocalInstance())) {
            return;
        }
        if (player.isFallFlying() || player.isInWater() || player.isInLava() || player.isPassenger()) {
            return;
        }
        if (!JetpackState.isJetpackActive(player)) {
            return;
        }

        double forward = this.zza * forwardBoost;
        double strafe = this.xxa * strafeBoost;
        if (forward == 0.0D && strafe == 0.0D) {
            return;
        }

        // Rotate (strafe, forward) by yaw into world X/Z (same basis as
        // Entity#getInputVector), leaving Y untouched for Mekanism's vertical thrust.
        float yawRad = player.getYRot() * ((float) Math.PI / 180F);
        float sin = Mth.sin(yawRad);
        float cos = Mth.cos(yawRad);
        double dx = strafe * cos - forward * sin;
        double dz = forward * cos + strafe * sin;

        player.setDeltaMovement(player.getDeltaMovement().add(new Vec3(dx, 0.0D, dz)));
    }
}
