package com.apophisgames.rustyraiding.reinforcedblocks;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3i;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lazy Caching wrapper for IReinforcedBlockRepository.
 *
 * <p>Blocks are loaded from the delegate repository only when requested per world.
 * Writes are updated in memory if the world is currently cached.
 */
public class CachedReinforcedBlockRepository implements IReinforcedBlockRepository {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final IReinforcedBlockRepository delegate;

    // WorldName -> reinforced block id -> Reinforced Block
    private final Map<String, Map<String, ReinforcedBlock>> cache = new ConcurrentHashMap<>();

    public CachedReinforcedBlockRepository(IReinforcedBlockRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public void initialize() throws Exception {
        // Lazy: Do not load data on startup. Just init delegate.
        delegate.initialize();
    }

    @Override
    public Map<String, Map<String, ReinforcedBlock>> loadAll() throws Exception {
        // Delegate and populate cache (optional, but good for consistency if someone calls this)
        Map<String, Map<String, ReinforcedBlock>> blocks = delegate.loadAll();
        cache.clear();
        blocks.forEach((world, list) -> cache.put(world, new ConcurrentHashMap<>(list)));

        // Return defensive copy
        Map<String, Map<String, ReinforcedBlock>> result = new HashMap<>();
        blocks.forEach((world, list) -> result.put(world, new ConcurrentHashMap<>(list)));
        return result;
    }

    @Override
    public Map<String, ReinforcedBlock> findByWorld(String worldName) throws Exception {
        // Double-checked locking via computeIfAbsent is simplest for lazy loading
        // Note: computeIfAbsent on ConcurrentHashMap behaves correctly (atomic per key)

        // We need to handle checked exceptions from delegate inside the lambda
        // This wrapper approach propagates the exception out
        try {
            return cache.computeIfAbsent(worldName, k -> {
                try {
                    Map<String, ReinforcedBlock> blockMap = delegate.findByWorld(k);
                    return new ConcurrentHashMap<>(blockMap);
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new RuntimeException("WrappedException", e);
                }
            });
        } catch (RuntimeException e) {
            if ("WrappedException".equals(e.getMessage()) && e.getCause() instanceof Exception) {
                throw (Exception) e.getCause();
            }
            throw e;
        }
    }

    @Override
    public Optional<ReinforcedBlock> findByPosition(String worldName, Vector3i position) throws Exception {
        // Use findByWorld to leverage the lazy cache
        Map<String, ReinforcedBlock> blockMap = findByWorld(worldName);
        String id = ReinforcedBlock.getInternalIdFromPosition(worldName, position);
        if (blockMap.containsKey(id))
            return Optional.of(blockMap.get(id));

        return Optional.empty();
    }

    @Override
    public Map<String, ReinforcedBlock> findInArea(String worldName, Vector3i boundsMin, Vector3i boundsMax) throws Exception {
        // TODO: Use proper caching with this function
        return delegate.findInArea(worldName, boundsMin, boundsMax);
    }

    @Override
    public void save(ReinforcedBlock reinforcedBlock) throws Exception {
        // 1. Update Delegate (Source of Truth)
        delegate.save(reinforcedBlock);

        // 2. Update Cache if present (Write-Through)
        // If not present, we don't load it. Next findByWorld will fetch the new state.
        cache.computeIfPresent(reinforcedBlock.worldName(), (key, blockMap) -> {
            blockMap.put(reinforcedBlock.internalId(), reinforcedBlock);
            return blockMap;
        });
    }

    @Override
    public void delete(String reinforcedBlockId) throws Exception {
        // 1. Update Delegate
        delegate.delete(reinforcedBlockId);

        // 2. Update Cache if present
        // Since we don't know the world, we scan loaded worlds.
        for (Map<String, ReinforcedBlock> blockMap : cache.values()) {
            if (blockMap.containsKey(reinforcedBlockId)) {
                blockMap.remove(reinforcedBlockId);
                break; // Optimization: ID is unique, so we can stop
            }
        }
    }

    @Override
    public void delete(String worldName, Vector3i position) throws Exception {
        // 1. Update Delegate
        delegate.delete(worldName, position);

        String id = ReinforcedBlock.getInternalIdFromPosition(worldName, position);
        // 2. Update Cache if present
        // Since we don't know the world, we scan loaded worlds.
        for (Map<String, ReinforcedBlock> blockMap : cache.values()) {
            if (blockMap.containsKey(id)) {
                blockMap.remove(id);
                break; // Optimization: ID is unique, so we can stop
            }
        }
    }

    @Override
    public void deleteInArea(String worldName, Vector3i boundsMin, Vector3i boundsMax) throws Exception {
        // Get all the blocks to delete, we will need this to update the cache from
        Map<String, ReinforcedBlock> blocksToDelete = delegate.findInArea(worldName, boundsMin, boundsMax);

        // 1. Update Delegate
        delegate.deleteInArea(worldName, boundsMin, boundsMax);

        // 2. Update Cache if present
        cache.computeIfPresent(worldName, (key, blockMap) -> {
            blocksToDelete.keySet().forEach((blockId) -> blockMap.remove(blockId));
            return blockMap;
        });
    }

    @Override
    public void close() {
        delegate.close();
        cache.clear();
    }
}

