package com.apophisgames.rustyraiding;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.protocol.InteractionChainData;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChain;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains;
import com.hypixel.hytale.protocol.packets.world.ServerSetBlock;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.io.adapter.PacketFilter;
import com.hypixel.hytale.server.core.io.handlers.game.GamePacketHandler;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.logger.HytaleLogger;

import java.util.function.Supplier;

public class ZoneInteractionPacketHandler implements PacketFilter {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private final Supplier<ZoneService> zoneService;

    public ZoneInteractionPacketHandler(Supplier<ZoneService> zoneService) {
        this.zoneService = zoneService;
    }

    @Override
    public boolean test(PacketHandler packetHandler, Packet packet) {
        if (!(packetHandler instanceof GamePacketHandler handler)) {
            return false;
        }

        if (!(packet instanceof SyncInteractionChains chains)) {
            return false;
        }

        if (chains.updates.length == 0) {
            return false;
        }

        SyncInteractionChain chain = chains.updates[0];
        PlayerRef playerRef = handler.getPlayerRef();
        if (chain.interactionType != InteractionType.Use) {
            return false;
        }

        Ref<EntityStore> ref = playerRef.getReference();
        InteractionChainData data = chain.data;
        if (ref == null || !ref.isValid() || data == null || data.blockPosition == null) {
            return false;
        }

        World world = ref.getStore().getExternalData().getWorld();
        BlockPosition pos = data.blockPosition;
        Vector3d targetPos = new Vector3d(pos.x, pos.y, pos.z);

        ZoneService service = zoneService.get();
        if (service == null) {
            return false;
        }

        Player player = ref.getStore().getComponent(ref, Player.getComponentType());
        if (player == null){
            return false;
        }

        // If there is no protection zone, or the zone allows block use, allow the packet
        Zone zone = service.getZoneAt(world.getName(), targetPos);
        if (zone == null) return false;

        world.execute(() -> {
            if (!ref.isValid()) return;

            // Access Player component SAFELY on world thread
            boolean isAuthed = service.playerIsAuthed(zone.zoneName(), player.getDisplayName());

            if (isAuthed) {
                // User has Auth, so we manually apply the packet we blocked
                try {
                    LOGGER.atFine().log("Player %s used bypass to interact in protected zone %s", player.getDisplayName(), zone.zoneName());
                    handler.handle(chains);
                } catch (Exception e) {
                    LOGGER.atSevere().withCause(e).log("Error while handling bypassed interaction packet for player %s", player.getDisplayName());
                }
            } else {
                // Valid block: Send visual revert to client
                WorldChunk chunk = world.getChunk(ChunkUtil.indexChunkFromBlock(pos.x, pos.z));
                if (chunk != null) {
                    int id = chunk.getBlock(pos.x, pos.y, pos.z);
                    int filler = chunk.getFiller(pos.x, pos.y, pos.z);
                    int rotation = chunk.getRotationIndex(pos.x, pos.y, pos.z);

                    handler.writeNoCache(new ServerSetBlock(pos.x, pos.y, pos.z, id, (short)filler, (byte)rotation));
                }
            }
        });
        
        return true; // Block the packet from default handling
    }
}
