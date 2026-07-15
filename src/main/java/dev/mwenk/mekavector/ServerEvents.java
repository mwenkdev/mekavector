package dev.mwenk.mekavector;

import dev.mwenk.mekavector.net.ServerVectorState;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Registered on the Forge event bus. Clears a player's synced Vector-Thrust flag
 * on logout so {@link ServerVectorState} never leaks entries.
 */
public final class ServerEvents {

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ServerVectorState.clear(event.getEntity().getUUID());
    }
}
