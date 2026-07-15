# PLAN addendum — Vector = Normal mode only, engaged by V alone

Supersedes §3.2 and the sneak-to-pillar item in PLAN.md. Also fixes two bugs found
in review of the current `main`.

## Final behavior spec

**Normal mode:**

| Input | Result |
|---|---|
| nothing | jetpack off, fall normally |
| Space | stock Mekanism normal thrust (straight up, capped) + horizontal forward boost |
| V | look-vector thrust; fuel drains; jetpack particles/sound play |
| Space + V | **Space wins** — stock normal thrust, no vector |

**Hover mode:** completely stock. V is ignored. (Horizontal forward boost still
applies in hover, as before — that is the "make it faster" fix and is unrelated
to vector.)

Sneak-to-pillar is **removed** from the spec. Space now serves that role, so the
`isShiftKeyDown()` check in `VectorThrust.handle` should be deleted. Shift does
nothing special while vectoring.

## Why two more redirects are needed

Mekanism gates the whole jetpack block on `IJetpackItem.getPlayerJetpackMode(...)`,
which returns `DISABLED` for `NORMAL` unless the ascend key is held:

```java
} else if (mode == JetpackMode.NORMAL && ascending) {
    return mode;
}
...
return JetpackMode.DISABLED;
```

Both call sites then do `if (mode != JetpackMode.DISABLED) { ... handleJetpackMotion(...) ... }`.
So with V-only (no Space), `handleJetpackMotion` is **never invoked** and the
existing `@Redirect` never fires. The client has a second gate: `tickStart` does
`if (jetpackInUse && IJetpackItem.handleJetpackMotion(...))`, and
`ClientTickHandler.isJetpackInUse` returns `rising` (jump held) for NORMAL —
Java short-circuits, so the call never happens there either.

Fix: redirect those two gates so they report "on" when V is engaged in NORMAL
mode. Both are ordinary call sites in the same two methods already being mixed
into — no new coupling surface, no `@Overwrite`.

**Bonus:** forcing `isJetpackInUse` true also makes
`Mekanism.playerState.setJetpackState(...)` report true, so jetpack flames/sound
play while vectoring. Desirable — keep it.

## Changes

### 1. `JetpackState` — add the vector-engaged gate

```java
/** True when this player should be vectoring: V engaged, NORMAL mode, Space NOT held. */
public static boolean isVectorEngaged(Player player, JetpackMode primaryMode, BooleanSupplier ascending) {
    return primaryMode == JetpackMode.NORMAL
            && !player.isSpectator()
            && isVectorActive(player)
            && !ascending.getAsBoolean();
}
```

### 2. `VectorThrustCommonMixin` + `VectorThrustClientMixin` — redirect `getPlayerJetpackMode`

Add to **both** mixins (same handler body; `tickEnd` / `tickStart` respectively):

```java
@Redirect(
    method = "tickEnd(Lnet/minecraft/world/entity/player/Player;)V",   // client: "tickStart()V"
    at = @At(value = "INVOKE",
        target = "Lmekanism/common/item/interfaces/IJetpackItem;getPlayerJetpackMode(Lnet/minecraft/world/entity/player/Player;Lmekanism/common/item/interfaces/IJetpackItem$JetpackMode;Ljava/util/function/BooleanSupplier;)Lmekanism/common/item/interfaces/IJetpackItem$JetpackMode;"),
    remap = false
)
private JetpackMode mekavector$modeForVector(Player player, JetpackMode primaryMode, BooleanSupplier ascending) {
    JetpackMode stock = IJetpackItem.getPlayerJetpackMode(player, primaryMode, ascending);
    // Only ever turn DISABLED -> NORMAL; never override a mode Mekanism already resolved.
    if (stock == JetpackMode.DISABLED && JetpackState.isVectorEngaged(player, primaryMode, ascending)) {
        return JetpackMode.NORMAL;
    }
    return stock;
}
```

This makes the jetpack block run (so `useJetpackFuel` also fires — correct, we
are thrusting).

### 3. `VectorThrustClientMixin` — redirect `isJetpackInUse` (client only)

