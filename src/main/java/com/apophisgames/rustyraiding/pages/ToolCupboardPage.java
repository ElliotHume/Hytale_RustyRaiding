package com.apophisgames.rustyraiding.pages;

import com.apophisgames.rustyraiding.RustyRaidingPlugin;
import com.apophisgames.rustyraiding.ToolCupboardDataComponent;
import com.apophisgames.rustyraiding.Zone;
import com.apophisgames.rustyraiding.ZoneService;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.modules.accesscontrol.provider.HytaleWhitelistProvider;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import java.util.*;

public class ToolCupboardPage extends InteractiveCustomUIPage<ToolCupboardPage.ToolCupboardEventData> {

    private final Holder<ChunkStore> blockEntity;
    private final World world;

    public ToolCupboardPage(@Nonnull PlayerRef playerRef, Holder<ChunkStore> blockEntity, World world) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, ToolCupboardEventData.CODEC);
        this.blockEntity = blockEntity;
        this.world = world;
    }

    public static class ToolCupboardEventData {
        public String action;
        public String id;

        public static final BuilderCodec<ToolCupboardEventData> CODEC = ((BuilderCodec.Builder<ToolCupboardEventData>) ((BuilderCodec.Builder<ToolCupboardEventData>)
                BuilderCodec.builder(ToolCupboardEventData.class, ToolCupboardEventData::new)
                        .append(new KeyedCodec<>("Action", Codec.STRING), (ToolCupboardEventData o, String v) -> o.action = v, (ToolCupboardEventData o) -> o.action)
                        .add())
                .append(new KeyedCodec<>("ID", Codec.STRING), (ToolCupboardEventData o, String v) -> o.id = v, (ToolCupboardEventData o) -> o.id)
                .add())
                .build();
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder commandBuilder,
            @Nonnull UIEventBuilder eventBuilder,
            @Nonnull Store<EntityStore> store
    ) {
        commandBuilder.append("Pages/ToolCupboardPage.ui");

        ZoneService zoneService = RustyRaidingPlugin.get().getZoneService();
        ToolCupboardDataComponent component = blockEntity.getComponent(RustyRaidingPlugin.TOOL_CUPBOARD_COMPONENT);
        List<String> authedPlayers = zoneService.getAuthedPlayersByZoneId(component.getUuid());
        // Zone zone = zoneService.getZoneByName(world.getName(),component.getUuid());

        // Set status
        commandBuilder.set("#ZoneName.Text", component.getUuid().toString());
        commandBuilder.set("#PlayerCount.Text", "PLAYERS (" + authedPlayers.size() + ")");

        // Build player list
        buildPlayerList(commandBuilder, eventBuilder, authedPlayers);

        eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#AddAuthButton",
                new EventData().append("Action", "GrantPlayerAuth")
        );

        eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#ClearAuthButton",
                new EventData().append("Action", "ClearAuth")
        );

        eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#RefreshButton",
                new EventData().append("Action", "Refresh")
        );

        eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#CloseButton",
                new EventData().append("Action", "Close")
        );
    }

    private void buildPlayerList(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder, List<String> authedPlayers) {
        commandBuilder.clear("#PlayerList");

        if (authedPlayers.isEmpty()) {
            commandBuilder.appendInline("#PlayerList", "Label { Text: \"No players authorized\"; Anchor: (Height: 40); Style: (FontSize: 14, TextColor: #6e7da1, HorizontalAlignment: Center, VerticalAlignment: Center); }");
            return;
        }

        int i = 0;
        for (String playerName : authedPlayers) {
            String selector = "#PlayerList[" + i + "]";
            commandBuilder.append("#PlayerList", "Pages/TCPlayerAuthEntry.ui");

            commandBuilder.set(selector + " #PlayerName.Text", playerName);

            eventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    selector + " #RemoveButton",
                    new EventData().append("Action", "RemovePlayerAuth").append("ID", playerName),
                    false
            );
            i++;
        }
    }

    @Override
    public void handleDataEvent(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull ToolCupboardEventData data
    ) {
        Player player = (Player) store.getComponent(ref, Player.getComponentType());
        ZoneService zoneService = RustyRaidingPlugin.get().getZoneService();
        ToolCupboardDataComponent component = blockEntity.getComponent(RustyRaidingPlugin.TOOL_CUPBOARD_COMPONENT);
        String zoneId = component != null ? component.getUuid() : null;

        switch (data.action) {
            case "GrantPlayerAuth":
                if (data.id != null && zoneId != null) {
                    zoneService.AuthenticatePlayerInZone(zoneId, data.id);
                    playerRef.sendMessage(Message.raw("Authenticated player '%s' in zone '%s'".formatted(data.id, zoneId)));
                    refreshPage(ref, store);
                }
                break;

            case "ClearAuth":
                if (data.id != null && zoneId != null) {
                    zoneService.ClearZoneAuthentications(zoneId);
                    playerRef.sendMessage(Message.raw("Cleared ALL authorizations in zone '%s'".formatted(zoneId)));
                    refreshPage(ref, store);
                }
                break;

            case "Refresh":
                refreshPage(ref, store);
                break;

            case "Close":
                player.getPageManager().setPage(ref, store, Page.None);
                break;

            default:
                break;
        }
    }

    private void refreshPage(Ref<EntityStore> ref, Store<EntityStore> store) {
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();

        ZoneService zoneService = RustyRaidingPlugin.get().getZoneService();
        ToolCupboardDataComponent component = blockEntity.getComponent(RustyRaidingPlugin.TOOL_CUPBOARD_COMPONENT);
        List<String> authedPlayers = zoneService.getAuthedPlayersByZoneId(component.getUuid());

        commandBuilder.set("#ZoneName.Text", component.getUuid().toString());
        commandBuilder.set("#PlayerCount.Text", "PLAYERS (" + authedPlayers.size() + ")");

        // Build player list
        buildPlayerList(commandBuilder, eventBuilder, authedPlayers);

        sendUpdate(commandBuilder, eventBuilder, false);
    }
}