package me.mklv.handshaker.fabric.server;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import me.mklv.handlib.fabric.PermissionsAdapter;
import me.mklv.handlib.text.TextEventCompat;
import me.mklv.handshaker.common.commands.ConfigCommandOperations;
import me.mklv.handshaker.common.commands.InfoCommandOperations;
import me.mklv.handshaker.common.commands.CommandDataOperations;
import me.mklv.handshaker.common.commands.CommandHelper;
import me.mklv.handshaker.common.commands.CommandSuggestionOperations;
import me.mklv.handshaker.common.commands.CommandVisualOperations;
import me.mklv.handshaker.common.commands.CommandRuntimeOperations;
import me.mklv.handshaker.common.commands.DiagnosticCommand;
import me.mklv.handshaker.common.commands.IgnoreCommandOperations;
import me.mklv.handshaker.common.commands.ModRuleCommandOperations;
import me.mklv.handshaker.common.database.PlayerHistoryDatabase;
import me.mklv.handshaker.common.configs.ConfigTypes.ConfigState;
import me.mklv.handshaker.common.configs.ConfigTypes.ModEntry;
import me.mklv.handshaker.common.utils.ClientInfo;
import me.mklv.handshaker.common.utils.LoggerAdapter;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.ChatFormatting;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class HandShakerCommand {
    
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var handshaker = Commands.literal("handshaker")
            .requires(source -> PermissionsAdapter.checkPermission(source, "handshaker.admin", 4))
            .executes(HandShakerCommand::showHelp)
            // Core Commands
            .then(Commands.literal("reload")
                .executes(HandShakerCommand::reload))
            .then(Commands.literal("info")
                .then(Commands.literal("configured_mods")
                    .executes(HandShakerCommand::showConfiguredMods)
                    .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(HandShakerCommand::showConfiguredModsWithPage)))
                .then(Commands.literal("all_mods")
                    .executes(HandShakerCommand::showAllMods)
                    .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(HandShakerCommand::showAllModsWithPage)))
                .then(Commands.literal("player")
                    .then(Commands.argument("playerName", StringArgumentType.word())
                        .suggests(HandShakerCommand::suggestPlayers)
                        .executes(HandShakerCommand::showPlayerHistory)
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                            .executes(HandShakerCommand::showPlayerHistoryWithPage))))
                .then(Commands.literal("mod")
                    .then(Commands.argument("modName", StringArgumentType.string())
                        .suggests(HandShakerCommand::suggestAllMods)
                        .executes(HandShakerCommand::showModInfo)))
                .then(Commands.literal("diagnostic")
                    .executes(HandShakerCommand::showDiagnostic)
                    .then(Commands.literal("export")
                        .executes(HandShakerCommand::exportDiagnosticReport)))
                .then(Commands.literal("export")
                    .executes(HandShakerCommand::exportSnapshot)))
            .then(Commands.literal("config")
                .executes(HandShakerCommand::showConfig)
                .then(Commands.argument("param", StringArgumentType.word())
                    .suggests(HandShakerCommand::suggestConfigParams)
                    .then(Commands.argument("value", StringArgumentType.word())
                        .suggests(HandShakerCommand::suggestConfigValues)
                        .executes(HandShakerCommand::setConfigValueDynamic))))
            .then(Commands.literal("mode")
                .then(Commands.argument("list", StringArgumentType.word())
                    .suggests(HandShakerCommand::suggestModeLists)
                    .then(Commands.argument("action", StringArgumentType.word())
                        .suggests(HandShakerCommand::suggestCurrentModeState)
                        .executes(HandShakerCommand::setMode))))
            // Manage subcommands
            .then(Commands.literal("manage")
                .then(Commands.literal("add")
                    .then(Commands.literal("*")
                        .then(Commands.argument("mode", StringArgumentType.word())
                            .suggests(HandShakerCommand::suggestModes)
                            .executes(HandShakerCommand::addMod)
                            .then(Commands.argument("action", StringArgumentType.word())
                                .suggests(HandShakerCommand::suggestActions)
                                .executes(HandShakerCommand::addModWithAction))))
                    .then(Commands.argument("mod", StringArgumentType.string())
                        .suggests(HandShakerCommand::suggestMods)
                        .then(Commands.argument("mode", StringArgumentType.word())
                            .suggests(HandShakerCommand::suggestModes)
                            .executes(HandShakerCommand::addMod)
                            .then(Commands.argument("action", StringArgumentType.word())
                                .suggests(HandShakerCommand::suggestActions)
                                .executes(HandShakerCommand::addModWithAction)))))
                .then(Commands.literal("change")
                    .then(Commands.argument("mod", StringArgumentType.string())
                        .suggests(HandShakerCommand::suggestConfiguredMods)
                        .then(Commands.argument("mode", StringArgumentType.word())
                            .suggests(HandShakerCommand::suggestModes)
                            .executes(HandShakerCommand::changeMod)
                            .then(Commands.argument("action", StringArgumentType.word())
                                .suggests(HandShakerCommand::suggestActions)
                                .executes(HandShakerCommand::changeModWithAction)))))
                .then(Commands.literal("remove")
                    .then(Commands.argument("mod", StringArgumentType.string())
                        .suggests(HandShakerCommand::suggestConfiguredMods)
                        .executes(HandShakerCommand::removeMod)))
                .then(Commands.literal("ignore")
                    .then(Commands.literal("add")
                        .then(Commands.literal("*")
                            .executes(HandShakerCommand::addIgnore))
                        .then(Commands.argument("mod", StringArgumentType.string())
                            .suggests(HandShakerCommand::suggestMods)
                            .executes(HandShakerCommand::addIgnore)))
                    .then(Commands.literal("remove")
                        .then(Commands.argument("mod", StringArgumentType.string())
                            .suggests(HandShakerCommand::suggestIgnoredMods)
                            .executes(HandShakerCommand::removeIgnore)))
                    .then(Commands.literal("list")
                        .executes(HandShakerCommand::listIgnore)))
                .then(Commands.literal("player")
                    .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(HandShakerCommand::suggestPlayers)
                        .executes(HandShakerCommand::showPlayerMods)
                        .then(Commands.literal("page")
                            .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(HandShakerCommand::showPlayerModsWithPageArg)))
                        .then(Commands.literal("*")
                            .then(Commands.argument("mode", StringArgumentType.word())
                                .suggests(HandShakerCommand::suggestModes)
                                .executes(HandShakerCommand::setPlayerModStatus)))
                        .then(Commands.argument("mod", StringArgumentType.string())
                            .suggests(HandShakerCommand::suggestPlayerMods)
                            .then(Commands.argument("mode", StringArgumentType.word())
                                .suggests(HandShakerCommand::suggestModes)
                                .executes(HandShakerCommand::setPlayerModStatus))))));
        
        dispatcher.register(handshaker);
    }

    private static int showHelp(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSystemMessage(Component.literal("HandShaker v7 Commands").withColor(0xFFAA00).withStyle(ChatFormatting.BOLD));
         ctx.getSource().sendSystemMessage(Component.empty());
         
        // Loop through help sections and render with Fabric Text API
        for (CommandHelper.HelpSection section : CommandHelper.getHelpSections()) {
            ctx.getSource().sendSystemMessage(Component.literal(section.title()).withColor(0xFFFF55).withStyle(ChatFormatting.BOLD));
            for (CommandHelper.HelpEntry entry : section.entries()) {
                ctx.getSource().sendSystemMessage(Component.literal(entry.command()).withColor(0xFFFF55)
                    .append(Component.literal(" - " + entry.description()).withColor(0xAAAAAA)));
            }
            ctx.getSource().sendSystemMessage(Component.empty());
        }
        
        return Command.SINGLE_SUCCESS;
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        ConfigManager config = HandShakerServer.getInstance().getConfigManager();
        config.load();
        Component message = Component.literal("✓ HandShaker config reloaded").withColor(0x54FF54);
        ctx.getSource().sendSuccess(() -> message, true);
        HandShakerServer.getInstance().checkAllPlayers();
        return Command.SINGLE_SUCCESS;
    }

    private static int showConfig(CommandContext<CommandSourceStack> ctx) {
        ConfigManager config = HandShakerServer.getInstance().getConfigManager();
        String title = "HandShaker Configuration";
        ctx.getSource().sendSystemMessage(sectionHeader(title));
        
        String requiredModpackHashes = config.getRequiredModpackHashes().isEmpty() ? "OFF" : String.join(", ", config.getRequiredModpackHashes());
        java.util.List<CommandHelper.InfoField> configFields = java.util.List.of(
            new CommandHelper.InfoField("Behavior", config.getBehavior().toString()),
            new CommandHelper.InfoField("Integrity Mode", config.getIntegrityMode().toString()),
            new CommandHelper.InfoField("Whitelist Mode", config.isWhitelist() ? "ON" : "OFF"),
            new CommandHelper.InfoField("Bedrock Players", config.isAllowBedrockPlayers() ? "Allowed" : "Blocked"),
            new CommandHelper.InfoField("Player Database", config.isPlayerdbEnabled() ? "Enabled" : "Disabled"),
            new CommandHelper.InfoField("Required Modpack Hashes", requiredModpackHashes)
        );
        
        for (CommandHelper.InfoField field : configFields) {
            ctx.getSource().sendSystemMessage(Component.literal(field.label()).withColor(0xFFFF55)
                .append(Component.literal(": " + field.value()).withColor(0xFFFFFF)));
        }
        ctx.getSource().sendSystemMessage(sectionFooter(title));
        
        return Command.SINGLE_SUCCESS;
    }

    private static int setConfigValueDynamic(CommandContext<CommandSourceStack> ctx) {
        return setConfigValue(ctx, StringArgumentType.getString(ctx, "param"));
    }

    private static int setConfigValue(CommandContext<CommandSourceStack> ctx, String param) {
        String value = StringArgumentType.getString(ctx, "value");
        ConfigManager config = HandShakerServer.getInstance().getConfigManager();
        Set<String> currentPlayerMods = null;
        if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
            ClientInfo info = HandShakerServer.getInstance().getClients().get(player.getUUID());
            if (info != null) {
                currentPlayerMods = info.mods();
            }
        }

        ConfigCommandOperations.MutationResult result =
            ConfigCommandOperations.applyConfigValue(config, param, value, currentPlayerMods);

        if (!result.success()) {
            ctx.getSource().sendFailure(Component.literal(result.message()));
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

        ctx.getSource().sendSuccess(() -> Component.literal("✓ " + result.message()).withColor(0x54FF54), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int addMod(CommandContext<CommandSourceStack> ctx) {
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
            ctx.getSource().sendFailure(Component.literal("Invalid mode. Use: required, blacklisted, or allowed"));
            return 0;
        }
        
        if (modId.equals("*")) {
            ServerPlayer player = ctx.getSource().getEntity() instanceof ServerPlayer ? (ServerPlayer) ctx.getSource().getEntity() : null;
            if (player == null) {
                ctx.getSource().sendFailure(Component.literal("Only players can use * wildcard"));
                return 0;
            }
            
            ClientInfo info = HandShakerServer.getInstance().getClients().get(player.getUUID());
            if (info == null || info.mods().isEmpty()) {
                ctx.getSource().sendFailure(Component.literal("No mods found on your client"));
                return 0;
            }
            
            int added = ModRuleCommandOperations.upsertBulkModRules(
                info.mods(),
                config::isIgnored,
                mode,
                null,
                config::getDefaultActionForMode,
                config::setModConfig,
                mod -> registerModFingerprint(config, mod)
            );
            final int finalAdded = added;
            final String finalMode = mode;
            ctx.getSource().sendSuccess(() -> Component.literal("✓ Added " + finalAdded + " of your mods as " + finalMode).withColor(0x54FF54), true);
        } else {
            final String finalModId = modId;
            final String finalMode = mode;
            ModRuleCommandOperations.upsertModRule(
                modId,
                mode,
                null,
                config::getDefaultActionForMode,
                config::setModConfig,
                mod -> registerModFingerprint(config, mod)
            );
            ctx.getSource().sendSuccess(() -> Component.literal("✓ Added " + finalModId + " as " + finalMode).withColor(0x54FF54), true);
        }
        
        HandShakerServer.getInstance().checkAllPlayers();
        return Command.SINGLE_SUCCESS;
    }

    private static int addModWithAction(CommandContext<CommandSourceStack> ctx) {
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
            ctx.getSource().sendFailure(Component.literal("Invalid mode. Use: required, blacklisted, or allowed"));
            return 0;
        }
        
        if (!isValidAction(action)) {
            Set<String> availableActions = config.getAvailableActions();
            String actionList = availableActions.isEmpty() ? "kick, ban" : String.join(", ", availableActions);
            ctx.getSource().sendFailure(Component.literal("Invalid action. Available: " + actionList));
            return 0;
        }
        
        if (modId.equals("*")) {
            ServerPlayer player = ctx.getSource().getEntity() instanceof ServerPlayer ? (ServerPlayer) ctx.getSource().getEntity() : null;
            if (player == null) {
                ctx.getSource().sendFailure(Component.literal("Only players can use * wildcard"));
                return 0;
            }
            
            ClientInfo info = HandShakerServer.getInstance().getClients().get(player.getUUID());
            if (info == null || info.mods().isEmpty()) {
                ctx.getSource().sendFailure(Component.literal("No mods found on your client"));
                return 0;
            }
            
            int added = ModRuleCommandOperations.upsertBulkModRules(
                info.mods(),
                config::isIgnored,
                mode,
                action,
                config::getDefaultActionForMode,
                config::setModConfig,
                mod -> registerModFingerprint(config, mod)
            );
            final int finalAdded = added;
            final String finalMode = mode;
            final String finalAction = action;
            ctx.getSource().sendSuccess(() -> Component.literal("✓ Added " + finalAdded + " of your mods as " + finalMode + " with " + finalAction).withColor(0x54FF54), true);
        } else {
            final String finalModId = modId;
            final String finalMode = mode;
            final String finalAction = action;
            ModRuleCommandOperations.upsertModRule(
                modId,
                mode,
                action,
                config::getDefaultActionForMode,
                config::setModConfig,
                mod -> registerModFingerprint(config, mod)
            );
            ctx.getSource().sendSuccess(() -> Component.literal("✓ Added " + finalModId + " as " + finalMode + " with " + finalAction).withColor(0x54FF54), true);
        }
        
        HandShakerServer.getInstance().checkAllPlayers();
        return Command.SINGLE_SUCCESS;
    }

    private static int changeMod(CommandContext<CommandSourceStack> ctx) {
        String modId = StringArgumentType.getString(ctx, "mod").toLowerCase();
        String mode = StringArgumentType.getString(ctx, "mode").toLowerCase();
        ConfigManager config = HandShakerServer.getInstance().getConfigManager();
        
        if (!CommandHelper.isValidMode(mode)) {
            ctx.getSource().sendFailure(Component.literal("Invalid mode. Use: required, blacklisted, or allowed"));
            return 0;
        }
        
        ModRuleCommandOperations.changeModRulePreserveAction(
            modId,
            mode,
            config.getModConfig(modId),
            config::getDefaultActionForMode,
            config::setModConfig
        );
        ctx.getSource().sendSuccess(() -> Component.literal("✓ Changed " + modId + " to " + mode).withColor(0x54FF54), true);
        HandShakerServer.getInstance().checkAllPlayers();
        return Command.SINGLE_SUCCESS;
    }

    private static int changeModWithAction(CommandContext<CommandSourceStack> ctx) {
        String modId = StringArgumentType.getString(ctx, "mod").toLowerCase();
        String mode = StringArgumentType.getString(ctx, "mode").toLowerCase();
        String action = StringArgumentType.getString(ctx, "action").toLowerCase();
        ConfigManager config = HandShakerServer.getInstance().getConfigManager();
        
        if (!CommandHelper.isValidMode(mode)) {
            ctx.getSource().sendFailure(Component.literal("Invalid mode. Use: required, blacklisted, or allowed"));
            return 0;
        }
        
        if (!isValidAction(action)) {
            ctx.getSource().sendFailure(Component.literal("Invalid action. Use: kick or ban"));
            return 0;
        }
        
        ModRuleCommandOperations.upsertModRule(
            modId,
            mode,
            action,
            config::getDefaultActionForMode,
            config::setModConfig,
            null
        );
        ctx.getSource().sendSuccess(() -> Component.literal("✓ Changed " + modId + " to " + mode + " with " + action).withColor(0x54FF54), true);
        HandShakerServer.getInstance().checkAllPlayers();
        return Command.SINGLE_SUCCESS;
    }

    private static int removeMod(CommandContext<CommandSourceStack> ctx) {
        String modId = StringArgumentType.getString(ctx, "mod").toLowerCase();
        ConfigManager config = HandShakerServer.getInstance().getConfigManager();
        
        boolean removed = ModRuleCommandOperations.removeModRule(modId, config::removeModConfig);
        if (removed) {
            ctx.getSource().sendSuccess(() -> Component.literal("✓ Removed " + modId).withColor(0x54FF54), true);
            HandShakerServer.getInstance().checkAllPlayers();
        } else {
            ctx.getSource().sendFailure(Component.literal("Mod not found: " + modId));
        }
        
        return removed ? Command.SINGLE_SUCCESS : 0;
    }
    
    private static int showDiagnostic(CommandContext<CommandSourceStack> ctx) {
        ConfigManager config = HandShakerServer.getInstance().getConfigManager();
        if (!config.isDiagnosticCommandEnabled()) {
            ctx.getSource().sendFailure(Component.literal("Diagnostic command is disabled in config"));
            return 0;
        }

        PlayerHistoryDatabase db = HandShakerServer.getInstance().getPlayerHistoryDb();
        ctx.getSource().sendSystemMessage(Component.literal("=== HandShaker Diagnostic ===").withColor(0xFFAA00));
        for (String line : DiagnosticCommand.buildDisplayLines(config, db)) {
            int color = "Database disabled".equals(line) ? 0xFFFF55 : 0xAAAAAA;
            ctx.getSource().sendSystemMessage(Component.literal(line).withColor(color));
        }
        ctx.getSource().sendSystemMessage(Component.literal("[ Save diagnostic to exports ]")
            .withColor(0x55FF55)
            .withStyle(style -> style
                .withClickEvent(TextEventCompat.runCommand("/handshaker info diagnostic export"))
                .withHoverEvent(TextEventCompat.showText("Save this diagnostic report to exports folder"))));
        return Command.SINGLE_SUCCESS;
    }

    private static int exportDiagnosticReport(CommandContext<CommandSourceStack> ctx) {
        ConfigManager config = HandShakerServer.getInstance().getConfigManager();
        if (!config.isDiagnosticCommandEnabled()) {
            ctx.getSource().sendFailure(Component.literal("Diagnostic command is disabled in config"));
            return 0;
        }

        PlayerHistoryDatabase db = HandShakerServer.getInstance().getPlayerHistoryDb();
        Path exportDir = HandShakerServer.getInstance().getConfigManager().getConfigDirPath().resolve("exports");
        DiagnosticCommand.FileExportResult result = DiagnosticCommand.writeDiagnosticReport(
            config,
            db,
            exportDir,
            System.currentTimeMillis()
        );
        if (!result.success()) {
            ctx.getSource().sendFailure(Component.literal("Diagnostic export failed: " + result.errorMessage()));
            return 0;
        }
        ctx.getSource().sendSystemMessage(Component.literal("Exported diagnostic: " + result.output().getFileName()).withColor(0x54FF54));
        return Command.SINGLE_SUCCESS;
    }

    private static int exportSnapshot(CommandContext<CommandSourceStack> ctx) {
        ConfigManager config = HandShakerServer.getInstance().getConfigManager();
        if (!config.isExportCommandEnabled()) {
            ctx.getSource().sendFailure(Component.literal("Export command is disabled in config"));
            return 0;
        }

        PlayerHistoryDatabase db = HandShakerServer.getInstance().getPlayerHistoryDb();
        if (db == null || !db.isEnabled()) {
            ctx.getSource().sendFailure(Component.literal("Database is not enabled"));
            return 0;
        }
        Path exportDir = HandShakerServer.getInstance().getConfigManager().getConfigDirPath().resolve("exports");
        DiagnosticCommand.FileExportResult result = DiagnosticCommand.writeSnapshot(
            config,
            db,
            exportDir,
            System.currentTimeMillis()
        );
        if (!result.success()) {
            ctx.getSource().sendFailure(Component.literal("Export failed: " + result.errorMessage()));
            return 0;
        }
        ctx.getSource().sendSystemMessage(Component.literal("Exported snapshot: " + result.output().getFileName()).withColor(0x54FF54));
        return Command.SINGLE_SUCCESS;
    }

    private static int showConfiguredMods(CommandContext<CommandSourceStack> ctx) {
        return showConfiguredModsWithPage(ctx, 1);
    }

    private static int showConfiguredModsWithPage(CommandContext<CommandSourceStack> ctx) {
        int page = IntegerArgumentType.getInteger(ctx, "page");
        return showConfiguredModsWithPage(ctx, page);
    }

    private static int showConfiguredModsWithPage(CommandContext<CommandSourceStack> ctx, int pageNum) {
        ConfigManager config = HandShakerServer.getInstance().getConfigManager();
        Map<String, ConfigState.ModConfig> mods = config.getModConfigMap();

        String title = "Configured Mods";
        ctx.getSource().sendSystemMessage(sectionHeader(title));
        
        if (mods.isEmpty()) {
            ctx.getSource().sendSystemMessage(Component.literal("No mods configured").withColor(0xFFFF55));
            ctx.getSource().sendSystemMessage(sectionFooter(title));
            return Command.SINGLE_SUCCESS;
        }
        
        InfoCommandOperations.ConfiguredModsPageResult paged =
            InfoCommandOperations.loadConfiguredModsPage(mods, pageNum, CommandHelper.PAGE_SIZE);
        if (paged.hasInvalidPage()) {
            ctx.getSource().sendFailure(Component.literal("Invalid page. Total pages: " + paged.totalPages()));
            return 0;
        }

        List<Map.Entry<String, ConfigState.ModConfig>> modList = paged.items();
        CommandHelper.Page<Map.Entry<String, ConfigState.ModConfig>> page = paged.page();

        ctx.getSource().sendSystemMessage(Component.literal("page: ").withColor(0xAAAAAA)
            .append(Component.literal(String.valueOf(page.pageNum())).withColor(0xFFFFFF))
            .append(Component.literal(" / ").withColor(0xAAAAAA))
            .append(Component.literal(String.valueOf(page.totalPages())).withColor(0xFFFFFF)));
        ctx.getSource().sendSystemMessage(Component.empty());

        for (int i = page.startIdx(); i < page.endIdx(); i++) {
            Map.Entry<String, ConfigState.ModConfig> entry = modList.get(i);
            ConfigState.ModConfig modCfg = entry.getValue();
            if (ctx.getSource().getEntity() instanceof ServerPlayer) {
                String modName = entry.getKey();
                String changeCommand = CommandVisualOperations.manageChangeCommand("/handshaker", modName);
                String removeCommand = CommandVisualOperations.manageRemoveCommand("/handshaker", modName);
                String actionLabel = CommandVisualOperations.actionDisplayLabel(modCfg);
                String hoverText = CommandVisualOperations.changeHoverText(modCfg);

                MutableComponent modComponent = Component.literal(modName).withStyle(style -> style
                    .withColor(ChatFormatting.WHITE)
                    .withClickEvent(TextEventCompat.suggestCommand(changeCommand))
                    .withHoverEvent(TextEventCompat.showText(Component.literal(hoverText))));

                MutableComponent removeBtn = Component.literal("[✕]").withStyle(style -> style
                    .withColor(ChatFormatting.RED)
                    .withClickEvent(TextEventCompat.runCommand(removeCommand))
                    .withHoverEvent(TextEventCompat.showText(Component.literal(CommandVisualOperations.removeHoverText()))));

                ctx.getSource().sendSystemMessage(Component.literal("- ").withColor(0xAAAAAA)
                    .append(modComponent)
                    .append(Component.literal(" ").withColor(0xAAAAAA))
                    .append(modeTag(modCfg != null ? modCfg.getMode() : null))
                    .append(Component.literal(" | Action: ").withColor(0x555555))
                    .append(Component.literal(actionLabel).withColor(0xAAAAAA))
                    .append(Component.literal(" | ").withColor(0x555555))
                    .append(removeBtn));
            } else {
                ctx.getSource().sendSystemMessage(Component.literal("- ").withColor(0xAAAAAA)
                    .append(Component.literal(entry.getKey()).withColor(0xFFFFFF))
                    .append(Component.literal(" ").withColor(0xAAAAAA))
                    .append(modeTag(modCfg != null ? modCfg.getMode() : null))
                    .append(Component.literal(" | Action: ").withColor(0x555555))
                    .append(Component.literal(CommandVisualOperations.actionDisplayLabel(modCfg)).withColor(0xAAAAAA)));
            }
        }

        if (page.totalPages() > 1) {
            ctx.getSource().sendSystemMessage(Component.empty());
            sendPaginationNavigation(
                ctx,
                page.pageNum() > 1,
                "/handshaker info configured_mods " + (page.pageNum() - 1),
                page.pageNum() < page.totalPages(),
                "/handshaker info configured_mods " + (page.pageNum() + 1),
                "/handshaker info configured_mods <page>"
            );
        }
        ctx.getSource().sendSystemMessage(sectionFooter(title));
        
        return Command.SINGLE_SUCCESS;
    }

    private static int showAllMods(CommandContext<CommandSourceStack> ctx) {
        return showAllModsWithPage(ctx, 1);
    }

    private static int showAllModsWithPage(CommandContext<CommandSourceStack> ctx) {
        int page = IntegerArgumentType.getInteger(ctx, "page");
        return showAllModsWithPage(ctx, page);
    }

    private static int showAllModsWithPage(CommandContext<CommandSourceStack> ctx, int pageNum) {
        PlayerHistoryDatabase db = HandShakerServer.getInstance().getPlayerHistoryDb();
        
        
        if (db == null || !db.isEnabled()) {
            ctx.getSource().sendFailure(Component.literal("Player history database not available"));
            return 0;
        }

        return executeAsyncDatabase(
            ctx,
            () -> InfoCommandOperations.loadPopularityPage(db.getModPopularity(), pageNum, CommandHelper.PAGE_SIZE),
            paged -> renderAllMods(ctx, paged),
            throwable -> ctx.getSource().sendFailure(Component.literal("Failed to load mod popularity: " + rootMessage(throwable)))
        );
    }

    private static int showModInfo(CommandContext<CommandSourceStack> ctx) {
        String modName = StringArgumentType.getString(ctx, "modName");
        PlayerHistoryDatabase db = HandShakerServer.getInstance().getPlayerHistoryDb();

        return executeAsyncDatabase(
            ctx,
            () -> InfoCommandOperations.loadModInfo(db, modName, true),
            result -> renderModInfo(ctx, result),
            throwable -> ctx.getSource().sendFailure(Component.literal("Failed to load mod info: " + rootMessage(throwable)))
        );
    }

    private static int showPlayerHistory(CommandContext<CommandSourceStack> ctx) {
        return showPlayerHistoryWithPage(ctx, 1);
    }

    private static int showPlayerHistoryWithPage(CommandContext<CommandSourceStack> ctx) {
        int page = IntegerArgumentType.getInteger(ctx, "page");
        return showPlayerHistoryWithPage(ctx, page);
    }

    private static int showPlayerHistoryWithPage(CommandContext<CommandSourceStack> ctx, int pageNum) {
        String playerName = StringArgumentType.getString(ctx, "playerName");
        PlayerHistoryDatabase db = HandShakerServer.getInstance().getPlayerHistoryDb();
        ServerPlayer online = ctx.getSource().getServer().getPlayerList().getPlayerByName(playerName);
        UUID onlineUuid = online != null ? online.getUUID() : null;

        return executeAsyncDatabase(
            ctx,
            () -> InfoCommandOperations.loadPlayerHistory(db, playerName, onlineUuid, pageNum, CommandHelper.PAGE_SIZE),
            result -> renderPlayerHistory(ctx, playerName, result),
            throwable -> ctx.getSource().sendFailure(Component.literal("Failed to load player history: " + rootMessage(throwable)))
        );
    }

    private static int setMode(CommandContext<CommandSourceStack> ctx) {
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
            ctx.getSource().sendSuccess(() -> Component.literal("✗ " + result.message()).withStyle(ChatFormatting.RED), true);
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

        ctx.getSource().sendSuccess(() -> Component.literal("✓ " + result.message()).withColor(0x54FF54), true);
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

    private static CompletableFuture<Suggestions> suggestModes(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        for (String mode : CommandSuggestionOperations.modeSuggestions(builder.getRemaining())) {
            builder.suggest(mode);
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestActions(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        ConfigManager config = HandShakerServer.getInstance().getConfigManager();
        Set<String> availableActions = config.getAvailableActions();

        List<String> actions = CommandSuggestionOperations.actionSuggestions(availableActions, false, true);
        for (String action : CommandSuggestionOperations.filterByPrefix(actions, builder.getRemaining())) {
            builder.suggest(action);
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestMods(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            return Suggestions.empty();
        }
        
        String remaining = builder.getRemaining().toLowerCase();
        // Always suggest * wildcard
        if ("*".startsWith(remaining)) {
            builder.suggest("*");
        }
        
        ClientInfo clientInfo = HandShakerServer.getInstance().getClients().get(player.getUUID());
        if (clientInfo != null) {
            List<String> displays = clientInfo.mods().stream()
                .map(CommandHelper::toDisplayModToken)
                .toList();
            for (String suggestion : CommandSuggestionOperations.autoQuotedSuggestions(builder.getRemaining(), displays)) {
                builder.suggest(suggestion);
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestConfigParams(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        for (String param : CommandSuggestionOperations.configParamSuggestions(builder.getRemaining())) {
            builder.suggest(param);
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestConfigValues(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String param = StringArgumentType.getString(ctx, "param");
        ConfigManager config = HandShakerServer.getInstance().getConfigManager();
        for (String value : CommandSuggestionOperations.configValueSuggestions(param.toLowerCase(), config.getAvailableActions())) {
            builder.suggest(value);
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestConfiguredMods(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        ConfigManager config = HandShakerServer.getInstance().getConfigManager();
        for (String suggestion : CommandSuggestionOperations.autoQuotedSuggestions(builder.getRemaining(), config.getModConfigMap().keySet())) {
            builder.suggest(suggestion);
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestAllMods(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        PlayerHistoryDatabase db = HandShakerServer.getInstance().getPlayerHistoryDb();
        if (db == null || !db.isEnabled()) {
            return Suggestions.empty();
        }
        
        Map<String, Integer> allMods = db.getModPopularity();
        List<String> displays = allMods.keySet().stream()
            .map(CommandHelper::toDisplayModToken)
            .toList();
        for (String suggestion : CommandSuggestionOperations.autoQuotedSuggestions(builder.getRemaining(), displays)) {
            builder.suggest(suggestion);
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestModeLists(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        for (String list : CommandSuggestionOperations.modeListSuggestions(builder.getRemaining())) {
            builder.suggest(list);
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestCurrentModeState(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String listName = null;
        try {
            listName = StringArgumentType.getString(ctx, "list").toLowerCase();
        } catch (IllegalArgumentException ignored) {
        }

        if (listName != null) {
            ConfigManager config = HandShakerServer.getInstance().getConfigManager();
            
            boolean isCurrentlyEnabled = switch (listName) {
                case "mods_required" -> config.areModsRequiredEnabled();
                case "mods_blacklisted" -> config.areModsBlacklistedEnabled();
                case "mods_whitelisted" -> config.areModsWhitelistedEnabled();
                default -> false;
            };
            
            // Only suggest the opposite of the current state
            for (String state : CommandSuggestionOperations.modeStateSuggestions(isCurrentlyEnabled)) {
                builder.suggest(state);
            }
        } else {
            for (String state : CommandSuggestionOperations.modeStateSuggestions(null)) {
                builder.suggest(state);
            }
        }
        return builder.buildFuture();
    }
 
    private static CompletableFuture<Suggestions> suggestPlayers(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        List<String> names = CommandDataOperations.collectPlayerNames(
            ctx.getSource().getServer().getPlayerList().getPlayers(),
            player -> player.getName().getString()
        );
        for (String name : CommandSuggestionOperations.filterByPrefix(names, builder.getRemaining())) {
            builder.suggest(name);
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestPlayerMods(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String playerName = StringArgumentType.getString(ctx, "player");
        ServerPlayer player = ctx.getSource().getServer().getPlayerList().getPlayerByName(playerName);
        
        if (player == null) {
            return Suggestions.empty();
        }
        
        ClientInfo info = HandShakerServer.getInstance().getClients().get(player.getUUID());
        if (info == null || info.mods().isEmpty()) {
            return Suggestions.empty();
        }
        
        List<String> displays = info.mods().stream()
            .map(CommandHelper::toDisplayModToken)
            .toList();
        for (String suggestion : CommandSuggestionOperations.autoQuotedSuggestions(builder.getRemaining(), displays)) {
            builder.suggest(suggestion);
        }
        return builder.buildFuture();
    }

    private static int showPlayerMods(CommandContext<CommandSourceStack> ctx) {
        return showPlayerMods(ctx, 1);
    }

    private static int showPlayerModsWithPageArg(CommandContext<CommandSourceStack> ctx) {
        int page = IntegerArgumentType.getInteger(ctx, "page");
        return showPlayerMods(ctx, page);
    }

    private static int showPlayerMods(CommandContext<CommandSourceStack> ctx, int pageNum) {
        String playerName = StringArgumentType.getString(ctx, "player");
        ServerPlayer player = ctx.getSource().getServer().getPlayerList().getPlayerByName(playerName);
        
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("Player '" + playerName + "' not found"));
            return 0;
        }
        
        ClientInfo info = HandShakerServer.getInstance().getClients().get(player.getUUID());
        if (info == null || info.mods().isEmpty()) {
            ctx.getSource().sendSystemMessage(Component.literal("No mod list found for " + playerName).withColor(0xFFFF55));
            return Command.SINGLE_SUCCESS;
        }

        List<ModEntry> parsedMods = info.mods().stream()
            .map(ModEntry::parse)
            .filter(Objects::nonNull)
            .sorted(Comparator.comparing(ModEntry::modId))
            .toList();

        if (parsedMods.isEmpty()) {
            ctx.getSource().sendSystemMessage(Component.literal("No visible mods found for " + playerName).withColor(0xFFFF55));
            return Command.SINGLE_SUCCESS;
        }

        int totalMods = parsedMods.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalMods / CommandHelper.PAGE_SIZE));
        if (pageNum < 1 || pageNum > totalPages) {
            ctx.getSource().sendFailure(Component.literal("Invalid page. Total pages: " + totalPages));
            return 0;
        }

        int start = (pageNum - 1) * CommandHelper.PAGE_SIZE;
        int end = Math.min(start + CommandHelper.PAGE_SIZE, totalMods);

        String title = totalMods + " Mods";
        ctx.getSource().sendSystemMessage(sectionHeader(title));
        ctx.getSource().sendSystemMessage(Component.literal("player: ").withColor(0xAAAAAA)
            .append(Component.literal(playerName).withColor(0xFFFFFF))
            .append(Component.literal(" : Page ").withColor(0xAAAAAA))
            .append(Component.literal(String.valueOf(pageNum)).withColor(0xFFFFFF))
            .append(Component.literal(" / ").withColor(0xAAAAAA))
            .append(Component.literal(String.valueOf(totalPages)).withColor(0xFFFFFF)));
        ctx.getSource().sendSystemMessage(Component.empty());

        for (int i = start; i < end; i++) {
            ModEntry entry = parsedMods.get(i);
            String modId = entry.modId();
            String version = entry.version() != null ? entry.version() : "unknown";
            String displayName = entry.displayName() != null ? entry.displayName() : CommandHelper.prettyModName(modId);

            ctx.getSource().sendSystemMessage(Component.literal("- ").withColor(0xAAAAAA)
                .append(Component.literal(displayName).withColor(0xFFFFFF)));
            ctx.getSource().sendSystemMessage(Component.literal("  ID: ").withColor(0x555555)
                .append(Component.literal(modId).withColor(0xAAAAAA))
                .append(Component.literal(" | Version: ").withColor(0x555555))
                .append(Component.literal(version).withColor(0xAAAAAA)));
        }

        if (totalPages > 1) {
            ctx.getSource().sendSystemMessage(Component.empty());
            sendPaginationNavigation(
                ctx,
                pageNum > 1,
                "/handshaker manage player " + playerName + " page " + (pageNum - 1),
                pageNum < totalPages,
                "/handshaker manage player " + playerName + " page " + (pageNum + 1),
                "/handshaker manage player <player> page <number>"
            );
        }
        ctx.getSource().sendSystemMessage(sectionFooter(title));
        
        return Command.SINGLE_SUCCESS;
    }

    private static MutableComponent sectionHeader(String title) {
        return Component.literal(sectionHeaderText(title)).withColor(0xFFAA00).withStyle(ChatFormatting.BOLD);
    }

    private static MutableComponent sectionFooter(String title) {
        return Component.literal(CommandVisualOperations.sectionFooterText(title)).withColor(0xFFAA00).withStyle(ChatFormatting.BOLD);
    }

    private static String sectionHeaderText(String title) {
        return CommandVisualOperations.sectionHeaderText(title);
    }

    private static void sendPaginationNavigation(
        CommandContext<CommandSourceStack> ctx,
        boolean hasPrevious,
        String previousCommand,
        boolean hasNext,
        String nextCommand,
        String fallbackUsage
    ) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer)) {
            ctx.getSource().sendSystemMessage(Component.literal(CommandVisualOperations.navigateUsageText(fallbackUsage)).withColor(0xAAAAAA));
            return;
        }

        MutableComponent prev = hasPrevious
            ? Component.literal(CommandVisualOperations.previousPageLabel()).withStyle(style -> style
                .withColor(ChatFormatting.GREEN)
                .withClickEvent(TextEventCompat.runCommand(previousCommand))
                .withHoverEvent(TextEventCompat.showText(Component.literal(previousCommand))))
            : Component.literal(CommandVisualOperations.previousPageLabel()).withColor(0x555555);

        MutableComponent next = hasNext
            ? Component.literal(CommandVisualOperations.nextPageLabel()).withStyle(style -> style
                .withColor(ChatFormatting.GREEN)
                .withClickEvent(TextEventCompat.runCommand(nextCommand))
                .withHoverEvent(TextEventCompat.showText(Component.literal(nextCommand))))
            : Component.literal(CommandVisualOperations.nextPageLabel()).withColor(0x555555);

        ctx.getSource().sendSystemMessage(prev.append(Component.literal(" ")).append(next));
    }

    private static MutableComponent modeTag(String mode) {
        String label = CommandHelper.modeTagLabel(mode);
        if (mode == null) {
            return Component.literal(label).withColor(0x555555);
        }

        ChatFormatting color = switch (mode) {
            case "required" -> ChatFormatting.GOLD;
            case "blacklisted" -> ChatFormatting.RED;
            case "allowed" -> ChatFormatting.GREEN;
            default -> ChatFormatting.DARK_GRAY;
        };

        return Component.literal(label).withStyle(color);
    }

    private static int setPlayerModStatus(CommandContext<CommandSourceStack> ctx) {
        String playerName = StringArgumentType.getString(ctx, "player");
        String modId = StringArgumentType.getString(ctx, "mod").toLowerCase();
        String mode = StringArgumentType.getString(ctx, "mode").toLowerCase();
        
        ServerPlayer player = ctx.getSource().getServer().getPlayerList().getPlayerByName(playerName);
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("Player '" + playerName + "' not found"));
            return 0;
        }
        
        ClientInfo info = HandShakerServer.getInstance().getClients().get(player.getUUID());
        if (info == null || info.mods().isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("Player has no mods"));
            return 0;
        }
        
        if (!CommandHelper.isValidMode(mode)) {
            ctx.getSource().sendFailure(Component.literal("Invalid mode. Use: required, blacklisted, or allowed"));
            return 0;
        }
        
        ConfigManager config = HandShakerServer.getInstance().getConfigManager();
        
        if (modId.equals("*")) {
            // Set all mods for this player
            int updated = 0;
            for (String mod : info.mods()) {
                if (!config.isIgnored(mod)) {
                    config.setModConfig(mod, mode, config.getDefaultActionForMode(mode), null);
                    registerModFingerprint(config, mod);
                    updated++;
                }
            }
            final int finalUpdated = updated;
            ctx.getSource().sendSuccess(() -> Component.literal("✓ Set " + finalUpdated + " mods to " + mode + " for player " + playerName).withColor(0x54FF54), true);
        } else {
            if (!info.mods().contains(modId)) {
                ctx.getSource().sendFailure(Component.literal("Player " + playerName + " does not have mod: " + modId));
                return 0;
            }
            config.setModConfig(modId, mode, config.getDefaultActionForMode(mode), null);
            registerModFingerprint(config, modId);
            ctx.getSource().sendSuccess(() -> Component.literal("✓ Set " + modId + " to " + mode + " for " + playerName).withColor(0x54FF54), true);
        }
        
        HandShakerServer.getInstance().checkAllPlayers();
        return Command.SINGLE_SUCCESS;
    }

    private static int addIgnore(CommandContext<CommandSourceStack> ctx) {
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
            ServerPlayer player = ctx.getSource().getEntity() instanceof ServerPlayer ? (ServerPlayer) ctx.getSource().getEntity() : null;
            if (player == null) {
                ctx.getSource().sendFailure(Component.literal("Only players can use * wildcard"));
                return 0;
            }
            
            ClientInfo info = HandShakerServer.getInstance().getClients().get(player.getUUID());
            if (info == null || info.mods().isEmpty()) {
                ctx.getSource().sendFailure(Component.literal("No mods found on your client"));
                return 0;
            }
            
            int added = IgnoreCommandOperations.addIgnoredMods(info.mods(), config::isIgnored, config::addIgnoredMod);
            final int finalAdded = added;
            ctx.getSource().sendSuccess(() -> Component.literal("✓ Added " + finalAdded + " of your mods to ignore list").withColor(0x54FF54), true);
        } else {
            final String finalModId = modId;
            boolean added = IgnoreCommandOperations.addIgnoredMod(modId, config::isIgnored, config::addIgnoredMod);
            if (added) {
                ctx.getSource().sendSuccess(() -> Component.literal("✓ Added " + finalModId + " to ignore list").withColor(0x54FF54), true);
            } else {
                ctx.getSource().sendSystemMessage(Component.literal("⚠ " + finalModId + " already in ignore list").withColor(0xFFFF55));
            }
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int removeIgnore(CommandContext<CommandSourceStack> ctx) {
        String modId = StringArgumentType.getString(ctx, "mod").toLowerCase();
        ConfigManager config = HandShakerServer.getInstance().getConfigManager();
        
        boolean removed = IgnoreCommandOperations.removeIgnoredMod(modId, config::removeIgnoredMod);
        if (removed) {
            ctx.getSource().sendSuccess(() -> Component.literal("✓ Removed " + modId + " from ignore list").withColor(0x54FF54), true);
        } else {
            ctx.getSource().sendSystemMessage(Component.literal("⚠ " + modId + " not in ignore list").withColor(0xFFFF55));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int listIgnore(CommandContext<CommandSourceStack> ctx) {
        ConfigManager config = HandShakerServer.getInstance().getConfigManager();
        Set<String> ignored = config.getIgnoredMods();
        
        if (ignored.isEmpty()) {
            ctx.getSource().sendSystemMessage(Component.literal("No mods in ignore list").withColor(0xFFFF55));
            return Command.SINGLE_SUCCESS;
        }
        
        ctx.getSource().sendSystemMessage(Component.literal("Ignored Mods").withColor(0xFFAA00).withStyle(ChatFormatting.BOLD));
        ctx.getSource().sendSystemMessage(Component.empty());
        
        for (String mod : ignored) {
            ctx.getSource().sendSystemMessage(Component.literal("  • " + mod).withColor(0xAAAAAA));
        }
        
        return Command.SINGLE_SUCCESS;
    }

    private static void registerModFingerprint(ConfigManager config, String modToken) {
        CommandRuntimeOperations.registerModFingerprint(
            modToken,
            HandShakerServer.getInstance().getPlayerHistoryDb(),
            config.isHashMods(),
            config.isModVersioning(),
            HandShakerServer.getInstance().getClients().values(),
            config.isAsyncDatabaseOperations(),
            HandShakerServer.getInstance().getScheduler()
        );
    }

    private static <T> int executeAsyncDatabase(
        CommandContext<CommandSourceStack> ctx,
        java.util.function.Supplier<T> supplier,
        java.util.function.Consumer<T> onSuccess,
        java.util.function.Consumer<Throwable> onFailure
    ) {
        ConfigManager config = HandShakerServer.getInstance().getConfigManager();
        return CommandRuntimeOperations.executeAsyncDatabase(
            config.isAsyncDatabaseOperations(),
            HandShakerServer.getInstance().getScheduler(),
            run -> ctx.getSource().getServer().execute(run),
            supplier,
            onSuccess,
            onFailure,
            Command.SINGLE_SUCCESS,
            0
        );
    }

    private static void renderAllMods(CommandContext<CommandSourceStack> ctx, InfoCommandOperations.PopularityPageResult paged) {
        ConfigManager config = HandShakerServer.getInstance().getConfigManager();
        if (paged.hasInvalidPage()) {
            ctx.getSource().sendFailure(Component.literal("Invalid page. Total pages: " + paged.totalPages()));
            return;
        }

        List<Map.Entry<String, Integer>> sortedMods = paged.items();
        CommandHelper.Page<Map.Entry<String, Integer>> page = paged.page();

        String title = "All Mods (Page " + page.pageNum() + "/" + page.totalPages() + ")";
        ctx.getSource().sendSystemMessage(sectionHeader(title));

        for (int i = page.startIdx(); i < page.endIdx(); i++) {
            Map.Entry<String, Integer> entry = sortedMods.get(i);
            ConfigState.ModConfig modCfg = config.getModConfig(entry.getKey());
            String modToken = entry.getKey();
            ModEntry parsed = ModEntry.parse(modToken);
            String modId = parsed != null ? parsed.modId() : modToken;
            String version = parsed != null && parsed.version() != null ? parsed.version() : "unknown";
            String displayName = parsed != null && parsed.displayName() != null ? parsed.displayName() : CommandHelper.prettyModName(modId);

            if (ctx.getSource().getEntity() instanceof ServerPlayer) {
                String modInfoCommand = CommandVisualOperations.infoModCommand("/handshaker", modToken);
                MutableComponent modComponent = Component.literal(displayName).withStyle(style -> style
                    .withColor(ChatFormatting.WHITE)
                    .withClickEvent(TextEventCompat.runCommand(modInfoCommand))
                    .withHoverEvent(TextEventCompat.showText(Component.literal(CommandVisualOperations.showPlayersUsingModHoverText()))));

                MutableComponent line = modeTag(modCfg != null ? modCfg.getMode() : null)
                    .append(Component.literal(" ").withColor(0xAAAAAA))
                    .append(modComponent)
                    .append(Component.literal(" x" + entry.getValue()).withColor(0xAAAAAA));
                ctx.getSource().sendSystemMessage(line);
                ctx.getSource().sendSystemMessage(Component.literal("  ID: ").withColor(0x555555)
                    .append(Component.literal(modId).withColor(0xAAAAAA))
                    .append(Component.literal(" | Version: ").withColor(0x555555))
                    .append(Component.literal(version).withColor(0xAAAAAA)));
            } else {
                MutableComponent line = modeTag(modCfg != null ? modCfg.getMode() : null)
                    .append(Component.literal(" ").withColor(0xAAAAAA))
                    .append(Component.literal(displayName).withColor(0xFFFFFF))
                    .append(Component.literal(" x" + entry.getValue()).withColor(0xAAAAAA));
                ctx.getSource().sendSystemMessage(line);
                ctx.getSource().sendSystemMessage(Component.literal("  ID: ").withColor(0x555555)
                    .append(Component.literal(modId).withColor(0xAAAAAA))
                    .append(Component.literal(" | Version: ").withColor(0x555555))
                    .append(Component.literal(version).withColor(0xAAAAAA)));
            }
        }

        if (page.totalPages() > 1) {
            ctx.getSource().sendSystemMessage(Component.empty());
            sendPaginationNavigation(
                ctx,
                page.pageNum() > 1,
                "/handshaker info all_mods " + (page.pageNum() - 1),
                page.pageNum() < page.totalPages(),
                "/handshaker info all_mods " + (page.pageNum() + 1),
                "/handshaker info all_mods <page>"
            );
        }
        ctx.getSource().sendSystemMessage(sectionFooter(title));
    }

    private static void renderModInfo(CommandContext<CommandSourceStack> ctx, InfoCommandOperations.ModInfoResult result) {
        if (!result.success()) {
            ctx.getSource().sendFailure(Component.literal(result.error()));
            return;
        }

        List<PlayerHistoryDatabase.PlayerModInfo> players = result.players();
        if (players.isEmpty()) {
            ctx.getSource().sendSystemMessage(Component.literal("No players found with mod: " + result.displayKey()).withColor(0xFFFF55));
            return;
        }

        String title = "Mod: " + result.displayKey();
        ctx.getSource().sendSystemMessage(sectionHeader(title));
        ctx.getSource().sendSystemMessage(Component.literal("Players: " + players.size()).withColor(0xFFFF55));
        ctx.getSource().sendSystemMessage(Component.empty());

        List<CommandHelper.ModInfoRow> rows =
            CommandHelper.buildModInfoRows(players, "✓ Active", "✗ Removed");
        for (CommandHelper.ModInfoRow row : rows) {
            ChatFormatting statusColor = row.active() ? ChatFormatting.GREEN : ChatFormatting.RED;

            ctx.getSource().sendSystemMessage(Component.literal(row.playerName()).withColor(0x55FFFF)
                .append(Component.literal(" - " + row.statusLabel()).withStyle(statusColor))
                .append(Component.literal(" (Since: " + row.firstSeenFormatted() + ")").withColor(0xAAAAAA)));
        }
        ctx.getSource().sendSystemMessage(sectionFooter(title));
    }

    private static void renderPlayerHistory(CommandContext<CommandSourceStack> ctx, String playerName, InfoCommandOperations.PlayerHistoryResult result) {
        if (!result.success()) {
            ctx.getSource().sendFailure(Component.literal(result.error()));
            return;
        }

        List<PlayerHistoryDatabase.ModHistoryEntry> history = result.history();
        if (history.isEmpty()) {
            ctx.getSource().sendSystemMessage(Component.literal("No mod history found for: " + playerName).withColor(0xFFFF55));
            return;
        }
        CommandHelper.Page<PlayerHistoryDatabase.ModHistoryEntry> page = result.page();

        String title = "Mod History";
        ctx.getSource().sendSystemMessage(sectionHeader(title));
        ctx.getSource().sendSystemMessage(Component.literal("player: ").withColor(0xAAAAAA)
            .append(Component.literal(playerName).withColor(0xFFFFFF))
            .append(Component.literal(" : Page ").withColor(0xAAAAAA))
            .append(Component.literal(String.valueOf(page.pageNum())).withColor(0xFFFFFF))
            .append(Component.literal(" / ").withColor(0xAAAAAA))
            .append(Component.literal(String.valueOf(page.totalPages())).withColor(0xFFFFFF)));
        ctx.getSource().sendSystemMessage(Component.empty());

        for (int i = page.startIdx(); i < page.endIdx(); i++) {
            PlayerHistoryDatabase.ModHistoryEntry entry = history.get(i);
            String status = CommandHelper.historyStatusLabel(entry.isActive());
            ChatFormatting statusColor = entry.isActive() ? ChatFormatting.GREEN : ChatFormatting.RED;
            String dates = CommandHelper.historyDates(entry);

            ctx.getSource().sendSystemMessage(Component.literal("- ").withColor(0xAAAAAA)
                .append(Component.literal(entry.modName()).withColor(0xFFFFFF)));
            ctx.getSource().sendSystemMessage(Component.literal("  Status: ").withColor(0x555555)
                .append(Component.literal(status).withStyle(statusColor))
                .append(Component.literal(" | ").withColor(0x555555))
                .append(Component.literal(dates).withColor(0xAAAAAA)));
        }

        if (page.totalPages() > 1) {
            ctx.getSource().sendSystemMessage(Component.empty());
            sendPaginationNavigation(
                ctx,
                page.pageNum() > 1,
                "/handshaker info player " + playerName + " " + (page.pageNum() - 1),
                page.pageNum() < page.totalPages(),
                "/handshaker info player " + playerName + " " + (page.pageNum() + 1),
                "/handshaker info player <player> <page>"
            );
        }
        ctx.getSource().sendSystemMessage(sectionFooter(title));
    }

    private static String rootMessage(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage() != null ? root.getMessage() : root.getClass().getSimpleName();
    }

    private static CompletableFuture<Suggestions> suggestIgnoredMods(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        ConfigManager config = HandShakerServer.getInstance().getConfigManager();
        for (String suggestion : CommandSuggestionOperations.autoQuotedSuggestions(builder.getRemaining(), config.getIgnoredMods())) {
            builder.suggest(suggestion);
        }
        return builder.buildFuture();
    }

}
