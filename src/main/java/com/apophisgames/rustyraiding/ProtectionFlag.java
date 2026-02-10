package com.apophisgames.rustyraiding;

/**
 * Flags representing different types of protection within a zone.
 */
public enum ProtectionFlag {
    /**
     * Controls whether PVP (Player vs Player) damage is allowed.
     * Default for safezones: false (Disabled)
     */
    PVP,

    /**
     * Controls whether blocks can be placed.
     * Default for safezones: false (Disabled)
     */
    BLOCK_PLACE,

    /**
     * Controls whether blocks can be broken.
     * Default for safezones: false (Disabled)
     */
    BLOCK_BREAK,

    /**
     * Controls whether players can interact with blocks (chests, doors, buttons, etc).
     * Default for safezones: false (Disabled)
     */
    BLOCK_USE
}
