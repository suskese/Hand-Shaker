package me.mklv.handlib.fabric;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import java.lang.reflect.Method;

public class PermissionsAdapter {
    private static final Method PERMISSIONS_CHECK;
    private static final boolean HAS_FABRIC_PERMISSIONS;

    static {
        Method checkMethod = null;
        boolean hasFabricPermissions = false;

        try {
            // Try to load the fabric-permissions-api
            Class<?> permissionsClass = Class.forName("me.lucko.fabric.api.permissions.v0.Permissions");
            checkMethod = permissionsClass.getMethod("check", CommandSourceStack.class, String.class, int.class);
            hasFabricPermissions = true;
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            // fabric-permissions-api not available, will use vanilla fallback
        }

        PERMISSIONS_CHECK = checkMethod;
        HAS_FABRIC_PERMISSIONS = hasFabricPermissions;
    }

    public static boolean checkPermission(CommandSourceStack source, String permission, int minimumLevel) {
        // Special handling for "handshaker.bypass": OPs should NOT have this permission by default
        if ("handshaker.bypass".equals(permission)) {
            if (HAS_FABRIC_PERMISSIONS) {
                try {
                    return (boolean) PERMISSIONS_CHECK.invoke(null, source, permission, minimumLevel);
                } catch (Exception e) {
                    // Fallback: deny bypass if explicit check fails
                    return false;
                }
            }
            // No fabric-permissions-api: deny bypass by default
            return false;
        }

        if (HAS_FABRIC_PERMISSIONS) {
            try {
                return (boolean) PERMISSIONS_CHECK.invoke(null, source, permission, minimumLevel);
            } catch (Exception e) {
                // Fallback if reflection fails
                return source.hasPermission(minimumLevel);
            }
        }

        // Fallback to vanilla permission level
        return source.hasPermission(minimumLevel);
    }

    public static boolean checkPermission(ServerPlayer player, String permission) {
        // Special handling for "handshaker.bypass": OPs should NOT have this permission by default
        // It must be explicitly assigned via the permission system to bypass HandShaker checks
        if ("handshaker.bypass".equals(permission)) {
            if (HAS_FABRIC_PERMISSIONS) {
                try {
                    // For older versions, try to use the ServerCommandSource approach
                    CommandSourceStack source = player.createCommandSourceStack();
                    if (source != null) {
                        // For handshaker.bypass, use checkPermission which explicitly denies OPs
                        return checkPermission(source, permission, 0);
                    }
                } catch (Exception e) {
                    // Fallback if that doesn't work
                }
            }

            // No explicit permission - deny bypass by default (OPs don't auto-get this)
            return false;
        }

        // For other permissions, use standard permission check
        if (HAS_FABRIC_PERMISSIONS) {
            try {
                // For older versions, try to use the ServerCommandSource approach
                // Some servers may provide the player as a command source
                CommandSourceStack source = player.createCommandSourceStack();
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

    public static boolean hasFabricPermissions() {
        return HAS_FABRIC_PERMISSIONS;
    }
}