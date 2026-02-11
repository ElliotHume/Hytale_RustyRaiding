package com.apophisgames.rustyraiding;

import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Represents a defined zone in a world.
 *
 * @param internalId Auto-generated UUID, database primary key
 * @param zoneName User-facing name, unique per world
 * @param worldName The name of the world this zone belongs to
 * @param min The minimum corner of the zone (inclusive)
 * @param max The maximum corner of the zone (inclusive)
 */
public record Zone(
    @Nonnull String internalId,
    @Nonnull String zoneName,
    @Nonnull String worldName,
    @Nonnull Vector3d min,
    @Nonnull Vector3d max
) {

    /**
     * Create a new zone with auto-generated internal ID and default safezone flags (all disabled).
     */
    public static Zone create(String zoneName, String worldName, Vector3d min, Vector3d max) {
        return new Zone(UUID.randomUUID().toString(), zoneName, worldName, min, max);
    }

    /**
     * Create a copy with updated bounds.
     */
    public Zone withBounds(Vector3d newMin, Vector3d newMax) {
        return new Zone(internalId, zoneName, worldName, newMin, newMax);
    }

    /**
     * Check if a position is within this zone.
     *
     * @param position The position to check
     * @return true if the position is inside the zone
     */
    public boolean contains(Vector3d position) {
        return position.x >= min.x && position.x < max.x &&
               position.y >= min.y && position.y < max.y &&
               position.z >= min.z && position.z < max.z;
    }

    public static String getZoneIdFromPosition(World world, Vector3i position){
        String positionString = "%sa%db%dc%d".formatted(world.getName(), position.x, position.y, position.z);
        return UUID.nameUUIDFromBytes(positionString.getBytes()).toString();
    }
}
