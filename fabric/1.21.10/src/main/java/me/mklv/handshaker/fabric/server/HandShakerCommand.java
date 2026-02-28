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
import me.mklv.handshaker.common.commands.CommandSuggestionOperations;
import me.mklv.handshaker.common.commands.CommandHelper;
import me.mklv.handshaker.common.commands.InfoCommandOperations;
import me.mklv.handshaker.common.commands.CommandVisualOperations;
import me.mklv.handshaker.common.commands.ModFingerprintRegistrar;
import me.mklv.handshaker.common.commands.ModListToggler;
import me.mklv.handshaker.common.database.PlayerHistoryDatabase;
import me.mklv.handshaker.common.configs.ConfigTypes.ConfigState;
import me.mklv.handshaker.common.configs.ConfigTypes.ModEntry;
import me.mklv.handshaker.common.utils.ClientInfo;
import me.mklv.handshaker.common.utils.LoggerAdapter;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import net.minecraft.server.world.ServerWorld;

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
                    .then(argument("modName", StringArgumentType.string())
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
                    .executes(ctx -> setConfigValue(ctx, "playerdb_enabled"))))
                .then(literal("hash_mods")
                    .then(argument("value", StringArgumentType.word())
                        .suggests((ctx, builder) -> builder.suggest("true").suggest("false").buildFuture())
                    .executes(ctx -> setConfigValue(ctx, "hash_mods"))))
                .then(literal("mod_versioning")
                    .then(argument("value", StringArgumentType.word())
                        .suggests((ctx, builder) -> builder.suggest("true").suggest("false").buildFuture())
                    .executes(ctx -> setConfigValue(ctx, "mod_versioning"))))
                .then(literal("runtime_cache")
                    .then(argument("value", StringArgumentType.word())
                        .suggests((ctx, builder) -> builder.suggest("true").suggest("false").buildFuture())
                    .executes(ctx -> setConfigValue(ctx, "runtime_cache"))))
                .then(literal("required_modpack_hash")
                    .then(argument("value", StringArgumentType.word())
                        .suggests((ctx, builder) -> builder.suggest("off").suggest("current").buildFuture())
                    .executes(ctx -> setConfigValue(ctx, "required_modpack_hash")))))
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
                    .then(argument("mod", StringArgumentType.string())
                        .suggests(HandShakerCommand::suggestMods)
                        .then(argument("mode", StringArgumentType.word())
                            .suggests(HandShakerCommand::suggestModes)
                            .executes(HandShakerCommand::addMod)
                            .then(argument("action", StringArgumentType.word())
                                .suggests(HandShakerCommand::suggestActions)
                                .executes(HandShakerCommand::addModWithAction)))))
                .then(literal("change")
                    .then(argument("mod", StringArgumentType.string())
                        .suggests(HandShakerCommand::suggestConfiguredMods)
                        .then(argument("mode", StringArgumentType.word())
                            .suggests(HandShakerCommand::suggestModes)
                            .executes(HandShakerCommand::changeMod)
                            .then(argument("action", StringArgumentType.word())
                                .suggests(HandShakerCommand::suggestActions)
                                .executes(HandShakerCommand::changeModWithAction)))))
                .then(literal("remove")
                    .then(argument("mod", StringArgumentType.string())
                        .suggests(HandShakerCommand::suggestConfiguredMods)
                        .executes(HandShakerCommand::removeMod)))
                .then(literal("ignore")
                    .then(literal("add")
                        .then(literal("*")
                            .executes(HandShakerCommand::addIgnore))
                        .then(argument("mod", StringArgumentType.string())
                            .suggests(HandShakerCommand::suggestMods)
                            .executes(HandShakerCommand::addIgnore)))
                    .then(literal("remove")
                        .then(argument("mod", StringArgumentType.string())
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
                        .then(argument("mod", StringArgumentType.string())
                            .suggests(HandShakerCommand::suggestPlayerMods)
                            .then(argument("mode", StringArgumentType.word())
                                .suggests(HandShakerCommand::suggestModes)
                                .executes(HandShakerCommand::setPlayerModStatus))))));
        
        dispatcher.register(handshaker);
    }

    private static int showHelp(CommandContext<ServerCommandSource> ctx) {
        String title = "HandShaker v6 Commands";
        ctx.getSource().sendMessage(sectionHeader(title));
        
        // Loop through help sections and render with Fabric Text API
        for (CommandHelper.HelpSection section : CommandHelper.getHelpSections()) {
            ctx.getSource().sendMessage(Text.literal(section.title()).formatted(Formatting.YELLOW, Formatting.BOLD));
            for (CommandHelper.HelpEntry entry : section.entries()) {
                ctx.getSource().sendMessage(Text.literal(entry.command()).formatted(Formatting.YELLOW)
                    .append(Text.literal(" - " + entry.description()).formatted(Formatting.GRAY)));
            }
            ctx.getSource().sendMessage(Text.empty());
        }

        ctx.getSource().sendMessage(sectionFooter(title));
        
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
        String title = "HandShaker Configuration";
        ctx.getSource().sendMessage(sectionHeader(title));
        
        String requiredModpackHash = config.getRequiredModpackHash();
        java.util.List<CommandHelper.InfoField> configFields = java.util.List.of(
            new CommandHelper.InfoField("Behavior", config.getBehavior().toString()),
            new CommandHelper.InfoField("Integrity Mode", config.getIntegrityMode().toString()),
            new CommandHelper.InfoField("Whitelist Mode", config.isWhitelist() ? "ON" : "OFF"),
            new CommandHelper.InfoField("Bedrock Players", config.isAllowBedrockPlayers() ? "Allowed" : "Blocked"),
            new CommandHelper.InfoField("Player Database", config.isPlayerdbEnabled() ? "Enabled" : "Disabled"),
            new CommandHelper.InfoField("Required Modpack Hash", requiredModpackHash != null ? requiredModpackHash : "OFF")
        );
        
        for (CommandHelper.InfoField field : configFields) {
            ctx.getSource().sendMessage(Text.literal(field.label()).formatted(Formatting.YELLOW)
                .append(Text.literal(": " + field.value()).formatted(Formatting.WHITE)));
        }

        ctx.getSource().sendMessage(sectionFooter(title));
        
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
            case "hash_mods" -> {
                if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                    ctx.getSource().sendError(Text.literal("hash_mods must be true or false"));
                    return 0;
                }
                boolean enable = value.equalsIgnoreCase("true");
                config.setHashMods(enable);
                ctx.getSource().sendFeedback(() -> Text.literal("✓ hash_mods " + (enable ? "enabled" : "disabled")).formatted(Formatting.GREEN), true);
            }
            case "mod_versioning" -> {
                if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                    ctx.getSource().sendError(Text.literal("mod_versioning must be true or false"));
                    return 0;
                }
                boolean enable = value.equalsIgnoreCase("true");
                config.setModVersioning(enable);
                ctx.getSource().sendFeedback(() -> Text.literal("✓ mod_versioning " + (enable ? "enabled" : "disabled")).formatted(Formatting.GREEN), true);
            }
            case "runtime_cache" -> {
                if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                    ctx.getSource().sendError(Text.literal("runtime_cache must be true or false"));
                    return 0;
                }
                boolean enable = value.equalsIgnoreCase("true");
                config.setRuntimeCache(enable);
                ctx.getSource().sendFeedback(() -> Text.literal("✓ runtime_cache " + (enable ? "enabled" : "disabled")).formatted(Formatting.GREEN), true);
            }
            case "required_modpack_hash" -> {
                if (value.equalsIgnoreCase("current")) {
                    ServerPlayerEntity player = ctx.getSource().getEntity() instanceof ServerPlayerEntity serverPlayer
                        ? serverPlayer
                        : null;
                    if (player == null) {
                        ctx.getSource().sendError(Text.literal("'current' can only be used by an in-game player"));
                        return 0;
                    }
                    ClientInfo info = HandShakerServer.getInstance().getClients().get(player.getUuid());
                    if (info == null || info.mods() == null || info.mods().isEmpty()) {
                        ctx.getSource().sendError(Text.literal("No client mod list available. Join with HandShaker client first."));
                        return 0;
                    }

                    String computed = CommandHelper.computeModpackHash(info.mods(), config.isHashMods());
                    config.setRequiredModpackHash(computed);
                    ctx.getSource().sendFeedback(() -> Text.literal("✓ required_modpack_hash set to current client hash: " + computed).formatted(Formatting.GREEN), true);
                    HandShakerServer.getInstance().checkAllPlayers();
                    break;
                }

                String normalized = CommandHelper.normalizeRequiredModpackHash(value);
                if (normalized == null && !value.equalsIgnoreCase("off") && !value.equalsIgnoreCase("none") && !value.equalsIgnoreCase("null")) {
                    ctx.getSource().sendError(Text.literal("required_modpack_hash must be 64-char SHA-256, 'off', or 'current'"));
                    return 0;
                }
                config.setRequiredModpackHash(normalized);
                ctx.getSource().sendFeedback(() -> Text.literal("✓ required_modpack_hash " + (normalized == null ? "disabled" : "set")).formatted(Formatting.GREEN), true);
                HandShakerServer.getInstance().checkAllPlayers();
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
        
        if (!CommandHelper.isValidMode(mode)) {
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
                    config.setModConfig(mod, mode, CommandHelper.defaultActionForMode(mode), null);
                    registerModFingerprint(config, mod);
                    added++;
                }
            }
            final int finalAdded = added;
            final String finalMode = mode;
            ctx.getSource().sendFeedback(() -> Text.literal("✓ Added " + finalAdded + " of your mods as " + finalMode).formatted(Formatting.GREEN), true);
        } else {
            final String finalModId = modId;
            final String finalMode = mode;
            config.setModConfig(modId, mode, CommandHelper.defaultActionForMode(mode), null);
            registerModFingerprint(config, modId);
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
        
        if (!CommandHelper.isValidMode(mode)) {
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
                    registerModFingerprint(config, mod);
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
            registerModFingerprint(config, modId);
            ctx.getSource().sendFeedback(() -> Text.literal("✓ Added " + finalModId + " as " + finalMode + " with " + finalAction).formatted(Formatting.GREEN), true);
        }
        
        HandShakerServer.getInstance().checkAllPlayers();
        return Command.SINGLE_SUCCESS;
    }

    private static int changeMod(CommandContext<ServerCommandSource> ctx) {
        String modId = StringArgumentType.getString(ctx, "mod").toLowerCase();
        String mode = StringArgumentType.getString(ctx, "mode").toLowerCase();
        ConfigManager config = HandShakerServer.getInstance().getConfigManager();
        
        if (!CommandHelper.isValidMode(mode)) {
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
        
        if (!CommandHelper.isValidMode(mode)) {
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

        String statsTitle = "HandShaker Statistics";
        ctx.getSource().sendMessage(sectionHeader(statsTitle));
        
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

        ctx.getSource().sendMessage(sectionFooter(statsTitle));
        ctx.getSource().sendMessage(Text.empty());

        String statusTitle = "HandShaker Status";
        ctx.getSource().sendMessage(sectionHeader(statusTitle));
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

        ctx.getSource().sendMessage(sectionFooter(statusTitle));

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

        String title = "Configured Mods";
        ctx.getSource().sendMessage(sectionHeader(title));
        
        if (mods.isEmpty()) {
            ctx.getSource().sendMessage(Text.literal(CommandVisualOperations.noModsConfiguredText()).formatted(Formatting.YELLOW));
            ctx.getSource().sendMessage(sectionFooter(title));
            return Command.SINGLE_SUCCESS;
        }

        CommandHelper.PagedList<Map.Entry<String, ConfigState.ModConfig>> paged =
            CommandHelper.configuredModsPage(mods, pageNum, PAGE_SIZE);
        List<Map.Entry<String, ConfigState.ModConfig>> modList = paged.items();
        CommandHelper.Page<Map.Entry<String, ConfigState.ModConfig>> page = paged.page();
        if (page == null) {
            int totalPages = CommandHelper.totalPages(modList.size(), PAGE_SIZE);
            ctx.getSource().sendError(Text.literal("Invalid page. Total pages: " + totalPages));
            return 0;
        }

        ctx.getSource().sendMessage(Text.literal(CommandVisualOperations.pageLabelPrefix()).formatted(Formatting.GRAY)
            .append(Text.literal(String.valueOf(page.pageNum())).formatted(Formatting.WHITE))
            .append(Text.literal(" / ").formatted(Formatting.GRAY))
            .append(Text.literal(String.valueOf(page.totalPages())).formatted(Formatting.WHITE)));
        ctx.getSource().sendMessage(Text.empty());

        for (int i = page.startIdx(); i < page.endIdx(); i++) {
            Map.Entry<String, ConfigState.ModConfig> entry = modList.get(i);
            ConfigState.ModConfig modCfg = entry.getValue();
            if (ctx.getSource().getEntity() instanceof ServerPlayerEntity) {
                String modName = entry.getKey();
                String changeCommand = CommandVisualOperations.manageChangeCommand("/handshaker", modName);
                String removeCommand = CommandVisualOperations.manageRemoveCommand("/handshaker", modName);
                String actionLabel = CommandVisualOperations.actionDisplayLabel(modCfg);
                String hoverText = CommandVisualOperations.changeHoverText(modCfg);

                MutableText modText = Text.literal(modName).setStyle(
                    Style.EMPTY
                        .withColor(Formatting.WHITE)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, changeCommand))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(hoverText)))
                );

                MutableText removeBtn = Text.literal("[✕]").setStyle(
                    Style.EMPTY
                        .withColor(Formatting.RED)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, removeCommand))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(CommandVisualOperations.removeHoverText())))
                );

                ctx.getSource().sendMessage(Text.literal("- ").formatted(Formatting.GRAY)
                    .append(modText)
                    .append(Text.literal(" ").formatted(Formatting.GRAY))
                    .append(modeTag(modCfg != null ? modCfg.getMode() : null))
                    .append(Text.literal(CommandVisualOperations.actionLabelPrefix()).formatted(Formatting.DARK_GRAY))
                    .append(Text.literal(actionLabel).formatted(Formatting.GRAY))
                    .append(Text.literal(" | ").formatted(Formatting.DARK_GRAY))
                    .append(removeBtn));
            } else {
                ctx.getSource().sendMessage(Text.literal("- ").formatted(Formatting.GRAY)
                    .append(Text.literal(entry.getKey()).formatted(Formatting.WHITE)));
                ctx.getSource().sendMessage(Text.literal("  Mode: ").formatted(Formatting.DARK_GRAY)
                    .append(modeTag(modCfg != null ? modCfg.getMode() : null))
                    .append(Text.literal(CommandVisualOperations.actionLabelPrefix()).formatted(Formatting.DARK_GRAY))
                    .append(Text.literal(CommandVisualOperations.actionDisplayLabel(modCfg)).formatted(Formatting.GRAY)));
            }
        }

        if (page.totalPages() > 1) {
            ctx.getSource().sendMessage(Text.empty());
            sendPaginationNavigation(
                ctx,
                page.pageNum() > 1,
                "/handshaker info configured_mods " + (page.pageNum() - 1),
                page.pageNum() < page.totalPages(),
                "/handshaker info configured_mods " + (page.pageNum() + 1),
                "/handshaker info configured_mods <page>"
            );
        }
        ctx.getSource().sendMessage(sectionFooter(title));
        
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
        CommandHelper.PagedList<Map.Entry<String, Integer>> paged =
            CommandHelper.popularityPage(popularity, pageNum, PAGE_SIZE);
        List<Map.Entry<String, Integer>> sortedMods = paged.items();
        CommandHelper.Page<Map.Entry<String, Integer>> page = paged.page();
        if (page == null) {
            int totalPages = CommandHelper.totalPages(sortedMods.size(), PAGE_SIZE);
            ctx.getSource().sendError(Text.literal("Invalid page. Total pages: " + totalPages));
            return 0;
        }
        
        String title = "All Mods";
        ctx.getSource().sendMessage(sectionHeader(title));
        ctx.getSource().sendMessage(Text.literal(CommandVisualOperations.pageLabelPrefix()).formatted(Formatting.GRAY)
            .append(Text.literal(String.valueOf(page.pageNum())).formatted(Formatting.WHITE))
            .append(Text.literal(" / ").formatted(Formatting.GRAY))
            .append(Text.literal(String.valueOf(page.totalPages())).formatted(Formatting.WHITE)));
        ctx.getSource().sendMessage(Text.empty());
        
        for (int i = page.startIdx(); i < page.endIdx(); i++) {
            Map.Entry<String, Integer> entry = sortedMods.get(i);
            ConfigState.ModConfig modCfg = config.getModConfig(entry.getKey());
            String modToken = entry.getKey();
            ModEntry parsed = ModEntry.parse(modToken);
            String modId = parsed != null ? parsed.modId() : modToken;
            String version = parsed != null && parsed.version() != null ? parsed.version() : "unknown";
            String displayName = parsed != null && parsed.displayName() != null ? parsed.displayName() : prettyModName(modId);

            MutableText modNameText;
            if (ctx.getSource().getEntity() instanceof ServerPlayerEntity) {
                String modInfoCommand = CommandVisualOperations.infoModCommand("/handshaker", modToken);
                modNameText = Text.literal(displayName).setStyle(
                    Style.EMPTY
                        .withColor(Formatting.WHITE)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, modInfoCommand))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(CommandVisualOperations.showPlayersUsingModHoverText())))
                );
            } else {
                modNameText = Text.literal(displayName).formatted(Formatting.WHITE);
            }

            ctx.getSource().sendMessage(modeTag(modCfg != null ? modCfg.getMode() : null)
                .append(Text.literal(" ").formatted(Formatting.GRAY))
                .append(modNameText)
                .append(Text.literal(" x" + entry.getValue()).formatted(Formatting.GRAY)));
            ctx.getSource().sendMessage(Text.literal("  ID: ").formatted(Formatting.DARK_GRAY)
                .append(Text.literal(modId).formatted(Formatting.GRAY))
                .append(Text.literal(" | Version: ").formatted(Formatting.DARK_GRAY))
                .append(Text.literal(version).formatted(Formatting.GRAY)));
        }
        
        if (page.totalPages() > 1) {
            ctx.getSource().sendMessage(Text.empty());
            sendPaginationNavigation(
                ctx,
                page.pageNum() > 1,
                "/handshaker info all_mods " + (page.pageNum() - 1),
                page.pageNum() < page.totalPages(),
                "/handshaker info all_mods " + (page.pageNum() + 1),
                "/handshaker info all_mods <page>"
            );
        }
        ctx.getSource().sendMessage(sectionFooter(title));
        
        return Command.SINGLE_SUCCESS;
    }

    private static int showModInfo(CommandContext<ServerCommandSource> ctx) {
        String modName = StringArgumentType.getString(ctx, "modName");
        PlayerHistoryDatabase db = HandShakerServer.getInstance().getPlayerHistoryDb();

        InfoCommandOperations.ModInfoResult result =
            InfoCommandOperations.loadModInfo(db, modName, true);
        if (!result.success()) {
            ctx.getSource().sendError(Text.literal(result.error()));
            return 0;
        }

        List<PlayerHistoryDatabase.PlayerModInfo> players = result.players();
        
        if (players.isEmpty()) {
            ctx.getSource().sendMessage(Text.literal("No players found with mod: " + result.displayKey()).formatted(Formatting.YELLOW));
            return Command.SINGLE_SUCCESS;
        }
        
        String title = "Mod: " + result.displayKey();
        ctx.getSource().sendMessage(sectionHeader(title));
        ctx.getSource().sendMessage(Text.literal("Players: " + players.size()).formatted(Formatting.YELLOW));
        ctx.getSource().sendMessage(Text.empty());
        
        List<CommandHelper.ModInfoRow> rows =
            CommandHelper.buildModInfoRows(players, "✓ Active", "✗ Removed");
        for (CommandHelper.ModInfoRow row : rows) {
            Formatting statusColor = row.active() ? Formatting.GREEN : Formatting.RED;
            
            ctx.getSource().sendMessage(Text.literal("- ").formatted(Formatting.GRAY)
                .append(Text.literal(row.playerName()).formatted(Formatting.WHITE)));
            ctx.getSource().sendMessage(Text.literal("  Status: ").formatted(Formatting.DARK_GRAY)
                .append(Text.literal(row.statusLabel()).formatted(statusColor))
                .append(Text.literal(" | Since: ").formatted(Formatting.DARK_GRAY))
                .append(Text.literal(row.firstSeenFormatted()).formatted(Formatting.GRAY)));
        }
        ctx.getSource().sendMessage(sectionFooter(title));
        
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

        CommandHelper.PagedList<PlayerHistoryDatabase.ModHistoryEntry> paged =
            CommandHelper.historyPage(history, pageNum, PAGE_SIZE);
        CommandHelper.Page<PlayerHistoryDatabase.ModHistoryEntry> page = paged.page();
        if (page == null) {
            int totalPages = CommandHelper.totalPages(history.size(), PAGE_SIZE);
            ctx.getSource().sendError(Text.literal("Invalid page. Total pages: " + totalPages));
            return 0;
        }

        String title = "Mod History";
        ctx.getSource().sendMessage(sectionHeader(title));
        ctx.getSource().sendMessage(Text.literal("player: ").formatted(Formatting.GRAY)
            .append(Text.literal(playerName).formatted(Formatting.WHITE))
            .append(Text.literal(" : Page ").formatted(Formatting.GRAY))
            .append(Text.literal(String.valueOf(page.pageNum())).formatted(Formatting.WHITE))
            .append(Text.literal(" / ").formatted(Formatting.GRAY))
            .append(Text.literal(String.valueOf(page.totalPages())).formatted(Formatting.WHITE)));
        ctx.getSource().sendMessage(Text.empty());

        for (int i = page.startIdx(); i < page.endIdx(); i++) {
            PlayerHistoryDatabase.ModHistoryEntry entry = history.get(i);
            String status = CommandHelper.historyStatusLabel(entry.isActive());
            Formatting statusColor = entry.isActive() ? Formatting.GREEN : Formatting.RED;
            String dates = CommandHelper.historyDates(entry);

            ctx.getSource().sendMessage(Text.literal("- ").formatted(Formatting.GRAY)
                .append(Text.literal(entry.modName()).formatted(Formatting.WHITE)));
            ctx.getSource().sendMessage(Text.literal("  Status: ").formatted(Formatting.DARK_GRAY)
                .append(Text.literal(status).formatted(statusColor))
                .append(Text.literal(" | ").formatted(Formatting.DARK_GRAY))
                .append(Text.literal(dates).formatted(Formatting.GRAY)));
        }

        if (page.totalPages() > 1) {
            ctx.getSource().sendMessage(Text.empty());
            sendPaginationNavigation(
                ctx,
                page.pageNum() > 1,
                "/handshaker info player " + playerName + " " + (page.pageNum() - 1),
                page.pageNum() < page.totalPages(),
                "/handshaker info player " + playerName + " " + (page.pageNum() + 1),
                "/handshaker info player <player> <page>"
            );
        }

        ctx.getSource().sendMessage(sectionFooter(title));

        return Command.SINGLE_SUCCESS;
    }

    private static int setMode(CommandContext<ServerCommandSource> ctx) {
        String listName = StringArgumentType.getString(ctx, "list").toLowerCase();
        String action = StringArgumentType.getString(ctx, "action").toLowerCase();
        ConfigManager config = HandShakerServer.getInstance().getConfigManager();

        Boolean enable = CommandHelper.parseEnableFlag(action);
        if (enable == null) {
            ctx.getSource().sendFeedback(() -> Text.literal("✗ Action must be on/off/true/false").formatted(Formatting.RED), true);
            return Command.SINGLE_SUCCESS;
        }

        switch (listName) {
            case "mods_required" -> {
                config.setModsRequiredEnabledState(enable);
                config.save();
                ctx.getSource().sendFeedback(() -> Text.literal("✓ Required Mods turned " + (enable ? "ON" : "OFF")).formatted(Formatting.GREEN), true);
            }
            case "mods_blacklisted" -> {
                config.setModsBlacklistedEnabledState(enable);
                config.save();
                ctx.getSource().sendFeedback(() -> Text.literal("✓ Blacklisted Mods turned " + (enable ? "ON" : "OFF")).formatted(Formatting.GREEN), true);
            }
            case "mods_whitelisted" -> {
                config.setModsWhitelistedEnabledState(enable);
                config.save();
                ctx.getSource().sendFeedback(() -> Text.literal("✓ Whitelisted Mods turned " + (enable ? "ON" : "OFF")).formatted(Formatting.GREEN), true);
            }
            default -> {
                var logger = LoggerAdapter.fromLoaderLogger(HandShakerServer.LOGGER);
                var toggleResult = ModListToggler.toggleListDetailed(config.getConfigDirPath(), listName, enable, logger);
                if (toggleResult.status() == ModListToggler.ToggleStatus.NOT_FOUND) {
                    ctx.getSource().sendFeedback(() -> Text.literal("✗ Unknown list file: " + listName + " (expected <name>.yml in config/HandShaker)").formatted(Formatting.RED), true);
                    return Command.SINGLE_SUCCESS;
                }
                if (toggleResult.status() == ModListToggler.ToggleStatus.UPDATE_FAILED) {
                    ctx.getSource().sendFeedback(() -> Text.literal("✗ Failed to update list file: " + toggleResult.listFile().getFileName()).formatted(Formatting.RED), true);
                    return Command.SINGLE_SUCCESS;
                }
                config.load();
                ctx.getSource().sendFeedback(() -> Text.literal("✓ " + toggleResult.listFile().getFileName() + " enabled=" + (enable ? "true" : "false")).formatted(Formatting.GREEN), true);
            }
        }

        HandShakerServer.getInstance().checkAllPlayers();
        return Command.SINGLE_SUCCESS;
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
                String display = CommandHelper.toDisplayModToken(mod);
                String suggestion = CommandSuggestionOperations.autoQuotedSuggestion(builder.getRemaining(), display);
                if (suggestion != null) {
                    builder.suggest(suggestion);
                }
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestConfiguredMods(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder builder) {
        ConfigManager config = HandShakerServer.getInstance().getConfigManager();
        for (String mod : config.getModConfigMap().keySet()) {
            String suggestion = CommandSuggestionOperations.autoQuotedSuggestion(builder.getRemaining(), mod);
            if (suggestion != null) {
                builder.suggest(suggestion);
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
        for (String mod : allMods.keySet()) {
            String display = CommandHelper.toDisplayModToken(mod);
            String suggestion = CommandSuggestionOperations.autoQuotedSuggestion(builder.getRemaining(), display);
            if (suggestion != null) {
                builder.suggest(suggestion);
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
        if (!(ctx.getSource().getWorld() instanceof ServerWorld world)) {
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
            String display = CommandHelper.toDisplayModToken(mod);
            String suggestion = CommandSuggestionOperations.autoQuotedSuggestion(builder.getRemaining(), display);
            if (suggestion != null) {
                builder.suggest(suggestion);
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
        
        String title = playerName + "'s Mods";
        ctx.getSource().sendMessage(sectionHeader(title));
        
        for (String mod : info.mods()) {
            ConfigState.ModConfig modCfg = HandShakerServer.getInstance().getConfigManager().getModConfig(mod);
            ctx.getSource().sendMessage(modeTag(modCfg != null ? modCfg.getMode() : null)
                .append(Text.literal(" " + mod).formatted(Formatting.WHITE)));
        }

        ctx.getSource().sendMessage(sectionFooter(title));
        
        return Command.SINGLE_SUCCESS;
    }

    private static MutableText sectionHeader(String title) {
        return Text.literal(CommandVisualOperations.sectionHeaderText(title)).formatted(Formatting.GOLD, Formatting.BOLD);
    }

    private static MutableText sectionFooter(String title) {
        return Text.literal(CommandVisualOperations.sectionFooterText(title)).formatted(Formatting.GOLD, Formatting.BOLD);
    }

    private static void sendPaginationNavigation(
        CommandContext<ServerCommandSource> ctx,
        boolean hasPrevious,
        String previousCommand,
        boolean hasNext,
        String nextCommand,
        String fallbackUsage
    ) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayerEntity)) {
            ctx.getSource().sendMessage(Text.literal(CommandVisualOperations.navigateUsageText(fallbackUsage)).formatted(Formatting.GRAY));
            return;
        }

        MutableText prev = hasPrevious
            ? Text.literal(CommandVisualOperations.previousPageLabel()).setStyle(
                Style.EMPTY
                    .withColor(Formatting.GREEN)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, previousCommand))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(previousCommand)))
            )
            : Text.literal(CommandVisualOperations.previousPageLabel()).formatted(Formatting.DARK_GRAY);

        MutableText next = hasNext
            ? Text.literal(CommandVisualOperations.nextPageLabel()).setStyle(
                Style.EMPTY
                    .withColor(Formatting.GREEN)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, nextCommand))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(nextCommand)))
            )
            : Text.literal(CommandVisualOperations.nextPageLabel()).formatted(Formatting.DARK_GRAY);

        ctx.getSource().sendMessage(prev.append(Text.literal(" ")).append(next));
    }

    private static MutableText modeTag(String mode) {
        String label = CommandHelper.modeTagLabel(mode);
        if (mode == null) {
            return Text.literal(label).formatted(Formatting.DARK_GRAY);
        }

        Formatting color = switch (mode) {
            case "required" -> Formatting.GOLD;
            case "blacklisted" -> Formatting.RED;
            case "allowed" -> Formatting.GREEN;
            default -> Formatting.DARK_GRAY;
        };

        return Text.literal(label).formatted(color);
    }

    private static String prettyModName(String modId) {
        if (modId == null || modId.isBlank()) {
            return "unknown";
        }
        String normalized = modId.replace('_', ' ').replace('-', ' ');
        String[] words = normalized.split("\\s+");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (result.length() > 0) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                result.append(word.substring(1));
            }
        }
        return result.toString();
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
        
        if (!CommandHelper.isValidMode(mode)) {
            ctx.getSource().sendError(Text.literal("Invalid mode. Use: required, blacklisted, or allowed"));
            return 0;
        }
        
        ConfigManager config = HandShakerServer.getInstance().getConfigManager();
        
        if (modId.equals("*")) {
            // Set all mods for this player
            int updated = 0;
            for (String mod : info.mods()) {
                if (!config.isIgnored(mod)) {
                    config.setModConfig(mod, mode, CommandHelper.defaultActionForMode(mode), null);
                    registerModFingerprint(config, mod);
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
            config.setModConfig(modId, mode, CommandHelper.defaultActionForMode(mode), null);
            registerModFingerprint(config, modId);
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

        String title = "Ignored Mods";
        ctx.getSource().sendMessage(sectionHeader(title));
        
        for (String mod : ignored) {
            ctx.getSource().sendMessage(Text.literal("  • " + mod).formatted(Formatting.GRAY));
        }

        ctx.getSource().sendMessage(sectionFooter(title));
        
        return Command.SINGLE_SUCCESS;
    }

    private static void registerModFingerprint(ConfigManager config, String modToken) {
        ModFingerprintRegistrar.registerFromCommand(
            modToken,
            HandShakerServer.getInstance().getPlayerHistoryDb(),
            config.isHashMods(),
            config.isModVersioning(),
            HandShakerServer.getInstance().getClients().values()
        );
    }

    private static CompletableFuture<Suggestions> suggestIgnoredMods(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder builder) {
        ConfigManager config = HandShakerServer.getInstance().getConfigManager();
        for (String mod : config.getIgnoredMods()) {
            String suggestion = CommandSuggestionOperations.autoQuotedSuggestion(builder.getRemaining(), mod);
            if (suggestion != null) {
                builder.suggest(suggestion);
            }
        }
        return builder.buildFuture();
    }

}