```java
@Redirect(
    method = "tickStart()V",
    at = @At(value = "INVOKE",
        target = "Lmekanism/client/ClientTickHandler;isJetpackInUse(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)Z"),
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
```

Note: calling `ClientTickHandler.isJetpackInUse(...)` from the handler does **not**
recurse — `@Redirect` rewrites only the specific invoke inside `tickStart`, and
the handler's own call is a separate instruction. (Same reason the existing
`VectorThrust.handle` can safely call `IJetpackItem.handleJetpackMotion`.)

### 4. `VectorThrust.handle` — Normal only, Space overrides, no sneak check

```java
public static boolean handle(Player player, JetpackMode mode, BooleanSupplier ascendingSupplier) {
    if (mode == JetpackMode.NORMAL
            && !player.isFallFlying()
            && JetpackState.isVectorActive(player)
            && !ascendingSupplier.getAsBoolean()) {   // Space wins
        double thrust = Config.vectorThrust();
        Vec3 look = player.getLookAngle();
        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(
                motion.x() + look.x() * thrust,
                look.y() * thrust,
                motion.z() + look.z() * thrust);
        return true;   // load-bearing: resets fall distance AND aboveGroundTickCount
    }
    return IJetpackItem.handleJetpackMotion(player, mode, ascendingSupplier);
}
```

Removed: the `!player.isShiftKeyDown()` check (sneak-to-pillar is gone).
`mode == JetpackMode.NORMAL` is what keeps hover stock.

## Bug fixes from review (do these too)

### Bug 1 — `HorizontalBoostMixin`: missing `/20`

Config values are blocks/**second**; the mixin adds them per **tick**, so the
boost is 20x too strong (default 2.0 → ~400 m/s terminal instead of ~20 m/s).

```java
double forwardBoost = Config.forwardSpeedBoost() / 20.0D;
double strafeBoost = Config.strafeSpeedBoost() / 20.0D;
```

**Do not** change the rotation math — the current `dx = strafe*cos - forward*sin;
dz = forward*cos + strafe*sin` correctly matches vanilla `Entity#getInputVector`.

### Bug 2 — `JetpackState.isJetpackActive`: wrong gate

It reads the raw item mode, so the forward boost applies while **walking on the
ground** with a jetpack equipped. Use Mekanism's real gate:

```java
public static boolean isJetpackActive(LivingEntity entity) {
    if (!(entity instanceof Player player)) return false;
    if (IJetpackItem.getActiveJetpack(player).isEmpty()) return false;
    ItemStack primary = IJetpackItem.getPrimaryJetpack(player);
    if (primary.isEmpty() || !(primary.getItem() instanceof IJetpackItem jetpack)) return false;
    JetpackMode primaryMode = jetpack.getJetpackMode(primary);
    JetpackMode mode = IJetpackItem.getPlayerJetpackMode(player, primaryMode,
            () -> Mekanism.keyMap.has(player.getUUID(), KeySync.ASCEND));
    return mode != JetpackMode.DISABLED;
}
```

Use the **stock** `getPlayerJetpackMode` here (not `isVectorEngaged`) — while
vectoring, horizontal thrust already comes from the look vector, so the forward
boost should stay off. It applies only when Space is held (or in hover).

Verify `Mekanism.keyMap` is populated client-side; `ClientTickHandler.tickStart`
calls `MekanismClient.updateKey(minecraft.player.input.jumping, KeySync.ASCEND)`,
which should cover it. If not, fall back to `minecraft.player.input.jumping` on
the client side of the check.

## Acceptance criteria (replaces PLAN.md §8.6)

6a. Normal mode, hold Space only → stock straight-up climb, faster horizontally
    than sprint, scales with `forward_speed_boost`.
6b. Normal mode, hold V only → fly along look vector; fuel drains; flames show.
6c. Normal mode, Space + V → behaves exactly as Space alone.
6d. Hover mode → identical to stock Mekanism; V changes nothing.
6e. Walk on the ground with a jetpack equipped, no keys → **no** speed boost
    (regression test for Bug 2).

Criterion 7 (dedicated-server rubber-band test on cozy-chaos) is unchanged and
still matters most for the horizontal boost.
