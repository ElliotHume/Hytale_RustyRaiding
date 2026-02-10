package com.apophisgames.rustyraiding;

import com.hypixel.hytale.math.vector.Vector3d;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;
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
    @Nonnull Vector3d max,
    @Nonnull Map<ProtectionFlag, Boolean> permissions
) {

    /**
     * Create a new zone with auto-generated internal ID and default safezone flags (all disabled).
     */
    public static Zone create(String zoneName, String worldName, Vector3d min, Vector3d max) {
        // Default safezone: All protections enabled
        Map<ProtectionFlag, Boolean> defaults = new HashMap<>();
        for (ProtectionFlag flag : ProtectionFlag.values()) {
            defaults.put(flag, false);
        }
        return new Zone(UUID.randomUUID().toString(), zoneName, worldName, min, max, defaults);
    }

    /**
     * Create a copy with updated bounds.
     */
    public Zone withBounds(Vector3d newMin, Vector3d newMax) {
        return new Zone(internalId, zoneName, worldName, newMin, newMax, new HashMap<>(permissions));
    }

    /**
     * Create a copy with updated permission for a specific flag.
     */
    public Zone withPermission(ProtectionFlag flag, boolean allowed) {
        Map<ProtectionFlag, Boolean> newPermissions = new HashMap<>(permissions);
        newPermissions.put(flag, allowed);
        return new Zone(internalId, zoneName, worldName, min, max, newPermissions);
    }

    /**
     * Check if a specific action is allowed in this zone.
     * Defaults to true (ALLOWED) if the flag is missing, though create() populates all.
     */
    public boolean isAllowed(ProtectionFlag flag) {
        return permissions.getOrDefault(flag, true);
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
}
