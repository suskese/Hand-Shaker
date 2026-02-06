package me.mklv.handshaker.fabric.server.utils;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import java.lang.reflect.Method;

/**
 * Adapter for handling permissions with fallback support.
 * Attempts to use fabric-permissions-api if available, otherwise falls back to vanilla permission levels.
 * This allows the mod to work whether or not fabric-permissions-api is installed.
 */
public class PermissionsAdapter {
    private static final Method PERMISSIONS_CHECK;
    private static final boolean HAS_FABRIC_PERMISSIONS;

    static {
        Method checkMethod = null;
        boolean hasFabricPermissions = false;
        
        try {
            // Try to load the fabric-permissions-api
            Class<?> permissionsClass = Class.forName("me.lucko.fabric.api.permissions.v0.Permissions");
            checkMethod = permissionsClass.getMethod("check", ServerCommandSource.class, String.class, int.class);
            hasFabricPermissions = true;
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            // fabric-permissions-api not available, will use vanilla fallback
        }
        
        PERMISSIONS_CHECK = checkMethod;
        HAS_FABRIC_PERMISSIONS = hasFabricPermissions;
    }

    /**
     * Check if a player has a specific permission.
     * If fabric-permissions-api is available, uses it.
     * Otherwise, falls back to vanilla permission level check.
     */
    public static boolean checkPermission(ServerCommandSource source, String permission, int minimumLevel) {
        if (HAS_FABRIC_PERMISSIONS) {
            try {
                return (boolean) PERMISSIONS_CHECK.invoke(null, source, permission, minimumLevel);
            } catch (Exception e) {
                // Fallback if reflection fails
                return source.hasPermissionLevel(minimumLevel);
            }
        }
        
        // Fallback to vanilla permission level
        return source.hasPermissionLevel(minimumLevel);
    }

    /**
     * Check if a ServerPlayerEntity has a specific permission (for older Minecraft versions).
     * Only checks if fabric-permissions-api is available. OPs do NOT automatically bypass.
     * 
     * @param player The player to check
     * @param permission The permission node to check
     * @return true if the player has the permission (via permission manager), false otherwise
     */
    public static boolean checkPermission(ServerPlayerEntity player, String permission) {
        if (HAS_FABRIC_PERMISSIONS) {
            try {
                // For older versions, try to use the ServerCommandSource approach
                // Some servers may provide the player as a command source
                ServerCommandSource source = player.getCommandSource();
                if (source != null) {
                    return checkPermission(source, permission, 4);
                }
            } catch (Exception e) {
                // Fallback if that doesn't work
            }
        }
        
        // No permission check available - deny by default (only explicit grants count)
        return false;
    }

    /**
     * Check if fabric-permissions-api is available.
     */
    public static boolean hasFabricPermissions() {
        return HAS_FABRIC_PERMISSIONS;
    }
}
