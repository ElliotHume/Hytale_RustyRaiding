package com.apophisgames.rustyraiding;

import com.apophisgames.rustyraiding.util.ColorPalette;
import com.apophisgames.rustyraiding.util.MessageBuilder;
import com.hypixel.hytale.builtin.buildertools.BuilderToolsPlugin;
import com.hypixel.hytale.builtin.buildertools.PrototypePlayerBuilderToolSettings;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.matrix.Matrix4d;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.DebugShape;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.debug.DebugUtils;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;

/**
 * Command to manage SafeZones.
 * 
 * <p>Usage:
 * <ul>
 *  <li>/safezone create <name> [--pvp-enabled] - Create a zone from current builder selection</li>
 *  <li>/safezone update <name> [--pvp-enabled] - Update a zone (selection optional if flag provided)</li>
 *  <li>/safezone delete <name> - Delete a zone</li>
 *  <li>/safezone list - List all zones in current world</li>
 * </ul>
 */
public class RaidingCommand extends CommandBase {

    private final RustyRaidingPlugin plugin;

    public RaidingCommand(RustyRaidingPlugin plugin) {
        super("safezone", "Manage SafeZones");
        this.plugin = plugin;
        
        this.addAliases("sz");

        // Register subcommands
        this.addSubCommand(new CreateSubCommand(plugin));
        this.addSubCommand(new UpdateSubCommand(plugin));
        this.addSubCommand(new EditSubCommand(plugin));
        this.addSubCommand(new DeleteSubCommand(plugin));
        this.addSubCommand(new ListSubCommand(plugin));
        this.addSubCommand(new ShowSubCommand(plugin));

        this.requirePermission("easy-safezone.admin");
    }

    @Override
    protected void executeSync(CommandContext context) {
        if (!context.isPlayer()) {
            context.sendMessage(MessageBuilder.create("This command is only for players.").color(ColorPalette.ERROR).build());
            return;
        }
        
        // Show help if no subcommand matched
        context.sendMessage(MessageBuilder.create("Usage:").color(ColorPalette.INFO).build());
        context.sendMessage(MessageBuilder.create("  /safezone create <name>").color(ColorPalette.WHITE).build());
        context.sendMessage(MessageBuilder.create("  /safezone update <name> - Update bounds from selection").color(ColorPalette.WHITE).build());
        context.sendMessage(MessageBuilder.create("  /safezone flag <name> <flag> <true/false> - Set protection").color(ColorPalette.WHITE).build());
        context.sendMessage(MessageBuilder.create("  /safezone edit <name>").color(ColorPalette.WHITE).build());
        context.sendMessage(MessageBuilder.create("  /safezone delete <name>").color(ColorPalette.WHITE).build());
        context.sendMessage(MessageBuilder.create("  /safezone list").color(ColorPalette.WHITE).build());
        context.sendMessage(MessageBuilder.create("  /safezone show <name>").color(ColorPalette.WHITE).build());
    }

    // ============================================
    // Helpers
    // ============================================

    /**
     * Get selection bounds from player's builder tools, if available.
     * @return Pair of min/max Vector3d, or null if no valid selection
     */
    @Nullable
    private static SelectionBounds getSelectionBounds(Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef playerRef) {
        Player player = (Player) store.getComponent(ref, Player.getComponentType());
        if (player == null || !PrototypePlayerBuilderToolSettings.isOkayToDoCommandsOnSelection(ref, player, store)) {
            return null;
        }

        BuilderToolsPlugin.BuilderState builderState = BuilderToolsPlugin.getState(player, playerRef);
        if (builderState.getSelection() == null || !builderState.getSelection().hasSelectionBounds()) {
            return null;
        }

        Vector3i min = builderState.getSelection().getSelectionMin();
        Vector3i max = builderState.getSelection().getSelectionMax();

        // Convert to double (world coordinates) covering full blocks
        Vector3d minD = new Vector3d(min.x, min.y, min.z);
        Vector3d maxD = new Vector3d(max.x + 1.0, max.y + 1.0, max.z + 1.0);

        return new SelectionBounds(minD, maxD);
    }

    private record SelectionBounds(Vector3d min, Vector3d max) {}

    // ============================================
    // SubCommands
    // ============================================

