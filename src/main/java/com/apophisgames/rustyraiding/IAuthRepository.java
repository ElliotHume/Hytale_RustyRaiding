package com.apophisgames.rustyraiding;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Repository for persisting Zone Authorizations.
 */
public interface IAuthRepository {
    /**
     * Initialize the storage (e.g. create tables).
     */
    void initialize() throws Exception;

    /**
     * Load all zones authorizations from storage.
     * @return Map of zone names to player Ids
     */
    Map<String, List<String>> loadAll() throws Exception;

    /**
     * Find a list of authorized player ids in a Zone
     * @param zoneId The id of the Zone
     * @return List of player ids that are authed in the zone
     */
    List<String> findByZone(String zoneId) throws Exception;

    /**
     * Save (create or update) a zone authorization.
     * @param zoneAuthorization The zone authorization to save
     */
    void save(ZoneAuthorization zoneAuthorization) throws Exception;

    /**
     * Delete a zone authorization in a specific zone
     * @param zoneId The id of the zone to delete the authorization from
     * @param playerId The id of the player to remove
     */
    void delete(String zoneId, String playerId) throws Exception;

    /**
     * Delete all zone authorizations for a specific zone
     * @param zoneId The id of the zone to delete
     */
    void delete(String zoneId) throws Exception;

    /**
     * Close any resources (connections, files).
     */
    void close();
}
