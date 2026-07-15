# MekaVector

**1.21-style Mekanism jetpack flight for Forge 1.20.1.**

MekaVector is a small **companion mixin mod** (not a fork of Mekanism) that gives
the Mekanism jetpack on Forge 1.20.1 the flight *feel* it has in Mekanism 1.21.x:

1. **Forward boost in normal flight** — while jetpacking you move faster than
   sprint speed horizontally (matches 1.21 normal mode). Applies in Normal and
   Hover, whenever the jetpack is actually thrusting.
2. **Keybind-gated vector thrust** — in **Normal mode**, hold the **Vector Thrust**
   key (default `V`) and the jetpack thrusts along your **look vector**: point
   where you want to go and fly there (matches 1.21 Vector mode's feel). Fuel
   drains and flames/sound play while vectoring.

### Normal-mode controls

| Input | Result |
|---|---|
| nothing | jetpack off, fall normally |
| Space | stock straight-up thrust (capped) + horizontal forward boost |
| V | look-vector thrust |
| Space + V | **Space wins** — stock straight-up thrust, no vector |

**Hover mode is fully stock** — `V` is ignored there (the horizontal forward boost
still applies). There is no sneak-to-pillar: Space serves that role now.

Mekanism's jar is **never modified**. This mod loads *after* Mekanism and weaves
into its compiled classes at runtime via SpongePowered Mixin, plus vanilla
`LivingEntity` for the horizontal component.

> **Not a new jetpack mode.** There is no real fourth `VECTOR` enum entry — the
> HUD still shows Normal/Hover. Vector thrust is a keybind that reproduces the
> *behavior*, not a selectable mode. This is intentional (see `PLAN.md` §"out of
> scope").

## Requirements

| Thing | Value |
|---|---|
| Minecraft | 1.20.1 |
| Forge | 47.4.10 (matches the cozy-chaos pack) |
| Mekanism | `[10.4,10.5)` — built against `1.20.1-10.4.16.80` |
| Java (runtime) | 17 |

## Building

The build is pinned to **JDK 17** via `org.gradle.java.home` in
`gradle.properties`. The machine default JVM (21) is left untouched.

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
./gradlew build
```

The reobfuscated jar lands in `build/libs/mekavector-<version>-mc1.20.1.jar`.

### Dependency resolution

Mekanism is pulled as a deobf artifact from **modmaven.dev**:

```gradle
repositories { maven { name = 'modmaven'; url = 'https://modmaven.dev' } }
dependencies { implementation fg.deobf("mekanism:Mekanism:1.20.1-10.4.16.80") }
```

This resolves as-is for the pinned version. If modmaven ever stops serving it:

- **Fallback A — CurseMaven:** uncomment the CurseMaven repo in `build.gradle`
  and reference the Mekanism CurseForge file id.
- **Fallback B — local jar:** drop the exact jar in `libs/` and swap the
  dependency for `fg.deobf(files('libs/Mekanism-1.20.1-10.4.16.80.jar'))`.

### Dev runs

```bash
./gradlew runClient   # launches a dev client; add Mekanism to run/mods to test in-game
./gradlew runServer
```

## Config

Generated on first run at `config/mekavector-common.toml`:

```toml
[jetpack]
    # Horizontal forward thrust while jetpacking. 0.0 = vanilla Mekanism.
    forward_speed_boost = 2.0   # range 0.0..15.0
    # Horizontal strafe thrust while jetpacking.
    strafe_speed_boost = 0.0    # range 0.0..15.0
    # Look-vector thrust strength while the Vector Thrust key is engaged.
    vector_thrust = 0.5         # range 0.0..5.0
    # false = hold the key; true = press to toggle.
    vector_keybind_toggle = false
```

The vector-thrust **blend is a starting point** — expect to tune `vector_thrust`
(and possibly the boosts) in-game until it feels like 1.21. The default blend is
"replace vertical with the look vector's Y, keep existing horizontal, add the look
vector's horizontal thrust" (see `VectorThrust.java`).

## Multiplayer / netcode

The Vector-Thrust key is client-only input, but Mekanism applies jetpack motion on
**both** client and server, so the server must know the key state or it will keep
thrusting straight up and rubber-band you back.

MekaVector syncs the key state with a tiny `SimpleChannel` packet
(`VectorKeyPacket`), sent **only on change**. The server stores it per-player
(cleared on logout) and both the client and server mixins read it, so motion
agrees on both sides.

**Zero-netcode escape hatch (not implemented):** if the packet ever causes
trouble, vector could instead be gated on an already-synced input such as
`isSprinting()` while jetpacking — removing the packet at the cost of overloading
sprint and losing an independent toggle.

## How it works (mixins)

- `HorizontalBoostMixin` → vanilla `LivingEntity#aiStep` (after `travel`): adds
  yaw-rotated forward/strafe thrust (config is blocks/second, converted to
  per-tick). Pure-vanilla injection site; only reads Mekanism statics, and gates
  on Mekanism's *effective* mode (`getPlayerJetpackMode`) so it never fires while
  walking.
- `VectorThrustCommonMixin` / `VectorThrustClientMixin` → `@Redirect`s on Mekanism's
  server (`CommonPlayerTickHandler#tickEnd`) and client (`ClientTickHandler#tickStart`)
  tick handlers:
  - `handleJetpackMotion` → substitute look-vector motion when vector is engaged
    (Normal mode, `V`, Space not held); otherwise call the stock method unchanged,
    so Hover and Space-thrust are untouched and Mekanism's vertical logic is never
    frozen.
  - `getPlayerJetpackMode` → turn `DISABLED` into `NORMAL` when vector is engaged.
    Mekanism gates the whole jetpack block on this returning non-`DISABLED` (which
    requires Space in Normal mode), so without this redirect `V`-only would never
    reach the motion call. Forcing it on also drains fuel and plays flames/sound —
    correct, since we are thrusting.
  - `isJetpackInUse` (client only) → a second client-side short-circuit that also
    requires Space in Normal mode; redirected the same way so `V`-only runs.

Redirecting these concrete call sites (rather than the `static` interface method
`handleJetpackMotion` itself) avoids Mixin's static-interface-method friction and
needs no MixinExtras. Each handler can safely call the stock method it redirects —
`@Redirect` only rewrites the one invoke inside the target method, not the handler's
own call.

## Distribution (packwiz)

Not on CurseForge/Modrinth. After a tagged GitHub release, add to
`cozy-chaos-modpack` as a manually-hosted mod — a `.pw.toml` under `mods/` with the
release asset URL + sha256:

```toml
name = "MekaVector"
filename = "mekavector-1.0.0-mc1.20.1.jar"
side = "both"

[download]
url = "https://github.com/mwenkdev/mekavector/releases/download/v1.0.0/mekavector-1.0.0-mc1.20.1.jar"
hash-format = "sha256"
hash = "<sha256 of the jar>"
```

Then bump the pack index and re-run the Pages deploy.

## License

MIT — see `LICENSE`.
