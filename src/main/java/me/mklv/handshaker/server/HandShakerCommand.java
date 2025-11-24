package me.mklv.handshaker.server;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class HandShakerCommand {
    
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        var handshaker = literal("handshaker")
            .then(literal("reload")
                .executes(HandShakerCommand::reload))
            .then(literal("add")
                .then(literal("*")
                    .then(argument("status", StringArgumentType.word())
                        .suggests(HandShakerCommand::suggestStatuses)
                        .executes(HandShakerCommand::addAllMods)))
                .then(argument("mod", StringArgumentType.word())
                    .suggests(HandShakerCommand::suggestMods)
                    .then(argument("status", StringArgumentType.word())
                        .suggests(HandShakerCommand::suggestStatuses)
                        .executes(HandShakerCommand::addMod))))
            .then(literal("change")
                .then(argument("mod", StringArgumentType.word())
                    .suggests(HandShakerCommand::suggestConfiguredMods)
                    .then(argument("status", StringArgumentType.word())
                        .suggests(HandShakerCommand::suggestStatuses)
                        .executes(HandShakerCommand::changeMod))))
            .then(literal("remove")
                .then(argument("mod", StringArgumentType.word())
                    .suggests(HandShakerCommand::suggestConfiguredMods)
                    .executes(HandShakerCommand::removeMod)))
            .then(literal("list")
                .executes(HandShakerCommand::listMods))
            .then(literal("player")
                .then(argument("player", StringArgumentType.word())
                    .suggests(HandShakerCommand::suggestPlayers)
                    .executes(HandShakerCommand::showPlayer)
                    .then(argument("mod", StringArgumentType.word())
                        .suggests(HandShakerCommand::suggestPlayerMods)
                        .then(argument("status", StringArgumentType.word())
                            .suggests(HandShakerCommand::suggestStatuses)
                            .executes(HandShakerCommand::setPlayerModStatus)))));
        
        dispatcher.register(handshaker);
    }

    private static int reload(CommandContext<ServerCommandSource> ctx) {
        BlacklistConfig config = HandShakerServer.getInstance().getBlacklistConfig();
        config.load();
        Text message = Text.literal("HandShaker config reloaded. Re-checking all online players.");
        ctx.getSource().sendFeedback(() -> message, true);
        HandShakerServer.getInstance().checkAllPlayers();
        return Command.SINGLE_SUCCESS;
    }
    
    // ===== V2 Commands =====
    
    private static int addMod(CommandContext<ServerCommandSource> ctx) {
        String modId = StringArgumentType.getString(ctx, "mod");
        String statusStr = StringArgumentType.getString(ctx, "status");
        
        BlacklistConfig.ModStatus status;
        try {
            status = BlacklistConfig.ModStatus.valueOf(statusStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendError(Text.literal("Invalid status. Use: allowed, required, or blacklisted"));
            return 0;
        }
        
        BlacklistConfig config = HandShakerServer.getInstance().getBlacklistConfig();
        config.setModStatus(modId, status);
        Text message = Text.literal("Set " + modId + " to " + statusStr.toLowerCase()).formatted(Formatting.GREEN);
        ctx.getSource().sendFeedback(() -> message, true);
        HandShakerServer.getInstance().checkAllPlayers();
        return Command.SINGLE_SUCCESS;
    }
    
    private static int addAllMods(CommandContext<ServerCommandSource> ctx) {
        String statusStr = StringArgumentType.getString(ctx, "status");
        
        BlacklistConfig.ModStatus status;
        try {
            status = BlacklistConfig.ModStatus.valueOf(statusStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendError(Text.literal("Invalid status. Use: allowed, required, or blacklisted"));
            return 0;
        }
        
        if (!(ctx.getSource().getEntity() instanceof ServerPlayerEntity player)) {
            ctx.getSource().sendError(Text.literal("This command can only be used by players."));
            return 0;
        }
        
        HandShakerServer.ClientInfo clientInfo = HandShakerServer.getInstance().getClients().get(player.getUuid());
        Set<String> mods = clientInfo != null ? clientInfo.mods() : null;
        if (mods == null || mods.isEmpty()) {
            ctx.getSource().sendError(Text.literal("No mod list found for you. Make sure you're using a modded client."));
            return 0;
        }
        
        BlacklistConfig config = HandShakerServer.getInstance().getBlacklistConfig();
        config.addAllMods(mods, status);
        
        int size = mods.size();
        Text message = Text.literal("Added " + size + " of your mods as " + statusStr.toLowerCase()).formatted(Formatting.GREEN);
        ctx.getSource().sendFeedback(() -> message, true);
        HandShakerServer.getInstance().checkAllPlayers();
        return Command.SINGLE_SUCCESS;
    }
    
    private static int changeMod(CommandContext<ServerCommandSource> ctx) {
        String modId = StringArgumentType.getString(ctx, "mod");
        String statusStr = StringArgumentType.getString(ctx, "status");
        
        BlacklistConfig.ModStatus status;
        try {
            status = BlacklistConfig.ModStatus.valueOf(statusStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendError(Text.literal("Invalid status. Use: allowed, required, or blacklisted"));
            return 0;
        }
        
        BlacklistConfig config = HandShakerServer.getInstance().getBlacklistConfig();
        config.setModStatus(modId, status);
        Text message = Text.literal("Changed " + modId + " to " + statusStr.toLowerCase()).formatted(Formatting.GREEN);
        ctx.getSource().sendFeedback(() -> message, true);
        HandShakerServer.getInstance().checkAllPlayers();
        return Command.SINGLE_SUCCESS;
    }
    
    private static int listMods(CommandContext<ServerCommandSource> ctx) {
        BlacklistConfig config = HandShakerServer.getInstance().getBlacklistConfig();
        Map<String, BlacklistConfig.ModStatus> mods = config.getModStatusMap();
        
        if (mods.isEmpty()) {
            ctx.getSource().sendMessage(Text.literal("No mods configured. Default mode: " + config.getDefaultMode()).formatted(Formatting.YELLOW));
            return Command.SINGLE_SUCCESS;
        }
        
        ctx.getSource().sendMessage(Text.literal("=== Configured Mods (Default: " + config.getDefaultMode() + ") ===").formatted(Formatting.GOLD));
        
        for (Map.Entry<String, BlacklistConfig.ModStatus> entry : mods.entrySet()) {
            String modId = entry.getKey();
            BlacklistConfig.ModStatus status = entry.getValue();
            
            Formatting statusColor = switch (status) {
                case REQUIRED -> Formatting.GREEN;
                case ALLOWED -> Formatting.YELLOW;
                case BLACKLISTED -> Formatting.RED;
            };
            
            ctx.getSource().sendMessage(Text.literal(modId + " [" + status + "]").formatted(statusColor));
        }
        
        return Command.SINGLE_SUCCESS;
    }
    
    private static int showPlayer(CommandContext<ServerCommandSource> ctx) {
        String playerName = StringArgumentType.getString(ctx, "player");
        ServerPlayerEntity player = ctx.getSource().getServer().getPlayerManager().getPlayer(playerName);
        
        if (player == null) {
            ctx.getSource().sendError(Text.literal("Player '" + playerName + "' not found."));
            return 0;
        }
        
        HandShakerServer.ClientInfo clientInfo = HandShakerServer.getInstance().getClients().get(player.getUuid());
        Set<String> mods = clientInfo != null ? clientInfo.mods() : null;
        if (mods == null || mods.isEmpty()) {
            ctx.getSource().sendError(Text.literal("No mod list found for " + playerName + ".")); 
            return 0;
        }
        
        BlacklistConfig config = HandShakerServer.getInstance().getBlacklistConfig();
        
        ctx.getSource().sendMessage(Text.literal("=== " + playerName + "'s Mods ===").formatted(Formatting.GOLD, Formatting.BOLD));
        
        for (String mod : mods) {
            BlacklistConfig.ModStatus currentStatus = config.getModStatus(mod);
            if (currentStatus == null) currentStatus = BlacklistConfig.ModStatus.ALLOWED;
            
            Formatting statusColor = switch (currentStatus) {
                case REQUIRED -> Formatting.GREEN;
                case ALLOWED -> Formatting.YELLOW;
                case BLACKLISTED -> Formatting.RED;
            };
            
            ctx.getSource().sendMessage(Text.literal("  " + mod + " [" + currentStatus + "]").formatted(statusColor));
        }
        
        return Command.SINGLE_SUCCESS;
    }
    
    private static int setPlayerModStatus(CommandContext<ServerCommandSource> ctx) {
        String playerName = StringArgumentType.getString(ctx, "player");
        String modId = StringArgumentType.getString(ctx, "mod");
        String statusStr = StringArgumentType.getString(ctx, "status");
        
        ServerPlayerEntity player = ctx.getSource().getServer().getPlayerManager().getPlayer(playerName);
        if (player == null) {
            ctx.getSource().sendError(Text.literal("Player '" + playerName + "' not found."));
            return 0;
        }
        
        HandShakerServer.ClientInfo clientInfo = HandShakerServer.getInstance().getClients().get(player.getUuid());
        Set<String> mods = clientInfo != null ? clientInfo.mods() : null;
        if (mods == null || !mods.contains(modId)) {
            ctx.getSource().sendError(Text.literal("Player " + playerName + " does not have mod: " + modId));
            return 0;
        }
        
        BlacklistConfig.ModStatus status;
        try {
            status = BlacklistConfig.ModStatus.valueOf(statusStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendError(Text.literal("Invalid status. Use: allowed, required, or blacklisted"));
            return 0;
        }
        
        BlacklistConfig config = HandShakerServer.getInstance().getBlacklistConfig();
        config.setModStatus(modId, status);
        
        Text message = Text.literal("Set " + modId + " to " + statusStr.toLowerCase()).formatted(Formatting.GREEN);
        ctx.getSource().sendFeedback(() -> message, true);
        HandShakerServer.getInstance().checkAllPlayers();
        return Command.SINGLE_SUCCESS;
    }

    
    private static int removeMod(CommandContext<ServerCommandSource> ctx) {
        String modId = StringArgumentType.getString(ctx, "mod");
        BlacklistConfig config = HandShakerServer.getInstance().getBlacklistConfig();
        
        boolean removed = config.removeModStatus(modId);
        Text message = Text.literal(removed ? "Removed " + modId : modId + " not found.");
        ctx.getSource().sendFeedback(() -> message, true);
        return Command.SINGLE_SUCCESS;
    }
    
    // ===== Suggestion Providers =====
    
    private static CompletableFuture<Suggestions> suggestStatuses(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder builder) {
        return CommandSource.suggestMatching(new String[]{"allowed", "required", "blacklisted"}, builder);
    }
    
    private static CompletableFuture<Suggestions> suggestPlayers(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder builder) {
        return CommandSource.suggestMatching(
            ctx.getSource().getServer().getPlayerManager().getPlayerList().stream().map(p -> p.getName().getString()),
            builder
        );
    }
    
    private static CompletableFuture<Suggestions> suggestMods(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder builder) {
        ServerCommandSource source = ctx.getSource();
        if (source.getEntity() instanceof ServerPlayerEntity player) {
            HandShakerServer.ClientInfo clientInfo = HandShakerServer.getInstance().getClients().get(player.getUuid());
            Set<String> mods = clientInfo != null ? clientInfo.mods() : null;
            if (mods != null && !mods.isEmpty()) {
                return CommandSource.suggestMatching(mods, builder);
            }
        }
        return Suggestions.empty();
    }
    
    private static CompletableFuture<Suggestions> suggestPlayerMods(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder builder) {
        try {
            String playerName = StringArgumentType.getString(ctx, "player");
            ServerPlayerEntity player = ctx.getSource().getServer().getPlayerManager().getPlayer(playerName);
            if (player != null) {
                HandShakerServer.ClientInfo clientInfo = HandShakerServer.getInstance().getClients().get(player.getUuid());
                Set<String> mods = clientInfo != null ? clientInfo.mods() : null;
                if (mods != null && !mods.isEmpty()) {
                    return CommandSource.suggestMatching(mods, builder);
                }
            }
        } catch (IllegalArgumentException e) {
            // Player argument not yet provided
        }
        return Suggestions.empty();
    }
    
    private static CompletableFuture<Suggestions> suggestConfiguredMods(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder builder) {
        BlacklistConfig config = HandShakerServer.getInstance().getBlacklistConfig();
        return CommandSource.suggestMatching(config.getModStatusMap().keySet(), builder);
    }
}
