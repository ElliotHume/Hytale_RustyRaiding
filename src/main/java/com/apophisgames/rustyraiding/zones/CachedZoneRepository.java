package com.apophisgames.rustyraiding.zones;

import com.hypixel.hytale.logger.HytaleLogger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Lazy Caching wrapper for IZoneRepository.
 * 
 * <p>Zones are loaded from the delegate repository only when requested per world.
 * Writes are updated in memory if the world is currently cached.
 */
public class CachedZoneRepository implements IZoneRepository {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final IZoneRepository delegate;
    
    // WorldName -> List of Zones
    private final Map<String, CopyOnWriteArrayList<Zone>> cache = new ConcurrentHashMap<>();

    public CachedZoneRepository(IZoneRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public void initialize() throws Exception {
        // Lazy: Do not load data on startup. Just init delegate.
        delegate.initialize();
    }

    @Override
    public Map<String, List<Zone>> loadAll() throws Exception {
        // Delegate and populate cache (optional, but good for consistency if someone calls this)
        Map<String, List<Zone>> zones = delegate.loadAll();
        cache.clear();
        zones.forEach((world, list) -> cache.put(world, new CopyOnWriteArrayList<>(list)));
        
        // Return defensive copy
        Map<String, List<Zone>> result = new HashMap<>();
        zones.forEach((world, list) -> result.put(world, new ArrayList<>(list)));
        return result;
    }

    @Override
    public List<Zone> findByWorld(String worldName) throws Exception {
        // Double-checked locking via computeIfAbsent is simplest for lazy loading
        // Note: computeIfAbsent on ConcurrentHashMap behaves correctly (atomic per key)
        
        // We need to handle checked exceptions from delegate inside the lambda
        // This wrapper approach propagates the exception out
        try {
            return cache.computeIfAbsent(worldName, k -> {
                try {
                    List<Zone> zones = delegate.findByWorld(k);
                    return new CopyOnWriteArrayList<>(zones);
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
    public Optional<Zone> findByName(String worldName, String zoneName) throws Exception {
        // Use findByWorld to leverage the lazy cache
        List<Zone> zones = findByWorld(worldName);
        
        for (Zone zone : zones) {
            if (zone.zoneName().equals(zoneName)) {
                return Optional.of(zone);
            }
        }
        return Optional.empty();
    }

    @Override
    public void save(Zone zone) throws Exception {
        // 1. Update Delegate (Source of Truth)
        delegate.save(zone);

        // 2. Update Cache if present (Write-Through)
        // If not present, we don't load it. Next findByWorld will fetch the new state.
        cache.computeIfPresent(zone.worldName(), (key, zones) -> {
            zones.removeIf(z -> z.internalId().equals(zone.internalId()));
            zones.add(zone);
            return zones;
        });
    }

    @Override
    public void delete(String zoneId) throws Exception {
        // 1. Update Delegate
        delegate.delete(zoneId);

        // 2. Update Cache if present
        // Since we don't know the world, we scan loaded worlds.
        for (List<Zone> zones : cache.values()) {
            boolean removed = zones.removeIf(z -> z.internalId().equals(zoneId));
            if (removed) break; // Optimization: ID is unique, so we can stop
        }
    }

    @Override
    public void close() {
        delegate.close();
        cache.clear();
    }
}
