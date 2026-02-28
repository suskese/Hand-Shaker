package me.mklv.handshaker.fabric.server;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import me.mklv.handshaker.fabric.server.configs.ConfigManager;
import me.mklv.handshaker.fabric.server.utils.PermissionsAdapter;
import me.mklv.handshaker.common.commands.ConfigCommandOperations;
import me.mklv.handshaker.common.commands.InfoCommandOperations;
import me.mklv.handshaker.common.commands.CommandHelper;
import me.mklv.handshaker.common.commands.CommandSuggestionOperations;
import me.mklv.handshaker.common.commands.CommandVisualOperations;
import me.mklv.handshaker.common.commands.ModFingerprintRegistrar;
import me.mklv.handshaker.common.commands.ModManagementOperations;
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
                        .then(literal("page")
                            .then(argument("page", IntegerArgumentType.integer(1))
                                .executes(HandShakerCommand::showPlayerModsWithPageArg)))
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
        ctx.getSource().sendMessage(Text.literal("HandShaker v6 Commands").formatted(Formatting.GOLD, Formatting.BOLD));
        ctx.getSource().sendMessage(Text.empty());
        
        // Loop through help sections and render with Fabric Text API
        for (CommandHelper.HelpSection section : CommandHelper.getHelpSections()) {
            ctx.getSource().sendMessage(Text.literal(section.title()).formatted(Formatting.YELLOW, Formatting.BOLD));
            for (CommandHelper.HelpEntry entry : section.entries()) {
                ctx.getSource().sendMessage(Text.literal(entry.command()).formatted(Formatting.YELLOW)
                    .append(Text.literal(" - " + entry.description()).formatted(Formatting.GRAY)));
            }
            ctx.getSource().sendMessage(Text.empty());
        }
        
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
        @SuppressWarnings("null")
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
        Set<String> currentPlayerMods = null;
        if (ctx.getSource().getEntity() instanceof ServerPlayerEntity player) {
            ClientInfo info = HandShakerServer.getInstance().getClients().get(player.getUuid());
            if (info != null) {
                currentPlayerMods = info.mods();
            }
        }

        ConfigCommandOperations.MutationResult result =
            ConfigCommandOperations.applyConfigValue(config, param, value, currentPlayerMods);

        if (!result.success()) {
            ctx.getSource().sendError(Text.literal(result.message()));
            return 0;
        }

        if (result.shouldSave()) {
            config.save();
        }
        if (result.shouldReloadConfig()) {
            config.load();
        }
        if (result.shouldRecheckPlayers()) {
            HandShakerServer.getInstance().checkAllPlayers();
        }

        ctx.getSource().sendFeedback(() -> Text.literal("✓ " + result.message()).formatted(Formatting.GREEN), true);
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
            
            int added = ModManagementOperations.applyToMods(info.mods(), config::isIgnored, mod -> {
                config.setModConfig(mod, mode, CommandHelper.defaultActionForMode(mode), null);
                registerModFingerprint(config, mod);
            });
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
            
            int added = ModManagementOperations.applyToMods(info.mods(), config::isIgnored, mod -> {
                config.setModConfig(mod, mode, action, null);
                registerModFingerprint(config, mod);
            });
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
            ctx.getSource().sendMessage(Text.literal("No mods configured").formatted(Formatting.YELLOW));
            ctx.getSource().sendMessage(sectionFooter(title));
            return Command.SINGLE_SUCCESS;
        }
        
        InfoCommandOperations.ConfiguredModsPageResult paged =
            InfoCommandOperations.loadConfiguredModsPage(mods, pageNum, PAGE_SIZE);
        if (paged.hasInvalidPage()) {
            ctx.getSource().sendError(Text.literal("Invalid page. Total pages: " + paged.totalPages()));
            return 0;
        }

        List<Map.Entry<String, ConfigState.ModConfig>> modList = paged.items();
        CommandHelper.Page<Map.Entry<String, ConfigState.ModConfig>> page = paged.page();

        ctx.getSource().sendMessage(Text.literal("page: ").formatted(Formatting.GRAY)
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
                        .withClickEvent(new ClickEvent.SuggestCommand(changeCommand))
                        .withHoverEvent(new HoverEvent.ShowText(Text.literal(hoverText)))
                );

                MutableText removeBtn = Text.literal("[✕]").setStyle(
                    Style.EMPTY
                        .withColor(Formatting.RED)
                        .withClickEvent(new ClickEvent.RunCommand(removeCommand))
                        .withHoverEvent(new HoverEvent.ShowText(Text.literal(CommandVisualOperations.removeHoverText())))
                );

                ctx.getSource().sendMessage(Text.literal("- ").formatted(Formatting.GRAY)
                    .append(modText)
                    .append(Text.literal(" ").formatted(Formatting.GRAY))
                    .append(modeTag(modCfg != null ? modCfg.getMode() : null))
                    .append(Text.literal(" | Action: ").formatted(Formatting.DARK_GRAY))
                    .append(Text.literal(actionLabel).formatted(Formatting.GRAY))
                    .append(Text.literal(" | ").formatted(Formatting.DARK_GRAY))
                    .append(removeBtn));
            } else {
                ctx.getSource().sendMessage(Text.literal("- ").formatted(Formatting.GRAY)
                    .append(Text.literal(entry.getKey()).formatted(Formatting.WHITE))
                    .append(Text.literal(" ").formatted(Formatting.GRAY))
                    .append(modeTag(modCfg != null ? modCfg.getMode() : null))
                    .append(Text.literal(" | Action: ").formatted(Formatting.DARK_GRAY))
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
        
        InfoCommandOperations.PopularityPageResult paged =
            InfoCommandOperations.loadPopularityPage(db.getModPopularity(), pageNum, PAGE_SIZE);
        if (paged.hasInvalidPage()) {
            ctx.getSource().sendError(Text.literal("Invalid page. Total pages: " + paged.totalPages()));
            return 0;
        }

        List<Map.Entry<String, Integer>> sortedMods = paged.items();
        CommandHelper.Page<Map.Entry<String, Integer>> page = paged.page();

        String title = "All Mods (Page " + page.pageNum() + "/" + page.totalPages() + ")";
        ctx.getSource().sendMessage(sectionHeader(title));
        
        for (int i = page.startIdx(); i < page.endIdx(); i++) {
            Map.Entry<String, Integer> entry = sortedMods.get(i);
            ConfigState.ModConfig modCfg = config.getModConfig(entry.getKey());
            String modToken = entry.getKey();
            ModEntry parsed = ModEntry.parse(modToken);
            String modId = parsed != null ? parsed.modId() : modToken;
            String version = parsed != null && parsed.version() != null ? parsed.version() : "unknown";
            String displayName = parsed != null && parsed.displayName() != null ? parsed.displayName() : prettyModName(modId);

            if (ctx.getSource().getEntity() instanceof ServerPlayerEntity) {
                String modInfoCommand = CommandVisualOperations.infoModCommand("/handshaker", modToken);
                MutableText modText = Text.literal(displayName).setStyle(
                    Style.EMPTY
                        .withColor(Formatting.WHITE)
                        .withClickEvent(new ClickEvent.RunCommand(modInfoCommand))
                        .withHoverEvent(new HoverEvent.ShowText(Text.literal(CommandVisualOperations.showPlayersUsingModHoverText())))
                );

                MutableText line = modeTag(modCfg != null ? modCfg.getMode() : null)
                    .append(Text.literal(" ").formatted(Formatting.GRAY))
                    .append(modText)
                    .append(Text.literal(" x" + entry.getValue()).formatted(Formatting.GRAY));
                ctx.getSource().sendMessage(line);
                ctx.getSource().sendMessage(Text.literal("  ID: ").formatted(Formatting.DARK_GRAY)
                    .append(Text.literal(modId).formatted(Formatting.GRAY))
                    .append(Text.literal(" | Version: ").formatted(Formatting.DARK_GRAY))
                    .append(Text.literal(version).formatted(Formatting.GRAY)));
            } else {
                MutableText line = modeTag(modCfg != null ? modCfg.getMode() : null)
                    .append(Text.literal(" ").formatted(Formatting.GRAY))
                    .append(Text.literal(displayName).formatted(Formatting.WHITE))
                    .append(Text.literal(" x" + entry.getValue()).formatted(Formatting.GRAY));
                ctx.getSource().sendMessage(line);
                ctx.getSource().sendMessage(Text.literal("  ID: ").formatted(Formatting.DARK_GRAY)
                    .append(Text.literal(modId).formatted(Formatting.GRAY))
                    .append(Text.literal(" | Version: ").formatted(Formatting.DARK_GRAY))
                    .append(Text.literal(version).formatted(Formatting.GRAY)));
            }
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
            
            ctx.getSource().sendMessage(Text.literal(row.playerName()).formatted(Formatting.AQUA)
                .append(Text.literal(" - " + row.statusLabel()).formatted(statusColor))
                .append(Text.literal(" (Since: " + row.firstSeenFormatted() + ")").formatted(Formatting.GRAY)));
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
        ServerPlayerEntity online = ctx.getSource().getServer().getPlayerManager().getPlayer(playerName);
        UUID onlineUuid = online != null ? online.getUuid() : null;

        InfoCommandOperations.PlayerHistoryResult result =
            InfoCommandOperations.loadPlayerHistory(db, playerName, onlineUuid, pageNum, PAGE_SIZE);
        if (!result.success()) {
            ctx.getSource().sendError(Text.literal(result.error()));
            return 0;
        }

        List<PlayerHistoryDatabase.ModHistoryEntry> history = result.history();
        if (history.isEmpty()) {
            ctx.getSource().sendMessage(Text.literal("No mod history found for: " + playerName).formatted(Formatting.YELLOW));
            return Command.SINGLE_SUCCESS;
        }
        CommandHelper.Page<PlayerHistoryDatabase.ModHistoryEntry> page = result.page();

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

        ConfigCommandOperations.MutationResult result = ConfigCommandOperations.applyModeToggle(
            config,
            config.getConfigDirPath(),
            LoggerAdapter.fromLoaderLogger(HandShakerServer.LOGGER),
            listName,
            action
        );

        if (!result.success()) {
            ctx.getSource().sendFeedback(() -> Text.literal("✗ " + result.message()).formatted(Formatting.RED), true);
            return Command.SINGLE_SUCCESS;
        }

        if (result.shouldSave()) {
            config.save();
        }
        if (result.shouldReloadConfig()) {
            config.load();
        }
        if (result.shouldRecheckPlayers()) {
            HandShakerServer.getInstance().checkAllPlayers();
        }

        ctx.getSource().sendFeedback(() -> Text.literal("✓ " + result.message()).formatted(Formatting.GREEN), true);
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
        for (String mode : CommandSuggestionOperations.modeSuggestions(builder.getRemaining())) {
            builder.suggest(mode);
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestActions(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder builder) {
        ConfigManager config = HandShakerServer.getInstance().getConfigManager();
        Set<String> availableActions = config.getAvailableActions();

        List<String> actions = CommandSuggestionOperations.actionSuggestions(availableActions, false, true);
        for (String action : CommandSuggestionOperations.filterByPrefix(actions, builder.getRemaining())) {
            builder.suggest(action);
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
        for (String list : CommandSuggestionOperations.modeListSuggestions(builder.getRemaining())) {
            builder.suggest(list);
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
        List<String> names = new ArrayList<>();
        for (ServerPlayerEntity player : world.getPlayers()) {
            names.add(player.getName().getString());
        }
        for (String name : CommandSuggestionOperations.filterByPrefix(names, builder.getRemaining())) {
            builder.suggest(name);
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
        return showPlayerMods(ctx, 1);
    }

    private static int showPlayerModsWithPageArg(CommandContext<ServerCommandSource> ctx) {
        int page = IntegerArgumentType.getInteger(ctx, "page");
        return showPlayerMods(ctx, page);
    }

    private static int showPlayerMods(CommandContext<ServerCommandSource> ctx, int pageNum) {
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

        @SuppressWarnings("null")
        List<ModEntry> parsedMods = info.mods().stream()
            .map(ModEntry::parse)
            .filter(Objects::nonNull)
            .sorted(Comparator.comparing(ModEntry::modId))
            .toList();

        if (parsedMods.isEmpty()) {
            ctx.getSource().sendMessage(Text.literal("No visible mods found for " + playerName).formatted(Formatting.YELLOW));
            return Command.SINGLE_SUCCESS;
        }

        int totalMods = parsedMods.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalMods / PAGE_SIZE));
        if (pageNum < 1 || pageNum > totalPages) {
            ctx.getSource().sendError(Text.literal("Invalid page. Total pages: " + totalPages));
            return 0;
        }

        int start = (pageNum - 1) * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, totalMods);

        String title = totalMods + " Mods";
        ctx.getSource().sendMessage(sectionHeader(title));
        ctx.getSource().sendMessage(Text.literal("player: ").formatted(Formatting.GRAY)
            .append(Text.literal(playerName).formatted(Formatting.WHITE))
            .append(Text.literal(" : Page ").formatted(Formatting.GRAY))
            .append(Text.literal(String.valueOf(pageNum)).formatted(Formatting.WHITE))
            .append(Text.literal(" / ").formatted(Formatting.GRAY))
            .append(Text.literal(String.valueOf(totalPages)).formatted(Formatting.WHITE)));
        ctx.getSource().sendMessage(Text.empty());

        for (int i = start; i < end; i++) {
            ModEntry entry = parsedMods.get(i);
            String modId = entry.modId();
            String version = entry.version() != null ? entry.version() : "unknown";
            String displayName = entry.displayName() != null ? entry.displayName() : prettyModName(modId);

            ctx.getSource().sendMessage(Text.literal("- ").formatted(Formatting.GRAY)
                .append(Text.literal(displayName).formatted(Formatting.WHITE)));
            ctx.getSource().sendMessage(Text.literal("  ID: ").formatted(Formatting.DARK_GRAY)
                .append(Text.literal(modId).formatted(Formatting.GRAY))
                .append(Text.literal(" | Version: ").formatted(Formatting.DARK_GRAY))
                .append(Text.literal(version).formatted(Formatting.GRAY)));
        }

        if (totalPages > 1) {
            ctx.getSource().sendMessage(Text.empty());
            sendPaginationNavigation(
                ctx,
                pageNum > 1,
                "/handshaker manage player " + playerName + " page " + (pageNum - 1),
                pageNum < totalPages,
                "/handshaker manage player " + playerName + " page " + (pageNum + 1),
                "/handshaker manage player <player> page <number>"
            );
        }
        ctx.getSource().sendMessage(sectionFooter(title));
        
        return Command.SINGLE_SUCCESS;
    }

    private static MutableText sectionHeader(String title) {
        return Text.literal(sectionHeaderText(title)).formatted(Formatting.GOLD, Formatting.BOLD);
    }

    private static MutableText sectionFooter(String title) {
        return Text.literal(CommandVisualOperations.sectionFooterText(title)).formatted(Formatting.GOLD, Formatting.BOLD);
    }

    private static String sectionHeaderText(String title) {
        return CommandVisualOperations.sectionHeaderText(title);
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
            ctx.getSource().sendMessage(Text.literal("Use " + fallbackUsage + " to navigate").formatted(Formatting.GRAY));
            return;
        }

        MutableText prev = hasPrevious
            ? Text.literal("[ < Previous page ]").setStyle(
                Style.EMPTY
                    .withColor(Formatting.GREEN)
                    .withClickEvent(new ClickEvent.RunCommand(previousCommand))
                    .withHoverEvent(new HoverEvent.ShowText(Text.literal(previousCommand)))
            )
            : Text.literal("[ < Previous page ]").formatted(Formatting.DARK_GRAY);

        MutableText next = hasNext
            ? Text.literal("[ Next page > ]").setStyle(
                Style.EMPTY
                    .withColor(Formatting.GREEN)
                    .withClickEvent(new ClickEvent.RunCommand(nextCommand))
                    .withHoverEvent(new HoverEvent.ShowText(Text.literal(nextCommand)))
            )
            : Text.literal("[ Next page > ]").formatted(Formatting.DARK_GRAY);

        ctx.getSource().sendMessage(prev.append(Text.literal(" ")).append(next));
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
            
            int added = ModManagementOperations.applyToMods(info.mods(), config::isIgnored, config::addIgnoredMod);
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
        
        ctx.getSource().sendMessage(Text.literal("Ignored Mods").formatted(Formatting.GOLD, Formatting.BOLD));
        ctx.getSource().sendMessage(Text.empty());
        
        for (String mod : ignored) {
            ctx.getSource().sendMessage(Text.literal("  • " + mod).formatted(Formatting.GRAY));
        }
        
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
