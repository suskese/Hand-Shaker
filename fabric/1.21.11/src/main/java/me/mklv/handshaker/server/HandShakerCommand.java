package me.mklv.handshaker.server;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class HandShakerCommand {
    
    @SuppressWarnings("deprecation")
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        var handshaker = literal("handshaker")
            .requires(source -> {
                return Permissions.check(source, "handshaker.admin", 4);
            })
            .then(literal("reload")
                .executes(HandShakerCommand::reload))
            .then(literal("add")
                .then(literal("*")
                    .then(argument("status", StringArgumentType.word())
                        .suggests(HandShakerCommand::suggestStatuses)
                        .executes(HandShakerCommand::addAllMods)
                        .then(argument("action", StringArgumentType.word())
                            .suggests(HandShakerCommand::suggestActions)
                            .executes(HandShakerCommand::addAllModsWithAction)
                            .then(argument("warn-message", StringArgumentType.greedyString())
                                .executes(HandShakerCommand::addAllModsWithActionAndMessage)))))
                .then(argument("mod", StringArgumentType.word())
                    .suggests(HandShakerCommand::suggestMods)
                    .then(argument("status", StringArgumentType.word())
                        .suggests(HandShakerCommand::suggestStatuses)
                        .executes(HandShakerCommand::addMod)
                        .then(argument("action", StringArgumentType.word())
                            .suggests(HandShakerCommand::suggestActions)
                            .executes(HandShakerCommand::addModWithAction)
                            .then(argument("warn-message", StringArgumentType.greedyString())
                                .executes(HandShakerCommand::addModWithActionAndMessage))))))
            .then(literal("change")
                .then(argument("mod", StringArgumentType.word())
                    .suggests(HandShakerCommand::suggestConfiguredMods)
                    .then(argument("status", StringArgumentType.word())
                        .suggests(HandShakerCommand::suggestStatuses)
                        .executes(HandShakerCommand::changeMod)
                        .then(argument("action", StringArgumentType.word())
                            .suggests(HandShakerCommand::suggestActions)
                            .executes(HandShakerCommand::changeModWithAction)
                            .then(argument("warn-message", StringArgumentType.greedyString())
                                .executes(HandShakerCommand::changeModWithActionAndMessage))))))
            .then(literal("remove")
                .then(argument("mod", StringArgumentType.word())
                    .suggests(HandShakerCommand::suggestConfiguredMods)
                    .executes(HandShakerCommand::removeMod)))
            .then(literal("list")
                .executes(HandShakerCommand::listMods))
            .then(literal("ignore")
                .then(literal("add")
                    .then(literal("*")
                        .executes(HandShakerCommand::ignoreAllPlayerMods))
                    .then(argument("mod", StringArgumentType.word())
                        .suggests(HandShakerCommand::suggestMods)
                        .executes(HandShakerCommand::ignoreMod)))
                .then(literal("remove")
                    .then(argument("mod", StringArgumentType.word())
                        .suggests(HandShakerCommand::suggestIgnoredMods)
                        .executes(HandShakerCommand::unignoreMod)))
                .then(literal("list")
                    .executes(HandShakerCommand::listIgnoredMods)))
            .then(literal("info")
                .executes(HandShakerCommand::showModInfo)
                .then(argument("mod", StringArgumentType.word())
                    .suggests(HandShakerCommand::suggestAllMods)
                    .executes(HandShakerCommand::showModDetails)))
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
    
    private static int addAllMods(CommandContext<ServerCommandSource> ctx) {
        return addAllModsWithActionAndMessage(ctx, BlacklistConfig.Action.KICK, null);
    }
    
    private static int addAllModsWithAction(CommandContext<ServerCommandSource> ctx) {
        String actionStr = StringArgumentType.getString(ctx, "action");
        BlacklistConfig.Action action;
        try {
            action = BlacklistConfig.Action.valueOf(actionStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendError(Text.literal("Invalid action. Use: kick, ban, or warn"));
            return 0;
        }
        return addAllModsWithActionAndMessage(ctx, action, null);
    }
    
    private static int addAllModsWithActionAndMessage(CommandContext<ServerCommandSource> ctx) {
        String actionStr = StringArgumentType.getString(ctx, "action");
        String warnMessage = StringArgumentType.getString(ctx, "warn-message");
        BlacklistConfig.Action action;
        try {
            action = BlacklistConfig.Action.valueOf(actionStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendError(Text.literal("Invalid action. Use: kick, ban, or warn"));
            return 0;
        }
        return addAllModsWithActionAndMessage(ctx, action, warnMessage);
    }
    
    private static int addAllModsWithActionAndMessage(CommandContext<ServerCommandSource> ctx, BlacklistConfig.Action action, String warnMessage) {
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
        // Filter out ignored mods
        Set<String> filteredMods = new HashSet<>();
        for (String mod : mods) {
            if (!config.isModIgnored(mod)) {
                filteredMods.add(mod);
            }
        }
        
        for (String mod : filteredMods) {
            config.setModConfig(mod, status, action, warnMessage);
        }
        config.save();
        
        int size = filteredMods.size();
        String actionStr = action != BlacklistConfig.Action.KICK ? " with action " + action : "";
        Text message = Text.literal("Added " + size + " of your mods as " + statusStr.toLowerCase() + actionStr).formatted(Formatting.GREEN);
        ctx.getSource().sendFeedback(() -> message, true);
        HandShakerServer.getInstance().checkAllPlayers();
        return Command.SINGLE_SUCCESS;
    }
    
    private static int addMod(CommandContext<ServerCommandSource> ctx) {
        return addModWithActionAndMessage(ctx, BlacklistConfig.Action.KICK, null);
    }
    
    private static int addModWithAction(CommandContext<ServerCommandSource> ctx) {
        String actionStr = StringArgumentType.getString(ctx, "action");
        BlacklistConfig.Action action;
        try {
            action = BlacklistConfig.Action.valueOf(actionStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendError(Text.literal("Invalid action. Use: kick, ban, or warn"));
            return 0;
        }
        return addModWithActionAndMessage(ctx, action, null);
    }
    
    private static int addModWithActionAndMessage(CommandContext<ServerCommandSource> ctx) {
        String actionStr = StringArgumentType.getString(ctx, "action");
        String warnMessage = StringArgumentType.getString(ctx, "warn-message");
        BlacklistConfig.Action action;
        try {
            action = BlacklistConfig.Action.valueOf(actionStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendError(Text.literal("Invalid action. Use: kick, ban, or warn"));
            return 0;
        }
        return addModWithActionAndMessage(ctx, action, warnMessage);
    }
    
    private static int addModWithActionAndMessage(CommandContext<ServerCommandSource> ctx, BlacklistConfig.Action action, String warnMessage) {
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
        config.setModConfig(modId, status, action, warnMessage);
        
        String actionStr = action != BlacklistConfig.Action.KICK ? " with action " + action : "";
        Text message = Text.literal("Set " + modId + " to " + statusStr.toLowerCase() + actionStr).formatted(Formatting.GREEN);
        ctx.getSource().sendFeedback(() -> message, true);
        HandShakerServer.getInstance().checkAllPlayers();
        return Command.SINGLE_SUCCESS;
    }
    
    private static int changeMod(CommandContext<ServerCommandSource> ctx) {
        return changeModWithActionAndMessage(ctx, null, null);
    }
    
    private static int changeModWithAction(CommandContext<ServerCommandSource> ctx) {
        String actionStr = StringArgumentType.getString(ctx, "action");
        BlacklistConfig.Action action;
        try {
            action = BlacklistConfig.Action.valueOf(actionStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendError(Text.literal("Invalid action. Use: kick, ban, or warn"));
            return 0;
        }
        return changeModWithActionAndMessage(ctx, action, null);
    }
    
    private static int changeModWithActionAndMessage(CommandContext<ServerCommandSource> ctx) {
        String actionStr = StringArgumentType.getString(ctx, "action");
        String warnMessage = StringArgumentType.getString(ctx, "warn-message");
        BlacklistConfig.Action action;
        try {
            action = BlacklistConfig.Action.valueOf(actionStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendError(Text.literal("Invalid action. Use: kick, ban, or warn"));
            return 0;
        }
        return changeModWithActionAndMessage(ctx, action, warnMessage);
    }
    
    private static int changeModWithActionAndMessage(CommandContext<ServerCommandSource> ctx, BlacklistConfig.Action action, String warnMessage) {
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
        BlacklistConfig.ModConfig oldConfig = config.getModConfig(modId);
        
        // Use existing action/message if not specified
        if (action == null) action = oldConfig.getAction();
        if (warnMessage == null) warnMessage = oldConfig.getWarnMessage();
        
        config.setModConfig(modId, status, action, warnMessage);
        
        String actionStr = action != BlacklistConfig.Action.KICK ? " with action " + action : "";
        Text message = Text.literal("Changed " + modId + " to " + statusStr.toLowerCase() + actionStr).formatted(Formatting.GREEN);
        ctx.getSource().sendFeedback(() -> message, true);
        HandShakerServer.getInstance().checkAllPlayers();
        return Command.SINGLE_SUCCESS;
    }
    
    private static int listMods(CommandContext<ServerCommandSource> ctx) {
        BlacklistConfig config = HandShakerServer.getInstance().getBlacklistConfig();
        Map<String, BlacklistConfig.ModConfig> mods = config.getModConfigMap();
        
        if (mods.isEmpty()) {
            ctx.getSource().sendMessage(Text.literal("No mods configured. Default mode: " + config.getDefaultMode()).formatted(Formatting.YELLOW));
            return Command.SINGLE_SUCCESS;
        }
        
        ctx.getSource().sendMessage(Text.literal("=== Configured Mods (Default: " + config.getDefaultMode() + ") ===").formatted(Formatting.GOLD));
        
        for (Map.Entry<String, BlacklistConfig.ModConfig> entry : mods.entrySet()) {
            String modId = entry.getKey();
            BlacklistConfig.ModConfig modConfig = entry.getValue();
            BlacklistConfig.ModStatus status = modConfig.getMode();
            BlacklistConfig.Action action = modConfig.getAction();
            
            Formatting statusColor = switch (status) {
                case REQUIRED -> Formatting.GREEN;
                case ALLOWED -> Formatting.YELLOW;
                case BLACKLISTED -> Formatting.RED;
            };
            
            String actionStr = action != BlacklistConfig.Action.KICK ? " [" + action + "]" : "";
            ctx.getSource().sendMessage(Text.literal(modId + " [" + status + "]" + actionStr).formatted(statusColor));
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
        PlayerHistoryDatabase db = HandShakerServer.getInstance().getPlayerHistoryDb();
        
        // Get history for this player
        List<PlayerHistoryDatabase.ModHistoryEntry> history = db != null ? db.getPlayerHistory(player.getUuid()) : new ArrayList<>();
        Map<String, PlayerHistoryDatabase.ModHistoryEntry> historyMap = new HashMap<>();
        for (PlayerHistoryDatabase.ModHistoryEntry entry : history) {
            if (entry.isActive()) {
                historyMap.put(entry.modName(), entry);
            }
        }
        
        ctx.getSource().sendMessage(Text.literal("=== " + playerName + "'s Mods ===").formatted(Formatting.GOLD, Formatting.BOLD));
        
        // Filter out ignored mods
        List<String> filteredMods = new ArrayList<>();
        for (String mod : mods) {
            if (!config.isModIgnored(mod)) {
                filteredMods.add(mod);
            }
        }
        
        for (String mod : filteredMods) {
            BlacklistConfig.ModStatus currentStatus = config.getModStatus(mod);
            if (currentStatus == null) currentStatus = BlacklistConfig.ModStatus.ALLOWED;
            
            Formatting statusColor = switch (currentStatus) {
                case REQUIRED -> Formatting.GREEN;
                case ALLOWED -> Formatting.YELLOW;
                case BLACKLISTED -> Formatting.RED;
            };
            
            MutableText modText = Text.literal("  " + mod + " [" + currentStatus + "]").formatted(statusColor);
            
            // Add history info to the text directly instead of hover
            if (historyMap.containsKey(mod)) {
                PlayerHistoryDatabase.ModHistoryEntry entry = historyMap.get(mod);
                modText = modText.append(Text.literal(" (Added: " + entry.getAddedDateFormatted() + ")").formatted(Formatting.GRAY));
            }
            
            ctx.getSource().sendMessage(modText);
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
        return CommandSource.suggestMatching(config.getModConfigMap().keySet(), builder);
    }
    
    private static CompletableFuture<Suggestions> suggestActions(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder builder) {
        return CommandSource.suggestMatching(new String[]{"kick", "ban"}, builder);
    }
    
    private static CompletableFuture<Suggestions> suggestIgnoredMods(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder builder) {
        BlacklistConfig config = HandShakerServer.getInstance().getBlacklistConfig();
        return CommandSource.suggestMatching(config.getIgnoredMods(), builder);
    }
    
    private static CompletableFuture<Suggestions> suggestAllMods(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder builder) {
        PlayerHistoryDatabase db = HandShakerServer.getInstance().getPlayerHistoryDb();
        BlacklistConfig config = HandShakerServer.getInstance().getBlacklistConfig();
        if (db != null) {
            Set<String> nonIgnoredMods = new HashSet<>();
            for (String mod : db.getModPopularity().keySet()) {
                if (!config.isModIgnored(mod)) {
                    nonIgnoredMods.add(mod);
                }
            }
            return CommandSource.suggestMatching(nonIgnoredMods, builder);
        }
        return Suggestions.empty();
    }
    
    private static int ignoreMod(CommandContext<ServerCommandSource> ctx) {
        String modId = StringArgumentType.getString(ctx, "mod");
        BlacklistConfig config = HandShakerServer.getInstance().getBlacklistConfig();
        
        boolean added = config.addIgnoredMod(modId);
        Text message = Text.literal(added ? "Added " + modId + " to ignore list" : modId + " already in ignore list")
            .formatted(added ? Formatting.GREEN : Formatting.YELLOW);
        ctx.getSource().sendFeedback(() -> message, true);
        return Command.SINGLE_SUCCESS;
    }
    
    private static int ignoreAllPlayerMods(CommandContext<ServerCommandSource> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayerEntity player)) {
            ctx.getSource().sendError(Text.literal("This command can only be used by players."));
            return 0;
        }
        
        HandShakerServer.ClientInfo clientInfo = HandShakerServer.getInstance().getClients().get(player.getUuid());
        Set<String> mods = clientInfo != null ? clientInfo.mods() : null;
        if (mods == null || mods.isEmpty()) {
            ctx.getSource().sendError(Text.literal("No mod list found for you."));
            return 0;
        }
        
        BlacklistConfig config = HandShakerServer.getInstance().getBlacklistConfig();
        int added = 0;
        for (String mod : mods) {
            if (config.addIgnoredMod(mod)) {
                added++;
            }
        }
        
        Text message = Text.literal("Added " + added + " of your mods to ignore list").formatted(Formatting.GREEN);
        ctx.getSource().sendFeedback(() -> message, true);
        return Command.SINGLE_SUCCESS;
    }
    
    private static int unignoreMod(CommandContext<ServerCommandSource> ctx) {
        String modId = StringArgumentType.getString(ctx, "mod");
        BlacklistConfig config = HandShakerServer.getInstance().getBlacklistConfig();
        
        boolean removed = config.removeIgnoredMod(modId);
        Text message = Text.literal(removed ? "Removed " + modId + " from ignore list" : modId + " not in ignore list")
            .formatted(removed ? Formatting.GREEN : Formatting.YELLOW);
        ctx.getSource().sendFeedback(() -> message, true);
        return Command.SINGLE_SUCCESS;
    }
    
    private static int listIgnoredMods(CommandContext<ServerCommandSource> ctx) {
        BlacklistConfig config = HandShakerServer.getInstance().getBlacklistConfig();
        Set<String> ignored = config.getIgnoredMods();
        
        if (ignored.isEmpty()) {
            ctx.getSource().sendMessage(Text.literal("No mods in ignore list").formatted(Formatting.YELLOW));
            return Command.SINGLE_SUCCESS;
        }
        
        ctx.getSource().sendMessage(Text.literal("=== Ignored Mods ===").formatted(Formatting.GOLD));
        for (String mod : ignored) {
            ctx.getSource().sendMessage(Text.literal("  " + mod).formatted(Formatting.GRAY));
        }
        
        return Command.SINGLE_SUCCESS;
    }
    
    private static int showModInfo(CommandContext<ServerCommandSource> ctx) {
        PlayerHistoryDatabase db = HandShakerServer.getInstance().getPlayerHistoryDb();
        if (db == null) {
            ctx.getSource().sendError(Text.literal("Player history database not available"));
            return 0;
        }
        
        BlacklistConfig config = HandShakerServer.getInstance().getBlacklistConfig();
        Map<String, Integer> popularity = db.getModPopularity();
        if (popularity.isEmpty()) {
            ctx.getSource().sendMessage(Text.literal("No mod usage data available yet").formatted(Formatting.YELLOW));
            return Command.SINGLE_SUCCESS;
        }
        
        ctx.getSource().sendMessage(Text.literal("=== Mod Popularity ===").formatted(Formatting.GOLD, Formatting.BOLD));
        int count = 0;
        int totalMods = 0;
        for (Map.Entry<String, Integer> entry : popularity.entrySet()) {
            if (config.isModIgnored(entry.getKey())) continue; // Skip ignored mods
            totalMods++;
            if (count < 20) {
                ctx.getSource().sendMessage(Text.literal(entry.getKey() + " - " + entry.getValue() + " player(s)").formatted(Formatting.YELLOW));
                count++;
            }
        }
        if (totalMods > 20) {
            ctx.getSource().sendMessage(Text.literal("... and " + (totalMods - 20) + " more").formatted(Formatting.GRAY));
        }
        
        return Command.SINGLE_SUCCESS;
    }
    
    private static int showModDetails(CommandContext<ServerCommandSource> ctx) {
        String modName = StringArgumentType.getString(ctx, "mod");
        PlayerHistoryDatabase db = HandShakerServer.getInstance().getPlayerHistoryDb();
        if (db == null) {
            ctx.getSource().sendError(Text.literal("Player history database not available"));
            return 0;
        }
        
        List<PlayerHistoryDatabase.PlayerModInfo> players = db.getPlayersWithMod(modName);
        if (players.isEmpty()) {
            ctx.getSource().sendMessage(Text.literal("No players found using mod: " + modName).formatted(Formatting.YELLOW));
            return Command.SINGLE_SUCCESS;
        }
        
        ctx.getSource().sendMessage(Text.literal("=== Players with " + modName + " ===").formatted(Formatting.GOLD, Formatting.BOLD));
        for (PlayerHistoryDatabase.PlayerModInfo info : players) {
            String statusMark = info.isActive() ? "●" : "○";
            Formatting color = info.isActive() ? Formatting.GREEN : Formatting.GRAY;
            String playerName = info.currentName() != null ? info.currentName() : info.uuid().toString();
            ctx.getSource().sendMessage(
                Text.literal(statusMark + " " + playerName + " (first seen: " + info.getFirstSeenFormatted() + ")")
                    .formatted(color)
            );
        }
        
        return Command.SINGLE_SUCCESS;
    }
}