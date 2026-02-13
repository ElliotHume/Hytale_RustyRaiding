package com.apophisgames.rustyraiding.zones;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.World;

import javax.annotation.Nonnull;
import java.util.List;
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

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

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

    public boolean checkOverlapWithZone(Zone targetZone) {

        LOGGER.atInfo().log("Checking Overlap between zones '%s' and '%s' ".formatted(zoneName, targetZone.zoneName));

        // Zones can only overlap if they're in the same world
        if (!worldName.equals(targetZone.worldName())) {
            LOGGER.atInfo().log("Zones are not in the same world");
            return false;
        }

        // Check for overlap in 3D space using the separating axis theorem
        // Two boxes overlap if they overlap on ALL three axes (X, Y, and Z)
        boolean xOverlap = min().x <= targetZone.max().x && max().x >= targetZone.min().x;
        boolean yOverlap = min().y <= targetZone.max().y && max().y >= targetZone.min().y;
        boolean zOverlap = min().z <= targetZone.max().z && max().z >= targetZone.min().z;

        LOGGER.atInfo().log("Zone overlaps: x=[%s] y=[%s] z=[%s]".formatted(xOverlap, yOverlap, zOverlap));

        return xOverlap && yOverlap && zOverlap;
    }

    public static List<Vector3d> getZoneCorners(Zone zone) {
        Vector3d min = zone.min();
        Vector3d max = zone.max();

        return List.of(
                new Vector3d(min.x, min.y, min.z),
                new Vector3d(max.x, min.y, min.z),
                new Vector3d(min.x, max.y, min.z),
                new Vector3d(max.x, max.y, min.z),
                new Vector3d(min.x, min.y, max.z),
                new Vector3d(max.x, min.y, max.z),
                new Vector3d(min.x, max.y, max.z),
                new Vector3d(max.x, max.y, max.z)
        );
    }
}