    /**
     * /safezone create <name>
     * Creates a new safezone from current selection. Fails if zone already exists.
     * PVP is disabled by default (safe zone).
     */
    public static class CreateSubCommand extends AbstractPlayerCommand {
        private final RustyRaidingPlugin plugin;
        private final RequiredArg<String> nameArg;

        public CreateSubCommand(RustyRaidingPlugin plugin) {
            super("create", "Create a SafeZone from selection");
            this.plugin = plugin;
            this.nameArg = this.withRequiredArg("name", "Zone name", ArgTypes.STRING);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            String zoneName = nameArg.get(context);

            // Selection is required for create
            SelectionBounds bounds = getSelectionBounds(store, ref, playerRef);
            if (bounds == null) {
                playerRef.sendMessage(MessageBuilder.create("You must have a valid builder selection to create a zone.").color(ColorPalette.ERROR).build());
                return;
            }

            Zone zone = Zone.create(zoneName, world.getName(), bounds.min(), bounds.max());
            // Default to safe zone (all flags false)
            
            ZoneService.CreateResult result = plugin.getZoneService().createZone(zone);

            switch (result) {
                case SUCCESS -> playerRef.sendMessage(
                    MessageBuilder.create("SafeZone '" + zoneName + "' created!")
                        .color(ColorPalette.SUCCESS)
                        .append(" (Modify flags with /safezone flag)", ColorPalette.MUTED)
                        .build());
                case ALREADY_EXISTS -> playerRef.sendMessage(
                    MessageBuilder.create("A zone named '" + zoneName + "' already exists in this world.")
                        .color(ColorPalette.ERROR)
                        .build());
                case ERROR -> playerRef.sendMessage(
                    MessageBuilder.create("Failed to create zone. Check server logs.")
                        .color(ColorPalette.ERROR)
                        .build());
            }
        }
    }

    /**
     * /safezone update <name>
     * Updates an existing zone. Selection is optional if a flag is provided.
     */
    public static class UpdateSubCommand extends AbstractPlayerCommand {
        private final RustyRaidingPlugin plugin;
        private final RequiredArg<String> nameArg;

        public UpdateSubCommand(RustyRaidingPlugin plugin) {
            super("update", "Update a SafeZone");
            this.plugin = plugin;
            this.nameArg = this.withRequiredArg("name", "Zone name", ArgTypes.STRING);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            String zoneName = nameArg.get(context);

            // Check if selection is available
            SelectionBounds bounds = getSelectionBounds(store, ref, playerRef);
            
            if (bounds == null) {
                playerRef.sendMessage(
                    MessageBuilder.create("You must provide a selection to update bounds.")
                        .color(ColorPalette.ERROR)
                        .build());
                return;
            }

            Vector3d newMin = bounds.min();
            Vector3d newMax = bounds.max();

            ZoneService.UpdateResult result = plugin.getZoneService().updateZone(
                world.getName(), zoneName, newMin, newMax);

            switch (result) {
                case SUCCESS -> {
                    playerRef.sendMessage(MessageBuilder.create("SafeZone '" + zoneName + "' bounds updated!").color(ColorPalette.SUCCESS).build());
                }
                case NOT_FOUND -> playerRef.sendMessage(
                    MessageBuilder.create("Zone '" + zoneName + "' not found in this world.")
                        .color(ColorPalette.ERROR)
                        .build());
                case ERROR -> playerRef.sendMessage(
                    MessageBuilder.create("Failed to update zone. Check server logs.")
                        .color(ColorPalette.ERROR)
                        .build());
            }
        }
    }

    /**
     * /safezone edit <name>
     */
    public static class EditSubCommand extends AbstractPlayerCommand {
        private final RustyRaidingPlugin plugin;
        private final OptionalArg<String> nameArg;

