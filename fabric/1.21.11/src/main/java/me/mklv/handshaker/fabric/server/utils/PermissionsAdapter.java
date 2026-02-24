package me.mklv.handshaker.fabric.server.utils;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import java.lang.reflect.Method;

public class PermissionsAdapter {
    private static final Method PERMISSIONS_CHECK_COMMAND_SOURCE;
    private static final Method PERMISSIONS_CHECK_PLAYER;
    private static final boolean HAS_FABRIC_PERMISSIONS;

    static {
        Method checkCommandSourceMethod = null;
        Method checkPlayerMethod = null;
        boolean hasFabricPermissions = false;
        
        try {
            // Try to load the fabric-permissions-api
            Class<?> permissionsClass = Class.forName("me.lucko.fabric.api.permissions.v0.Permissions");
            
            // Try to get the ServerCommandSource version (takes permission string and int level)
            try {
                checkCommandSourceMethod = permissionsClass.getMethod("check", ServerCommandSource.class, String.class, int.class);
            } catch (NoSuchMethodException e1) {
                // Fallback: try without the int parameter (some versions don't require it)
                try {
                    checkCommandSourceMethod = permissionsClass.getMethod("check", ServerCommandSource.class, String.class);
                } catch (NoSuchMethodException e2) {
                    // Method not found
                }
            }
            
            // Try to get the ServerPlayerEntity version (1.21.11+, takes permission string only)
            try {
                checkPlayerMethod = permissionsClass.getMethod("check", ServerPlayerEntity.class, String.class);
            } catch (NoSuchMethodException e1) {
                // Try with int parameter as fallback
                try {
                    checkPlayerMethod = permissionsClass.getMethod("check", ServerPlayerEntity.class, String.class, int.class);
                } catch (NoSuchMethodException e2) {
                    // Method not found
                }
            }
            
            hasFabricPermissions = checkCommandSourceMethod != null || checkPlayerMethod != null;
        } catch (ClassNotFoundException e) {
            // fabric-permissions-api not available, will use defaults
        }
        
        PERMISSIONS_CHECK_COMMAND_SOURCE = checkCommandSourceMethod;
        PERMISSIONS_CHECK_PLAYER = checkPlayerMethod;
        HAS_FABRIC_PERMISSIONS = hasFabricPermissions;
    }

    public static boolean checkPermission(ServerCommandSource source, String permission, int minimumLevel) {
        if (HAS_FABRIC_PERMISSIONS && PERMISSIONS_CHECK_COMMAND_SOURCE != null) {
            try {
                // Try calling with int parameter first
                try {
                    return (boolean) PERMISSIONS_CHECK_COMMAND_SOURCE.invoke(null, source, permission, minimumLevel);
                } catch (IllegalArgumentException e) {
                    // If that fails, try without int parameter
                    return (boolean) PERMISSIONS_CHECK_COMMAND_SOURCE.invoke(null, source, permission);
                }
            } catch (Exception e) {
                // If all reflection fails, allow by default
                return true;
            }
        }
        
        // No fabric-permissions-api, allow by default
        return true;
    }

    public static boolean checkPermission(ServerPlayerEntity player, String permission) {
        if (HAS_FABRIC_PERMISSIONS && PERMISSIONS_CHECK_PLAYER != null) {
            try {
                return (boolean) PERMISSIONS_CHECK_PLAYER.invoke(null, player, permission);
            } catch (Exception e) {
                // If reflection fails, allow by default
                return true;
            }
        }
        
        // No fabric-permissions-api, allow by default
        return true;
    }

    public static boolean hasFabricPermissions() {
        return HAS_FABRIC_PERMISSIONS;
    }
}
