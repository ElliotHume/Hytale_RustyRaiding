package com.apophisgames.rustyraiding;

import com.apophisgames.rustyraiding.reinforcedblocks.IReinforcedBlockRepository;
import com.apophisgames.rustyraiding.reinforcedblocks.ReinforcedBlock;
import com.apophisgames.rustyraiding.util.ColorPalette;
import com.apophisgames.rustyraiding.util.MessageBuilder;
import com.apophisgames.rustyraiding.zoneauthorizations.IAuthRepository;
import com.apophisgames.rustyraiding.zoneauthorizations.ZoneAuthorization;
import com.apophisgames.rustyraiding.zones.IZoneRepository;
import com.apophisgames.rustyraiding.zones.Zone;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.NameMatching;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for managing SafeZones.
 * 
 * <p>Business logic layer. Delegates persistence and data access to {@link IZoneRepository}.
 */
public class ZoneService {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final IZoneRepository zoneRepository;
    private final IAuthRepository authRepository;
    private final IReinforcedBlockRepository reinforcedBlockRepository;

    public ZoneService(@Nonnull IZoneRepository zoneRepository, @Nonnull IAuthRepository authRepository, @Nonnull IReinforcedBlockRepository reinforcedBlockRepository) {
        this.zoneRepository = zoneRepository;
        this.authRepository = authRepository;
        this.reinforcedBlockRepository = reinforcedBlockRepository;
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
            zoneRepository.initialize();
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to initialize zone repository");
        }

        try {
            authRepository.initialize();
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to initialize auth repository");
        }

