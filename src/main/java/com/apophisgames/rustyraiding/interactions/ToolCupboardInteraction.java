package com.apophisgames.rustyraiding.interactions;

import com.apophisgames.rustyraiding.RustyRaidingPlugin;
import com.apophisgames.rustyraiding.Zone;
import com.apophisgames.rustyraiding.ZoneService;
import com.apophisgames.rustyraiding.pages.ToolCupboardPage;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

public class ToolCupboardInteraction extends SimpleBlockInteraction {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    public static final BuilderCodec<ToolCupboardInteraction> CODEC = BuilderCodec.builder(ToolCupboardInteraction.class, ToolCupboardInteraction::new).build();

    @Override
    protected void interactWithBlock(@NonNullDecl World world, @NonNullDecl CommandBuffer<EntityStore> commandBuffer, @NonNullDecl InteractionType interactionType,
                                     @NonNullDecl InteractionContext interactionContext, @NullableDecl ItemStack itemStack, @NonNullDecl Vector3i pos, @NonNullDecl CooldownHandler cooldownHandler) {
        Ref<EntityStore> ref = interactionContext.getEntity();
        Store<EntityStore> store = ref.getStore();
        Player player = ref.getStore().getComponent(ref, Player.getComponentType());
        PlayerRef playerRefComponent = store.getComponent(ref, PlayerRef.getComponentType());

        ZoneService zoneService = RustyRaidingPlugin.get().getZoneService();
        String zoneIdFromPosition = Zone.getZoneIdFromPosition(world, pos);
        Zone zone = zoneService.getZoneByName(world.getName(), zoneIdFromPosition);

        // Create a new zone if one does not exist already
        if (zone == null){
            int zoneWidth = RustyRaidingPlugin.CONFIG.get().getWidth();
            int zoneHeight = RustyRaidingPlugin.CONFIG.get().getHeight();

            Vector3d minBounds = new Vector3d(pos.x - zoneWidth, pos.y - zoneHeight, pos.z - zoneWidth);
            Vector3d maxBounds = new Vector3d(pos.x + zoneWidth, pos.y + zoneHeight, pos.z + zoneWidth);

            zone = Zone.create(zoneIdFromPosition, world.getName(), minBounds, maxBounds);
            zoneService.createZone(zone);
            zoneService.AuthenticatePlayerInZone(zone.zoneName(), player.getDisplayName());
        }

        player.getPageManager().openCustomPage(ref, store, new ToolCupboardPage(playerRefComponent, zoneService, zone));
    }

    @Override
    protected void simulateInteractWithBlock(@NonNullDecl InteractionType interactionType, @NonNullDecl InteractionContext interactionContext, @NullableDecl ItemStack itemStack, @NonNullDecl World world, @NonNullDecl Vector3i pos) {

    }
}
