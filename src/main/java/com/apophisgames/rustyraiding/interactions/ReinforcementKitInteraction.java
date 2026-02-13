package com.apophisgames.rustyraiding.interactions;

import com.apophisgames.rustyraiding.RaidingService;
import com.apophisgames.rustyraiding.RustyRaidingPlugin;
import com.apophisgames.rustyraiding.ZoneBlockProtection;
import com.apophisgames.rustyraiding.pages.ToolCupboardPage;
import com.apophisgames.rustyraiding.reinforcedblocks.ReinforcedBlock;
import com.apophisgames.rustyraiding.util.ColorPalette;
import com.apophisgames.rustyraiding.util.MessageBuilder;
import com.apophisgames.rustyraiding.zones.Zone;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import javax.annotation.Nonnull;
import java.util.Optional;

import static com.hypixel.hytale.math.util.MathUtil.lerp;

public class ReinforcementKitInteraction extends SimpleBlockInteraction {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    public static final BuilderCodec<ReinforcementKitInteraction> CODEC = BuilderCodec.builder(ReinforcementKitInteraction.class, ReinforcementKitInteraction::new).build();

    @Override
    protected void interactWithBlock(@NonNullDecl World world, @NonNullDecl CommandBuffer<EntityStore> commandBuffer, @NonNullDecl InteractionType interactionType,
                                     @NonNullDecl InteractionContext interactionContext, @NullableDecl ItemStack itemStack, @NonNullDecl Vector3i pos, @NonNullDecl CooldownHandler cooldownHandler) {
        Ref<EntityStore> ref = interactionContext.getEntity();
        Store<EntityStore> store = ref.getStore();
        Player player = ref.getStore().getComponent(ref, Player.getComponentType());

        if (player == null)
            return;

        Inventory inv = player.getInventory();
        ItemStack tool = inv.getItemInHand();
        if (tool == null || tool.isBroken())
            return;

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null)
            return;

        RaidingService raidingService = RustyRaidingPlugin.get().getZoneService();
        Zone zone = raidingService.getZoneAt(world.getName(), pos.toVector3d());
        if (zone == null)
            return;

        boolean isAuthed = raidingService.playerIsAuthed(zone.zoneName(), player.getDisplayName());
        if (!isAuthed){
            interactionContext.getState().state = InteractionState.Failed;
            return;
        }

        int reinforcementBonus = RustyRaidingPlugin.CONFIG.get().getReinforcementToAddWithKit();
        int maxReinforcementThreshold = RustyRaidingPlugin.CONFIG.get().getMaxReinforcementThreshold();
        int newReinforcement = reinforcementBonus;

        Optional<ReinforcedBlock> reinforcedBlock = raidingService.getReinforcedBlockAtPosition(world.getName(), pos);
        if (reinforcedBlock.isEmpty()){
            newReinforcement += RustyRaidingPlugin.CONFIG.get().getReinforceBlockAmount();
            newReinforcement = Integer.min(maxReinforcementThreshold, newReinforcement);
            raidingService.CreateReinforcedBlock(world.getName(), pos, newReinforcement);
        } else {
            int currentReinforcement = reinforcedBlock.get().reinforcement();
            if (currentReinforcement >= maxReinforcementThreshold){
                playerRef.sendMessage(MessageBuilder.create("Reinforcement of block at [%s] has reached the maximum limit of: %s".formatted(pos.toString(), maxReinforcementThreshold))
                        .color(ColorPalette.MUTED)
                        .build());
                PlayMaxedReinforcementEffects(world, pos);
                return;
            }
            newReinforcement += currentReinforcement;
            newReinforcement = Integer.min(maxReinforcementThreshold, newReinforcement);
            raidingService.UpdateReinforcement(reinforcedBlock.get(), newReinforcement);
        }

        PlayReinforcedAddEffects(world, pos, (float) newReinforcement / maxReinforcementThreshold);

        if (player.getGameMode() != GameMode.Creative)
            RemoveDurability(inv, tool, playerRef);

        playerRef.sendMessage(MessageBuilder.create("Added %s reinforcement to block at [%s], total: %s".formatted(reinforcementBonus, pos.toString(), newReinforcement))
                .color(ColorPalette.MUTED)
                .build());

    }

    private static void PlayReinforcedAddEffects(World world, Vector3i blockPosition, float pitchLerp){
        Vector3d position = new Vector3d(blockPosition).add(0.5, 0.5, 0.5);
        EntityStore store = world.getEntityStore();
        int index = SoundEvent.getAssetMap().getIndex("SFX_Metal_Break");
        ParticleUtil.spawnParticleEffect("Block_Hit_Metal", position, store.getStore());
        SoundUtil.playSoundEvent3d(index, SoundCategory.SFX, position.x, position.y, position.z, 1.0f, lerp(1.0f, 2.0f, pitchLerp), store.getStore());
    }

    private static void PlayMaxedReinforcementEffects(World world, Vector3i blockPosition){
        Vector3d position = new Vector3d(blockPosition).add(0.5, 0.5, 0.5);
        EntityStore store = world.getEntityStore();
        int index = SoundEvent.getAssetMap().getIndex("SFX_Metal_Hit");
        SoundUtil.playSoundEvent3d(index, SoundCategory.SFX, position.x, position.y, position.z, 0.5f, 0.8F, store.getStore());
    }

    private static void RemoveDurability(@Nonnull Inventory inv, @Nonnull ItemStack tool, @Nonnull PlayerRef playerRef){
        ItemStack damaged = tool.withDurability(tool.getDurability()-1.0);
        ItemContainer targetContainer;
        short slotIndex;

        if (inv.getActiveToolsSlot() != -1 && tool.equals(inv.getToolsItem())) {
            targetContainer = inv.getTools();
            slotIndex = inv.getActiveToolsSlot();
        } else {
            targetContainer = inv.getHotbar();
            slotIndex = inv.getActiveHotbarSlot();
        }

        if (targetContainer != null && slotIndex != -1) {
            targetContainer.setItemStackForSlot(slotIndex, damaged);
            // send the update inventory data as packet
            playerRef.getPacketHandler().writeNoCache(inv.toPacket());
        }
    }

    @Override
    protected void simulateInteractWithBlock(@NonNullDecl InteractionType interactionType, @NonNullDecl InteractionContext interactionContext, @NullableDecl ItemStack itemStack, @NonNullDecl World world, @NonNullDecl Vector3i pos) {

    }
}
