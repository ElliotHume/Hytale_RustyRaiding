package com.apophisgames.rustyraiding;

import com.apophisgames.rustyraiding.config.RaidingConfig;
import com.apophisgames.rustyraiding.interactions.ReinforcementKitInteraction;
import com.apophisgames.rustyraiding.interactions.ToolCupboardInteraction;
import com.apophisgames.rustyraiding.reinforcedblocks.CachedReinforcedBlockRepository;
import com.apophisgames.rustyraiding.reinforcedblocks.IReinforcedBlockRepository;
import com.apophisgames.rustyraiding.reinforcedblocks.SqliteReinforcedBlockRepository;
import com.apophisgames.rustyraiding.zoneauthorizations.CachedZoneAuthorizationRepository;
import com.apophisgames.rustyraiding.zoneauthorizations.IAuthRepository;
import com.apophisgames.rustyraiding.zoneauthorizations.SqliteZoneAuthorizationRepository;
import com.apophisgames.rustyraiding.zones.CachedZoneRepository;
import com.apophisgames.rustyraiding.zones.IZoneRepository;
import com.apophisgames.rustyraiding.zones.SqliteZoneRepository;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.util.Config;

public class RustyRaidingPlugin extends JavaPlugin {
    
    private static com.apophisgames.rustyraiding.RustyRaidingPlugin instance;
    private RaidingService raidingService;

    public static Config<RaidingConfig> CONFIG;

    public RustyRaidingPlugin(JavaPluginInit init) {
        super(init);
        instance = this;
        CONFIG = this.withConfig("RustyRaiding", RaidingConfig.CODEC);
    }

    public static com.apophisgames.rustyraiding.RustyRaidingPlugin get() {
        return instance;
    }

    public RaidingService getZoneService() {
        return raidingService;
    }

    @Override
    protected void setup() {
        super.setup();
        CONFIG.save();

        getLogger().atInfo().log("Setting up Rusty Raiding...");

        // Initialize service
        IZoneRepository zoneSqliteRepo = new SqliteZoneRepository(getDataDirectory());
        IZoneRepository zoneCachedRepo = new CachedZoneRepository(zoneSqliteRepo);

        IAuthRepository authSqliteRepo = new SqliteZoneAuthorizationRepository(getDataDirectory());
        IAuthRepository authCachedRepo = new CachedZoneAuthorizationRepository(authSqliteRepo);

        IReinforcedBlockRepository reinforcedBlockSqliteRepo = new SqliteReinforcedBlockRepository(getDataDirectory());
        IReinforcedBlockRepository reinforcedBlockCachedRepo = new CachedReinforcedBlockRepository(reinforcedBlockSqliteRepo);

        raidingService = new RaidingService(zoneCachedRepo, authCachedRepo, reinforcedBlockCachedRepo);
        raidingService.initialize();

        // Register command
        getCommandRegistry().registerCommand(new RaidingCommand(this));
        getCommandRegistry().registerCommand(new TCCommand(this));
        
        // Register ECS system
        getEntityStoreRegistry().registerSystem(new ZoneBlockProtection.PlaceBlock(() -> raidingService));
        getEntityStoreRegistry().registerSystem(new ZoneBlockProtection.BreakBlock(() -> raidingService));
        getEntityStoreRegistry().registerSystem(new ZoneBlockProtection.UseBlock(() -> raidingService));

        this.getCodecRegistry(Interaction.CODEC).register("RustyRaiding_ToolCupboard_Interaction", ToolCupboardInteraction.class, ToolCupboardInteraction.CODEC);
        this.getCodecRegistry(Interaction.CODEC).register("RustyRaiding_ReinforcementKit_Interaction", ReinforcementKitInteraction.class, ReinforcementKitInteraction.CODEC);

        getLogger().atInfo().log("Rusty Raiding setup complete.");
    }
    
    @Override
    protected void start() {
        getLogger().atInfo().log("Rusty Raiding started!");
    }

    @Override
    protected void shutdown() {
        if (raidingService != null) {
            raidingService.shutdown();
        }
    }
}
