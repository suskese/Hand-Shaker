package me.mklv.handlib.neoforge;

import java.util.function.Predicate;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import static net.minecraft.commands.Commands.LEVEL_OWNERS;

public final class NeoForgeVersionCompat {
    private NeoForgeVersionCompat() {
    }

    public static Predicate<CommandSourceStack> ownerPermission() {
        return Commands.hasPermission(LEVEL_OWNERS);
    }
}