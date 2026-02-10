package com.apophisgames.rustyraiding;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * consolidated systems for Zone block protections.
 */
public class ZoneBlockProtection {

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

            boolean isAuthed = service.playerIsAuthed(zone.zoneName(), player.getDisplayName());

            if (!isAuthed)
                event.setCancelled(true);
        }
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
}
