# MekaVector

**1.21-style Mekanism jetpack flight for Forge 1.20.1.**

MekaVector is a small **companion mixin mod** (not a fork of Mekanism) that gives
the Mekanism jetpack on Forge 1.20.1 the flight *feel* it has in Mekanism 1.21.x:

1. **Forward boost in normal flight** — while thrusting in **Normal mode** the
   jetpack adds a fraction of your *current* horizontal velocity each tick, so you
   build up to roughly 3.5x sprint speed instead of being capped at it (this is
   1.21's `0.08 * motion` term, verbatim). **Normal only** — 1.21's hover has no
   horizontal term, and neither do we.
2. **Keybind-gated vector thrust** — in **Normal mode**, hold the **Vector Thrust**
   key (default `V`) and the jetpack thrusts along your head's **up-axis**, exactly
   as 1.21's Vector mode does. Fuel drains and flames/sound play while vectoring.

### Normal-mode controls

| Input | Result |
|---|---|
| nothing | jetpack off, fall normally |
| Space | straight-up thrust (self-limiting) + horizontal forward boost |
| V | vector thrust along your up-axis |
| Space + V | **Space wins** — straight-up thrust, no vector |

**Vector is not point-and-go.** 1.21 thrusts along `getUpVector` — your head's up
axis, not your look direction — so the thrust comes out of your back like a real
jetpack:

| Looking | Result |
|---|---|
| level | thrust straight up |
| **down** | accelerate **forward** |
| **up** | brake / reverse |

**Hover mode is fully stock** — `V` is ignored there, and there is no horizontal
boost. There is no sneak-to-pillar: Space serves that role now.

Mekanism's jar is **never modified**. This mod loads *after* Mekanism and weaves
into its compiled classes at runtime via SpongePowered Mixin. It touches no vanilla
classes — every mixin is a redirect on a Mekanism method, applied identically on
both logical sides.

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
    # Jetpack thrust per tick. Governs both NORMAL vertical climb and VECTOR thrust.
    jetpack_thrust = 0.15        # range 0.0..1.0
    # Fraction of current horizontal velocity added per tick while thrusting in
    # NORMAL mode. 0.0 = no forward boost.
    forward_boost_factor = 0.08  # range 0.0..0.5
    # false = hold the key; true = press to toggle.
    vector_keybind_toggle = false
```

**The defaults are 1.21's actual constants**, not tuning guesses: `0.15` is
`ItemJetpack.getJetpackThrust()` and `0.08` is the horizontal coefficient in
`IJetpackItem.handleJetpackMotion`. Leaving them alone gives 1.21 parity; they're
exposed so you can deviate if you want to.

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

- `VectorThrustCommonMixin` / `VectorThrustClientMixin` → `@Redirect`s on Mekanism's
  server (`CommonPlayerTickHandler#tickEnd`) and client (`ClientTickHandler#tickStart`)
  tick handlers:
  - `handleJetpackMotion` → substitute 1.21's motion math in Normal mode: vector
    thrust along the up-axis when `V` is held (and Space isn't), otherwise 1.21's
    velocity-proportional horizontal boost plus a self-limiting vertical. Hover and
    fall-flying call the stock method unchanged.
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
