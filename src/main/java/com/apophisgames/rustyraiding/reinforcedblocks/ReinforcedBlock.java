package com.apophisgames.rustyraiding.reinforcedblocks;

import com.apophisgames.rustyraiding.zones.Zone;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.World;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Represents a reinforced block in a world
 *
 * @param internalId Auto-generated UUID, database primary key
 * @param worldName The name of the world this block belongs to
 * @param position The coordinate position of the block
 * @param reinforcement The reinforcement level of the block (how many times it takes to break it)
 */
public record ReinforcedBlock(
        @Nonnull String internalId,
        @Nonnull String worldName,
        @Nonnull Vector3i position,
        int reinforcement
) {

    public static ReinforcedBlock create(String worldName, Vector3i position, int reinforcement) {
        return new ReinforcedBlock(getInternalIdFromPosition(worldName, position), worldName, position, reinforcement);
    }

    /**
     * Create a copy with updated reinforcement.
     */
    public ReinforcedBlock withNewReinforcement(int newReinforcement) {
        return new ReinforcedBlock(internalId, worldName, position, newReinforcement);
    }

    public static String getInternalIdFromPosition(String worldName, Vector3i position){
        return "%s|%d|%d|%d".formatted(worldName, position.x, position.y, position.z);
    }

    public static Vector3i getPositionFromInternalId(String internalId){
        String[] parts = internalId.split("\\|");
        return new Vector3i(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
    }

    @Override
    @Nonnull
    public String toString(){
        return "ID:[%s], World:[%s], Position:[%s], Reinforcement:[%s]".formatted(internalId, worldName, position.toString(), reinforcement);
    }
}
