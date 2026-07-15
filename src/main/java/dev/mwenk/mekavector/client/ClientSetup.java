package dev.mwenk.mekavector.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mwenk.mekavector.Config;
import dev.mwenk.mekavector.JetpackState;
import dev.mwenk.mekavector.net.Network;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import org.lwjgl.glfw.GLFW;

/**
 * Client-only wiring: registers the Vector-Thrust keybind and, each client tick,
 * mirrors its state into {@link JetpackState#LOCAL_VECTOR_ACTIVE} and (on change)
 * to the server via {@link Network}. Loaded only on {@code Dist.CLIENT}.
 */
public final class ClientSetup {

    public static final KeyMapping VECTOR_THRUST_KEY = new KeyMapping(
            "key.mekavector.vector_thrust",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            "key.categories.mekavector");

    /** Toggle-mode latch (used only when vector_keybind_toggle=true). */
    private static boolean toggled = false;
    /** Last state pushed to the server, to debounce packets. */
    private static boolean lastSent = false;

    private ClientSetup() {
    }

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(ClientSetup::registerKeyMappings);
        MinecraftForge.EVENT_BUS.addListener(ClientSetup::onClientTick);
    }

    private static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(VECTOR_THRUST_KEY);
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        // Not in-world, or a GUI is capturing input: treat as released so vector
        // thrust can't get stuck on.
        if (mc.player == null || mc.screen != null) {
            setActive(false);
            return;
        }

        boolean active;
        if (Config.vectorKeybindToggle()) {
            while (VECTOR_THRUST_KEY.consumeClick()) {
                toggled = !toggled;
            }
            active = toggled;
        } else {
            // Hold mode: drain the click queue so presses don't leak if the user
            // later switches to toggle mode mid-session.
            while (VECTOR_THRUST_KEY.consumeClick()) {
                // no-op
            }
            active = VECTOR_THRUST_KEY.isDown();
        }

        setActive(active);
    }

    private static void setActive(boolean active) {
        JetpackState.LOCAL_VECTOR_ACTIVE = active;
        if (active != lastSent) {
            lastSent = active;
            Network.sendVectorKey(active);
        }
    }
}
