package dev.mwenk.mekavector;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * COMMON config (loaded on both client and server). Values are read live in the
 * hot path, so we cache the {@link ForgeConfigSpec.ConfigValue}s and dereference
 * them each tick — cheap and always current.
 */
public final class Config {

    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.DoubleValue JETPACK_THRUST;
    private static final ForgeConfigSpec.DoubleValue FORWARD_BOOST_FACTOR;
    private static final ForgeConfigSpec.BooleanValue VECTOR_KEYBIND_TOGGLE;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("mekavector — 1.21-style Mekanism jetpack flight").push("jetpack");

        JETPACK_THRUST = b
                .comment("Jetpack thrust per tick. 0.15 matches Mekanism 1.21's ItemJetpack.getJetpackThrust().",
                        "Governs both NORMAL vertical climb and VECTOR thrust.")
                .defineInRange("jetpack_thrust", 0.15D, 0.0D, 1.0D);

        FORWARD_BOOST_FACTOR = b
                .comment("Fraction of current horizontal velocity added per tick while thrusting in",
                        "NORMAL mode. 0.08 matches Mekanism 1.21. 0.0 = no forward boost.")
                .defineInRange("forward_boost_factor", 0.08D, 0.0D, 0.5D);

        VECTOR_KEYBIND_TOGGLE = b
                .comment("false = hold the key to engage vector thrust; true = press to toggle it.")
                .define("vector_keybind_toggle", false);

        b.pop();
        SPEC = b.build();
    }

    private Config() {
    }

    public static double jetpackThrust() {
        return JETPACK_THRUST.get();
    }

    public static double forwardBoostFactor() {
        return FORWARD_BOOST_FACTOR.get();
    }

    public static boolean vectorKeybindToggle() {
        return VECTOR_KEYBIND_TOGGLE.get();
    }
}
