package com.apophisgames.rustyraiding;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Service for managing SafeZones.
 * 
 * <p>Business logic layer. Delegates persistence and data access to {@link IZoneRepository}.
 */
public class ZoneService {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final IZoneRepository repository;

    public ZoneService(@Nonnull IZoneRepository repository) {
        this.repository = repository;
    }

    // ============================================
    // Result Types
    // ============================================

    public enum CreateResult {
        SUCCESS,
        ALREADY_EXISTS,
        ERROR
    }

    public enum UpdateResult {
        SUCCESS,
        NOT_FOUND,
        ERROR
    }

    // ============================================
    // Initialization
    // ============================================

    public void initialize() {
        try {
            repository.initialize();
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to initialize ZoneService");
        }
    }

    // ============================================
    // Query Methods
    // ============================================

    @Nullable
    public Zone getZoneByName(String worldName, String zoneName) {
        try {
            return repository.findByName(worldName, zoneName).orElse(null);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Error finding zone by name");
            return null;
        }
    }

    public boolean zoneExists(String worldName, String zoneName) {
        return getZoneByName(worldName, zoneName) != null;
    }

    public List<Zone> getZones(String worldName) {
        try {
            return repository.findByWorld(worldName);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Error getting zones for world " + worldName);
            return Collections.emptyList();
        }
    }

    @Nullable
    public Zone getClosestZone(String worldName, Vector3d position, double maxDistance) {
        List<Zone> zones = getZones(worldName);
        if (zones.isEmpty()) return null;

        Zone closest = null;
        double closestDistance = Double.MAX_VALUE;

        for (Zone zone : zones) {
            double distance = distanceToZone(zone, position);
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = zone;
            }
        }

        if (maxDistance > 0 && closestDistance > maxDistance) {
            return null;
        }

        return closest;
    }

    public static double distanceToZone(Zone zone, Vector3d position) {
        Vector3d center = new Vector3d(
            (zone.min().x + zone.max().x) / 2,
            (zone.min().y + zone.max().y) / 2,
            (zone.min().z + zone.max().z) / 2
        );
        return position.distanceTo(center);
    }

    @Nullable
    public Zone getZoneAt(String worldName, Vector3d position) {
        List<Zone> zones = getZones(worldName);
        
        for (Zone zone : zones) {
            if (zone.contains(position)) {
                return zone;
            }
        }
        return null;
    }

    // ============================================
    // Write Methods
    // ============================================

    public CreateResult createZone(Zone zone) {
        if (zoneExists(zone.worldName(), zone.zoneName())) {
            return CreateResult.ALREADY_EXISTS;
        }

        try {
            repository.save(zone);
            LOGGER.atInfo().log("Created zone: " + zone.zoneName());
            return CreateResult.SUCCESS;
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to create zone: " + zone.zoneName());
            return CreateResult.ERROR;
        }
    }

    public UpdateResult updateZone(String worldName, String zoneName, 
                                   @Nullable Vector3d newMin, @Nullable Vector3d newMax,
                                   @Nullable Map<ProtectionFlag, Boolean> newPermissions) {
        
        Zone existing = getZoneByName(worldName, zoneName);
        if (existing == null) {
            return UpdateResult.NOT_FOUND;
        }

        Zone updated = existing;
        if (newMin != null && newMax != null) {
            updated = updated.withBounds(newMin, newMax);
        }
        if (newPermissions != null) {
            for (Map.Entry<ProtectionFlag, Boolean> entry : newPermissions.entrySet()) {
                updated = updated.withPermission(entry.getKey(), entry.getValue());
            }
        }

        try {
            repository.save(updated);
            LOGGER.atInfo().log("Updated zone: " + zoneName);
            return UpdateResult.SUCCESS;
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to update zone: " + zoneName);
            return UpdateResult.ERROR;
        }
    }

    public boolean deleteZone(String worldName, String zoneName) {
        Zone existing = getZoneByName(worldName, zoneName);
        if (existing == null) {
            return false;
        }

        try {
            repository.delete(existing.internalId());
            LOGGER.atInfo().log("Deleted zone: " + zoneName);
            return true;
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to delete zone: " + zoneName);
            return false;
        }
    }

    // ============================================
    // Lifecycle
    // ============================================

    public void shutdown() {
        repository.close();
    }
}
