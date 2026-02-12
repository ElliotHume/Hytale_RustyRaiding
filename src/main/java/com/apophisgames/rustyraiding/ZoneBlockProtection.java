package com.apophisgames.rustyraiding;

import com.apophisgames.rustyraiding.reinforcedblocks.ReinforcedBlock;
import com.apophisgames.rustyraiding.zones.Zone;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockBreakingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * consolidated systems for Zone block protections.
 */
public class ZoneBlockProtection {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final List<String> BYPASS_GATHER_TYPES = List.of("Soils");
    //private static final String TOOL_CUPBOARD_GATHER_TYPE = "RustyRaiding_ToolCupboard";

    private static final Query<EntityStore> QUERY = Query.and(
        Player.getComponentType(),
        TransformComponent.getComponentType()
    );

    public static class PlaceBlock extends EntityEventSystem<EntityStore, PlaceBlockEvent> {
        private final Supplier<ZoneService> zoneService;

        public PlaceBlock(Supplier<ZoneService> zoneService) {
            super(PlaceBlockEvent.class);
            this.zoneService = zoneService;
        }

        @Nonnull
        @Override
        public Query<EntityStore> getQuery() {
            return QUERY;
        }

        @Nullable
        @Override
        public SystemGroup<EntityStore> getGroup() {
             return null; 
        }

        @Override
        public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk, 
                           @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer, 
                           @Nonnull PlaceBlockEvent event) {

            // Raiders are allowed to bypass protections for certain types of blocks, like soils
            ItemStack itemInHand = event.getItemInHand();
            if (itemInHand != null) {
                String blockKey = itemInHand.getBlockKey();
                if (blockKey != null){
                    BlockType blockAsset = BlockType.getAssetMap().getAsset(blockKey);
                    if (IsAllowedBlockType(blockAsset))
                        return;
                }
            }

            ZoneService service = zoneService.get();
            if (service == null) return;

            Player player = chunk.getComponent(index, Player.getComponentType());
            if (player == null) return;

            World world = store.getExternalData().getWorld();
            TransformComponent transform = chunk.getComponent(index, TransformComponent.getComponentType());
            if (transform == null) return;

            Vector3i target = event.getTargetBlock();
            Vector3d targetPos = new Vector3d(target.x, target.y, target.z);

            Zone zone = service.getZoneAt(world.getName(), targetPos);
            if (zone == null) return;

            boolean isAuthed = service.playerIsAuthed(zone.zoneName(), player.getDisplayName());

            if (!isAuthed)
                event.setCancelled(true);
        }
    }

    public static class BreakBlock extends EntityEventSystem<EntityStore, BreakBlockEvent> {
        private final Supplier<ZoneService> zoneService;

        public BreakBlock(Supplier<ZoneService> zoneService) {
            super(BreakBlockEvent.class);
            this.zoneService = zoneService;
        }

        @Nonnull
        @Override
        public Query<EntityStore> getQuery() {
            return QUERY;
        }

        @Nullable
        @Override
        public SystemGroup<EntityStore> getGroup() {
             return null;
        }

        @Override
        public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk, 
                           @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer, 
                           @Nonnull BreakBlockEvent event) {

            ZoneService service = zoneService.get();
            if (service == null) return;

            Player player = chunk.getComponent(index, Player.getComponentType());
            if (player == null) return;

            World world = store.getExternalData().getWorld();
            
            Vector3i target = event.getTargetBlock();
            Vector3d targetPos = new Vector3d(target.x, target.y, target.z);

            Zone zone = service.getZoneAt(world.getName(), targetPos);
            if (zone == null) return;

            BlockType blockType = event.getBlockType();
            if (blockType.getId().equals("Bench_Tool_Cupboard")){
                service.deleteZone(zone.worldName(), zone.zoneName());
                service.DeleteReinforcedBlocksInArea(zone.worldName(), zone.min().toVector3i(), zone.max().toVector3i());
                return;
            }

            if (IsAllowedBlockType(blockType))
                return;

            boolean isAuthed = service.playerIsAuthed(zone.zoneName(), player.getDisplayName());

            if (!isAuthed){
                boolean shouldCancelBreak = true;
                Optional<ReinforcedBlock> reinforcedBlock = service.getReinforcedBlockAtPosition(world.getName(), target);
                if (reinforcedBlock.isEmpty()){
                    int startingReinforcement = RustyRaidingPlugin.CONFIG.get().getReinforceBlockAmount()-1;
                    // Create a reinforced block here if it is the first time a block is being broken without authorization (with -1 reinforcement because of this break).
                    service.CreateReinforcedBlock(world.getName(), target, startingReinforcement);
                    PlayReinforcedBreakEffects(target, startingReinforcement);
                } else {
                    int currentReinforcement = reinforcedBlock.get().reinforcement();
                    if (currentReinforcement > 0){
                        service.UpdateReinforcement(reinforcedBlock.get(), currentReinforcement-1);
                        PlayReinforcedBreakEffects(target, currentReinforcement-1);
                    } else {
                        service.DeleteReinforcedBlock(reinforcedBlock.get());
                        shouldCancelBreak = false;
                    }
                }

                if (shouldCancelBreak)
                    event.setCancelled(true);
            }

        }
    }

    // TODO: Move to a new package?
    private static void PlayReinforcedBreakEffects(Vector3i position, int reinforcement){
        // TODO: Play reinforced break effects (particles, sounds)
    }

    public static class UseBlock extends EntityEventSystem<EntityStore, UseBlockEvent.Pre> {
        private final Supplier<ZoneService> zoneService;

        public UseBlock(Supplier<ZoneService> zoneService) {
            super(UseBlockEvent.Pre.class);
            this.zoneService = zoneService;
        }

        @Nonnull
        @Override
        public Query<EntityStore> getQuery() {
            return QUERY;
        }

        @Nullable
        @Override
        public SystemGroup<EntityStore> getGroup() {
             return null;
        }

        @Override
        public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk, 
                           @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer, 
                           @Nonnull UseBlockEvent.Pre event) {

            BlockType blockType = event.getBlockType();
            if (blockType.getId().equals("Bench_Tool_Cupboard")){
                return;
            }

            ZoneService service = zoneService.get();
            if (service == null) return;

            Player player = chunk.getComponent(index, Player.getComponentType());
            if (player == null) return;

            World world = store.getExternalData().getWorld();
            
            Vector3i target = event.getTargetBlock();
            Vector3d targetPos = new Vector3d(target.x, target.y, target.z);

            Zone zone = service.getZoneAt(world.getName(), targetPos);
            if (zone == null) return;

            boolean isAuthed = service.playerIsAuthed(zone.zoneName(), player.getDisplayName());

            if (!isAuthed)
                event.setCancelled(true);
        }
    }

    private static boolean IsAllowedBlockType(BlockType blockType){
        if (blockType == null)
            return false;

        // Raiders are allowed to bypass protections for certain types of blocks, like soils
        BlockGathering gathering = blockType.getGathering();
        if (gathering != null){
            BlockBreakingDropType breakingDropType = gathering.getBreaking();
            if (breakingDropType != null){
                String gatherType = breakingDropType.getGatherType();
                if(gatherType != null && !gatherType.trim().isEmpty()){
                    return BYPASS_GATHER_TYPES.contains(gatherType);
                }
            }
        }

        return false;
    }
}
