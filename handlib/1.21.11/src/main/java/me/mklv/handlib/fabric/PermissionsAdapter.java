package me.mklv.handlib.fabric;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
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

            // Try to get the CommandSourceStack version (takes permission string and int level)
            try {
                checkCommandSourceMethod = permissionsClass.getMethod("check", CommandSourceStack.class, String.class, int.class);
            } catch (NoSuchMethodException e1) {
                // Fallback: try without the int parameter (some versions don't require it)
                try {
                    checkCommandSourceMethod = permissionsClass.getMethod("check", CommandSourceStack.class, String.class);
                } catch (NoSuchMethodException e2) {
                    // Method not found
                }
            }

            // Try to get the ServerPlayer version (1.21.11+, takes permission string only)
            try {
                checkPlayerMethod = permissionsClass.getMethod("check", ServerPlayer.class, String.class);
            } catch (NoSuchMethodException e1) {
                // Try with int parameter as fallback
                try {
                    checkPlayerMethod = permissionsClass.getMethod("check", ServerPlayer.class, String.class, int.class);
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

    public static boolean checkPermission(CommandSourceStack source, String permission, int minimumLevel) {
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
                // If reflection fails, allow by default for commands
                return true;
            }
        }

        // No fabric-permissions-api, allow by default for commands
        return true;
    }

    public static boolean checkPermission(ServerPlayer player, String permission) {
        // Special handling for "handshaker.bypass": OPs should NOT have this permission by default
        // It must be explicitly assigned via the permission system to bypass HandShaker checks
        if ("handshaker.bypass".equals(permission)) {
            // If no permission system available, OPs cannot bypass by default
            if (!HAS_FABRIC_PERMISSIONS || PERMISSIONS_CHECK_PLAYER == null) {
                return false; // Deny bypass if no explicit permission system
            }

            // If permission system exists, check if explicitly assigned
            // This prevents OPs from auto-getting the permission
            try {
                return (boolean) PERMISSIONS_CHECK_PLAYER.invoke(null, player, permission);
            } catch (Exception e) {
                // If reflection fails for this specific permission, deny by default (safer)
                return false;
            }
        }

        // For other permissions, use standard Fabric permission system
        if (HAS_FABRIC_PERMISSIONS && PERMISSIONS_CHECK_PLAYER != null) {
            try {
                return (boolean) PERMISSIONS_CHECK_PLAYER.invoke(null, player, permission);
            } catch (Exception e) {
                // If reflection fails, deny by default (safer than allowing unknown permissions)
                return false;
            }
        }

        // No fabric-permissions-api, deny by default (only explicit grants via third-party mods)
        return false;
    }

    public static boolean hasFabricPermissions() {
        return HAS_FABRIC_PERMISSIONS;
    }
}