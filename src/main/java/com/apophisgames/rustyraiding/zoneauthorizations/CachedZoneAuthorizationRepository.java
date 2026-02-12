package com.apophisgames.rustyraiding.zoneauthorizations;

import com.hypixel.hytale.logger.HytaleLogger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Lazy Caching wrapper for IZoneRepository.
 *
 * <p>Zones are loaded from the delegate repository only when requested per world.
 * Writes are updated in memory if the world is currently cached.
 */
public class CachedZoneAuthorizationRepository implements IAuthRepository {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final IAuthRepository delegate;

    // Map of zone ids to player ids
    private final Map<String, CopyOnWriteArrayList<String>> cache = new ConcurrentHashMap<>();

    public CachedZoneAuthorizationRepository(IAuthRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public void initialize() throws Exception {
        // Lazy: Do not load data on startup. Just init delegate.
        delegate.initialize();
    }

    @Override
    public Map<String, List<String>> loadAll() throws Exception {
        // Delegate and populate cache (optional, but good for consistency if someone calls this)
        Map<String, List<String>> zoneAuths = delegate.loadAll();
        cache.clear();
        zoneAuths.forEach((zoneId, playerIds) -> cache.put(zoneId, new CopyOnWriteArrayList<>(playerIds)));

        // Return defensive copy
        Map<String, List<String>> result = new HashMap<>();
        zoneAuths.forEach((zoneId, playerIds) -> result.put(zoneId, new ArrayList<>(playerIds)));
        return result;
    }


    @Override
    public List<String> findByZone(String zoneId) throws Exception {
        // Double-checked locking via computeIfAbsent is simplest for lazy loading
        // Note: computeIfAbsent on ConcurrentHashMap behaves correctly (atomic per key)

        // We need to handle checked exceptions from delegate inside the lambda
        // This wrapper approach propagates the exception out
        try {
            return cache.computeIfAbsent(zoneId, k -> {
                try {
                    List<String> playerIds = delegate.findByZone(k);
                    return new CopyOnWriteArrayList<>(playerIds);
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
    public void save(ZoneAuthorization zoneAuthorization) throws Exception {
        // 1. Update Delegate (Source of Truth)
        delegate.save(zoneAuthorization);

        // 2. Update Cache if present (Write-Through)
        // If not present, we don't load it. Next findByWorld will fetch the new state.
        cache.computeIfPresent(zoneAuthorization.zoneId(), (key, playerIds) -> {
            if (!playerIds.contains(zoneAuthorization.playerId()))
                playerIds.add(zoneAuthorization.playerId());
            return playerIds;
        });
    }

    @Override
    public void delete(String zoneId) throws Exception {
        // 1. Update Delegate
        delegate.delete(zoneId);

        // 2. Update the Cache
        if (cache.containsKey(zoneId))
            cache.remove(zoneId);
    }

    @Override
    public void delete(String zoneId, String playerId) throws Exception {
        // 1. Update Delegate
        delegate.delete(zoneId, playerId);

        // 2. Update the Cache
        cache.computeIfPresent(zoneId, (key, playerIds) -> {
            if (playerIds.contains(playerId))
                playerIds.remove(playerId);
            return playerIds;
        });
    }

    @Override
    public void close() {
        delegate.close();
        cache.clear();
    }
}
