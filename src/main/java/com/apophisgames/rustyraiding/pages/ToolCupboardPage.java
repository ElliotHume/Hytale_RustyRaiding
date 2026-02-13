package com.apophisgames.rustyraiding.pages;

import com.apophisgames.rustyraiding.RustyRaidingPlugin;
import com.apophisgames.rustyraiding.zones.Zone;
import com.apophisgames.rustyraiding.ZoneService;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import java.util.*;

public class ToolCupboardPage extends InteractiveCustomUIPage<ToolCupboardPage.ToolCupboardEventData> {

    private final ZoneService zoneService;
    private final Zone zone;

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public ToolCupboardPage(@Nonnull PlayerRef playerRef, ZoneService zoneService, Zone zone) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, ToolCupboardEventData.CODEC);
        this.zoneService = zoneService;
        this.zone = zone;
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
        if (zone != null){
            List<String> authedPlayers = zoneService.getAuthedPlayersByZoneId(zone.zoneName());
            commandBuilder.set("#ZoneName.Text", zone.zoneName());
            commandBuilder.set("#PlayerCount.Text", "PLAYERS (" + authedPlayers.size() + ")");
            buildPlayerList(commandBuilder, eventBuilder, authedPlayers);
        } else {
            commandBuilder.set("#ZoneName.Text", "Not Found -> !!! TC zone is overlapping another !!!");
            commandBuilder.set("#PlayerCount.Text", "PLAYERS (?)");

            buildPlayerList(commandBuilder, eventBuilder, new ArrayList<>());
        }

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

        switch (data.action) {
            case "GrantPlayerAuth":
                if (zone != null && player != null) {
                    zoneService.AuthenticatePlayerInZone(zone.zoneName(), player.getDisplayName());
                    playerRef.sendMessage(Message.raw("Authenticated player '%s' in zone '%s'".formatted(player.getDisplayName(), zone.zoneName())));
                }
                refreshPage(ref, store);
                break;

            case "ClearAuth":
                if (zone != null) {
                    zoneService.ClearZoneAuthentications(zone.zoneName());
                    playerRef.sendMessage(Message.raw("Cleared ALL authorizations in zone '%s'".formatted(zone.zoneName())));
                }
                refreshPage(ref, store);
                break;

            case "RemovePlayerAuth":
                if (zone != null && data.id != null) {
                    zoneService.RemoveZoneAuthentication(zone.zoneName(), data.id);
                    playerRef.sendMessage(Message.raw("Removed authentication for player '%s' in zone '%s'".formatted(data.id, zone.zoneName())));
                }
                refreshPage(ref, store);
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
        if (zone != null){
            List<String> authedPlayers = zoneService.getAuthedPlayersByZoneId(zone.zoneName());
            commandBuilder.set("#ZoneName.Text", zone.zoneName());
            commandBuilder.set("#PlayerCount.Text", "PLAYERS (" + authedPlayers.size() + ")");
            buildPlayerList(commandBuilder, eventBuilder, authedPlayers);
        } else {
            commandBuilder.set("#ZoneName.Text", "Not Found");
            commandBuilder.set("#PlayerCount.Text", "PLAYERS (?)");

            buildPlayerList(commandBuilder, eventBuilder, new ArrayList<>());
        }

        sendUpdate(commandBuilder, eventBuilder, false);
    }
}