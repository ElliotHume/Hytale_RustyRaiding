package com.apophisgames.rustyraiding;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.knockback.KnockbackComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.component.Ref;

import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * ECS System to enforce SafeZones.
 * 
 * <p>Cancels damage and knockback events if the victim is in a SafeZone (pvpEnabled=false).
 */
public class ZoneDamageSystem extends DamageEventSystem {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final Query<EntityStore> QUERY = Query.and(
        Player.getComponentType(),
        TransformComponent.getComponentType()
    );

    private final Supplier<ZoneService> zoneServiceSupplier;

    public ZoneDamageSystem(Supplier<ZoneService> zoneServiceSupplier) {
        this.zoneServiceSupplier = zoneServiceSupplier;
    }

    @Nullable
    @Override
    public SystemGroup<EntityStore> getGroup() {
        // Run during 'FilterDamage' phase, same as other PVP checks
        return DamageModule.get().getFilterDamageGroup();
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }

    @Override
    public void handle(int index, 
                       @Nonnull ArchetypeChunk<EntityStore> archetypeChunk, 
                       @Nonnull Store<EntityStore> store, 
                       @Nonnull CommandBuffer<EntityStore> commandBuffer, 
                       @Nonnull Damage damage) {
        
        ZoneService zoneService = zoneServiceSupplier.get();
        if (zoneService == null) return;

        // Check for PVP bypass permission on the attacker
        if (damage.getSource() instanceof Damage.EntitySource entitySource) {
            Ref<EntityStore> attackerRef = entitySource.getRef();
            if (attackerRef != null && attackerRef.isValid()) {
                Player attacker = store.getComponent(attackerRef, Player.getComponentType());
                if (attacker != null && (attacker.hasPermission("easy-safezone.bypass.pvp", false) || attacker.hasPermission("easy-safezone.bypass.all", false))) {
                    // Bypass PVP restriction, allow damage
                    return;
                }
            }
        }

        World world = store.getExternalData().getWorld();
        
        TransformComponent transform = archetypeChunk.getComponent(index, TransformComponent.getComponentType());
        if (transform == null) return;

        // Check if PVP is allowed at current position
        Zone zone = zoneService.getZoneAt(world.getName(), transform.getPosition());
        if (zone != null && !zone.isAllowed(ProtectionFlag.PVP)) {
            // Cancel damage
            damage.setCancelled(true);
            
            // Cancel knockback by zeroing velocity
            KnockbackComponent knockback = damage.getIfPresentMetaObject(Damage.KNOCKBACK_COMPONENT);
            if (knockback != null) {
                knockback.setVelocity(new Vector3d(0, 0, 0));
            }
            
            // Debug log: who was damaged and in which zone
            Player player = archetypeChunk.getComponent(index, Player.getComponentType());
            String victimName = player != null ? player.getDisplayName() : "Unknown";
            LOGGER.atFine().log("Damage cancelled for '" + victimName + "' in safe zone '" + zone.zoneName() + "'");
        }
    }
}
