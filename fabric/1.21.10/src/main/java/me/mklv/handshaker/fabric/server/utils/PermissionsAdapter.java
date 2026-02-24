package me.mklv.handshaker.fabric.server.utils;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
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
            checkMethod = permissionsClass.getMethod("check", ServerCommandSource.class, String.class, int.class);
            hasFabricPermissions = true;
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            // fabric-permissions-api not available, will use vanilla fallback
        }
        
        PERMISSIONS_CHECK = checkMethod;
        HAS_FABRIC_PERMISSIONS = hasFabricPermissions;
    }

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

    public static boolean hasFabricPermissions() {
        return HAS_FABRIC_PERMISSIONS;
    }
}
