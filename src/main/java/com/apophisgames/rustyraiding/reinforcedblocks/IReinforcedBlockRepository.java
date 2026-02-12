package com.apophisgames.rustyraiding.reinforcedblocks;

import com.apophisgames.rustyraiding.zones.Zone;
import com.hypixel.hytale.math.vector.Vector3i;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Repository for persisting Zones.
 */
public interface IReinforcedBlockRepository {

    /**
     * Initialize the storage (e.g. create tables).
     */
    void initialize() throws Exception;

    /**
     * Load all reinforcements from storage.
     * @return Map of WorldName -> (Map of Internal Id -> Reinforced Block)
     */
    Map<String, Map<String, ReinforcedBlock>> loadAll() throws Exception;


    /**
     * Find all reinforced blocks in a specific world.
     * @param worldName The name of the world
     * @return Map of Internal Id -> Reinforced Block
     */
    Map<String, ReinforcedBlock> findByWorld(String worldName) throws Exception;

    /**
     * Find a reinforced block by name in a specific world.
     * @param worldName The world name
     * @param position the coordinate positions
     * @return Optional containing the reinforced block if found
     */
    Optional<ReinforcedBlock> findByPosition(String worldName, Vector3i position) throws Exception;

    /**
     * Find all reinforced blocks in a specific area in a world
     * @param worldName The name of the world
     * @param boundsMin The min corner of the bounded area to search
     * @param boundsMax The max corner of the bounded area to search
     * @return Map of Internal Id -> Reinforced Block
     */
    Map<String, ReinforcedBlock> findInArea(String worldName, Vector3i boundsMin, Vector3i boundsMax) throws Exception;


    /**
     * Save (create or update) a reinforced block.
     * @param reinforcedBlock The reinforced block to save
     */
    void save(ReinforcedBlock reinforcedBlock) throws Exception;

    /**
     * Delete a reinforced block by id.
     * @param reinforcedBlockId The internal ID of the reinforced block to delete
     */
    void delete(String reinforcedBlockId) throws Exception;

    /**
     * Delete a reinforced block by position.
     * @param worldName The name of the world to delete it from
     * @param position The coordinate position of the block to delete
     */
    void delete(String worldName, Vector3i position) throws Exception;

    /**
     * Delete all reinforced blocks in a specific area in a world
     * @param worldName The name of the world
     * @param boundsMin The min corner of the bounded area to search
     * @param boundsMax The max corner of the bounded area to search
     */
    void deleteInArea(String worldName, Vector3i boundsMin, Vector3i boundsMax) throws Exception;

    /**
     * Close any resources (connections, files).
     */
    void close();
}
