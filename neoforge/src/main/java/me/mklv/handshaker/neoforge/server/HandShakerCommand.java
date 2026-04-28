package me.mklv.handshaker.neoforge.server;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import me.mklv.handlib.neoforge.NeoForgeVersionCompat;
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
import me.mklv.handshaker.common.utils.LoggerAdapter;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.ChatFormatting;
import net.neoforged.fml.ModList;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static me.mklv.handshaker.common.commands.CommandVisualOperations.Colors.*;

public class HandShakerCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var handshaker = Commands.literal("handshaker")
            .requires(NeoForgeVersionCompat.ownerPermission())
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
                    .then(Commands.argument("modToken", StringArgumentType.string())
                        .suggests(HandShakerCommand::suggestAllMods)
                        .executes(HandShakerCommand::showModInfo)))
                .then(Commands.literal("diagnostic")
                    .executes(HandShakerCommand::showDiagnostic)
                    .then(Commands.literal("export")
                        .executes(HandShakerCommand::exportDiagnosticReport))))
            .then(Commands.literal("config")
                .executes(HandShakerCommand::showConfig)
                .then(Commands.literal("compatibility")
                    .then(Commands.argument("subparam", StringArgumentType.word())
                        .suggests(HandShakerCommand::suggestCompatSubparams)
                        .then(Commands.argument("value", StringArgumentType.word())
                            .suggests(HandShakerCommand::suggestCompatValues)
                            .executes(HandShakerCommand::setCompatValueDynamic))))
                .then(Commands.argument("param", StringArgumentType.word())
                    .suggests(HandShakerCommand::suggestConfigParams)
                    .then(Commands.argument("value", StringArgumentType.word())
                        .suggests(HandShakerCommand::suggestConfigValues)
                        .executes(HandShakerCommand::setConfigValueDynamic))))
            .then(Commands.literal("mode")
                .then(Commands.argument("ruleList", StringArgumentType.word())
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
                            .executes(HandShakerCommand::addAllMods)
                            .then(Commands.argument("action", StringArgumentType.word())
                                .suggests(HandShakerCommand::suggestActions)
                                .executes(HandShakerCommand::addAllModsWithAction))))
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
                        .executes(HandShakerCommand::listIgnored)))
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
                                .executes(HandShakerCommand::changePlayerMod)))
                        .then(Commands.argument("mod", StringArgumentType.string())
                            .suggests(HandShakerCommand::suggestPlayerMods)
                            .then(Commands.argument("mode", StringArgumentType.word())
                                .suggests(HandShakerCommand::suggestModes)
                                .executes(HandShakerCommand::changePlayerMod)
                                .then(Commands.argument("action", StringArgumentType.word())
                                    .suggests(HandShakerCommand::suggestActions)
                                    .executes(HandShakerCommand::changePlayerMod)))))));

        dispatcher.register(handshaker);
    }

    private static int showHelp(CommandContext<CommandSourceStack> ctx) {
        String title = "HandShaker v7 Commands";
        ctx.getSource().sendSystemMessage(sectionHeader(title));
        ctx.getSource().sendSystemMessage(Component.empty());
        
        // Loop through help sections and render with NeoForge Component API
        for (CommandHelper.HelpSection section : CommandHelper.getHelpSections()) {
            ctx.getSource().sendSystemMessage(Component.literal(section.title()).withColor(YELLOW).withStyle(ChatFormatting.BOLD));
            for (CommandHelper.HelpEntry entry : section.entries()) {
                ctx.getSource().sendSystemMessage(Component.literal(entry.command()).withColor(YELLOW)
                    .append(Component.literal(" - " + entry.description()).withColor(GRAY)));
            }
            ctx.getSource().sendSystemMessage(Component.empty());
        }

        ctx.getSource().sendSystemMessage(sectionFooter(title));
        
        return Command.SINGLE_SUCCESS;
    }

    private static int showDiagnostic(CommandContext<CommandSourceStack> ctx) {
        ConfigManager config = HandShakerServerMod.getInstance().getConfigManager();
        if (!config.isDiagnosticCommandEnabled()) {
            ctx.getSource().sendFailure(Component.literal("Diagnostic command is disabled in config"));
            return 0;
        }

        PlayerHistoryDatabase db = HandShakerServerMod.getInstance().getPlayerHistoryDb();
        ctx.getSource().sendSystemMessage(sectionHeader("HandShaker Diagnostic"));
        String handShakerVersion = resolveHandShakerVersion();
        String serverInfo = ctx.getSource().getServer().getServerModName();
        String minecraftInfo = ctx.getSource().getServer().getServerVersion();
        for (String line : DiagnosticCommand.buildDisplayLines(config, db, handShakerVersion, serverInfo, minecraftInfo)) {
            int color = "Database disabled".equals(line) ? YELLOW : GRAY;
            ctx.getSource().sendSystemMessage(Component.literal(line).withColor(color));
        }
        ctx.getSource().sendSystemMessage(Component.literal("[ Save diagnostic to exports ]")
            .withColor(SUCCESS_GREEN_COLOR)
            .withStyle(style -> style
                .withClickEvent(TextEventCompat.runCommand("/handshaker info diagnostic export"))
                .withHoverEvent(TextEventCompat.showText(Component.literal("Save this diagnostic report to exports folder")))));
        ctx.getSource().sendSystemMessage(sectionFooter("HandShaker Diagnostic"));
        return Command.SINGLE_SUCCESS;
    }

    private static int exportDiagnosticReport(CommandContext<CommandSourceStack> ctx) {
        ConfigManager config = HandShakerServerMod.getInstance().getConfigManager();
        if (!config.isDiagnosticCommandEnabled()) {
            ctx.getSource().sendFailure(Component.literal("Diagnostic command is disabled in config"));
            return 0;
        }

        PlayerHistoryDatabase db = HandShakerServerMod.getInstance().getPlayerHistoryDb();
        Path exportDir = HandShakerServerMod.getInstance().getConfigManager().getConfigDirPath().resolve("exports");
        String handShakerVersion = resolveHandShakerVersion();
        String serverInfo = ctx.getSource().getServer().getServerModName();
        String minecraftInfo = ctx.getSource().getServer().getServerVersion();
        DiagnosticCommand.FileExportResult result = DiagnosticCommand.writeDiagnosticReport(
            config,
            db,
            exportDir,
            System.currentTimeMillis(),
            handShakerVersion,
            serverInfo,
            minecraftInfo
        );
        if (!result.success()) {
            ctx.getSource().sendFailure(Component.literal("Diagnostic export failed: " + result.errorMessage()));
            return 0;
        }
        ctx.getSource().sendSystemMessage(Component.literal("Exported diagnostic: " + result.output().getFileName()).withColor(SUCCESS_GREEN_COLOR));
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
        ConfigManager config = HandShakerServerMod.getInstance().getConfigManager();
        Map<String, ConfigState.ModConfig> mods = config.getModConfigMap();

        String title = "Configured Mods";
        ctx.getSource().sendSystemMessage(sectionHeader(title));
        
        if (mods.isEmpty()) {
            ctx.getSource().sendSystemMessage(Component.literal("No mods configured").withColor(YELLOW));
            ctx.getSource().sendSystemMessage(sectionFooter(title));
            return Command.SINGLE_SUCCESS;
        }

        InfoCommandOperations.ConfiguredModsPageResult paged =
            InfoCommandOperations.loadConfiguredModsPage(mods, pageNum, CommandHelper.PAGE_SIZE, config::isModIgnored);
        if (paged.hasInvalidPage()) {
            ctx.getSource().sendFailure(Component.literal("Invalid page. Total pages: " + paged.totalPages()));
            return 0;
        }

        List<Map.Entry<String, ConfigState.ModConfig>> modList = paged.items();
        CommandHelper.Page<Map.Entry<String, ConfigState.ModConfig>> page = paged.page();

        ctx.getSource().sendSystemMessage(Component.literal("page: ").withColor(GRAY)
            .append(Component.literal(String.valueOf(page.pageNum())).withColor(WHITE))
            .append(Component.literal(" / ").withColor(GRAY))
            .append(Component.literal(String.valueOf(page.totalPages())).withColor(WHITE)));
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

                ctx.getSource().sendSystemMessage(Component.literal("- ").withColor(GRAY)
                    .append(modComponent)
                    .append(Component.literal(" ").withColor(GRAY))
                    .append(modeTag(modCfg != null ? modCfg.getMode() : null))
                    .append(Component.literal(" | Action: ").withColor(DARK_GRAY))
                    .append(Component.literal(actionLabel).withColor(GRAY))
                    .append(Component.literal(" | ").withColor(DARK_GRAY))
                    .append(removeBtn));
            } else {
                ctx.getSource().sendSystemMessage(Component.literal("- ").withColor(GRAY)
                    .append(Component.literal(entry.getKey()).withColor(WHITE))
                    .append(Component.literal(" ").withColor(GRAY))
                    .append(modeTag(modCfg != null ? modCfg.getMode() : null))
                    .append(Component.literal(" | Action: ").withColor(DARK_GRAY))
                    .append(Component.literal(CommandVisualOperations.actionDisplayLabel(modCfg)).withColor(GRAY)));
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
        PlayerHistoryDatabase db = HandShakerServerMod.getInstance().getPlayerHistoryDb();
        ConfigManager config = HandShakerServerMod.getInstance().getConfigManager();

        if (db == null || !db.isEnabled()) {
            ctx.getSource().sendFailure(Component.literal("Player history database not available"));
            return 0;
        }

        return executeAsyncDatabase(
            ctx,
            () -> InfoCommandOperations.loadPopularityPage(db.getModPopularity(), pageNum, CommandHelper.PAGE_SIZE, config::isModIgnored),
            paged -> renderAllMods(ctx, paged),
            throwable -> ctx.getSource().sendFailure(Component.literal("Failed to load mod popularity: " + rootMessage(throwable)))
        );
    }

    private static void renderAllMods(CommandContext<CommandSourceStack> ctx, InfoCommandOperations.PopularityPageResult paged) {
        if (paged.hasInvalidPage()) {
            ctx.getSource().sendFailure(Component.literal("Invalid page. Total pages: " + paged.totalPages()));
            return;
        }

        List<Map.Entry<String, Integer>> sortedMods = paged.items();
        CommandHelper.Page<Map.Entry<String, Integer>> page = paged.page();
        ConfigManager config = HandShakerServerMod.getInstance().getConfigManager();

        String title = "All Mods";
        ctx.getSource().sendSystemMessage(sectionHeader(title));
        ctx.getSource().sendSystemMessage(Component.literal("page: ").withColor(GRAY)
            .append(Component.literal(String.valueOf(page.pageNum())).withColor(WHITE))
            .append(Component.literal(" / ").withColor(GRAY))
            .append(Component.literal(String.valueOf(page.totalPages())).withColor(WHITE)));
        ctx.getSource().sendSystemMessage(Component.empty());

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
                    .append(Component.literal(" ").withColor(GRAY))
                    .append(modComponent)
                    .append(Component.literal(" x" + entry.getValue()).withColor(GRAY));
                ctx.getSource().sendSystemMessage(line);
                ctx.getSource().sendSystemMessage(Component.literal("  ID: ").withColor(DARK_GRAY)
                    .append(Component.literal(modId).withColor(GRAY))
                    .append(Component.literal(" | Version: ").withColor(DARK_GRAY))
                    .append(Component.literal(version).withColor(GRAY)));
            } else {
                MutableComponent line = modeTag(modCfg != null ? modCfg.getMode() : null)
                    .append(Component.literal(" ").withColor(GRAY))
                    .append(Component.literal(displayName).withColor(WHITE))
                    .append(Component.literal(" x" + entry.getValue()).withColor(GRAY));
                ctx.getSource().sendSystemMessage(line);
                ctx.getSource().sendSystemMessage(Component.literal("  ID: ").withColor(DARK_GRAY)
                    .append(Component.literal(modId).withColor(GRAY))
                    .append(Component.literal(" | Version: ").withColor(DARK_GRAY))
                    .append(Component.literal(version).withColor(GRAY)));
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


    private static int showModInfo(CommandContext<CommandSourceStack> ctx) {
        String modToken = StringArgumentType.getString(ctx, "modToken");
        PlayerHistoryDatabase db = HandShakerServerMod.getInstance().getPlayerHistoryDb();
        ConfigManager config = HandShakerServerMod.getInstance().getConfigManager();

        return executeAsyncDatabase(
            ctx,
            () -> InfoCommandOperations.loadModInfo(db, modToken, true, config::isModIgnored),
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
        PlayerHistoryDatabase db = HandShakerServerMod.getInstance().getPlayerHistoryDb();
        ConfigManager config = HandShakerServerMod.getInstance().getConfigManager();
        ServerPlayer online = ctx.getSource().getServer().getPlayerList().getPlayerByName(playerName);
        UUID onlineUuid = online != null ? online.getUUID() : null;

        return executeAsyncDatabase(
            ctx,
            () -> InfoCommandOperations.loadPlayerHistory(db, playerName, onlineUuid, pageNum, CommandHelper.PAGE_SIZE, config::isModIgnored),
            result -> renderPlayerHistory(ctx, playerName, result),
            throwable -> ctx.getSource().sendFailure(Component.literal("Failed to load player history: " + rootMessage(throwable)))
        );
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        ConfigManager config = HandShakerServerMod.getInstance().getConfigManager();
        config.load();
        Component message = Component.literal("✓ HandShaker config reloaded").withColor(SUCCESS_GREEN_COLOR);
        ctx.getSource().sendSuccess(() -> message, true);
        HandShakerServerMod.getInstance().checkAllPlayers();
        return Command.SINGLE_SUCCESS;
    }


    private static int showConfig(CommandContext<CommandSourceStack> ctx) {
        ConfigManager config = HandShakerServerMod.getInstance().getConfigManager();
        String title = "HandShaker Configuration";
        ctx.getSource().sendSystemMessage(sectionHeader(title));
        
        String requiredModpackHashes = config.getRequiredModpackHashes().isEmpty() ? "OFF" : String.join(", ", config.getRequiredModpackHashes());
        java.util.List<CommandHelper.InfoField> configFields = java.util.List.of(
            new CommandHelper.InfoField("Behavior", config.getBehavior().toString()),
            new CommandHelper.InfoField("Integrity Mode", config.getDisplayedIntegrityMode()),
            new CommandHelper.InfoField("Whitelist Mode", config.isWhitelist() ? "ON" : "OFF"),
            new CommandHelper.InfoField("Bedrock Players", config.isAllowBedrockPlayers() ? "Allowed" : "Blocked"),
            new CommandHelper.InfoField("Player Database", config.isPlayerdbEnabled() ? "Enabled" : "Disabled"),
            new CommandHelper.InfoField("Required Modpack Hashes", requiredModpackHashes)
        );
        
        for (CommandHelper.InfoField field : configFields) {
            ctx.getSource().sendSystemMessage(Component.literal(field.label()).withColor(YELLOW)
                .append(Component.literal(": " + field.value()).withColor(WHITE)));
        }
        ctx.getSource().sendSystemMessage(sectionFooter(title));
        return Command.SINGLE_SUCCESS;
    }

    private static int setConfigValueDynamic(CommandContext<CommandSourceStack> ctx) {
        String param = StringArgumentType.getString(ctx, "param");
        return setConfigValue(ctx, param);
    }

    private static int setCompatValueDynamic(CommandContext<CommandSourceStack> ctx) {
        String subparam = StringArgumentType.getString(ctx, "subparam");
        return setConfigValue(ctx, "compat_" + subparam);
    }


    private static int setConfigValue(CommandContext<CommandSourceStack> ctx, String param) {
        String value = StringArgumentType.getString(ctx, "value");
        ConfigManager config = HandShakerServerMod.getInstance().getConfigManager();
        Set<String> currentPlayerMods = null;
        if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
            currentPlayerMods = HandShakerServerMod.getInstance().getClientMods(player.getUUID());
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
            HandShakerServerMod.getInstance().checkAllPlayers();
        }

        ctx.getSource().sendSuccess(() -> Component.literal("✓ " + result.message()).withColor(SUCCESS_GREEN_COLOR), true);
        
        return Command.SINGLE_SUCCESS;
    }


    private static int setMode(CommandContext<CommandSourceStack> ctx) {
        String ruleList = StringArgumentType.getString(ctx, "ruleList");
        String action = StringArgumentType.getString(ctx, "action");
        ConfigManager config = HandShakerServerMod.getInstance().getConfigManager();

        ConfigCommandOperations.MutationResult result = ConfigCommandOperations.applyModeToggle(
            config,
            config.getConfigDirPath(),
            LoggerAdapter.fromLoaderLogger(HandShakerServerMod.LOGGER),
            ruleList,
            action
        );

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
            HandShakerServerMod.getInstance().checkAllPlayers();
        }

        ctx.getSource().sendSuccess(() -> Component.literal("✓ " + result.message()).withColor(SUCCESS_GREEN_COLOR), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int addMod(CommandContext<CommandSourceStack> ctx) {
        String mod = StringArgumentType.getString(ctx, "mod").toLowerCase();
        String mode = StringArgumentType.getString(ctx, "mode").toLowerCase();
        ConfigManager config = HandShakerServerMod.getInstance().getConfigManager();
        if (!CommandHelper.isValidMode(mode)) {
            ctx.getSource().sendFailure(Component.literal("Invalid mode. Use: required, blacklisted, or allowed"));
            return 0;
        }
        ModRuleCommandOperations.upsertModRule(
            mod,
            mode,
            null,
            config::getDefaultActionForMode,
            config::setModConfigByString,
            token -> registerModFingerprint(config, token)
        );
        config.save();
        ctx.getSource().sendSuccess(() -> Component.literal("✓ Added '" + mod + "' as " + mode).withColor(SUCCESS_GREEN_COLOR), true);
        HandShakerServerMod.getInstance().checkAllPlayers();
        return Command.SINGLE_SUCCESS;
    }

    private static int addModWithAction(CommandContext<CommandSourceStack> ctx) {
        String mod = StringArgumentType.getString(ctx, "mod").toLowerCase();
        String mode = StringArgumentType.getString(ctx, "mode").toLowerCase();
        String action = StringArgumentType.getString(ctx, "action").toLowerCase();
        ConfigManager config = HandShakerServerMod.getInstance().getConfigManager();
        if (!CommandHelper.isValidMode(mode)) {
            ctx.getSource().sendFailure(Component.literal("Invalid mode. Use: required, blacklisted, or allowed"));
            return 0;
        }
        Set<String> availableActions = config.getAvailableActions();
        if (!availableActions.isEmpty() && !availableActions.contains(action)) {
            ctx.getSource().sendFailure(Component.literal("Invalid action. Available: " + String.join(", ", availableActions)));
            return 0;
        }
        ModRuleCommandOperations.upsertModRule(
            mod,
            mode,
            action,
            config::getDefaultActionForMode,
            config::setModConfigByString,
            token -> registerModFingerprint(config, token)
        );
        config.save();
        ctx.getSource().sendSuccess(() -> Component.literal("✓ Added '" + mod + "' as " + mode + " with " + action).withColor(SUCCESS_GREEN_COLOR), true);
        HandShakerServerMod.getInstance().checkAllPlayers();
        return Command.SINGLE_SUCCESS;
    }

    private static int changeMod(CommandContext<CommandSourceStack> ctx) {
        String mod = StringArgumentType.getString(ctx, "mod").toLowerCase();
        String mode = StringArgumentType.getString(ctx, "mode").toLowerCase();
        ConfigManager config = HandShakerServerMod.getInstance().getConfigManager();
        if (!CommandHelper.isValidMode(mode)) {
            ctx.getSource().sendFailure(Component.literal("Invalid mode. Use: required, blacklisted, or allowed"));
            return 0;
        }
        ModRuleCommandOperations.changeModRulePreserveAction(
            mod,
            mode,
            config.getModConfig(mod),
            config::getDefaultActionForMode,
            config::setModConfigByString
        );
        config.save();
        ctx.getSource().sendSuccess(() -> Component.literal("✓ Changed '" + mod + "' to " + mode).withColor(SUCCESS_GREEN_COLOR), true);
        HandShakerServerMod.getInstance().checkAllPlayers();
        return Command.SINGLE_SUCCESS;
    }

    private static int addAllMods(CommandContext<CommandSourceStack> ctx) {
        String mode = StringArgumentType.getString(ctx, "mode");
        ConfigManager config = HandShakerServerMod.getInstance().getConfigManager();

        if (!(ctx.getSource().getEntity() instanceof ServerPlayer senderPlayer)) {
            ctx.getSource().sendFailure(Component.literal("Only players can use the * wildcard"));
            return 0;
        }
        Set<String> mods = HandShakerServerMod.getInstance().getClientMods(senderPlayer.getUUID());
        if (mods == null || mods.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("No mod list found for you"));
            return 0;
        }

        int added = ModRuleCommandOperations.upsertBulkModRules(
            mods,
            config::isModIgnored,
            mode,
            null,
            config::getDefaultActionForMode,
            config::setModConfigByString,
            token -> registerModFingerprint(config, token)
        );

        config.save();
        ctx.getSource().sendSuccess(() -> Component.literal("✓ Added " + added + " of your mods as " + mode).withColor(SUCCESS_GREEN_COLOR), true);
        HandShakerServerMod.getInstance().checkAllPlayers();
        return Command.SINGLE_SUCCESS;
    }

    private static int addAllModsWithAction(CommandContext<CommandSourceStack> ctx) {
        String mode = StringArgumentType.getString(ctx, "mode");
        String action = StringArgumentType.getString(ctx, "action");
        ConfigManager config = HandShakerServerMod.getInstance().getConfigManager();

        if (!(ctx.getSource().getEntity() instanceof ServerPlayer senderPlayer)) {
            ctx.getSource().sendFailure(Component.literal("Only players can use the * wildcard"));
            return 0;
        }
        Set<String> mods = HandShakerServerMod.getInstance().getClientMods(senderPlayer.getUUID());
        if (mods == null || mods.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("No mod list found for you"));
            return 0;
        }

        int added = ModRuleCommandOperations.upsertBulkModRules(
            mods,
            config::isModIgnored,
            mode,
            action,
            config::getDefaultActionForMode,
            config::setModConfigByString,
            token -> registerModFingerprint(config, token)
        );

        config.save();
        ctx.getSource().sendSuccess(() -> Component.literal("✓ Added " + added + " of your mods as " + mode + " with " + action).withColor(SUCCESS_GREEN_COLOR), true);
        HandShakerServerMod.getInstance().checkAllPlayers();
        return Command.SINGLE_SUCCESS;
    }

    private static int changeModWithAction(CommandContext<CommandSourceStack> ctx) {
        String mod = StringArgumentType.getString(ctx, "mod").toLowerCase();
        String mode = StringArgumentType.getString(ctx, "mode").toLowerCase();
        String action = StringArgumentType.getString(ctx, "action").toLowerCase();
        ConfigManager config = HandShakerServerMod.getInstance().getConfigManager();
        if (!CommandHelper.isValidMode(mode)) {
            ctx.getSource().sendFailure(Component.literal("Invalid mode. Use: required, blacklisted, or allowed"));
            return 0;
        }
        Set<String> availableActions = config.getAvailableActions();
        if (!availableActions.isEmpty() && !availableActions.contains(action)) {
            ctx.getSource().sendFailure(Component.literal("Invalid action. Available: " + String.join(", ", availableActions)));
            return 0;
        }
        ModRuleCommandOperations.upsertModRule(
            mod,
            mode,
            action,
            config::getDefaultActionForMode,
            config::setModConfigByString,
            null
        );
        config.save();
        ctx.getSource().sendSuccess(() -> Component.literal("✓ Changed '" + mod + "' to " + mode + " with " + action).withColor(SUCCESS_GREEN_COLOR), true);
        HandShakerServerMod.getInstance().checkAllPlayers();
        return Command.SINGLE_SUCCESS;
    }


    private static int removeMod(CommandContext<CommandSourceStack> ctx) {
        String mod = StringArgumentType.getString(ctx, "mod").toLowerCase();
        ConfigManager config = HandShakerServerMod.getInstance().getConfigManager();
        boolean removed = ModRuleCommandOperations.removeModRule(mod, config::removeModConfig);
        if (!removed) {
            ctx.getSource().sendFailure(Component.literal("Mod not found: " + mod));
            return 0;
        }
        config.save();
        ctx.getSource().sendSuccess(() -> Component.literal("✓ Removed " + mod).withColor(SUCCESS_GREEN_COLOR), true);
        HandShakerServerMod.getInstance().checkAllPlayers();
        return Command.SINGLE_SUCCESS;
    }

    private static int addIgnore(CommandContext<CommandSourceStack> ctx) {
        String modId;
        try {
            modId = StringArgumentType.getString(ctx, "mod").trim().toLowerCase();
        } catch (IllegalArgumentException e) {
            // mod was a literal("*"), so just use "*"
            modId = "*";
        }

        ConfigManager config = HandShakerServerMod.getInstance().getConfigManager();

        if (modId.equals("*")) {
            if (!(ctx.getSource().getEntity() instanceof ServerPlayer senderPlayer)) {
                ctx.getSource().sendFailure(Component.literal("Only players can use the * wildcard"));
                return 0;
            }
            Set<String> mods = HandShakerServerMod.getInstance().getClientMods(senderPlayer.getUUID());
            if (mods == null || mods.isEmpty()) {
                ctx.getSource().sendFailure(Component.literal("No mod list found for you"));
                return 0;
            }
            int added = IgnoreCommandOperations.addIgnoredMods(mods, config::isModIgnored, config::addIgnoredMod);
            config.save();
            ctx.getSource().sendSuccess(() -> Component.literal("✓ Added " + added + " mods to ignore list").withColor(SUCCESS_GREEN_COLOR), true);
        } else {
            final String finalModId = modId;
            boolean added = IgnoreCommandOperations.addIgnoredMod(modId, config::isModIgnored, config::addIgnoredMod);
            if (added) {
                config.save();
                ctx.getSource().sendSuccess(() -> Component.literal("✓ Added " + finalModId + " to ignore list").withColor(SUCCESS_GREEN_COLOR), true);
            } else {
                ctx.getSource().sendSystemMessage(Component.literal(finalModId + " already in ignore list").withColor(YELLOW));
            }
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int removeIgnore(CommandContext<CommandSourceStack> ctx) {
        String mod = StringArgumentType.getString(ctx, "mod");
        ConfigManager config = HandShakerServerMod.getInstance().getConfigManager();
        boolean removed = IgnoreCommandOperations.removeIgnoredMod(mod, config::removeIgnoredMod);
        if (removed) {
            config.save();
            ctx.getSource().sendSuccess(() -> Component.literal("✓ Removed " + mod + " from ignore list").withColor(SUCCESS_GREEN_COLOR), true);
        } else {
            ctx.getSource().sendSystemMessage(Component.literal(mod + " not in ignore list").withColor(YELLOW));
        }
        return Command.SINGLE_SUCCESS;
    }


    private static int listIgnored(CommandContext<CommandSourceStack> ctx) {
        ConfigManager config = HandShakerServerMod.getInstance().getConfigManager();
        String title = "Ignored Mods";

        ctx.getSource().sendSystemMessage(sectionHeader(title));
        if (config.getIgnoredMods().isEmpty()) {
            ctx.getSource().sendSystemMessage(Component.literal("No mods ignored").withColor(YELLOW));
            ctx.getSource().sendSystemMessage(sectionFooter(title));
            return Command.SINGLE_SUCCESS;
        }

        ctx.getSource().sendSystemMessage(Component.literal("Count: ").withColor(DARK_GRAY)
            .append(Component.literal(String.valueOf(config.getIgnoredMods().size())).withColor(WHITE)));
        ctx.getSource().sendSystemMessage(Component.empty());
        for (String mod : config.getIgnoredMods()) {
            ctx.getSource().sendSystemMessage(Component.literal("  • " + mod).withColor(YELLOW));
        }

        ctx.getSource().sendSystemMessage(sectionFooter(title));
        return Command.SINGLE_SUCCESS;
    }

    private static void registerModFingerprint(ConfigManager config, String modToken) {
        CommandRuntimeOperations.registerModFingerprint(
            modToken,
            HandShakerServerMod.getInstance().getPlayerHistoryDb(),
            config.isHashMods(),
            config.isModVersioning(),
            HandShakerServerMod.getInstance().getClients().values(),
            config.isAsyncDatabaseOperations(),
            HandShakerServerMod.getInstance().getScheduler()
        );
    }

    private static <T> int executeAsyncDatabase(
        CommandContext<CommandSourceStack> ctx,
        java.util.function.Supplier<T> supplier,
        java.util.function.Consumer<T> onSuccess,
        java.util.function.Consumer<Throwable> onFailure
    ) {
        ConfigManager config = HandShakerServerMod.getInstance().getConfigManager();
        return CommandRuntimeOperations.executeAsyncDatabase(
            config.isAsyncDatabaseOperations(),
            HandShakerServerMod.getInstance().getScheduler(),
            run -> ctx.getSource().getServer().execute(run),
            supplier,
            onSuccess,
            onFailure,
            Command.SINGLE_SUCCESS,
            0
        );
    }


    private static void renderModInfo(CommandContext<CommandSourceStack> ctx, InfoCommandOperations.ModInfoResult result) {
        if (!result.success()) {
            ctx.getSource().sendFailure(Component.literal(result.error()));
            return;
        }

        List<PlayerHistoryDatabase.PlayerModInfo> players = result.players();
        if (players.isEmpty()) {
            ctx.getSource().sendSystemMessage(Component.literal("No players found with mod: " + result.displayKey()).withColor(YELLOW));
            return;
        }

        String title = "Mod: " + result.displayKey();
        ctx.getSource().sendSystemMessage(sectionHeader(title));
        ctx.getSource().sendSystemMessage(Component.literal("Players: " + players.size()).withColor(YELLOW));
        ctx.getSource().sendSystemMessage(Component.empty());

        List<CommandHelper.ModInfoRow> rows =
            CommandHelper.buildModInfoRows(players, "✓ Active", "✗ Removed");
        for (CommandHelper.ModInfoRow row : rows) {
            ChatFormatting statusColor = row.active() ? ChatFormatting.GREEN : ChatFormatting.RED;
            ctx.getSource().sendSystemMessage(Component.literal(row.playerName()).withColor(AQUA)
                .append(Component.literal(" - " + row.statusLabel()).withStyle(statusColor))
                .append(Component.literal(" (Since: " + row.firstSeenFormatted() + ")").withColor(GRAY)));
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
            ctx.getSource().sendSystemMessage(Component.literal("No mod history found for: " + playerName).withColor(YELLOW));
            return;
        }
        CommandHelper.Page<PlayerHistoryDatabase.ModHistoryEntry> page = result.page();

        String title = "Mod History";
        ctx.getSource().sendSystemMessage(sectionHeader(title));
        ctx.getSource().sendSystemMessage(Component.literal("player: ").withColor(GRAY)
            .append(Component.literal(playerName).withColor(WHITE))
            .append(Component.literal(" : Page ").withColor(GRAY))
            .append(Component.literal(String.valueOf(page.pageNum())).withColor(WHITE))
            .append(Component.literal(" / ").withColor(GRAY))
            .append(Component.literal(String.valueOf(page.totalPages())).withColor(WHITE)));
        ctx.getSource().sendSystemMessage(Component.empty());

        for (int i = page.startIdx(); i < page.endIdx(); i++) {
            PlayerHistoryDatabase.ModHistoryEntry entry = history.get(i);
            String status = CommandHelper.historyStatusLabel(entry.isActive());
            ChatFormatting statusColor = entry.isActive() ? ChatFormatting.GREEN : ChatFormatting.RED;
            String dates = CommandHelper.historyDates(entry);

            ctx.getSource().sendSystemMessage(Component.literal("- ").withColor(GRAY)
                .append(Component.literal(entry.modName()).withColor(WHITE)));
            ctx.getSource().sendSystemMessage(Component.literal("  Status: ").withColor(DARK_GRAY)
                .append(Component.literal(status).withStyle(statusColor))
                .append(Component.literal(" | ").withColor(DARK_GRAY))
                .append(Component.literal(dates).withColor(GRAY)));
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
            ctx.getSource().sendFailure(Component.literal("Player not found: " + playerName));
            return 0;
        }
        Set<String> mods = HandShakerServerMod.getInstance().getClientMods(player.getUUID());
        ConfigManager config = HandShakerServerMod.getInstance().getConfigManager();
        if (mods == null || mods.isEmpty()) {
            ctx.getSource().sendSystemMessage(Component.literal("No mod list found for " + playerName).withColor(YELLOW));
            return Command.SINGLE_SUCCESS;
        }

        List<ModEntry> parsedMods = mods.stream()
            .filter(mod -> !config.isModIgnored(mod))
            .map(ModEntry::parse)
            .filter(Objects::nonNull)
            .sorted(Comparator.comparing(ModEntry::modId))
            .toList();

        if (parsedMods.isEmpty()) {
            ctx.getSource().sendSystemMessage(Component.literal("No visible mods found for " + playerName).withColor(YELLOW));
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
        ctx.getSource().sendSystemMessage(Component.literal("player: ").withColor(GRAY)
            .append(Component.literal(playerName).withColor(WHITE))
            .append(Component.literal(" : Page ").withColor(GRAY))
            .append(Component.literal(String.valueOf(pageNum)).withColor(WHITE))
            .append(Component.literal(" / ").withColor(GRAY))
            .append(Component.literal(String.valueOf(totalPages)).withColor(WHITE)));
        ctx.getSource().sendSystemMessage(Component.empty());

        for (int i = start; i < end; i++) {
            ModEntry entry = parsedMods.get(i);
            String modId = entry.modId();
            String version = entry.version() != null ? entry.version() : "unknown";
            String displayName = entry.displayName() != null ? entry.displayName() : CommandHelper.prettyModName(modId);

            ctx.getSource().sendSystemMessage(Component.literal("- ").withColor(GRAY)
                .append(Component.literal(displayName).withColor(WHITE)));
            ctx.getSource().sendSystemMessage(Component.literal("  ID: ").withColor(DARK_GRAY)
                .append(Component.literal(modId).withColor(GRAY))
                .append(Component.literal(" | Version: ").withColor(DARK_GRAY))
                .append(Component.literal(version).withColor(GRAY)));
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

    private static int changePlayerMod(CommandContext<CommandSourceStack> ctx) {
        String playerName = StringArgumentType.getString(ctx, "player");
        String modId;
        try {
            modId = StringArgumentType.getString(ctx, "mod").toLowerCase();
        } catch (IllegalArgumentException e) {
            modId = "*";
        }
        String mode = StringArgumentType.getString(ctx, "mode").toLowerCase();
        String action;
        try {
            action = StringArgumentType.getString(ctx, "action").toLowerCase();
        } catch (IllegalArgumentException e) {
            action = null;
        }

        ServerPlayer player = ctx.getSource().getServer().getPlayerList().getPlayerByName(playerName);
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("Player '" + playerName + "' not found"));
            return 0;
        }

        if (!CommandHelper.isValidMode(mode)) {
            ctx.getSource().sendFailure(Component.literal("Invalid mode. Use: required, blacklisted, or allowed"));
            return 0;
        }

        ConfigManager config = HandShakerServerMod.getInstance().getConfigManager();
        Set<String> mods = HandShakerServerMod.getInstance().getClientMods(player.getUUID());
        if (mods == null || mods.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("Player has no mods"));
            return 0;
        }

        final String finalAction = action;
        if (modId.equals("*")) {
            int updated = 0;
            for (String mod : mods) {
                if (!config.isModIgnored(mod)) {
                    ModRuleCommandOperations.upsertModRule(
                        mod, mode, finalAction,
                        config::getDefaultActionForMode,
                        config::setModConfigByString,
                        null
                    );
                    registerModFingerprint(config, mod);
                    updated++;
                }
            }
            final int finalUpdated = updated;
            final String finalMode = mode;
            ctx.getSource().sendSuccess(() -> Component.literal("✓ Set " + finalUpdated + " mods to " + finalMode + " for " + playerName).withColor(SUCCESS_GREEN_COLOR), true);
        } else {
            if (!mods.contains(modId)) {
                ctx.getSource().sendFailure(Component.literal("Player " + playerName + " does not have mod: " + modId));
                return 0;
            }
            final String finalModId = modId;
            final String finalMode = mode;
            ModRuleCommandOperations.upsertModRule(
                modId, mode, finalAction,
                config::getDefaultActionForMode,
                config::setModConfigByString,
                null
            );
            registerModFingerprint(config, modId);
            ctx.getSource().sendSuccess(() -> Component.literal("✓ Set " + finalModId + " to " + finalMode + " for " + playerName).withColor(SUCCESS_GREEN_COLOR), true);
        }

        config.save();
        HandShakerServerMod.getInstance().checkAllPlayers();
        return Command.SINGLE_SUCCESS;
    }

    private static CompletableFuture<Suggestions> suggestPlayerMods(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String playerName = StringArgumentType.getString(ctx, "player");
        ServerPlayer player = ctx.getSource().getServer().getPlayerList().getPlayerByName(playerName);

        if (player == null) {
            return Suggestions.empty();
        }

        Set<String> mods = HandShakerServerMod.getInstance().getClientMods(player.getUUID());
        if (mods == null || mods.isEmpty()) {
            return Suggestions.empty();
        }

        List<String> displays = mods.stream()
            .map(CommandHelper::toDisplayModToken)
            .toList();
        for (String suggestion : CommandSuggestionOperations.autoQuotedSuggestions(builder.getRemaining(), displays)) {
            builder.suggest(suggestion);
        }
        return builder.buildFuture();
    }


    private static MutableComponent sectionHeader(String title) {
        return Component.literal(sectionHeaderText(title)).withColor(DIRTY_GOLD_COLOR).withStyle(ChatFormatting.BOLD);
    }


    private static MutableComponent sectionFooter(String title) {
        return Component.literal(CommandVisualOperations.sectionFooterText(title)).withColor(DIRTY_GOLD_COLOR).withStyle(ChatFormatting.BOLD);
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
            ctx.getSource().sendSystemMessage(Component.literal(CommandVisualOperations.navigateUsageText(fallbackUsage)).withColor(GRAY));
            return;
        }

        MutableComponent prev = hasPrevious
            ? Component.literal(CommandVisualOperations.previousPageLabel()).withStyle(style -> style
                .withColor(ChatFormatting.GREEN)
                .withClickEvent(TextEventCompat.runCommand(previousCommand))
                .withHoverEvent(TextEventCompat.showText(Component.literal(previousCommand))))
            : Component.literal(CommandVisualOperations.previousPageLabel()).withColor(DARK_GRAY);

        MutableComponent next = hasNext
            ? Component.literal(CommandVisualOperations.nextPageLabel()).withStyle(style -> style
                .withColor(ChatFormatting.GREEN)
                .withClickEvent(TextEventCompat.runCommand(nextCommand))
                .withHoverEvent(TextEventCompat.showText(Component.literal(nextCommand))))
            : Component.literal(CommandVisualOperations.nextPageLabel()).withColor(DARK_GRAY);

        ctx.getSource().sendSystemMessage(prev.append(Component.literal(" ")).append(next));
    }


    private static MutableComponent modeTag(String mode) {
        String label = CommandHelper.modeTagLabel(mode);
        if (mode == null) {
            return Component.literal(label).withStyle(ChatFormatting.GRAY);
        }

        ChatFormatting color = switch (mode) {
            case "required" -> ChatFormatting.GOLD;
            case "blacklisted" -> ChatFormatting.RED;
            case "allowed" -> ChatFormatting.GREEN;
            default -> ChatFormatting.GRAY;
        };

        return Component.literal(label).withStyle(color);
    }

    // ===== Suggestion Providers =====


    private static CompletableFuture<Suggestions> suggestConfigParams(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        for (String param : CommandSuggestionOperations.configParamSuggestions(builder.getRemaining())) {
            builder.suggest(param);
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestCompatSubparams(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        for (String sp : CommandSuggestionOperations.compatSubparamSuggestions(builder.getRemaining())) {
            builder.suggest(sp);
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestCompatValues(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        for (String value : CommandSuggestionOperations.filterByPrefix(CommandSuggestionOperations.BOOLEAN_VALUES, builder.getRemaining())) {
            builder.suggest(value);
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestConfigValues(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String param = null;
        try {
            param = StringArgumentType.getString(ctx, "param");
        } catch (IllegalArgumentException ignored) {
        }

        ConfigManager config = HandShakerServerMod.getInstance().getConfigManager();
        for (String value : CommandSuggestionOperations.configValueSuggestions(
            param != null ? param.toLowerCase() : "", config.getAvailableActions())) {
            builder.suggest(value);
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestModes(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        for (String mode : CommandSuggestionOperations.modeSuggestions(builder.getRemaining())) {
            builder.suggest(mode);
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestCurrentModeState(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String listName = null;
        try {
            listName = StringArgumentType.getString(ctx, "ruleList").toLowerCase();
        } catch (IllegalArgumentException ignored) {
        }

        if (listName != null) {
            ConfigManager config = HandShakerServerMod.getInstance().getConfigManager();
            boolean isCurrentlyEnabled = switch (listName) {
                case "mods_required" -> config.areModsRequiredEnabled();
                case "mods_blacklisted" -> config.areModsBlacklistedEnabled();
                case "mods_whitelisted" -> config.areModsWhitelistedEnabled();
                default -> false;
            };
            
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

    private static CompletableFuture<Suggestions> suggestActions(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        ConfigManager config = HandShakerServerMod.getInstance().getConfigManager();
        List<String> actions = CommandSuggestionOperations.actionSuggestions(config.getAvailableActions(), false, true);
        for (String action : CommandSuggestionOperations.filterByPrefix(actions, builder.getRemaining())) {
            builder.suggest(action);
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestModeLists(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        for (String list : CommandSuggestionOperations.modeListSuggestions(builder.getRemaining())) {
            builder.suggest(list);
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestConfiguredMods(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        ConfigManager config = HandShakerServerMod.getInstance().getConfigManager();
        for (String suggestion : CommandSuggestionOperations.autoQuotedSuggestions(
            builder.getRemaining(),
            CommandHelper.sanitizeModSuggestions(config.getModConfigMap().keySet())
        )) {
            builder.suggest(suggestion);
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestIgnoredMods(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        ConfigManager config = HandShakerServerMod.getInstance().getConfigManager();
        for (String suggestion : CommandSuggestionOperations.autoQuotedSuggestions(builder.getRemaining(), config.getIgnoredMods())) {
            builder.suggest(suggestion);
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestMods(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
            Set<String> mods = HandShakerServerMod.getInstance().getClientMods(player.getUUID());
            if (mods != null && !mods.isEmpty()) {
                List<String> displays = mods.stream()
                    .map(CommandHelper::toDisplayModToken)
                    .toList();
                for (String suggestion : CommandSuggestionOperations.autoQuotedSuggestions(builder.getRemaining(), displays)) {
                    builder.suggest(suggestion);
                }
                return builder.buildFuture();
            }
        }

        PlayerHistoryDatabase db = HandShakerServerMod.getInstance().getPlayerHistoryDb();
        if (db == null || !db.isEnabled()) return builder.buildFuture();

        List<String> displays = CommandHelper.sanitizeModSuggestions(db.getModPopularity().keySet()).stream()
            .map(CommandHelper::toDisplayModToken)
            .toList();
        for (String suggestion : CommandSuggestionOperations.autoQuotedSuggestions(builder.getRemaining(), displays)) {
            builder.suggest(suggestion);
        }
        return builder.buildFuture();
    }


    private static CompletableFuture<Suggestions> suggestAllMods(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        PlayerHistoryDatabase db = HandShakerServerMod.getInstance().getPlayerHistoryDb();
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


    private static CompletableFuture<Suggestions> suggestPlayers(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        List<String> playerNames = CommandDataOperations.collectPlayerNames(
            ctx.getSource().getServer().getPlayerList().getPlayers(),
            player -> player.getName().getString()
        );
        return SharedSuggestionProvider.suggest(
            playerNames,
            builder
        );
    }

    private static String resolveHandShakerVersion() {
        return ModList.get()
            .getModContainerById(HandShakerServerMod.MOD_ID)
            .map(container -> container.getModInfo().getVersion().toString())
            .orElseGet(() -> {
                Package pkg = HandShakerCommand.class.getPackage();
                String implVersion = pkg != null ? pkg.getImplementationVersion() : null;
                return implVersion != null ? implVersion : "unknown";
            });
    }
}