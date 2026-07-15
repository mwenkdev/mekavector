# PLAN.md — mekavector

**1.21-style Mekanism jetpack flight for Forge 1.20.1**

## Purpose

A standalone **companion mixin mod** (not a fork of Mekanism) that gives the
Mekanism jetpack on **Forge 1.20.1** the flight behavior it has in Mekanism
1.21.x, specifically:

1. **Forward boost in normal flight** — while jetpacking, you move faster than
   normal sprint speed horizontally (matches 1.21 normal mode).
2. **Keybind-gated vector thrust** — while a dedicated "Vector Thrust" key is
   engaged, the jetpack thrusts along the player's **look vector** (point where
   you want to go and fly there), matching 1.21 Vector mode's *feel*.
3. **Sneak-to-pillar** — while vector thrust is engaged, holding sneak reverts to
   vertical-only thrust so you can still pillar straight up (mirrors 1.21's
   "hold shift in vector mode behaves as normal").

Mekanism's own jar is never modified. This mod loads *after* Mekanism and weaves
into its compiled classes at runtime via SpongePowered Mixin, plus vanilla
`LivingEntity` for the horizontal component.

## Explicitly out of scope

- **No** real fourth `VECTOR` enum entry in `IJetpackItem.JetpackMode`. Adding an
  enum constant via mixin is fragile and would require patching Mekanism's
  mode-cycle, HUD render, keybind, and client/server mode-sync paths. We
  reproduce the *behavior* via a keybind, not a new selectable mode. Consequence:
  the HUD still shows Normal/Hover, not "Vector". This is accepted.
- **No** fork of Mekanism, **no** backport of other 10.5.x jetpack features.
- **No** attempt to publish to CurseForge/Modrinth. Distribution is a GitHub
  release consumed by packwiz (see §9).

## Target environment (pin these)

| Thing | Value |
|---|---|
| Minecraft | 1.20.1 |
| Mod loader | Forge 47.x — **match the exact Forge build of the cozy-chaos pack** |
| Java (compile + daemon) | JDK 17 — `/usr/lib/jvm/java-17-openjdk-amd64` |
| Gradle wrapper | 8.14.x |
| ForgeGradle | `[6.0.16,6.2)` |
| Mekanism (compile dep) | `1.20.1-10.4.x` — **match the pack's exact Mekanism build** (e.g. `1.20.1-10.4.9.61` or later 10.4.x) |
| Mekanism version range (mods.toml) | `[10.4,10.5)` — 10.5.x does not exist for 1.20.1; this prevents an accidental wrong-branch load |
| MixinExtras | Use the copy bundled with modern Forge (available at runtime) if a non-`@Overwrite` hook is cleaner |

**Build must be pinned to JDK 17.** Put this in the repo's `gradle.properties`:

```
org.gradle.java.home=/usr/lib/jvm/java-17-openjdk-amd64
```

This puts both the Gradle daemon and the compile toolchain on 17, sidestepping
the occasional ForgeGradle-6-on-JDK-21 decompile flakiness. The machine's default
`java`/`javac` is 21 and should be left alone.

## 1. Repo scaffold (files to create)

Target repo: **`mwenkdev/mekavector`** (public). `mod_id = mekavector`.

```
build.gradle
settings.gradle                     # include foojay-resolver-convention
gradle.properties                   # metadata + version pins + JDK17 pin
gradle/wrapper/gradle-wrapper.properties   # 8.14.x
gradlew / gradlew.bat
LICENSE                             # MIT
README.md
.gitignore                          # standard Forge/Gradle ignores (run/, build/, .gradle/)
src/main/resources/META-INF/mods.toml
src/main/resources/META-INF/accesstransformer.cfg   # only if actually needed (see §6)
src/main/resources/mekavector.mixins.json
src/main/resources/pack.mcmeta
src/main/java/dev/mwenk/mekavector/MekaVector.java           # @Mod, config registration
src/main/java/dev/mwenk/mekavector/Config.java               # ForgeConfigSpec
src/main/java/dev/mwenk/mekavector/JetpackState.java         # shared "is jetpack active / mode" helper (common)
src/main/java/dev/mwenk/mekavector/net/VectorKeyPacket.java  # client->server keystate sync
src/main/java/dev/mwenk/mekavector/net/Network.java          # SimpleChannel registration
src/main/java/dev/mwenk/mekavector/client/ClientSetup.java   # keybind registration + tick listener
src/main/java/dev/mwenk/mekavector/mixin/HorizontalBoostMixin.java   # vanilla LivingEntity
src/main/java/dev/mwenk/mekavector/mixin/VectorThrustMixin.java      # Mekanism hook (see §5.2)
```