        try {
            reinforcedBlockRepository.initialize();
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to initialize reinforced block repository");
        }
    }

    // ============================================
    // Zone Query Methods
    // ============================================

    @Nullable
    public Zone getZoneByName(String worldName, String zoneName) {
        try {
            return zoneRepository.findByName(worldName, zoneName).orElse(null);
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
            return zoneRepository.findByWorld(worldName);
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
    // Zone Write Methods
    // ============================================

    public CreateResult createZone(Zone zone) {
        if (zoneExists(zone.worldName(), zone.zoneName())) {
            return CreateResult.ALREADY_EXISTS;
        }

        boolean overlappingAnotherZone = getZones(zone.worldName()).stream().anyMatch(zone::checkOverlapWithZone);
        if (overlappingAnotherZone){
            LOGGER.atSevere().log("Overlapping zone boundaries detected, cannot create zone.");
            return CreateResult.ERROR;
        }

        try {
            zoneRepository.save(zone);
            LOGGER.atInfo().log("Created zone: " + zone.zoneName());
            return CreateResult.SUCCESS;
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to create zone: " + zone.zoneName());
            return CreateResult.ERROR;
        }
    }

    public UpdateResult updateZone(String worldName, String zoneName, 
                                   @Nullable Vector3d newMin, @Nullable Vector3d newMax) {
        
        Zone existing = getZoneByName(worldName, zoneName);
        if (existing == null) {
            return UpdateResult.NOT_FOUND;
        }

        Zone updated = existing;
        if (newMin != null && newMax != null) {
            updated = updated.withBounds(newMin, newMax);
        }

        Zone closestMaxZone = getClosestZone(updated.worldName(), updated.max(), Integer.max(RustyRaidingPlugin.CONFIG.get().getWidth(), RustyRaidingPlugin.CONFIG.get().getHeight()));
        Zone closestMinZone = getClosestZone(updated.worldName(), updated.min(), Integer.max(RustyRaidingPlugin.CONFIG.get().getWidth(), RustyRaidingPlugin.CONFIG.get().getHeight()));
        if (closestMaxZone != null && updated.checkOverlapWithZone(closestMaxZone)){
            LOGGER.atSevere().log("Overlapping updated boundaries detected, cannot create zone.");
            return UpdateResult.ERROR;
        }
        if (closestMinZone != null && updated.checkOverlapWithZone(closestMinZone)){
            LOGGER.atSevere().log("Overlapping updated boundaries detected, cannot create zone.");
            return UpdateResult.ERROR;
        }

        try {
            zoneRepository.save(updated);
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
            zoneRepository.delete(existing.internalId());
            LOGGER.atInfo().log("Deleted zone: " + zoneName);
            return true;
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to delete zone: " + zoneName);
            return false;
        }
    }

    // ============================================
    // Auth Query Methods
    // ============================================

    @Nullable
    public List<String> getAuthedPlayersByZoneId(String zoneId) {
        try {
            return authRepository.findByZone(zoneId);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Error finding zone players by zone name");
            return null;
        }
    }

    public boolean playerIsAuthed(String zoneId, String playerId) {
        if (playerId == null)
            return false;

        return getAuthedPlayersByZoneId(zoneId).contains(playerId);
    }

    // ============================================
    // Auth Write Methods
    // ============================================

    public CreateResult AuthenticatePlayerInZone(String zoneId, String playerId) {
        if (playerIsAuthed(zoneId, playerId)) {
            return CreateResult.ALREADY_EXISTS;
        }

        try {
            authRepository.save(ZoneAuthorization.create(zoneId, playerId));
            LOGGER.atInfo().log("Created zone authorization in zone: " + zoneId +" for player: "+playerId);
            return CreateResult.SUCCESS;
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to authorize player: "+playerId+ " in zone: " + zoneId);
            return CreateResult.ERROR;
        }
    }

    public boolean ClearZoneAuthentications(String zoneId) {
        try {
            List<String> playerAuths = authRepository.findByZone(zoneId);
            playerAuths.forEach((playerId) -> {
                PlayerRef playerRef = Universe.get().getPlayerByUsername(playerId, NameMatching.EXACT);
                if (playerRef != null){
                    playerRef.sendMessage(MessageBuilder.create("Your authorization for zone '%s' has been cleared.".formatted(zoneId))
                            .color(ColorPalette.ERROR)
                            .build());
                }
            });

            authRepository.delete(zoneId);
            LOGGER.atInfo().log("Cleared authorizations for zone: " + zoneId);
            return true;
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to clear authorizations for zone: " + zoneId);
            return false;
        }
    }

    public boolean RemoveZoneAuthentication(String zoneId, String playerId){
        try {
            PlayerRef playerRef = Universe.get().getPlayerByUsername(playerId, NameMatching.EXACT);
            if (playerRef != null){
                playerRef.sendMessage(MessageBuilder.create("Your authorization for zone '%s' has been revoked.".formatted(zoneId))
                        .color(ColorPalette.ERROR)
                        .build());
            }

            authRepository.delete(zoneId, playerId);
            LOGGER.atInfo().log("Removed Authorization for player '%s' in zone '%s'".formatted(playerId, zoneId));
            return true;
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed when trying to remove authorization for player '%s' in zone '%s'".formatted(playerId, zoneId));
            return false;
        }
    }

    // ============================================
    // Reinforced Block Query Methods
    // ============================================

    public Optional<ReinforcedBlock> getReinforcedBlockAtPosition(String worldName, Vector3i position) {
        try {
            return reinforcedBlockRepository.findByPosition(worldName, position);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Error finding reinforced block by position");
            return null;
        }
    }

    public Map<String, ReinforcedBlock> getReinforcedBlocksInArea(String worldName, Vector3i boundsMin, Vector3i boundsMax) {
        try {
            return reinforcedBlockRepository.findInArea(worldName, boundsMin, boundsMax);
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Error finding blocks by area");
            return null;
        }
    }

    public Map<String, ReinforcedBlock> getReinforcedBlockInZone(Zone zone) {
        try {
            return reinforcedBlockRepository.findInArea(zone.worldName(), zone.min().toVector3i(), zone.max().toVector3i());
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Error finding blocks in zone '%s'".formatted(zone.zoneName()));
            return null;
        }
    }

    // ============================================
    // Reinforced Block Write Methods
    // ============================================

    public CreateResult CreateReinforcedBlock(String worldName, Vector3i position, int reinforcement) {
        if (getReinforcedBlockAtPosition(worldName, position).isPresent()) {
            return CreateResult.ALREADY_EXISTS;
        }

        try {
            ReinforcedBlock block = ReinforcedBlock.create(worldName, position, reinforcement);
            LOGGER.atInfo().log("Try Create Reinforced Block"+block.toString());
            reinforcedBlockRepository.save(block);
            LOGGER.atInfo().log("Created Reinforced Block in world '%s' at position '%s' with '%s' reinforcement".formatted(worldName, position.toString(), reinforcement));
            return CreateResult.SUCCESS;
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to create Reinforced Block in world '%s' at position '%s' with '%s' reinforcement: %s".formatted(worldName, position.toString(), reinforcement, e.getMessage()));
            return CreateResult.ERROR;
        }
    }

    public boolean UpdateReinforcement(ReinforcedBlock reinforcedBlock, int newReinforcement){
        try {
            reinforcedBlockRepository.save(reinforcedBlock.withNewReinforcement(newReinforcement));
            LOGGER.atInfo().log("Updated reinforcement of block '%s' to '%s'".formatted(reinforcedBlock.internalId(), newReinforcement));
            return true;
        } catch (Exception e) {
            LOGGER.atInfo().log("Failed to update reinforcement of block '%s' to '%s', current reinforcement: '%s'".formatted(reinforcedBlock.internalId(), newReinforcement, reinforcedBlock.reinforcement()));
            return false;
        }
    }

    public boolean DeleteReinforcedBlock(String worldName, Vector3i position){
        try {
            Optional<ReinforcedBlock> reinforcedBlock = reinforcedBlockRepository.findByPosition(worldName, position);
            if (reinforcedBlock.isPresent()){
                reinforcedBlockRepository.delete(reinforcedBlock.get().internalId());
                LOGGER.atInfo().log("Deleted Reinforced Block in world '%s' at position '%s'".formatted(worldName, position.toString()));
                return true;
            }
            LOGGER.atInfo().log("There was no Reinforced Block to delete in world '%s' at position '%s'".formatted(worldName, position.toString()));
            return false;
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed when trying to delete Reinforced Block in world '%s' at position '%s'".formatted(worldName, position.toString()));
            return false;
        }
    }

    public boolean DeleteReinforcedBlock(ReinforcedBlock reinforcedBlock){
        try {
            reinforcedBlockRepository.delete(reinforcedBlock.internalId());
            LOGGER.atInfo().log("Deleted Reinforced Block '%s'".formatted(reinforcedBlock.internalId()));
            return true;

        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed when trying to delete Reinforced Block '%s'".formatted(reinforcedBlock.internalId()));
            return false;
        }
    }

    public boolean DeleteReinforcedBlocksInArea(String worldName, Vector3i boundsMin, Vector3i boundsMax){
        try {
            reinforcedBlockRepository.deleteInArea(worldName, boundsMin, boundsMax);
            LOGGER.atInfo().log("Deleted Reinforced Blocks in world '%s' in area - min:'%s', max:'%s'".formatted(worldName, boundsMin.toString(), boundsMax.toString()));
            return true;
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed when trying to delete Reinforced Blocks in world '%s' area - min:'%s', max:'%s'".formatted(worldName, boundsMin.toString(), boundsMax.toString()));
            return false;
        }
    }

    // ============================================
    // Lifecycle
    // ============================================

    public void shutdown() {
        zoneRepository.close();
        authRepository.close();
        reinforcedBlockRepository.close();
    }
}