        public EditSubCommand(RustyRaidingPlugin plugin) {
            super("edit", "Edit a SafeZone (selects it)");
            this.plugin = plugin;
            this.nameArg = this.withOptionalArg("name", "Zone name to edit", ArgTypes.STRING);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            Zone zone;

            if (nameArg.provided(context)) {
                // Find by name
                String zoneName = nameArg.get(context);
                zone = plugin.getZoneService().getZoneByName(world.getName(), zoneName);
                if (zone == null) {
                    playerRef.sendMessage(MessageBuilder.create("Zone '" + zoneName + "' not found in this world.").color(ColorPalette.ERROR).build());
                    return;
                }
                
                // Warn if far away
                TransformComponent transform = (TransformComponent) store.getComponent(ref, TransformComponent.getComponentType());
                if (transform != null) {
                    double distance = ZoneService.distanceToZone(zone, transform.getPosition());
                    if (distance > 100.0) {
                         playerRef.sendMessage(MessageBuilder.create("Warning: Zone is " + (int) distance + " blocks away. Center: " + formatCenter(zone))
                            .color(ColorPalette.WARNING)
                            .build());
                    }
                }
            } else {
                // Auto scan
                TransformComponent transform = (TransformComponent) store.getComponent(ref, TransformComponent.getComponentType());
                if (transform == null) return;
                
                zone = plugin.getZoneService().getClosestZone(world.getName(), transform.getPosition(), 100.0);
                if (zone == null) {
                    playerRef.sendMessage(MessageBuilder.create("No zones found within 100 blocks.")
                        .color(ColorPalette.ERROR)
                        .append(" Use /safezone edit <name> to edit a specific zone.", ColorPalette.MUTED)
                        .build());
                    return;
                }
                playerRef.sendMessage(MessageBuilder.create("Found closest zone: " + zone.zoneName()).color(ColorPalette.SUCCESS).build());
            }

            // Set selection
            setBuilderSelection(store, ref, playerRef, zone);
            playerRef.sendMessage(MessageBuilder.create("Selected zone '" + zone.zoneName() + "'. Use builder tools to modify and then /safezone update.").color(ColorPalette.SUCCESS).build());
        }

        private void setBuilderSelection(Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef playerRef, Zone zone) {
            Player player = (Player) store.getComponent(ref, Player.getComponentType());
            if (player == null) return;

            BuilderToolsPlugin.addToQueue(player, playerRef, (r, builderState, componentAccessor) -> {
                // Zone bounds match the world coordinates
                Vector3i min = new Vector3i((int) zone.min().x, (int) zone.min().y, (int) zone.min().z);
                Vector3i max = new Vector3i((int) zone.max().x - 1, (int) zone.max().y - 1, (int) zone.max().z - 1);
                
                // Use the select method which handles selection update logic
                builderState.select(min, max, "Safe Zone: " + zone.zoneName(), componentAccessor);
            });
        }
    }

    /**
     * /safezone delete <name>
     */
    public static class DeleteSubCommand extends AbstractPlayerCommand {
        private final RustyRaidingPlugin plugin;
        private final RequiredArg<String> nameArg;

        public DeleteSubCommand(RustyRaidingPlugin plugin) {
            super("delete", "Delete a SafeZone");
            this.plugin = plugin;
            this.nameArg = this.withRequiredArg("name", "Zone name", ArgTypes.STRING);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            String zoneName = nameArg.get(context);
            boolean deleted = plugin.getZoneService().deleteZone(world.getName(), zoneName);

            if (deleted) {
                playerRef.sendMessage(MessageBuilder.create("SafeZone '" + zoneName + "' deleted.").color(ColorPalette.SUCCESS).build());
            } else {
                playerRef.sendMessage(MessageBuilder.create("Zone '" + zoneName + "' not found in this world.").color(ColorPalette.ERROR).build());
            }
        }
    }

    /**
     * /safezone list
     */
    public static class ListSubCommand extends AbstractPlayerCommand {
        private final RustyRaidingPlugin plugin;

        public ListSubCommand(RustyRaidingPlugin plugin) {
            super("list", "List SafeZones");
            this.plugin = plugin;
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            var zones = plugin.getZoneService().getZones(world.getName());
            
            playerRef.sendMessage(MessageBuilder.create("SafeZones in " + world.getName() + ":").color(ColorPalette.INFO).build());
            if (zones.isEmpty()) {
                playerRef.sendMessage(MessageBuilder.create("  (None)").color(ColorPalette.MUTED).build());
            } else {
                for (Zone zone : zones) {
                    playerRef.sendMessage(MessageBuilder.create("  - " + zone.zoneName()).color(ColorPalette.WHITE)
                        .append(" " + formatCenter(zone), ColorPalette.MUTED)
                        .build());
                }
            }
        }
    }

    /**
     * /safezone show <name>
     * Shows the closest zone (within 100 blocks) or a specific zone by name.
     */
    public static class ShowSubCommand extends AbstractPlayerCommand {
        private static final double MAX_AUTO_DISTANCE = 100.0;
        private static final float DISPLAY_TIME = 30.0f;

