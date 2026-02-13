package com.apophisgames.rustyraiding;

import com.apophisgames.rustyraiding.reinforcedblocks.ReinforcedBlock;
import com.apophisgames.rustyraiding.util.ColorPalette;
import com.apophisgames.rustyraiding.util.MessageBuilder;
import com.apophisgames.rustyraiding.zones.Zone;
import com.hypixel.hytale.builtin.buildertools.BuilderToolsPlugin;
import com.hypixel.hytale.builtin.buildertools.PrototypePlayerBuilderToolSettings;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.matrix.Matrix4d;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.DebugShape;
import com.hypixel.hytale.server.core.Message;
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
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Command to manage Tool Cupboards you have authorization over
 * 
 * <p>Usage:
 * <ul>
 *  <li>/tc show - show the boundaries of the closest TC zone</li>
 * </ul>
 */
public class TCCommand extends CommandBase {

    private final RustyRaidingPlugin plugin;

    public TCCommand(RustyRaidingPlugin plugin) {
        super("tc", "Manage Tool Cupboards you have authorization over");
        this.plugin = plugin;

        // Register subcommands
        this.addSubCommand(new ShowSubCommand(plugin));

        this.requirePermission("tc.show");
    }

    @Override
    protected void executeSync(CommandContext context) {
        if (!context.isPlayer()) {
            context.sendMessage(MessageBuilder.create("This command is only for players.").color(ColorPalette.ERROR).build());
            return;
        }
        
        // Show help if no subcommand matched
        context.sendMessage(MessageBuilder.create("Usage:").color(ColorPalette.INFO).build());
        context.sendMessage(MessageBuilder.create("  /tc show").color(ColorPalette.WHITE).build());
    }

    // ============================================
    // SubCommands
    // ============================================
    /**
     * /raiding show <name>
     * Shows the closest zone (within 100 blocks) or a specific zone by name.
     */
    public static class ShowSubCommand extends AbstractPlayerCommand {
        private static final double MAX_AUTO_DISTANCE = 100.0;
        private static final float DISPLAY_TIME = 15.0f;

        private final RustyRaidingPlugin plugin;

        public ShowSubCommand(RustyRaidingPlugin plugin) {
            super("show", "Show TC zone boundaries (debug visualization)");
            this.plugin = plugin;
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
                playerRef.sendMessage(MessageBuilder.create("Could not find authorized TC zone, walk closer to an authorized TC and try again")
                    .color(ColorPalette.ERROR)
                    .build());
                return;
            }

            Player player = (Player) store.getComponent(ref, Player.getComponentType());
            if (player == null) return;

            boolean hasAuth = plugin.getZoneService().playerIsAuthed(closest.zoneName(), player.getDisplayName());
            if (!hasAuth){
                playerRef.sendMessage(MessageBuilder.create("Could not find authorized TC zone, walk closer to an authorized TC and try again")
                        .color(ColorPalette.ERROR)
                        .build());
                return;
            }

            double distance = ZoneService.distanceToZone(closest, playerPos);
            renderZone(world, closest, DISPLAY_TIME);
            playerRef.sendMessage(MessageBuilder.create("Showing TC zone '" + closest.zoneName() + "'")
                .color(ColorPalette.SUCCESS)
                .append(" (" + (int) distance + " blocks away)", ColorPalette.MUTED)
                .build());
        }
    }

    // ============================================
    // Helpers
    // ============================================

    /**
     * Render a cube from bounding points using debug shapes.
     */
    private static void renderZone(World world, Zone zone, float displayTime) {
        // Calculate center and dimensions
        Vector3d center = new Vector3d(
                (zone.min().x + zone.max().x) / 2,
                (zone.min().y + zone.max().y) / 2,
                (zone.min().z + zone.max().z) / 2
        );

        double sizeX = zone.max().x - zone.min().x;
        double sizeY = zone.max().y - zone.min().y;
        double sizeZ = zone.max().z - zone.min().z;

        Vector3f color = new Vector3f(0.3f, 1.0f, 0.3f);

        // Create transform matrix for the cube
        Matrix4d matrix = new Matrix4d();
        matrix.identity();
        matrix.translate(center);
        matrix.scale(sizeX, sizeY, sizeZ);

        // Draw the debug cube
        DebugUtils.add(world, DebugShape.Cube, matrix, color, displayTime, true);
    }

    private static String formatCenter(Zone zone) {
        double cx = (zone.min().x + zone.max().x) / 2;
        double cy = (zone.min().y + zone.max().y) / 2;
        double cz = (zone.min().z + zone.max().z) / 2;
        return String.format("[%.1f, %.1f, %.1f]", cx, cy, cz);
    }
}
