# PLAN addendum 2 — Port 1.21's actual motion math; delete HorizontalBoostMixin

Supersedes §3.1 and §3.2 of PLAN.md and the motion math in addendum 1. The
keybind, netcode, and the three `@Redirect`s from addendum 1 all stay exactly as
they are — this only changes what `VectorThrust.handle` computes, and deletes the
horizontal-boost mixin.

## Why

In-game testing found two things, and reading Mekanism 1.21.x's source explains both:

1. **Hover was cheetah-fast.** 1.21's HOVER branch has **no horizontal term at
   all** — only NORMAL and VECTOR boost. Our `HorizontalBoostMixin` gated on
   `getPlayerJetpackMode != DISABLED`, and HOVER returns non-DISABLED the whole
   time you're airborne, so the boost applied every tick with nothing to
   interrupt it → terminal ≈ 1 block/tick ≈ 20 m/s, sustained. NORMAL felt right
   only because you're always climbing/falling and never sit at terminal.
2. **V was ~18x sprint.** The blend overwrote Y (capped at `thrust`) while X/Z
   accumulated to ~10x `thrust` → ≈ 5 blocks/tick ≈ 100 m/s.

1.21 fixes both structurally: every branch uses `addDeltaMovement` on all three
axes, the vertical self-limits via an exponential coefficient instead of a hard
clamp, and the horizontal boost is **velocity-proportional** and lives *inside*
`handleJetpackMotion` — which both call sites already invoke on both logical
sides. So porting it makes `HorizontalBoostMixin` redundant.

## Reference: Mekanism 1.21.x `IJetpackItem.handleJetpackMotion`

```java
static <PLAYER extends Player> boolean handleJetpackMotion(PLAYER player, JetpackMode mode, double thrust, Predicate<PLAYER> ascendingCheck) {
    Vec3 motion = player.getDeltaMovement();
    if (mode == JetpackMode.VECTOR && player.isShiftKeyDown()) {
        mode = JetpackMode.NORMAL;
    }
    if ((mode == JetpackMode.NORMAL || mode == JetpackMode.VECTOR) && player.isFallFlying()) {
        Vec3 forward = player.getLookAngle();
        Vec3 drag = forward.scale(1.5).subtract(motion).scale(0.5);
        player.addDeltaMovement(forward.scale(thrust).add(drag));
        return false;
    } else if (mode == JetpackMode.NORMAL) {
        player.addDeltaMovement(new Vec3(0.08 * motion.x, thrust * getVerticalCoefficient(motion.y()), 0.08 * motion.z));
    } else if (mode == JetpackMode.VECTOR) {
        Vec3 thrustVec = player.getUpVector(1F).scale(thrust);
        player.addDeltaMovement(new Vec3(thrustVec.x, thrustVec.y * getVerticalCoefficient(motion.y()), thrustVec.z));
    } else if (mode == JetpackMode.HOVER) {
        // vertical only — NO horizontal term
    }
    return true;
}

private static double getVerticalCoefficient(double currentYVelocity) {
    return Math.min(1, Math.exp(-currentYVelocity));
}
```

`ItemJetpack.getJetpackThrust(stack)` returns **0.15**.

**`getUpVector` is the player's head up-axis, not the look direction.** Look
level → thrust straight up. Look **down** → accelerate forward. Look **up** →
brake/reverse. Thrust comes out of your back, like a real jetpack. This is the
chosen behavior (option a: true 1.21 parity). It is *not* point-and-go.

## Changes

### 1. DELETE `mixin/HorizontalBoostMixin.java`

Remove `"HorizontalBoostMixin"` from the `mixins` array in
`mekavector.mixins.json`. No vanilla-targeting mixins remain; the generated
refmap will be empty, which is correct and harmless (the Mekanism mixins are all
`remap = false`). Leave the mixin/refmap wiring in `build.gradle` alone.

### 2. DELETE `JetpackState.isJetpackActive(...)`

Only `HorizontalBoostMixin` used it. This also removes the mod's last dependency
on `Mekanism.keyMap` / `KeySync`, so drop those imports. Keep
`LOCAL_VECTOR_ACTIVE`, `isVectorActive`, and `isVectorEngaged` — addendum 1's
redirects still need all three.

### 3. Rewrite `VectorThrust.handle`