        private final RustyRaidingPlugin plugin;

        public ShowSubCommand(RustyRaidingPlugin plugin) {
            super("show", "Show zone boundaries (debug visualization)");
            this.plugin = plugin;
            
            // Add variant for showing by name
            this.addUsageVariant(new ShowByNameVariant(plugin));
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            // Get player position
            TransformComponent transform = (TransformComponent) store.getComponent(ref, TransformComponent.getComponentType());
            if (transform == null) {
                playerRef.sendMessage(MessageBuilder.create("Could not get player position.").color(ColorPalette.ERROR).build());
                return;
            }
            Vector3d playerPos = transform.getPosition();

            // Find closest zone within 100 blocks
            Zone closest = plugin.getZoneService().getClosestZone(world.getName(), playerPos, MAX_AUTO_DISTANCE);
            
            if (closest == null) {
                playerRef.sendMessage(MessageBuilder.create("No zones found within " + (int) MAX_AUTO_DISTANCE + " blocks.")
                    .color(ColorPalette.ERROR)
                    .append(" Use /safezone show <name> to show a specific zone.", ColorPalette.MUTED)
                    .build());
                return;
            }

            double distance = ZoneService.distanceToZone(closest, playerPos);
            renderZone(world, closest);
            playerRef.sendMessage(MessageBuilder.create("Showing zone '" + closest.zoneName() + "'")
                .color(ColorPalette.SUCCESS)
                .append(" (" + (int) distance + " blocks away)", ColorPalette.MUTED)
                .build());
        }

        /**
         * Variant: /safezone show <name>
         */
        private static class ShowByNameVariant extends AbstractPlayerCommand {
            private final RustyRaidingPlugin plugin;
            private final RequiredArg<String> nameArg;

            public ShowByNameVariant(RustyRaidingPlugin plugin) {
                super("Show zone by name");
                this.plugin = plugin;
                this.nameArg = this.withRequiredArg("name", "Zone name", ArgTypes.STRING);
            }

            @Override
            protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
                String zoneName = nameArg.get(context);

                Zone zone = plugin.getZoneService().getZoneByName(world.getName(), zoneName);
                if (zone == null) {
                    playerRef.sendMessage(MessageBuilder.create("Zone '" + zoneName + "' not found in this world.")
                        .color(ColorPalette.ERROR)
                        .build());
                    return;
                }

                // Get player position for distance check
                TransformComponent transform = (TransformComponent) store.getComponent(ref, TransformComponent.getComponentType());
                if (transform != null) {
                    double distance = ZoneService.distanceToZone(zone, transform.getPosition());
                    if (distance > MAX_AUTO_DISTANCE) {
                        playerRef.sendMessage(MessageBuilder.create("Warning: Zone is " + (int) distance + " blocks away. Center: " + formatCenter(zone))
                            .color(ColorPalette.WARNING)
                            .build());
                    }
                }

                renderZone(world, zone);
                playerRef.sendMessage(MessageBuilder.create("Showing zone '" + zoneName + "'")
                    .color(ColorPalette.SUCCESS)
                    .build());
            }
        }

        /**
         * Render a zone using debug shapes.
         */
        private static void renderZone(World world, Zone zone) {
            // Calculate center and dimensions
            Vector3d center = new Vector3d(
                (zone.min().x + zone.max().x) / 2,
                (zone.min().y + zone.max().y) / 2,
                (zone.min().z + zone.max().z) / 2
            );
            
            double sizeX = zone.max().x - zone.min().x;
            double sizeY = zone.max().y - zone.min().y;
            double sizeZ = zone.max().z - zone.min().z;

            Vector3f color = new Vector3f(0.3f, 1.0f, 0.3f);  // Green for safe zones

            // Create transform matrix for the cube
            Matrix4d matrix = new Matrix4d();
            matrix.identity();
            matrix.translate(center);
            matrix.scale(sizeX, sizeY, sizeZ);

            // Draw the debug cube
            DebugUtils.add(world, DebugShape.Cube, matrix, color, DISPLAY_TIME, true);
        }
    }

    private static String formatCenter(Zone zone) {
        double cx = (zone.min().x + zone.max().x) / 2;
        double cy = (zone.min().y + zone.max().y) / 2;
        double cz = (zone.min().z + zone.max().z) / 2;
        return String.format("[%.1f, %.1f, %.1f]", cx, cy, cz);
    }
}
