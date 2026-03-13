package me.mklv.handlib.neoforge;

import java.util.function.Predicate;
import net.minecraft.commands.CommandSourceStack;

public final class NeoForgeVersionCompat {
    private NeoForgeVersionCompat() {
    }

    public static Predicate<CommandSourceStack> ownerPermission() {
        return source -> source.hasPermission(4);
    }
}