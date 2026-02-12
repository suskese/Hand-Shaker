package me.mklv.handshaker.fabric.server;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import me.mklv.handshaker.fabric.server.configs.ConfigManager;
import me.mklv.handshaker.fabric.server.utils.PermissionsAdapter;
import me.mklv.handshaker.common.commands.CommandSuggestionData;
import me.mklv.handshaker.common.database.PlayerHistoryDatabase;
import me.mklv.handshaker.common.configs.ConfigState;
import me.mklv.handshaker.common.utils.ClientInfo;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class HandShakerCommand {
    private static final int PAGE_SIZE = 10;
    
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        var handshaker = literal("handshaker")
            .requires(source -> PermissionsAdapter.checkPermission(source, "handshaker.admin", 4))
            .executes(HandShakerCommand::showHelp)
            // Core Commands
            .then(literal("reload")
                .executes(HandShakerCommand::reload))
            .then(literal("info")
                .executes(HandShakerCommand::showInfo)
                .then(literal("configured_mods")
                    .executes(HandShakerCommand::showConfiguredMods)
                    .then(argument("page", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                        .executes(HandShakerCommand::showConfiguredModsWithPage)))
                .then(literal("all_mods")
                    .executes(HandShakerCommand::showAllMods)
                    .then(argument("page", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                        .executes(HandShakerCommand::showAllModsWithPage)))
                .then(literal("player")
                    .then(argument("playerName", StringArgumentType.word())
                        .suggests(HandShakerCommand::suggestPlayers)
                        .executes(HandShakerCommand::showPlayerHistory)
                        .then(argument("page", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                            .executes(HandShakerCommand::showPlayerHistoryWithPage))))
                .then(literal("mod")
                    .then(argument("modName", StringArgumentType.word())
                        .suggests(HandShakerCommand::suggestAllMods)
                        .executes(HandShakerCommand::showModInfo))))
            .then(literal("config")
                .executes(HandShakerCommand::showConfig)
                .then(literal("behavior")
                    .then(argument("value", StringArgumentType.word())
                        .suggests((ctx, builder) -> builder.suggest("STRICT").suggest("VANILLA").buildFuture())
                        .executes(ctx -> setConfigValue(ctx, "behavior"))))
                .then(literal("integrity")
                    .then(argument("value", StringArgumentType.word())
                        .suggests((ctx, builder) -> builder.suggest("SIGNED").suggest("DEV").buildFuture())
                        .executes(ctx -> setConfigValue(ctx, "integrity"))))
                .then(literal("whitelist")
                    .then(argument("value", StringArgumentType.word())
                        .suggests((ctx, builder) -> builder.suggest("true").suggest("false").buildFuture())
                        .executes(ctx -> setConfigValue(ctx, "whitelist"))))
                .then(literal("allow_bedrock")
                    .then(argument("value", StringArgumentType.word())
                        .suggests((ctx, builder) -> builder.suggest("true").suggest("false").buildFuture())
                    .executes(ctx -> setConfigValue(ctx, "allow_bedrock"))))
                .then(literal("playerdb_enabled")
                    .then(argument("value", StringArgumentType.word())
                        .suggests((ctx, builder) -> builder.suggest("true").suggest("false").buildFuture())
                    .executes(ctx -> setConfigValue(ctx, "playerdb_enabled")))))
            .then(literal("mode")
                .then(argument("list", StringArgumentType.word())
                    .suggests(HandShakerCommand::suggestModeLists)
                    .then(argument("action", StringArgumentType.word())
                        .suggests(HandShakerCommand::suggestCurrentModeState)
                        .executes(HandShakerCommand::setMode))))
            // Manage subcommands
            .then(literal("manage")
                .then(literal("add")
                    .then(literal("*")
                        .then(argument("mode", StringArgumentType.word())
                            .suggests(HandShakerCommand::suggestModes)
                            .executes(HandShakerCommand::addMod)
                            .then(argument("action", StringArgumentType.word())
                                .suggests(HandShakerCommand::suggestActions)
                                .executes(HandShakerCommand::addModWithAction))))
                    .then(argument("mod", StringArgumentType.word())
                        .suggests(HandShakerCommand::suggestMods)
                        .then(argument("mode", StringArgumentType.word())
                            .suggests(HandShakerCommand::suggestModes)
                            .executes(HandShakerCommand::addMod)
                            .then(argument("action", StringArgumentType.word())
                                .suggests(HandShakerCommand::suggestActions)
                                .executes(HandShakerCommand::addModWithAction)))))
                .then(literal("change")
                    .then(argument("mod", StringArgumentType.word())
                        .suggests(HandShakerCommand::suggestConfiguredMods)
                        .then(argument("mode", StringArgumentType.word())
                            .suggests(HandShakerCommand::suggestModes)
                            .executes(HandShakerCommand::changeMod)
                            .then(argument("action", StringArgumentType.word())
                                .suggests(HandShakerCommand::suggestActions)
                                .executes(HandShakerCommand::changeModWithAction)))))
                .then(literal("remove")
                    .then(argument("mod", StringArgumentType.word())
                        .suggests(HandShakerCommand::suggestConfiguredMods)
                        .executes(HandShakerCommand::removeMod)))
                .then(literal("ignore")
                    .then(literal("add")
                        .then(literal("*")
                            .executes(HandShakerCommand::addIgnore))
                        .then(argument("mod", StringArgumentType.word())
                            .suggests(HandShakerCommand::suggestMods)
                            .executes(HandShakerCommand::addIgnore)))
                    .then(literal("remove")
                        .then(argument("mod", StringArgumentType.word())
                            .suggests(HandShakerCommand::suggestIgnoredMods)
                            .executes(HandShakerCommand::removeIgnore)))
                    .then(literal("list")
                        .executes(HandShakerCommand::listIgnore)))
                .then(literal("player")
                    .then(argument("player", StringArgumentType.word())
                        .suggests(HandShakerCommand::suggestPlayers)
                        .executes(HandShakerCommand::showPlayerMods)
                        .then(literal("*")
                            .then(argument("mode", StringArgumentType.word())
                                .suggests(HandShakerCommand::suggestModes)
                                .executes(HandShakerCommand::setPlayerModStatus)))
                        .then(argument("mod", StringArgumentType.word())
                            .suggests(HandShakerCommand::suggestPlayerMods)
                            .then(argument("mode", StringArgumentType.word())
                                .suggests(HandShakerCommand::suggestModes)
                                .executes(HandShakerCommand::setPlayerModStatus))))));
        
        dispatcher.register(handshaker);
    }

    private static int showHelp(CommandContext<ServerCommandSource> ctx) {
        ctx.getSource().sendMessage(Text.literal("═══════════════════════════════").formatted(Formatting.GOLD));
        ctx.getSource().sendMessage(Text.literal("HandShaker v6 Commands").formatted(Formatting.GOLD, Formatting.BOLD));
        ctx.getSource().sendMessage(Text.literal("═══════════════════════════════").formatted(Formatting.GOLD));
        
        ctx.getSource().sendMessage(Text.literal("Core Commands:").formatted(Formatting.YELLOW, Formatting.BOLD));
        ctx.getSource().sendMessage(Text.literal("/handshaker reload").formatted(Formatting.YELLOW)
            .append(Text.literal(" - Reload config").formatted(Formatting.GRAY)));
        ctx.getSource().sendMessage(Text.literal("/handshaker info [mod]").formatted(Formatting.YELLOW)
            .append(Text.literal(" - Show statistics").formatted(Formatting.GRAY)));
        ctx.getSource().sendMessage(Text.literal("/handshaker config [param] [value]").formatted(Formatting.YELLOW)
            .append(Text.literal(" - View/change configuration").formatted(Formatting.GRAY)));
        ctx.getSource().sendMessage(Text.literal("/handshaker mode <mods_required|mods_blacklisted|mods_whitelisted> <on|off>").formatted(Formatting.YELLOW)
            .append(Text.literal(" - Toggle mod lists").formatted(Formatting.GRAY)));
        
        ctx.getSource().sendMessage(Text.empty());
        ctx.getSource().sendMessage(Text.literal("Mod Management:").formatted(Formatting.YELLOW, Formatting.BOLD));
        ctx.getSource().sendMessage(Text.literal("/handshaker manage add <mod> <mode> [action]").formatted(Formatting.YELLOW)
            .append(Text.literal(" - Add mod").formatted(Formatting.GRAY)));
        ctx.getSource().sendMessage(Text.literal("/handshaker manage change <mod> <mode> [action]").formatted(Formatting.YELLOW)
            .append(Text.literal(" - Change mod").formatted(Formatting.GRAY)));
        ctx.getSource().sendMessage(Text.literal("/handshaker manage remove <mod>").formatted(Formatting.YELLOW)
            .append(Text.literal(" - Remove mod").formatted(Formatting.GRAY)));
        
        return Command.SINGLE_SUCCESS;
    }

    private static int reload(CommandContext<ServerCommandSource> ctx) {
        ConfigManager config = HandShakerServer.getInstance().getConfigManager();
        config.load();
        Text message = Text.literal("✓ HandShaker config reloaded").formatted(Formatting.GREEN);
        ctx.getSource().sendFeedback(() -> message, true);
        HandShakerServer.getInstance().checkAllPlayers();
        return Command.SINGLE_SUCCESS;
    }

    private static int showConfig(CommandContext<ServerCommandSource> ctx) {
        ConfigManager config = HandShakerServer.getInstance().getConfigManager();
        ctx.getSource().sendMessage(Text.literal("═══════════════════════════════").formatted(Formatting.GOLD));
        ctx.getSource().sendMessage(Text.literal("HandShaker Configuration").formatted(Formatting.GOLD, Formatting.BOLD));
        ctx.getSource().sendMessage(Text.literal("═══════════════════════════════").formatted(Formatting.GOLD));
        
        ctx.getSource().sendMessage(Text.literal("Behavior: ").formatted(Formatting.YELLOW)
            .append(Text.literal(config.getBehavior().toString()).formatted(Formatting.WHITE)));
        ctx.getSource().sendMessage(Text.literal("Integrity Mode: ").formatted(Formatting.YELLOW)
            .append(Text.literal(config.getIntegrityMode().toString()).formatted(Formatting.WHITE)));
        ctx.getSource().sendMessage(Text.literal("Whitelist Mode: ").formatted(Formatting.YELLOW)
            .append(Text.literal(config.isWhitelist() ? "ON" : "OFF").formatted(Formatting.WHITE)));
        ctx.getSource().sendMessage(Text.literal("Bedrock Players: ").formatted(Formatting.YELLOW)
            .append(Text.literal(config.isAllowBedrockPlayers() ? "Allowed" : "Blocked").formatted(Formatting.WHITE)));
        ctx.getSource().sendMessage(Text.literal("Player Database: ").formatted(Formatting.YELLOW)
            .append(Text.literal(config.isPlayerdbEnabled() ? "Enabled" : "Disabled").formatted(Formatting.WHITE)));
        
        ctx.getSource().sendMessage(Text.literal("Required Mods Enabled: ").formatted(Formatting.YELLOW)
            .append(Text.literal(config.areModsRequiredEnabled() ? "ON" : "OFF").formatted(Formatting.WHITE)));
        ctx.getSource().sendMessage(Text.literal("Blacklisted Mods Enabled: ").formatted(Formatting.YELLOW)
            .append(Text.literal(config.areModsBlacklistedEnabled() ? "ON" : "OFF").formatted(Formatting.WHITE)));
        ctx.getSource().sendMessage(Text.literal("Whitelisted Mods Enabled: ").formatted(Formatting.YELLOW)
            .append(Text.literal(config.areModsWhitelistedEnabled() ? "ON" : "OFF").formatted(Formatting.WHITE)));
        
        return Command.SINGLE_SUCCESS;
    }

    private static int setConfigValue(CommandContext<ServerCommandSource> ctx, String param) {
        String value = StringArgumentType.getString(ctx, "value");
        ConfigManager config = HandShakerServer.getInstance().getConfigManager();
        
        switch (param) {
            case "behavior" -> {
                if (!value.equalsIgnoreCase("STRICT") && !value.equalsIgnoreCase("VANILLA")) {
                    ctx.getSource().sendError(Text.literal("Behavior must be STRICT or VANILLA"));
                    return 0;
                }
                config.setBehavior(value);
                ctx.getSource().sendFeedback(() -> Text.literal("✓ Behavior set to " + value.toUpperCase()).formatted(Formatting.GREEN), true);
                HandShakerServer.getInstance().checkAllPlayers();
            }
            case "integrity" -> {
                if (!value.equalsIgnoreCase("SIGNED") && !value.equalsIgnoreCase("DEV")) {
                    ctx.getSource().sendError(Text.literal("Integrity must be SIGNED or DEV"));
                    return 0;
                }
                config.setIntegrityMode(value);
                ctx.getSource().sendFeedback(() -> Text.literal("✓ Integrity mode set to " + value.toUpperCase()).formatted(Formatting.GREEN), true);
            }
            case "whitelist" -> {
                if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                    ctx.getSource().sendError(Text.literal("Whitelist must be true or false"));
                    return 0;
                }
                boolean enable = value.equalsIgnoreCase("true");
                config.setWhitelist(enable);
                ctx.getSource().sendFeedback(() -> Text.literal("✓ Whitelist mode " + (enable ? "ON" : "OFF")).formatted(Formatting.GREEN), true);
                HandShakerServer.getInstance().checkAllPlayers();
            }
            case "allow_bedrock" -> {
                if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                    ctx.getSource().sendError(Text.literal("Bedrock must be true or false"));
                    return 0;
                }
                boolean allow = value.equalsIgnoreCase("true");
                config.setAllowBedrockPlayers(allow);
                ctx.getSource().sendFeedback(() -> Text.literal("✓ Bedrock players " + (allow ? "allowed" : "blocked")).formatted(Formatting.GREEN), true);
                HandShakerServer.getInstance().checkAllPlayers();
            }
            case "playerdb_enabled" -> {
                if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                    ctx.getSource().sendError(Text.literal("Player database must be true or false"));
                    return 0;
                }
                boolean enable = value.equalsIgnoreCase("true");
                config.setPlayerdbEnabled(enable);
                ctx.getSource().sendFeedback(() -> Text.literal("✓ Player database " + (enable ? "enabled" : "disabled")).formatted(Formatting.GREEN), true);
            }
            default -> {
                ctx.getSource().sendError(Text.literal("Unknown config parameter: " + param));
                return 0;
            }
        }
        
        config.save();
        return Command.SINGLE_SUCCESS;
    }

    private static int addMod(CommandContext<ServerCommandSource> ctx) {
        // Try to get mod from argument, fallback to "*" if it's a literal
        String modId;
        try {
            modId = StringArgumentType.getString(ctx, "mod").trim().toLowerCase();
        } catch (IllegalArgumentException e) {
            // mod was a literal("*"), so just use "*"
            modId = "*";
        }
        
        String mode = StringArgumentType.getString(ctx, "mode").toLowerCase();
        ConfigManager config = HandShakerServer.getInstance().getConfigManager();
        
        if (!isValidMode(mode)) {
            ctx.getSource().sendError(Text.literal("Invalid mode. Use: required, blacklisted, or allowed"));
            return 0;
        }
        
        if (modId.equals("*")) {
            ServerPlayerEntity player = ctx.getSource().getEntity() instanceof ServerPlayerEntity ? (ServerPlayerEntity) ctx.getSource().getEntity() : null;
            if (player == null) {
                ctx.getSource().sendError(Text.literal("Only players can use * wildcard"));
                return 0;
            }
            
            ClientInfo info = HandShakerServer.getInstance().getClients().get(player.getUuid());
            if (info == null || info.mods().isEmpty()) {
                ctx.getSource().sendError(Text.literal("No mods found on your client"));
                return 0;
            }
            
            int added = 0;
            for (String mod : info.mods()) {
                if (!config.isIgnored(mod)) {
                    config.setModConfig(mod, mode, "kick", null);
                    added++;
                }
            }
            final int finalAdded = added;
            final String finalMode = mode;
            ctx.getSource().sendFeedback(() -> Text.literal("✓ Added " + finalAdded + " of your mods as " + finalMode).formatted(Formatting.GREEN), true);
        } else {
            final String finalModId = modId;
            final String finalMode = mode;
            config.setModConfig(modId, mode, "kick", null);
            ctx.getSource().sendFeedback(() -> Text.literal("✓ Added " + finalModId + " as " + finalMode).formatted(Formatting.GREEN), true);
        }
        
        HandShakerServer.getInstance().checkAllPlayers();
        return Command.SINGLE_SUCCESS;
    }

    private static int addModWithAction(CommandContext<ServerCommandSource> ctx) {
        // Try to get mod from argument, fallback to "*" if it's a literal
        String modId;
        try {
            modId = StringArgumentType.getString(ctx, "mod").trim().toLowerCase();
        } catch (IllegalArgumentException e) {
            // mod was a literal("*"), so just use "*"
            modId = "*";
        }
        
        String mode = StringArgumentType.getString(ctx, "mode").toLowerCase();
        String action = StringArgumentType.getString(ctx, "action").toLowerCase();
        ConfigManager config = HandShakerServer.getInstance().getConfigManager();
        
        if (!isValidMode(mode)) {
            ctx.getSource().sendError(Text.literal("Invalid mode. Use: required, blacklisted, or allowed"));
            return 0;
        }
        
        if (!isValidAction(action)) {
            Set<String> availableActions = config.getAvailableActions();
            String actionList = availableActions.isEmpty() ? "kick, ban" : String.join(", ", availableActions);
            ctx.getSource().sendError(Text.literal("Invalid action. Available: " + actionList));
            return 0;
        }
        
        if (modId.equals("*")) {
            ServerPlayerEntity player = ctx.getSource().getEntity() instanceof ServerPlayerEntity ? (ServerPlayerEntity) ctx.getSource().getEntity() : null;
            if (player == null) {
                ctx.getSource().sendError(Text.literal("Only players can use * wildcard"));
                return 0;
            }
            
            ClientInfo info = HandShakerServer.getInstance().getClients().get(player.getUuid());
            if (info == null || info.mods().isEmpty()) {
                ctx.getSource().sendError(Text.literal("No mods found on your client"));
                return 0;
            }
            
            int added = 0;
            for (String mod : info.mods()) {
                if (!config.isIgnored(mod)) {
                    config.setModConfig(mod, mode, action, null);
                    added++;
                }
            }
            final int finalAdded = added;
            final String finalMode = mode;
            final String finalAction = action;
            ctx.getSource().sendFeedback(() -> Text.literal("✓ Added " + finalAdded + " of your mods as " + finalMode + " with " + finalAction).formatted(Formatting.GREEN), true);
        } else {
            final String finalModId = modId;
            final String finalMode = mode;
            final String finalAction = action;
            config.setModConfig(modId, mode, action, null);
            ctx.getSource().sendFeedback(() -> Text.literal("✓ Added " + finalModId + " as " + finalMode + " with " + finalAction).formatted(Formatting.GREEN), true);
        }
        
        HandShakerServer.getInstance().checkAllPlayers();
        return Command.SINGLE_SUCCESS;
    }

    private static int changeMod(CommandContext<ServerCommandSource> ctx) {
        String modId = StringArgumentType.getString(ctx, "mod").toLowerCase();
        String mode = StringArgumentType.getString(ctx, "mode").toLowerCase();
        ConfigManager config = HandShakerServer.getInstance().getConfigManager();
        
        if (!isValidMode(mode)) {
            ctx.getSource().sendError(Text.literal("Invalid mode. Use: required, blacklisted, or allowed"));
            return 0;
        }
        
        ConfigState.ModConfig oldConfig = config.getModConfig(modId);
        config.setModConfig(modId, mode, oldConfig.getAction().toString().toLowerCase(), oldConfig.getWarnMessage());
        ctx.getSource().sendFeedback(() -> Text.literal("✓ Changed " + modId + " to " + mode).formatted(Formatting.GREEN), true);
        HandShakerServer.getInstance().checkAllPlayers();
        return Command.SINGLE_SUCCESS;
    }

    private static int changeModWithAction(CommandContext<ServerCommandSource> ctx) {
        String modId = StringArgumentType.getString(ctx, "mod").toLowerCase();
        String mode = StringArgumentType.getString(ctx, "mode").toLowerCase();
        String action = StringArgumentType.getString(ctx, "action").toLowerCase();
        ConfigManager config = HandShakerServer.getInstance().getConfigManager();
        
        if (!isValidMode(mode)) {
            ctx.getSource().sendError(Text.literal("Invalid mode. Use: required, blacklisted, or allowed"));
            return 0;
        }
        
        if (!isValidAction(action)) {
            ctx.getSource().sendError(Text.literal("Invalid action. Use: kick or ban"));
            return 0;
        }
        
        config.setModConfig(modId, mode, action, null);
        ctx.getSource().sendFeedback(() -> Text.literal("✓ Changed " + modId + " to " + mode + " with " + action).formatted(Formatting.GREEN), true);
        HandShakerServer.getInstance().checkAllPlayers();
        return Command.SINGLE_SUCCESS;
    }

    private static int removeMod(CommandContext<ServerCommandSource> ctx) {
        String modId = StringArgumentType.getString(ctx, "mod").toLowerCase();
        ConfigManager config = HandShakerServer.getInstance().getConfigManager();
        
        boolean removed = config.removeModConfig(modId);
        if (removed) {
            ctx.getSource().sendFeedback(() -> Text.literal("✓ Removed " + modId).formatted(Formatting.GREEN), true);
            HandShakerServer.getInstance().checkAllPlayers();
        } else {
            ctx.getSource().sendError(Text.literal("Mod not found: " + modId));
        }
        
        return removed ? Command.SINGLE_SUCCESS : 0;
    }

    private static int showInfo(CommandContext<ServerCommandSource> ctx) {
        ConfigManager config = HandShakerServer.getInstance().getConfigManager();
        PlayerHistoryDatabase db = HandShakerServer.getInstance().getPlayerHistoryDb();
        
        ctx.getSource().sendMessage(Text.literal("═══════════════════════════════").formatted(Formatting.GOLD));
        ctx.getSource().sendMessage(Text.literal("HandShaker Statistics").formatted(Formatting.GOLD, Formatting.BOLD));
        ctx.getSource().sendMessage(Text.literal("═══════════════════════════════").formatted(Formatting.GOLD));
        
        int uniqueMods = 0;
        int activePlayers = 0;
        if (db != null && db.isEnabled()) {
            Map<String, Integer> popularity = db.getModPopularity();
            uniqueMods = popularity.size();
            activePlayers = db.getUniqueActivePlayers();
        }
        
        int configuredMods = config.getModConfigMap().size();
        
        ctx.getSource().sendMessage(Text.literal("Unique Mods Detected: ").formatted(Formatting.YELLOW)
            .append(Text.literal(uniqueMods + "").formatted(Formatting.WHITE)));
        ctx.getSource().sendMessage(Text.literal("Configured Mods: ").formatted(Formatting.YELLOW)
            .append(Text.literal(configuredMods + "").formatted(Formatting.WHITE)));
        if (db != null && db.isEnabled()) {
            ctx.getSource().sendMessage(Text.literal("Active Players: ").formatted(Formatting.YELLOW)
                .append(Text.literal(activePlayers + "").formatted(Formatting.WHITE)));
        }
        
        ctx.getSource().sendMessage(Text.empty());
        ctx.getSource().sendMessage(Text.literal("HandShaker Status").formatted(Formatting.GOLD, Formatting.BOLD));
        ctx.getSource().sendMessage(Text.literal("Behavior: ").formatted(Formatting.YELLOW)
            .append(Text.literal(config.getBehavior().toString()).formatted(Formatting.WHITE)));
        ctx.getSource().sendMessage(Text.literal("Integrity Mode: ").formatted(Formatting.YELLOW)
            .append(Text.literal(config.getIntegrityMode().toString()).formatted(Formatting.WHITE)));
        ctx.getSource().sendMessage(Text.literal("Whitelist Mode: ").formatted(Formatting.YELLOW)
            .append(Text.literal(config.isWhitelist() ? "ON" : "OFF").formatted(Formatting.WHITE)));
        ctx.getSource().sendMessage(Text.literal("Handshake Timeout: ").formatted(Formatting.YELLOW)
            .append(Text.literal(config.getHandshakeTimeoutSeconds() + "s").formatted(Formatting.WHITE)));
        ctx.getSource().sendMessage(Text.literal("Mod Lists: ").formatted(Formatting.YELLOW)
            .append(Text.literal("required=" + (config.areModsRequiredEnabled() ? "on" : "off")
                + ", blacklisted=" + (config.areModsBlacklistedEnabled() ? "on" : "off")
                + ", whitelisted=" + (config.areModsWhitelistedEnabled() ? "on" : "off"))
                .formatted(Formatting.WHITE)));

        ctx.getSource().sendMessage(Text.empty());
        ctx.getSource().sendMessage(Text.literal("Use /handshaker info configured_mods [page] to list configured mods").formatted(Formatting.GRAY));
        ctx.getSource().sendMessage(Text.literal("Use /handshaker info all_mods [page] to see all detected mods").formatted(Formatting.GRAY));
        ctx.getSource().sendMessage(Text.literal("Use /handshaker info player <player> [page] to see player history").formatted(Formatting.GRAY));
        
        return Command.SINGLE_SUCCESS;
    }

    private static int showConfiguredMods(CommandContext<ServerCommandSource> ctx) {
        return showConfiguredModsWithPage(ctx, 1);
    }

    private static int showConfiguredModsWithPage(CommandContext<ServerCommandSource> ctx) {
        int page = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "page");
        return showConfiguredModsWithPage(ctx, page);
    }

    private static int showConfiguredModsWithPage(CommandContext<ServerCommandSource> ctx, int pageNum) {
        ConfigManager config = HandShakerServer.getInstance().getConfigManager();
        Map<String, ConfigState.ModConfig> mods = config.getModConfigMap();
        
        ctx.getSource().sendMessage(Text.literal("═══════════════════════════════").formatted(Formatting.GOLD));
        ctx.getSource().sendMessage(Text.literal("Configured Mods (Whitelist: " + (config.isWhitelist() ? "ON" : "OFF") + ")").formatted(Formatting.GOLD, Formatting.BOLD));
        ctx.getSource().sendMessage(Text.literal("═══════════════════════════════").formatted(Formatting.GOLD));
        
        if (mods.isEmpty()) {
            ctx.getSource().sendMessage(Text.literal("No mods configured").formatted(Formatting.YELLOW));
            return Command.SINGLE_SUCCESS;
        }

        List<Map.Entry<String, ConfigState.ModConfig>> modList = new ArrayList<>(mods.entrySet());
        modList.sort(Map.Entry.comparingByKey());
        int totalPages = (int) Math.ceil((double) modList.size() / PAGE_SIZE);
        if (pageNum < 1 || pageNum > totalPages) {
            ctx.getSource().sendError(Text.literal("Invalid page. Total pages: " + totalPages));
            return 0;
        }
        int startIdx = (pageNum - 1) * PAGE_SIZE;
        int endIdx = Math.min(startIdx + PAGE_SIZE, modList.size());

        for (int i = startIdx; i < endIdx; i++) {
            Map.Entry<String, ConfigState.ModConfig> entry = modList.get(i);
            ConfigState.ModConfig modCfg = entry.getValue();
            ctx.getSource().sendMessage(formatModLine(entry.getKey(), modCfg, null, modCfg.getAction()));
        }

        if (totalPages > 1) {
            ctx.getSource().sendMessage(Text.literal("Use /handshaker info configured_mods <page> to navigate").formatted(Formatting.GRAY));
        }
        
        return Command.SINGLE_SUCCESS;
    }

    private static int showAllMods(CommandContext<ServerCommandSource> ctx) {
        return showAllModsWithPage(ctx, 1);
    }

    private static int showAllModsWithPage(CommandContext<ServerCommandSource> ctx) {
        int page = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "page");
        return showAllModsWithPage(ctx, page);
    }

    private static int showAllModsWithPage(CommandContext<ServerCommandSource> ctx, int pageNum) {
        PlayerHistoryDatabase db = HandShakerServer.getInstance().getPlayerHistoryDb();
        ConfigManager config = HandShakerServer.getInstance().getConfigManager();
        
        if (db == null || !db.isEnabled()) {
            ctx.getSource().sendError(Text.literal("Player history database not available"));
            return 0;
        }
        
        Map<String, Integer> popularity = db.getModPopularity();
        List<Map.Entry<String, Integer>> sortedMods = new ArrayList<>(popularity.entrySet());
        sortedMods.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        
        int totalPages = (int) Math.ceil((double) sortedMods.size() / PAGE_SIZE);
        
        if (pageNum < 1 || pageNum > totalPages) {
            ctx.getSource().sendError(Text.literal("Invalid page. Total pages: " + totalPages));
            return 0;
        }
        
        int startIdx = (pageNum - 1) * PAGE_SIZE;
        int endIdx = Math.min(startIdx + PAGE_SIZE, sortedMods.size());
        
        ctx.getSource().sendMessage(Text.literal("═══════════════════════════════").formatted(Formatting.GOLD));
        ctx.getSource().sendMessage(Text.literal("All Detected Mods (Page " + pageNum + "/" + totalPages + ")").formatted(Formatting.GOLD, Formatting.BOLD));
        ctx.getSource().sendMessage(Text.literal("═══════════════════════════════").formatted(Formatting.GOLD));
        
        for (int i = startIdx; i < endIdx; i++) {
            Map.Entry<String, Integer> entry = sortedMods.get(i);
            ConfigState.ModConfig modCfg = config.getModConfig(entry.getKey());

            ctx.getSource().sendMessage(formatModLine(entry.getKey(), modCfg, entry.getValue(), null));
        }
        
        if (pageNum < totalPages) {
            ctx.getSource().sendMessage(Text.literal("Use /handshaker info all_mods " + (pageNum + 1) + " for next page").formatted(Formatting.GRAY));
        }
        
        return Command.SINGLE_SUCCESS;
    }

    private static int showModInfo(CommandContext<ServerCommandSource> ctx) {
        String modName = StringArgumentType.getString(ctx, "modName");
        PlayerHistoryDatabase db = HandShakerServer.getInstance().getPlayerHistoryDb();
        
        if (db == null || !db.isEnabled()) {
            ctx.getSource().sendError(Text.literal("Player history database not available"));
            return 0;
        }
        
        List<PlayerHistoryDatabase.PlayerModInfo> players = db.getPlayersWithMod(modName);
        
        if (players.isEmpty()) {
            ctx.getSource().sendMessage(Text.literal("No players found with mod: " + modName).formatted(Formatting.YELLOW));
            return Command.SINGLE_SUCCESS;
        }
        
        ctx.getSource().sendMessage(Text.literal("═══════════════════════════════").formatted(Formatting.GOLD));
        ctx.getSource().sendMessage(Text.literal("Mod: " + modName).formatted(Formatting.GOLD, Formatting.BOLD));
        ctx.getSource().sendMessage(Text.literal("═══════════════════════════════").formatted(Formatting.GOLD));
        ctx.getSource().sendMessage(Text.literal("Players: " + players.size()).formatted(Formatting.YELLOW));
        ctx.getSource().sendMessage(Text.empty());
        
        for (PlayerHistoryDatabase.PlayerModInfo player : players) {
            String status = player.isActive() ? "✓ Active" : "✗ Removed";
            Formatting statusColor = player.isActive() ? Formatting.GREEN : Formatting.RED;
            
            ctx.getSource().sendMessage(Text.literal(player.currentName()).formatted(Formatting.AQUA)
                .append(Text.literal(" - " + status).formatted(statusColor))
                .append(Text.literal(" (Since: " + player.getFirstSeenFormatted() + ")").formatted(Formatting.GRAY)));
        }
        
        return Command.SINGLE_SUCCESS;
    }

    private static int showPlayerHistory(CommandContext<ServerCommandSource> ctx) {
        return showPlayerHistoryWithPage(ctx, 1);
    }

    private static int showPlayerHistoryWithPage(CommandContext<ServerCommandSource> ctx) {
        int page = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "page");
        return showPlayerHistoryWithPage(ctx, page);
    }

    private static int showPlayerHistoryWithPage(CommandContext<ServerCommandSource> ctx, int pageNum) {
        String playerName = StringArgumentType.getString(ctx, "playerName");
        PlayerHistoryDatabase db = HandShakerServer.getInstance().getPlayerHistoryDb();

        if (db == null || !db.isEnabled()) {
            ctx.getSource().sendError(Text.literal("Player history database not available"));
            return 0;
        }

        UUID uuid = null;
        ServerPlayerEntity online = ctx.getSource().getServer().getPlayerManager().getPlayer(playerName);
        if (online != null) {
            uuid = online.getUuid();
        } else {
            uuid = db.getPlayerUuidByName(playerName).orElse(null);
        }

        if (uuid == null) {
            ctx.getSource().sendError(Text.literal("No player history found for: " + playerName));
            return 0;
        }

        List<PlayerHistoryDatabase.ModHistoryEntry> history = db.getPlayerHistory(uuid);
        if (history.isEmpty()) {
            ctx.getSource().sendMessage(Text.literal("No mod history found for: " + playerName).formatted(Formatting.YELLOW));
            return Command.SINGLE_SUCCESS;
        }

        int totalPages = (int) Math.ceil((double) history.size() / PAGE_SIZE);
        if (pageNum < 1 || pageNum > totalPages) {
            ctx.getSource().sendError(Text.literal("Invalid page. Total pages: " + totalPages));
            return 0;
        }

        int startIdx = (pageNum - 1) * PAGE_SIZE;
        int endIdx = Math.min(startIdx + PAGE_SIZE, history.size());

        ctx.getSource().sendMessage(Text.literal("═══════════════════════════════").formatted(Formatting.GOLD));
        ctx.getSource().sendMessage(Text.literal("Mod History: " + playerName + " (Page " + pageNum + "/" + totalPages + ")")
            .formatted(Formatting.GOLD, Formatting.BOLD));
        ctx.getSource().sendMessage(Text.literal("═══════════════════════════════").formatted(Formatting.GOLD));

        for (int i = startIdx; i < endIdx; i++) {
            PlayerHistoryDatabase.ModHistoryEntry entry = history.get(i);
            String status = entry.isActive() ? "ACTIVE" : "REMOVED";
            Formatting statusColor = entry.isActive() ? Formatting.GREEN : Formatting.RED;
            String dates = "Added: " + entry.getAddedDateFormatted();
            if (!entry.isActive() && entry.getRemovedDateFormatted() != null) {
                dates += " | Removed: " + entry.getRemovedDateFormatted();
            }

            ctx.getSource().sendMessage(Text.literal(entry.modName()).formatted(Formatting.YELLOW)
                .append(Text.literal(" [" + status + "] ").formatted(statusColor))
                .append(Text.literal(dates).formatted(Formatting.DARK_GRAY)));
        }

        if (totalPages > 1) {
            ctx.getSource().sendMessage(Text.literal("Use /handshaker info player " + playerName + " <page> to navigate").formatted(Formatting.GRAY));
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int setMode(CommandContext<ServerCommandSource> ctx) {
        String listName = StringArgumentType.getString(ctx, "list").toLowerCase();
        String action = StringArgumentType.getString(ctx, "action").toLowerCase();
        ConfigManager config = HandShakerServer.getInstance().getConfigManager();

        if (!action.equals("on") && !action.equals("off")) {
            ctx.getSource().sendFeedback(() -> Text.literal("✗ Action must be 'on' or 'off'").formatted(Formatting.RED), true);
            return Command.SINGLE_SUCCESS;
        }

        boolean isActive = action.equals("on");
        String displayName;
        boolean newState;

        switch (listName) {
            case "mods_required" -> {
                newState = config.toggleRequiredModsActive();
                displayName = "Required Mods";
                if (isActive && config.getRequiredMods().isEmpty()) {
                    ctx.getSource().sendMessage(Text.literal("⚠ Warning: No required mods configured in mods-required.yml").formatted(Formatting.YELLOW));
                }
            }
            case "mods_blacklisted" -> {
                newState = config.toggleBlacklistedModsActive();
                displayName = "Blacklisted Mods";
                if (isActive && config.getBlacklistedMods().isEmpty()) {
                    ctx.getSource().sendMessage(Text.literal("⚠ Warning: No blacklisted mods configured in mods-blacklisted.yml").formatted(Formatting.YELLOW));
                }
            }
            case "mods_whitelisted" -> {
                newState = config.toggleWhitelistedModsActive();
                displayName = "Whitelisted Mods";
                if (isActive && config.getWhitelistedMods().isEmpty()) {
                    ctx.getSource().sendMessage(Text.literal("⚠ Warning: No whitelisted mods configured in mods-whitelisted.yml").formatted(Formatting.YELLOW));
                }
            }
            default -> {
                ctx.getSource().sendFeedback(() -> Text.literal("✗ Unknown list: " + listName).formatted(Formatting.RED), true);
                ctx.getSource().sendMessage(Text.literal("Available lists: mods_required, mods_blacklisted, mods_whitelisted").formatted(Formatting.GRAY));
                return Command.SINGLE_SUCCESS;
            }
        }

        final String finalDisplayName = displayName;
        final boolean finalNewState = newState;
        ctx.getSource().sendFeedback(() -> Text.literal("✓ " + finalDisplayName + " turned " + (finalNewState ? "ON" : "OFF")).formatted(Formatting.GREEN), true);
        HandShakerServer.getInstance().checkAllPlayers();
        return Command.SINGLE_SUCCESS;
    }

    private static boolean isValidMode(String mode) {
        return CommandSuggestionData.MOD_MODES.contains(mode);
    }

    private static boolean isValidAction(String action) {
        ConfigManager config = HandShakerServer.getInstance().getConfigManager();
        Set<String> availableActions = config.getAvailableActions();
        
        if (availableActions.isEmpty()) {
            // Fall back to default validation if none are configured
            return action.equals("kick") || action.equals("ban");
        }
        
        return availableActions.contains(action.toLowerCase());
    }

    // Suggestion methods
    private static CompletableFuture<Suggestions> suggestModes(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase();
        for (String mode : CommandSuggestionData.MOD_MODES) {
            if (mode.startsWith(remaining)) {
                builder.suggest(mode);
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestActions(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder builder) {
        ConfigManager config = HandShakerServer.getInstance().getConfigManager();
        Set<String> availableActions = config.getAvailableActions();
        
        if (availableActions.isEmpty()) {
            // Fall back to default actions if none are configured
            for (String action : CommandSuggestionData.DEFAULT_ACTIONS) {
                builder.suggest(action);
            }
            return builder.buildFuture();
        }
        
        String remaining = builder.getRemaining().toLowerCase();
        for (String action : availableActions) {
            if (action.toLowerCase().startsWith(remaining)) {
                builder.suggest(action);
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestMods(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder builder) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayerEntity player)) {
            return Suggestions.empty();
        }
        
        String remaining = builder.getRemaining().toLowerCase();
        // Always suggest * wildcard
        if ("*".startsWith(remaining)) {
            builder.suggest("*");
        }
        
        ClientInfo clientInfo = HandShakerServer.getInstance().getClients().get(player.getUuid());
        if (clientInfo != null) {
            for (String mod : clientInfo.mods()) {
                if (mod.toLowerCase().startsWith(remaining)) {
                    builder.suggest(mod);
                }
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestConfiguredMods(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder builder) {
        ConfigManager config = HandShakerServer.getInstance().getConfigManager();
        for (String mod : config.getModConfigMap().keySet()) {
            if (mod.toLowerCase().startsWith(builder.getRemaining().toLowerCase())) {
                builder.suggest(mod);
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestAllMods(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder builder) {
        PlayerHistoryDatabase db = HandShakerServer.getInstance().getPlayerHistoryDb();
        if (db == null || !db.isEnabled()) {
            return Suggestions.empty();
        }
        
        Map<String, Integer> allMods = db.getModPopularity();
        String remaining = builder.getRemaining().toLowerCase();
        for (String mod : allMods.keySet()) {
            if (mod.toLowerCase().startsWith(remaining)) {
                builder.suggest(mod);
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestModeLists(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase();
        for (String list : CommandSuggestionData.MODE_LISTS) {
            if (list.startsWith(remaining)) {
                builder.suggest(list);
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestCurrentModeState(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder builder) {
        String listName = StringArgumentType.getString(ctx, "list").toLowerCase();
        ConfigManager config = HandShakerServer.getInstance().getConfigManager();
        
        boolean isCurrentlyEnabled = switch (listName) {
            case "mods_required" -> config.areModsRequiredEnabled();
            case "mods_blacklisted" -> config.areModsBlacklistedEnabled();
            case "mods_whitelisted" -> config.areModsWhitelistedEnabled();
            default -> false;
        };
        
        // Only suggest the opposite of the current state
        if (isCurrentlyEnabled) {
            builder.suggest("off");
        } else {
            builder.suggest("on");
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestPlayers(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder builder) {
        if (!(ctx.getSource().getWorld() instanceof net.minecraft.server.world.ServerWorld world)) {
            return Suggestions.empty();
        }
        for (ServerPlayerEntity player : world.getPlayers()) {
            String name = player.getName().getString();
            if (name.toLowerCase().startsWith(builder.getRemaining().toLowerCase())) {
                builder.suggest(name);
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestPlayerMods(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder builder) {
        String playerName = StringArgumentType.getString(ctx, "player");
        ServerPlayerEntity player = ctx.getSource().getServer().getPlayerManager().getPlayer(playerName);
        
        if (player == null) {
            return Suggestions.empty();
        }
        
        ClientInfo info = HandShakerServer.getInstance().getClients().get(player.getUuid());
        if (info == null || info.mods().isEmpty()) {
            return Suggestions.empty();
        }
        
        for (String mod : info.mods()) {
            if (mod.toLowerCase().startsWith(builder.getRemaining().toLowerCase())) {
                builder.suggest(mod);
            }
        }
        return builder.buildFuture();
    }

    private static int showPlayerMods(CommandContext<ServerCommandSource> ctx) {
        String playerName = StringArgumentType.getString(ctx, "player");
        ServerPlayerEntity player = ctx.getSource().getServer().getPlayerManager().getPlayer(playerName);
        
        if (player == null) {
            ctx.getSource().sendError(Text.literal("Player '" + playerName + "' not found"));
            return 0;
        }
        
        ClientInfo info = HandShakerServer.getInstance().getClients().get(player.getUuid());
        if (info == null || info.mods().isEmpty()) {
            ctx.getSource().sendMessage(Text.literal("No mod list found for " + playerName).formatted(Formatting.YELLOW));
            return Command.SINGLE_SUCCESS;
        }
        
        ctx.getSource().sendMessage(Text.literal("═══════════════════════════════").formatted(Formatting.GOLD));
        ctx.getSource().sendMessage(Text.literal(playerName + "'s Mods").formatted(Formatting.GOLD, Formatting.BOLD));
        ctx.getSource().sendMessage(Text.literal("═══════════════════════════════").formatted(Formatting.GOLD));
        
        for (String mod : info.mods()) {
            ConfigState.ModConfig modCfg = HandShakerServer.getInstance().getConfigManager().getModConfig(mod);
            ctx.getSource().sendMessage(formatModLine(mod, modCfg, null, null));
        }
        
        return Command.SINGLE_SUCCESS;
    }

    private static MutableText formatModLine(String modId, ConfigState.ModConfig modCfg, Integer count, ConfigState.Action action) {
        MutableText line = modeTag(modCfg != null ? modCfg.getMode() : null)
            .append(Text.literal(" " + modId).formatted(Formatting.WHITE));

        if (count != null) {
            line = line.append(Text.literal(" x" + count).formatted(Formatting.GRAY));
        }

        if (action != null && action != ConfigState.Action.KICK) {
            line = line.append(Text.literal(" [" + action.toString().toLowerCase(Locale.ROOT) + "]").formatted(Formatting.GRAY));
        }

        return line;
    }

    private static MutableText modeTag(String mode) {
        if (mode == null) {
            return Text.literal("[DET]").formatted(Formatting.DARK_GRAY);
        }

        return switch (mode) {
            case "required" -> Text.literal("[REQ]").formatted(Formatting.GOLD);
            case "blacklisted" -> Text.literal("[BLK]").formatted(Formatting.RED);
            case "allowed" -> Text.literal("[ALW]").formatted(Formatting.GREEN);
            default -> Text.literal("[UNK]").formatted(Formatting.DARK_GRAY);
        };
    }

    private static int setPlayerModStatus(CommandContext<ServerCommandSource> ctx) {
        String playerName = StringArgumentType.getString(ctx, "player");
        String modId = StringArgumentType.getString(ctx, "mod").toLowerCase();
        String mode = StringArgumentType.getString(ctx, "mode").toLowerCase();
        
        ServerPlayerEntity player = ctx.getSource().getServer().getPlayerManager().getPlayer(playerName);
        if (player == null) {
            ctx.getSource().sendError(Text.literal("Player '" + playerName + "' not found"));
            return 0;
        }
        
        ClientInfo info = HandShakerServer.getInstance().getClients().get(player.getUuid());
        if (info == null || info.mods().isEmpty()) {
            ctx.getSource().sendError(Text.literal("Player has no mods"));
            return 0;
        }
        
        if (!isValidMode(mode)) {
            ctx.getSource().sendError(Text.literal("Invalid mode. Use: required, blacklisted, or allowed"));
            return 0;
        }
        
        ConfigManager config = HandShakerServer.getInstance().getConfigManager();
        
        if (modId.equals("*")) {
            // Set all mods for this player
            int updated = 0;
            for (String mod : info.mods()) {
                if (!config.isIgnored(mod)) {
                    config.setModConfig(mod, mode, "kick", null);
                    updated++;
                }
            }
            final int finalUpdated = updated;
            ctx.getSource().sendFeedback(() -> Text.literal("✓ Set " + finalUpdated + " mods to " + mode + " for player " + playerName).formatted(Formatting.GREEN), true);
        } else {
            if (!info.mods().contains(modId)) {
                ctx.getSource().sendError(Text.literal("Player " + playerName + " does not have mod: " + modId));
                return 0;
            }
            config.setModConfig(modId, mode, "kick", null);
            ctx.getSource().sendFeedback(() -> Text.literal("✓ Set " + modId + " to " + mode + " for " + playerName).formatted(Formatting.GREEN), true);
        }
        
        HandShakerServer.getInstance().checkAllPlayers();
        return Command.SINGLE_SUCCESS;
    }

    private static int addIgnore(CommandContext<ServerCommandSource> ctx) {
        // Try to get mod from argument, fallback to "*" if it's a literal
        String modId;
        try {
            modId = StringArgumentType.getString(ctx, "mod").trim().toLowerCase();
        } catch (IllegalArgumentException e) {
            // mod was a literal("*"), so just use "*"
            modId = "*";
        }
        
        ConfigManager config = HandShakerServer.getInstance().getConfigManager();
        
        if (modId.equals("*")) {
            ServerPlayerEntity player = ctx.getSource().getEntity() instanceof ServerPlayerEntity ? (ServerPlayerEntity) ctx.getSource().getEntity() : null;
            if (player == null) {
                ctx.getSource().sendError(Text.literal("Only players can use * wildcard"));
                return 0;
            }
            
            ClientInfo info = HandShakerServer.getInstance().getClients().get(player.getUuid());
            if (info == null || info.mods().isEmpty()) {
                ctx.getSource().sendError(Text.literal("No mods found on your client"));
                return 0;
            }
            
            int added = 0;
            for (String mod : info.mods()) {
                if (config.addIgnoredMod(mod)) {
                    added++;
                }
            }
            final int finalAdded = added;
            ctx.getSource().sendFeedback(() -> Text.literal("✓ Added " + finalAdded + " of your mods to ignore list").formatted(Formatting.GREEN), true);
        } else {
            final String finalModId = modId;
            boolean added = config.addIgnoredMod(modId);
            if (added) {
                ctx.getSource().sendFeedback(() -> Text.literal("✓ Added " + finalModId + " to ignore list").formatted(Formatting.GREEN), true);
            } else {
                ctx.getSource().sendMessage(Text.literal("⚠ " + finalModId + " already in ignore list").formatted(Formatting.YELLOW));
            }
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int removeIgnore(CommandContext<ServerCommandSource> ctx) {
        String modId = StringArgumentType.getString(ctx, "mod").toLowerCase();
        ConfigManager config = HandShakerServer.getInstance().getConfigManager();
        
        boolean removed = config.removeIgnoredMod(modId);
        if (removed) {
            ctx.getSource().sendFeedback(() -> Text.literal("✓ Removed " + modId + " from ignore list").formatted(Formatting.GREEN), true);
        } else {
            ctx.getSource().sendMessage(Text.literal("⚠ " + modId + " not in ignore list").formatted(Formatting.YELLOW));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int listIgnore(CommandContext<ServerCommandSource> ctx) {
        ConfigManager config = HandShakerServer.getInstance().getConfigManager();
        Set<String> ignored = config.getIgnoredMods();
        
        if (ignored.isEmpty()) {
            ctx.getSource().sendMessage(Text.literal("No mods in ignore list").formatted(Formatting.YELLOW));
            return Command.SINGLE_SUCCESS;
        }
        
        ctx.getSource().sendMessage(Text.literal("═══════════════════════════════").formatted(Formatting.GOLD));
        ctx.getSource().sendMessage(Text.literal("Ignored Mods").formatted(Formatting.GOLD, Formatting.BOLD));
        ctx.getSource().sendMessage(Text.literal("═══════════════════════════════").formatted(Formatting.GOLD));
        
        for (String mod : ignored) {
            ctx.getSource().sendMessage(Text.literal("  • " + mod).formatted(Formatting.GRAY));
        }
        
        return Command.SINGLE_SUCCESS;
    }

    private static CompletableFuture<Suggestions> suggestIgnoredMods(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder builder) {
        ConfigManager config = HandShakerServer.getInstance().getConfigManager();
        for (String mod : config.getIgnoredMods()) {
            if (mod.toLowerCase().startsWith(builder.getRemaining().toLowerCase())) {
                builder.suggest(mod);
            }
        }
        return builder.buildFuture();
    }
}
