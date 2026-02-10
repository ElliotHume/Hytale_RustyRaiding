package com.apophisgames.rustyraiding;

import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Represents a defined zone in a world.
 *
 * @param internalId Auto-generated UUID, database primary key
 * @param zoneId Name of the zone the player is authorized in
 * @param playerId Id of the player, however that is defined...
 */
public record ZoneAuthorization(
        @Nonnull String internalId,
        @Nonnull String zoneId,
        @Nonnull String playerId

) {

    /**
     * Create a new zone with auto-generated internal ID and default safezone flags (all disabled).
     */
    public static ZoneAuthorization create(String zoneId, String playerId) {
        return new ZoneAuthorization(UUID.randomUUID().toString(), zoneId, playerId);
    }

    /**
     * Create a copy with updated Ids.
     */
    public ZoneAuthorization withIds(String zoneId, String playerId) {
        return new ZoneAuthorization(internalId, zoneId, playerId);
    }
}
