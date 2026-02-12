package com.apophisgames.rustyraiding.zones;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Repository for persisting Zones.
 */
public interface IZoneRepository {

    /**
     * Initialize the storage (e.g. create tables).
     */
    void initialize() throws Exception;

    /**
     * Load all zones from storage.
     * @return Map of WorldName -> List of Zones
     */
    Map<String, List<Zone>> loadAll() throws Exception;


    /**
     * Find all zones in a specific world.
     * @param worldName The name of the world
     * @return List of zones
     */
    List<Zone> findByWorld(String worldName) throws Exception;

    /**
     * Find a zone by name in a specific world.
     * @param worldName The world name
     * @param zoneName The zone name
     * @return Optional containing the zone if found
     */
    Optional<Zone> findByName(String worldName, String zoneName) throws Exception;

    /**
     * Save (create or update) a zone.
     * @param zone The zone to save
     */
    void save(Zone zone) throws Exception;

    /**
     * Delete a zone.
     * @param zoneId The internal ID of the zone to delete
     */
    void delete(String zoneId) throws Exception;

    /**
     * Close any resources (connections, files).
     */
    void close();
}
