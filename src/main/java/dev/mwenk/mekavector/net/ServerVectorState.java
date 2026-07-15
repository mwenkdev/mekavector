package dev.mwenk.mekavector.net;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side store of each player's Vector-Thrust key state, synced from the
 * client via {@link VectorKeyPacket}. Keyed by UUID and cleared on logout so it
 * never leaks memory. Accessed from the server tick thread (and set from the
 * network thread), hence the concurrent map.
 */
public final class ServerVectorState {

    private static final Map<UUID, Boolean> ACTIVE = new ConcurrentHashMap<>();

    private ServerVectorState() {
    }

    public static void set(UUID player, boolean active) {
        if (active) {
            ACTIVE.put(player, Boolean.TRUE);
        } else {
            ACTIVE.remove(player);
        }
    }

    public static boolean isActive(UUID player) {
        return ACTIVE.getOrDefault(player, Boolean.FALSE);
    }

    public static void clear(UUID player) {
        ACTIVE.remove(player);
    }
}