```java
/**
 * Replaces Mekanism 10.4.x's stock jetpack motion with the math from Mekanism
 * 1.21.x, which is what this mod exists to backport:
 *   - NORMAL: velocity-proportional horizontal boost + self-limiting vertical.
 *   - VECTOR (our V key): thrust along the player's up-axis (out of the back).
 *   - HOVER + fall-flying: delegate to stock 10.4.x, untouched.
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
 * Equilibrium against gravity (~0.08/tick) at thrust=0.15 is y ~= 0.63/tick.
 */
private static double verticalCoefficient(double currentYVelocity) {
    return Math.min(1.0, Math.exp(-currentYVelocity));
}
```

Note: `VectorThrust` is a plain (non-mixin) class, so `getUpVector`/`getLookAngle`
are reobfuscated normally by `reobfJar` — no `remap` concerns, same as the
existing `getLookAngle()` call.

### 4. Rewrite the config (`Config.java`)

**Remove** `forward_speed_boost` and `strafe_speed_boost` entirely — the boost is
now velocity-proportional and lives in the NORMAL branch. **Rename**
`vector_thrust` → `jetpack_thrust`, since it now governs NORMAL's vertical too.

```
[jetpack]
jetpack_thrust        (double, default 0.15, range 0.0..1.0)
  # Jetpack thrust per tick. 0.15 matches Mekanism 1.21's ItemJetpack.getJetpackThrust().
  # Governs both NORMAL vertical climb and VECTOR thrust.
forward_boost_factor  (double, default 0.08, range 0.0..0.5)
  # Fraction of current horizontal velocity added per tick while thrusting in
  # NORMAL mode. 0.08 matches Mekanism 1.21. 0.0 = no forward boost.
vector_keybind_toggle (bool,  default false)
  # false = hold V to vector; true = press to toggle.
```

Accessors: `jetpackThrust()`, `forwardBoostFactor()`, `vectorKeybindToggle()`.
Existing `config/mekavector-common.toml` files will be corrected to the new schema
on next launch — expected, no migration needed.

### 5. Fix the `ClientSetup` disconnect NPE (still outstanding from review)

If vector is active when you leave a world, the next tick hits `mc.player == null`
→ `setActive(false)` → `Network.sendVectorKey(false)` →
`SimpleChannel.sendToServer` derefs a null `Minecraft.getInstance().getConnection()`.
Also resets `toggled`, which currently survives world changes.

```java
if (mc.player == null) {          // left the world — reset locally, don't send
    JetpackState.LOCAL_VECTOR_ACTIVE = false;
    toggled = false;
    lastSent = false;
    return;
}
if (mc.screen != null) {          // still connected — safe to send
    setActive(false);
    return;
}
```

### 6. Update README

The controls table is unchanged (Space wins, hover stock, no sneak-to-pillar), but:
- Vector is **look-down-to-fly-forward** (`getUpVector`), not point-and-go.
- The forward boost applies in **NORMAL only**, not hover.
- `HorizontalBoostMixin` and the vanilla `LivingEntity` injection are gone; the
  mod is now purely redirects on two Mekanism methods, identical on both sides.

## Expected numbers after this change

| | before | after |
|---|---|---|
| Hover horizontal | ~1 block/tick (~20 m/s) sustained | **stock — no boost** |
| V | ~5 blocks/tick (~100 m/s) | ~0.15 thrust, self-limiting |
| Normal climb | clamped 0.5/tick | settles ~0.63/tick |
| Normal horizontal | 0.1/tick input-based | converges ~1.06 blocks/tick (~21 m/s) |

Normal should feel about the same as it does now — Mike confirmed it already
feels right, and `0.08 * motion` converges to roughly where `2.0/20` landed.

## Acceptance criteria (replaces 6a–6e)

6a. Normal + Space → climb settles smoothly (no hard clamp); horizontal builds to
    ~3.5x sprint and holds.
6b. Normal + V, looking **level** → thrust straight up.
6c. Normal + V, looking **down** → accelerate forward. Looking **up** → brake /
    reverse. (This is `getUpVector`; verify it feels like 1.21, not point-and-go.)
6d. Normal + Space + V → identical to Space alone.
6e. **Hover → no horizontal boost at all.** Airborne hover should drift at plain
    walk/sprint speed. This is the regression test for the bug found in testing.
6f. Walk on the ground with a jetpack equipped, no keys → no boost (now trivially
    true: the only boost path is inside `handleJetpackMotion`, which isn't called).
6g. Multiplayer on cozy-chaos → no rubber-banding. Should now be strictly safer:
    all motion is computed identically on both sides, and there is no client-only
    horizontal term left.