Use the existing **MekanismJetpackTweaks** repo (squeeglii/MekanismJetpackTweaks —
`master` is already ported to 1.20.1) as a structural reference for
`build.gradle`, `settings.gradle`, mixin config, and the `IJetpackUser`-style
logical-side helper. Do **not** copy it wholesale — we want a clean,
differently-scoped mod.

## 2. Dependency resolution (known risk — surface early)

The Mekanism deobf artifact is pulled from `modmaven.dev` (already the convention
in the tweaks repo's `repositories` block). **First build step: confirm the exact
`1.20.1-10.4.x` version resolves from modmaven.** If it does not:

- Fallback A: use **CurseMaven** (`https://cursemaven.com`) and reference the
  Mekanism CurseForge file id.
- Fallback B: `flatDir` / local-jar — point `fg.deobf(files("libs/Mekanism-....jar"))`
  at a copy of the exact jar from the pack.

Document whichever path works in README so the build is reproducible.

## 3. Behavior spec — the math

Let `player` be the flying entity. Config values are read live from `Config`.

### 3.1 Horizontal forward boost (both sides, no netcode needed)

Applied every tick while a jetpack is active and mode != DISABLED. Driven by the
player's existing movement input (`zza` forward, `xxa` strafe), which is present
on **both** client and server, so **no packet is required** for this feature.

```
forward = zza * forwardBoost      // forwardBoost from config, e.g. 2.0
strafe  = xxa * strafeBoost       // strafeBoost from config, default 0.0
// rotate (strafe, forward) by player yaw into world X/Z, add to deltaMovement (Y unchanged)
```

Hook: inject into vanilla `LivingEntity#aiStep()V` **after** the call to
`travel(Vec3)`. Gate on `isEffectiveAi()` (server) OR `isControlledByLocalInstance()`
(client owner), and skip when fall-flying or in fluid. (Same shape as the tweaks
mod's `PlayerSpeedModifierMixin` — that part is proven and robust because the
injection site is pure vanilla.)

### 3.2 Vector thrust (keybind-gated — needs side sync, see §4)

Active only when ALL of: jetpack active, mode != DISABLED, and the player's
"vector active" flag is true (local key state on client; synced flag on server).

```
if (vectorActive) {
    if (player.isShiftKeyDown()) {
        // pillaring: vertical-only, let Mekanism's normal vertical thrust stand
        // -> do nothing extra here; do NOT apply look-vector
    } else {
        look = player.getLookAngle().normalize()   // full 3D look vector
        thrust = look.scale(vectorThrust)          // vectorThrust from config
        // Replace the vertical up-thrust with look-vector thrust:
        //  - either cancel Mekanism's vertical contribution and set delta along `look`
        //  - or add `thrust` and separately damp the stock vertical add
        player.setDeltaMovement( blend(player.getDeltaMovement(), thrust) )
    }
}
```

The exact blend is a **tuning decision for Mike in-game** — start with "replace
vertical, keep existing horizontal, add look-vector thrust," then adjust
`vectorThrust` and any damping until it feels like 1.21. Ship sane config
defaults; expect iteration.

## 4. Client/server sync (the fragile part — do this carefully)

The Vector-Thrust keybind is **client-only input**. Mekanism applies jetpack
motion on **both** client and server (it already syncs the ASCEND key via
`Mekanism.keyMap` / `KeySync`). If only the client applies vector thrust, the
server will keep applying straight-up thrust and correct the position →
rubber-banding. So the server must know the key state.

**Chosen approach: a tiny Forge `SimpleChannel` packet.**

- Register a `SimpleChannel` in `Network.java`.
- On the client, in a `ClientTickEvent` (or key state-change), detect the
  keybind's pressed/toggled state. When it changes, send `VectorKeyPacket(bool)`
  to the server.
- Server handler stores it per-player: `Map<UUID, Boolean>` (WeakHashMap keyed by
  UUID, cleared on `PlayerLoggedOutEvent`), OR a lightweight capability.
- `VectorThrustMixin` reads: client side → local key state directly; server side
  → the synced map.
- Debounce: only send on change, not every tick.

**Zero-netcode fallback (document as an option, don't implement unless asked):**
gate vector on an input combo the server *already* knows — e.g. `isSprinting()`
while jetpacking (sprint is vanilla-synced; ASCEND is Mekanism-synced). This
removes the packet entirely at the cost of overloading sprint and losing an
independent toggle. Note it in README as the "if the netcode gets annoying, here's
the escape hatch" path.

## 5. Mixin strategy — verify against decompiled 10.4.x, prefer least-fragile

### 5.1 Horizontal boost mixin

`@Inject` into vanilla `LivingEntity#aiStep`. Low risk. No Mekanism internals at
the injection site (only reads Mekanism statics to know a jetpack is active).

### 5.2 Vector thrust mixin — pick the least-invasive hook that works

Target is Mekanism's jetpack motion path. Candidate strategies, in order of
preference (least fragile first):

1. **Additive-only, no Mekanism method touched** — apply look-vector thrust in
   our own `aiStep`/tick injection (same style as §5.1), reading
   `IJetpackItem.getActiveJetpack` / `getPlayerJetpackMode` to know state, and
   damping the stock vertical component via a `@Redirect`/`@WrapOperation` on the
   `setDeltaMovement` call inside Mekanism's motion method if needed. Keeps
   coupling minimal.
2. **`@ModifyExpressionValue` / `@WrapOperation` (MixinExtras)** on the specific
   vertical-thrust computation inside `IJetpackItem.handleJetpackMotion` —
   surgical, avoids replacing the whole method.
3. **`@Overwrite(remap = false)` of `handleJetpackMotion`** — the tweaks mod does
   this *because Mixin dislikes injecting into static interface methods*. If 1 and
   2 prove impractical due to that same static-interface-method constraint, fall
   back to this, copying the 10.4.x method body verbatim and adding the vector
   branch. Document that this freezes Mekanism's vertical logic at the 10.4.x
   snapshot (acceptable — 1.20.1 Mekanism is EOL at 10.4.x).

**Before writing the hook, decompile/inspect the actual `handleJetpackMotion`
signature and body in the pinned Mekanism version** and confirm the enum
constants (`NORMAL`, `HOVER`, `DISABLED`), `CommonPlayerTickHandler.isOnGroundOrSleeping`,
and the getters used still match. Do not trust the tweaks-mod copy blindly.

## 6. Access transformer

Only add one if a needed member is inaccessible (the tweaks mod widened
`LivingEntity#isAffectedByFluids`). Add the minimum necessary; omit the file
entirely if not needed.

## 7. Config schema (`ForgeConfigSpec`, COMMON)

```
[jetpack]
forward_speed_boost   (double, default 2.0,  range 0.0..15.0)  # 0 = vanilla
strafe_speed_boost    (double, default 0.0,  range 0.0..15.0)
vector_thrust         (double, default 0.5,  range 0.0..5.0)   # look-vector thrust strength
vector_keybind_toggle (bool,   default false)                  # false = hold, true = toggle
```

Keep it small; expand only if tuning demands it.

## 8. Acceptance criteria

The agent can self-verify 1–4; **5–7 require Mike in-game**:

1. `./gradlew build` is green on JDK 17; a reobf'd jar is produced in `build/libs`.
2. Dev client (`./gradlew runClient`) launches with Mekanism 10.4.x present and
   the log shows the mixins **applied** (no mixin apply / `@Inject`
   target-not-found errors), no crash.
3. Config file generates on first run with the schema in §7.
4. The built (reobf'd) jar loads in a **non-dev** instance — verify against
   production mappings, not just the dev environment. (Common failure point:
   works in dev, refmap/reobf wrong in prod.)
5. In-game: normal flight is noticeably faster horizontally; forward boost scales
   with `forward_speed_boost`.
6. In-game: holding the Vector-Thrust key makes the jetpack fly along the look
   vector; releasing returns to stock behavior; sneak-while-vector pillars
   straight up.
7. **Multiplayer**: on a second client (or the k3s cozy-chaos server), no
   rubber-banding while using vector thrust — confirms the sync in §4 works.

## 9. Distribution / packwiz integration

Not on CF/Modrinth, so no `packwiz cf install`. After a tagged GitHub release:

- Add to `cozy-chaos-modpack` as a **manually-hosted** mod: a `.pw.toml` under
  `mods/` with the release asset URL + hash (`packwiz url add`, or hand-write the
  `[download]` block with `hash-format = "sha256"`).
- Alternatively host the jar alongside the pack on the existing GitHub Pages
  deployment.
- Bump the pack index and re-run the GitHub Actions Pages deploy
  (`runs-on: marauders-runner`).

## 10. Open decisions for Mike (resolve before or during build)

- **Keybind default: hold vs toggle.** Plan defaults to *hold*
  (`vector_keybind_toggle=false`).
- **Exact Forge + Mekanism versions** to pin — must match the live cozy-chaos pack.
- **Vector thrust default strength / blend feel** — expect to tune `vector_thrust`
  in-game; the shipped default is a starting point, not a final value.
- **Java package** — `dev.mwenk.mekavector` assumed here.

## 11. Suggested commit sequence (for a clean history)

1. Scaffold + build files + empty `@Mod` class → confirm `build` green, deps resolve.
2. Config + horizontal boost mixin → confirm forward boost works in dev.
3. Keybind + client tick + packet + server flag store.
4. Vector thrust mixin reading the synced flag.
5. Sneak-to-pillar + tuning defaults.
6. README (build steps, dep-resolution path used, config docs, packwiz snippet),
   LICENSE, tag release.
