package dev.mwenk.mekavector;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * COMMON config (loaded on both client and server). Values are read live in the
 * hot path, so we cache the {@link ForgeConfigSpec.ConfigValue}s and dereference
 * them each tick — cheap and always current.
 */
public final class Config {

    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.DoubleValue FORWARD_SPEED_BOOST;
    private static final ForgeConfigSpec.DoubleValue STRAFE_SPEED_BOOST;
    private static final ForgeConfigSpec.DoubleValue VECTOR_THRUST;
    private static final ForgeConfigSpec.BooleanValue VECTOR_KEYBIND_TOGGLE;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("mekavector — 1.21-style Mekanism jetpack flight").push("jetpack");

        FORWARD_SPEED_BOOST = b
                .comment("Horizontal forward thrust added while jetpacking. 0.0 = vanilla Mekanism behavior.")
                .defineInRange("forward_speed_boost", 2.0D, 0.0D, 15.0D);

        STRAFE_SPEED_BOOST = b
                .comment("Horizontal strafe thrust added while jetpacking. 0.0 = no strafe boost.")
                .defineInRange("strafe_speed_boost", 0.0D, 0.0D, 15.0D);

        VECTOR_THRUST = b
                .comment("Look-vector thrust strength while the Vector Thrust key is engaged.")
                .defineInRange("vector_thrust", 0.5D, 0.0D, 5.0D);

        VECTOR_KEYBIND_TOGGLE = b
                .comment("false = hold the key to engage vector thrust; true = press to toggle it.")
                .define("vector_keybind_toggle", false);

        b.pop();
        SPEC = b.build();
    }

    private Config() {
    }

    public static double forwardSpeedBoost() {
        return FORWARD_SPEED_BOOST.get();
    }

    public static double strafeSpeedBoost() {
        return STRAFE_SPEED_BOOST.get();
    }

    public static double vectorThrust() {
        return VECTOR_THRUST.get();
    }

    public static boolean vectorKeybindToggle() {
        return VECTOR_KEYBIND_TOGGLE.get();
    }
}
