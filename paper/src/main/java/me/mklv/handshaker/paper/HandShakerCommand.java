package me.mklv.handshaker.paper;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.*;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.CommandSourceStack;
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

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class HandShakerCommand{
    private final HandShakerPlugin plugin;
    private final ConfigManager config;

    public HandShakerCommand(HandShakerPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
    }

    public static void register(HandShakerPlugin plugin, Commands commands) {
        try {
            HandShakerCommand handler = new HandShakerCommand(plugin);
            
            // Build the main command tree
            var builder = Commands.literal("handshaker")
                    .requires(ctx -> ctx.getSender().hasPermission("handshaker.admin"))
                    .executes(ctx -> handler.showHelp(ctx.getSource()))
                    .then(Commands.literal("reload")
                            .executes(ctx -> handler.reload(ctx.getSource())))
                    .then(handler.buildInfoCommand())
                    .then(handler.buildConfigCommand())
                    .then(handler.buildModeCommand())
                    .then(handler.buildManageCommand());
            
            commands.register(
                    plugin.getPluginMeta(),
                    builder.build(),
                    "Advanced mod management command (Brigadier with autocompletion)",
                    java.util.List.of("hs")
            );
            
            plugin.getLogger().info("✓ Registered HandShaker Brigadier command (/handshaker)");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to register Brigadier command: " + e.getMessage());
            plugin.getLogger().info("Ensure this is called from LifecycleEvents.COMMANDS handler");
            plugin.getLogger().log(java.util.logging.Level.WARNING,
                "Brigadier command registration failed", e);
        }
    }
    // ===== Command Tree Builders =====

    private LiteralArgumentBuilder<CommandSourceStack> buildInfoCommand() {
        return Commands.literal("info")
            .then(Commands.literal("configured_mods")
                .executes(ctx -> showConfiguredMods(ctx.getSource(), 1))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                    .executes(ctx -> showConfiguredMods(ctx.getSource(), ctx.getArgument("page", int.class)))))
            .then(Commands.literal("all_mods")
                .executes(ctx -> showAllMods(ctx.getSource(), 1))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                    .executes(ctx -> showAllMods(ctx.getSource(), ctx.getArgument("page", int.class)))))
            .then(Commands.literal("player")
                .then(Commands.argument("playerName", StringArgumentType.string())
                    .suggests(this::suggestPlayers)
                    .executes(ctx -> showPlayerHistory(ctx.getSource(), ctx.getArgument("playerName", String.class), 1))
                    .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(ctx -> showPlayerHistory(ctx.getSource(), ctx.getArgument("playerName", String.class), ctx.getArgument("page", int.class))))))
            .then(Commands.literal("mod")
                .then(Commands.argument("modName", StringArgumentType.string())
                    .suggests(this::suggestAllMods)
                    .executes(ctx -> showModInfo(ctx.getSource(), getModToken(ctx, "modName")))))
            .then(Commands.literal("diagnostic")
                .executes(ctx -> showDiagnostic(ctx.getSource()))
                .then(Commands.literal("export")
                    .executes(ctx -> exportDiagnosticReport(ctx.getSource()))))
            .then(Commands.literal("export")
                .executes(ctx -> exportSnapshot(ctx.getSource())));
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildConfigCommand() {
        return Commands.literal("config")
            .executes(ctx -> showConfig(ctx.getSource()))
            .then(Commands.argument("param", StringArgumentType.string())
                .suggests(this::suggestConfigParams)
                .then(Commands.argument("value", StringArgumentType.greedyString())
                    .suggests(this::suggestConfigValues)
                    .executes(ctx -> setConfigValue(ctx.getSource(), 
                        ctx.getArgument("param", String.class), 
                        ctx.getArgument("value", String.class)))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildModeCommand() {
        return Commands.literal("mode")
            .then(Commands.argument("list", StringArgumentType.string())
                .suggests(this::suggestModeLists)
                .then(Commands.argument("action", StringArgumentType.string())
                    .suggests(this::suggestCurrentModeState)
                    .executes(ctx -> setMode(ctx.getSource(), ctx.getArgument("list", String.class), ctx.getArgument("action", String.class)))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> buildManageCommand() {
        return Commands.literal("manage")
            .then(Commands.literal("add")
                .then(Commands.literal("*")
                    .then(Commands.argument("mode", StringArgumentType.string())
                        .suggests(this::suggestModes)
                        .executes(ctx -> addAllMods(ctx.getSource(), ctx.getArgument("mode", String.class), null))
                        .then(Commands.argument("action", StringArgumentType.string())
                            .suggests(this::suggestActions)
                            .executes(ctx -> addAllMods(ctx.getSource(), ctx.getArgument("mode", String.class), ctx.getArgument("action", String.class))))))
                .then(Commands.argument("mod", StringArgumentType.string())
                    .suggests(this::suggestMods)
                    .then(Commands.argument("mode", StringArgumentType.string())
                        .suggests(this::suggestModes)
                        .executes(ctx -> addMod(ctx.getSource(), getModToken(ctx, "mod"), ctx.getArgument("mode", String.class), null))
                        .then(Commands.argument("action", StringArgumentType.string())
                            .suggests(this::suggestActions)
                            .executes(ctx -> addMod(ctx.getSource(), getModToken(ctx, "mod"), ctx.getArgument("mode", String.class), ctx.getArgument("action", String.class)))))))
            .then(Commands.literal("change")
                .then(Commands.argument("mod", StringArgumentType.string())
                    .suggests(this::suggestConfiguredMods)
                    .then(Commands.argument("mode", StringArgumentType.string())
                        .suggests(this::suggestModes)
                        .executes(ctx -> changeMod(ctx.getSource(), getModToken(ctx, "mod"), ctx.getArgument("mode", String.class), null))
                        .then(Commands.argument("action", StringArgumentType.string())
                            .suggests(this::suggestActions)
                            .executes(ctx -> changeMod(ctx.getSource(), getModToken(ctx, "mod"), ctx.getArgument("mode", String.class), ctx.getArgument("action", String.class)))))))
            .then(Commands.literal("remove")
                .then(Commands.argument("mod", StringArgumentType.string())
                    .suggests(this::suggestConfiguredMods)
                    .executes(ctx -> removeMod(ctx.getSource(), getModToken(ctx, "mod")))))
            .then(Commands.literal("ignore")
                .then(Commands.literal("add")
                    .then(Commands.literal("*")
                        .executes(ctx -> addIgnoreAll(ctx.getSource())))
                    .then(Commands.argument("mod", StringArgumentType.string())
                        .suggests(this::suggestMods)
                        .executes(ctx -> addIgnore(ctx.getSource(), getModToken(ctx, "mod")))))
                .then(Commands.literal("remove")
                    .then(Commands.argument("mod", StringArgumentType.string())
                        .suggests(this::suggestIgnoredMods)
                        .executes(ctx -> removeIgnore(ctx.getSource(), getModToken(ctx, "mod")))))
                .then(Commands.literal("list")
                    .executes(ctx -> listIgnored(ctx.getSource()))))
            .then(Commands.literal("player")
                .then(Commands.argument("playerName", StringArgumentType.string())
                    .suggests(this::suggestPlayers)
                    .executes(ctx -> showPlayerMods(ctx.getSource(), ctx.getArgument("playerName", String.class)))
                    .then(Commands.literal("page")
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                            .executes(ctx -> showPlayerMods(
                                ctx.getSource(),
                                ctx.getArgument("playerName", String.class),
                                ctx.getArgument("page", int.class)))))
                    .then(Commands.argument("mod", StringArgumentType.string())
                        .suggests(this::suggestPlayerMods)
                        .then(Commands.argument("status", StringArgumentType.string())
                            .suggests(this::suggestModes)
                            .executes(ctx -> changePlayerMod(
                                ctx.getSource(),
                                ctx.getArgument("playerName", String.class),
                                getModToken(ctx, "mod"),
                                ctx.getArgument("status", String.class),
                                null))
                            .then(Commands.argument("action", StringArgumentType.string())
                                .suggests(this::suggestActions)
                                .executes(ctx -> changePlayerMod(
                                    ctx.getSource(),
                                    ctx.getArgument("playerName", String.class),
                                    getModToken(ctx, "mod"),
                                    ctx.getArgument("status", String.class),
                                    ctx.getArgument("action", String.class))))))));
    }

    // ===== Command Handlers =====

    private int showHelp(CommandSourceStack source) {
        String title = "HandShaker v7 Commands";
        source.getSender().sendMessage(sectionHeader(title));
        source.getSender().sendMessage(Component.empty());
        
        // Loop through help sections and render with Paper API
        for (CommandHelper.HelpSection section : CommandHelper.getHelpSections()) {
            source.getSender().sendMessage(Component.text(section.title()).color(NamedTextColor.YELLOW).decorate(TextDecoration.BOLD));
            for (CommandHelper.HelpEntry entry : section.entries()) {
                source.getSender().sendMessage(Component.text(entry.command()).color(NamedTextColor.YELLOW)
                    .append(Component.text(" - " + entry.description()).color(NamedTextColor.GRAY)));
            }
            source.getSender().sendMessage(Component.empty());
        }

        source.getSender().sendMessage(sectionFooter(title));
        
        return 1;
    }

    private int reload(CommandSourceStack source) {
        config.load();
        source.getSender().sendMessage(Component.text("✓ HandShaker config reloaded").color(NamedTextColor.GREEN));
        plugin.checkAllPlayers();
        return 1;
    }


    private int showDiagnostic(CommandSourceStack source) {
        if (!config.isDiagnosticCommandEnabled()) {
            source.getSender().sendMessage(Component.text("Diagnostic command is disabled in config").color(NamedTextColor.RED));
            return 0;
        }

        PlayerHistoryDatabase db = plugin.getPlayerHistoryDb();
        source.getSender().sendMessage(sectionHeader("HandShaker Diagnostic"));
        for (String line : DiagnosticCommand.buildDisplayLines(config, db)) {
            NamedTextColor color = "Database disabled".equals(line) ? NamedTextColor.YELLOW : NamedTextColor.GRAY;
            source.getSender().sendMessage(Component.text(line).color(color));
        }

        source.getSender().sendMessage(Component.text("[ Save diagnostic to exports ]").color(NamedTextColor.GREEN)
            .clickEvent(ClickEvent.runCommand("/handshaker info diagnostic export"))
            .hoverEvent(HoverEvent.showText(Component.text("Save this diagnostic report to exports folder"))));

        source.getSender().sendMessage(sectionFooter("HandShaker Diagnostic"));
        return 1;
    }

    private int exportDiagnosticReport(CommandSourceStack source) {
        if (!config.isDiagnosticCommandEnabled()) {
            source.getSender().sendMessage(Component.text("Diagnostic command is disabled in config").color(NamedTextColor.RED));
            return 0;
        }

        PlayerHistoryDatabase db = plugin.getPlayerHistoryDb();
        Path exportDir = plugin.getDataFolder().toPath().resolve("exports");
        DiagnosticCommand.FileExportResult result = DiagnosticCommand.writeDiagnosticReport(
            config,
            db,
            exportDir,
            System.currentTimeMillis()
        );
        if (!result.success()) {
            source.getSender().sendMessage(Component.text("Diagnostic export failed: " + result.errorMessage()).color(NamedTextColor.RED));
            return 0;
        }
        source.getSender().sendMessage(Component.text("Exported diagnostic: " + result.output().getFileName()).color(NamedTextColor.GREEN));
        return 1;
    }

    private int exportSnapshot(CommandSourceStack source) {
        if (!config.isExportCommandEnabled()) {
            source.getSender().sendMessage(Component.text("Export command is disabled in config").color(NamedTextColor.RED));
            return 0;
        }

        PlayerHistoryDatabase db = plugin.getPlayerHistoryDb();
        if (db == null || !db.isEnabled()) {
            source.getSender().sendMessage(Component.text("Database is not enabled").color(NamedTextColor.RED));
            return 0;
        }
        Path exportDir = plugin.getDataFolder().toPath().resolve("exports");
        DiagnosticCommand.FileExportResult result = DiagnosticCommand.writeSnapshot(
            config,
            db,
            exportDir,
            System.currentTimeMillis()
        );
        if (!result.success()) {
            source.getSender().sendMessage(Component.text("Export failed: " + result.errorMessage()).color(NamedTextColor.RED));
            return 0;
        }
        source.getSender().sendMessage(Component.text("Exported snapshot: " + result.output().getFileName()).color(NamedTextColor.GREEN));
        return 1;
    }

    private int showConfiguredMods(CommandSourceStack source, int pageNum) {
        Map<String, ConfigState.ModConfig> mods = config.getModConfigMap();
        if (mods.isEmpty()) {
            source.getSender().sendMessage(Component.text("No mods configured.").color(NamedTextColor.YELLOW));
            return 1;
        }

        InfoCommandOperations.ConfiguredModsPageResult paged =
            InfoCommandOperations.loadConfiguredModsPage(mods, pageNum, CommandHelper.PAGE_SIZE);
        if (paged.hasInvalidPage()) {
            source.getSender().sendMessage(Component.text("Invalid page. Total pages: " + paged.totalPages()).color(NamedTextColor.RED));
            return 0;
        }

        List<Map.Entry<String, ConfigState.ModConfig>> modList = paged.items();
        CommandHelper.Page<Map.Entry<String, ConfigState.ModConfig>> page = paged.page();

        String title = "Configured Mods";
        source.getSender().sendMessage(sectionHeader(title));
        source.getSender().sendMessage(Component.text("page: ").color(NamedTextColor.DARK_GRAY)
            .append(Component.text(page.pageNum()).color(NamedTextColor.WHITE))
            .append(Component.text(" / ").color(NamedTextColor.DARK_GRAY))
            .append(Component.text(page.totalPages()).color(NamedTextColor.WHITE)));
        source.getSender().sendMessage(Component.empty());
        
        if (!(source.getSender() instanceof Player)) {
            for (int i = page.startIdx(); i < page.endIdx(); i++) {
                Map.Entry<String, ConfigState.ModConfig> entry = modList.get(i);
                ConfigState.ModConfig modCfg = entry.getValue();
                String actionLabel = CommandVisualOperations.actionDisplayLabel(modCfg);
                Component line = Component.text("- ").color(NamedTextColor.GRAY)
                    .append(Component.text(entry.getKey()).color(NamedTextColor.WHITE))
                    .append(Component.text(" ").color(NamedTextColor.GRAY))
                    .append(modeTagComponent(modCfg))
                    .append(Component.text(" | Action: ").color(NamedTextColor.DARK_GRAY))
                    .append(Component.text(actionLabel).color(NamedTextColor.GRAY));
                source.getSender().sendMessage(line);
            }
        } else {
            for (int i = page.startIdx(); i < page.endIdx(); i++) {
                Map.Entry<String, ConfigState.ModConfig> entry = modList.get(i);
                ConfigState.ModConfig modCfg = entry.getValue();
                NamedTextColor statusColor = modCfg.isRequired() ? NamedTextColor.GREEN : modCfg.isBlacklisted() ? NamedTextColor.RED : NamedTextColor.YELLOW;

                String actionLabel = CommandVisualOperations.actionDisplayLabel(modCfg);
                String hoverText = CommandVisualOperations.changeHoverText(modCfg);
                
                Component modComponent = buildModComponent(entry.getKey(), statusColor, hoverText, CommandVisualOperations.manageChangeCommand("/handshakerv3", entry.getKey()));
                Component removeBtn = Component.text(" [✕]")
                    .color(NamedTextColor.RED)
                    .hoverEvent(HoverEvent.showText(Component.text(CommandVisualOperations.removeHoverText())))
                    .clickEvent(ClickEvent.runCommand(CommandVisualOperations.manageRemoveCommand("/handshaker", entry.getKey())));

                Component line = Component.text("- ").color(NamedTextColor.GRAY)
                    .append(modComponent)
                    .append(Component.text(" ").color(NamedTextColor.GRAY))
                    .append(modeTagComponent(modCfg))
                    .append(Component.text(" | Action: ").color(NamedTextColor.DARK_GRAY))
                    .append(Component.text(actionLabel).color(NamedTextColor.GRAY))
                    .append(Component.text(" | ").color(NamedTextColor.DARK_GRAY))
                    .append(removeBtn);
                source.getSender().sendMessage(line);
            }
        }

        if (page.totalPages() > 1) {
            source.getSender().sendMessage(Component.empty());
            sendPaginationNavigation(
                source,
                page.pageNum() > 1,
                "/handshaker info configured_mods " + (page.pageNum() - 1),
                page.pageNum() < page.totalPages(),
                "/handshaker info configured_mods " + (page.pageNum() + 1),
                "/handshaker info configured_mods <page>"
            );
        }
        source.getSender().sendMessage(sectionFooter(title));
        
        return 1;
    }

    private int showAllMods(CommandSourceStack source, int pageNum) {
        PlayerHistoryDatabase db = plugin.getPlayerHistoryDb();
        if (db == null || !db.isEnabled()) {
            source.getSender().sendMessage(Component.text("Player history database not available").color(NamedTextColor.RED));
            return 0;
        }

        return executeAsyncDatabase(
            () -> {
                InfoCommandOperations.PopularityPageResult paged =
                    InfoCommandOperations.loadPopularityPage(db.getModPopularity(), pageNum, CommandHelper.PAGE_SIZE);
                Map<String, String> playerPreviewByMod = new HashMap<>();
                if (source.getSender() instanceof Player && !paged.hasInvalidPage() && paged.page() != null) {
                    List<Map.Entry<String, Integer>> sortedMods = paged.items();
                    CommandHelper.Page<Map.Entry<String, Integer>> page = paged.page();
                    for (int i = page.startIdx(); i < page.endIdx(); i++) {
                        String modToken = sortedMods.get(i).getKey();
                        playerPreviewByMod.put(modToken, buildPlayerPreview(db.getPlayersWithMod(modToken)));
                    }
                }
                return new AllModsAsyncResult(paged, playerPreviewByMod);
            },
            data -> renderAllMods(source, data),
            throwable -> source.getSender().sendMessage(Component.text("Failed to load mod popularity: " + rootMessage(throwable)).color(NamedTextColor.RED))
        );
    }

    private int showModInfo(CommandSourceStack source, String modName) {
        PlayerHistoryDatabase db = plugin.getPlayerHistoryDb();

        return executeAsyncDatabase(
            () -> InfoCommandOperations.loadModInfo(db, modName, true),
            result -> renderModInfo(source, result),
            throwable -> source.getSender().sendMessage(Component.text("Failed to load mod info: " + rootMessage(throwable)).color(NamedTextColor.RED))
        );
    }

    private int showPlayerHistory(CommandSourceStack source, String playerName, int pageNum) {
        PlayerHistoryDatabase db = plugin.getPlayerHistoryDb();
        Player online = plugin.getServer().getPlayerExact(playerName);
        UUID onlineUuid = online != null ? online.getUniqueId() : null;

        return executeAsyncDatabase(
            () -> InfoCommandOperations.loadPlayerHistory(db, playerName, onlineUuid, pageNum, CommandHelper.PAGE_SIZE),
            result -> renderPlayerHistory(source, playerName, result),
            throwable -> source.getSender().sendMessage(Component.text("Failed to load player history: " + rootMessage(throwable)).color(NamedTextColor.RED))
        );
    }

    private int showConfig(CommandSourceStack source) {
        String title = "HandShaker Configuration";
        source.getSender().sendMessage(sectionHeader(title));
        
        String requiredModpackHashes = config.getRequiredModpackHashes().isEmpty() ? "OFF" : String.join(", ", config.getRequiredModpackHashes());
        List<CommandHelper.InfoField> configFields = List.of(
            new CommandHelper.InfoField("Behavior", config.getBehavior().toString()),
            new CommandHelper.InfoField("Integrity Mode", config.getIntegrityMode().toString()),
            new CommandHelper.InfoField("Whitelist Mode", config.isWhitelist() ? "ON" : "OFF"),
            new CommandHelper.InfoField("Bedrock Players", config.isAllowBedrockPlayers() ? "Allowed" : "Blocked"),
            new CommandHelper.InfoField("Player Database", config.isPlayerdbEnabled() ? "Enabled" : "Disabled"),
            new CommandHelper.InfoField("Modpack Hashes", requiredModpackHashes)
        );
        
        for (CommandHelper.InfoField field : configFields) {
            Component line = Component.text(field.label() + ": ").color(NamedTextColor.YELLOW)
                .append(Component.text(field.value()).color(NamedTextColor.WHITE));
            source.getSender().sendMessage(line);
        }
        source.getSender().sendMessage(sectionFooter(title));
        return 1;
    }

    private int setConfigValue(CommandSourceStack source, String param, String value) {
        Set<String> currentPlayerMods = null;
        if (source.getSender() instanceof Player player) {
            ClientInfo info = plugin.getClients().get(player.getUniqueId());
            if (info != null) {
                currentPlayerMods = info.mods();
            }
        }

        ConfigCommandOperations.MutationResult result =
            ConfigCommandOperations.applyConfigValue(config, param, value, currentPlayerMods);

        if (!result.success()) {
            source.getSender().sendMessage(Component.text(result.message()).color(NamedTextColor.RED));
            return 0;
        }

        if (result.shouldSave()) {
            config.save();
        }
        if (result.shouldReloadConfig()) {
            config.load();
        }
        if (result.shouldRecheckPlayers()) {
            plugin.checkAllPlayers();
        }

        source.getSender().sendMessage(Component.text("✓ " + result.message()).color(NamedTextColor.GREEN));
        return 1;
    }

    private int setMode(CommandSourceStack source, String listName, String action) {
        ConfigCommandOperations.MutationResult result = ConfigCommandOperations.applyModeToggle(
            config,
            plugin.getDataFolder().toPath(),
            LoggerAdapter.fromLoaderLogger(plugin.getLogger()),
            listName,
            action
        );

        if (!result.success()) {
            source.getSender().sendMessage(Component.text(result.message()).color(NamedTextColor.RED));
            return 0;
        }

        if (result.shouldSave()) {
            config.save();
        }
        if (result.shouldReloadConfig()) {
            config.load();
        }
        if (result.shouldRecheckPlayers()) {
            plugin.checkAllPlayers();
        }

        source.getSender().sendMessage(Component.text("✓ " + result.message()).color(NamedTextColor.GREEN));
        return 1;
    }

    private int addMod(CommandSourceStack source, String mod, String mode, String action) {
        ModRuleCommandOperations.upsertModRule(
            mod,
            mode,
            action,
            config::getDefaultActionForMode,
            config::setModConfig,
            token -> registerModFingerprint(config, token)
        );
        source.getSender().sendMessage(Component.text("✓ Added mod '" + mod + "' as " + mode.toLowerCase()).color(NamedTextColor.GREEN));
        plugin.checkAllPlayers();
        return 1;
    }

    private int addAllMods(CommandSourceStack source, String mode, String action) {
        if (!(source.getSender() instanceof Player senderPlayer)) {
            source.getSender().sendMessage(Component.text("Only players can use the * wildcard").color(NamedTextColor.RED));
            return 0;
        }

        ClientInfo info = plugin.getClients().get(senderPlayer.getUniqueId());
        Set<String> mods = info != null ? info.mods() : null;
        if (mods == null || mods.isEmpty()) {
            source.getSender().sendMessage(Component.text("No mod list found for you").color(NamedTextColor.YELLOW));
            return 0;
        }

        int added = ModRuleCommandOperations.upsertBulkModRules(
            mods,
            config::isIgnored,
            mode,
            action,
            config::getDefaultActionForMode,
            config::setModConfig,
            token -> registerModFingerprint(config, token)
        );

        config.save();
        source.getSender().sendMessage(Component.text("✓ Added " + added + " of your mods as " + mode.toLowerCase()).color(NamedTextColor.GREEN));
        plugin.checkAllPlayers();
        return 1;
    }

    private int changeMod(CommandSourceStack source, String mod, String mode, String action) {
        ModRuleCommandOperations.upsertModRule(
            mod,
            mode,
            action,
            config::getDefaultActionForMode,
            config::setModConfig,
            null
        );
        source.getSender().sendMessage(Component.text("✓ Changed " + mod + " to " + mode.toLowerCase()).color(NamedTextColor.GREEN));
        plugin.checkAllPlayers();
        return 1;
    }

    private int removeMod(CommandSourceStack source, String mod) {
        boolean removed = ModRuleCommandOperations.removeModRule(mod, config::removeModConfig);
        if (removed) {
            source.getSender().sendMessage(Component.text("✓ Removed " + mod).color(NamedTextColor.GREEN));
        } else {
            source.getSender().sendMessage(Component.text(mod + " not found").color(NamedTextColor.YELLOW));
        }
        plugin.checkAllPlayers();
        return 1;
    }

    private int addIgnore(CommandSourceStack source, String mod) {
        boolean added = IgnoreCommandOperations.addIgnoredMod(mod, config::isIgnored, config::addIgnoredMod);
        if (added) {
            source.getSender().sendMessage(Component.text("✓ Added " + mod + " to ignore list").color(NamedTextColor.GREEN));
        } else {
            source.getSender().sendMessage(Component.text(mod + " already in ignore list").color(NamedTextColor.YELLOW));
        }
        return 1;
    }

    private int addIgnoreAll(CommandSourceStack source) {
        if (!(source.getSender() instanceof Player senderPlayer)) {
            source.getSender().sendMessage(Component.text("Only players can use the * wildcard").color(NamedTextColor.RED));
            return 0;
        }

        ClientInfo info = plugin.getClients().get(senderPlayer.getUniqueId());
        Set<String> mods = info != null ? info.mods() : null;
        if (mods == null || mods.isEmpty()) {
            source.getSender().sendMessage(Component.text("No mod list found for you").color(NamedTextColor.YELLOW));
            return 0;
        }

        int added = IgnoreCommandOperations.addIgnoredMods(mods, config::isIgnored, config::addIgnoredMod);
        source.getSender().sendMessage(Component.text("✓ Added " + added + " mods to ignore list").color(NamedTextColor.GREEN));
        return 1;
    }

    private int removeIgnore(CommandSourceStack source, String mod) {
        boolean removed = IgnoreCommandOperations.removeIgnoredMod(mod, config::removeIgnoredMod);
        if (removed) {
            source.getSender().sendMessage(Component.text("✓ Removed " + mod + " from ignore list").color(NamedTextColor.GREEN));
        } else {
            source.getSender().sendMessage(Component.text(mod + " not in ignore list").color(NamedTextColor.YELLOW));
        }
        return 1;
    }

    private int listIgnored(CommandSourceStack source) {
        Set<String> ignored = config.getIgnoredMods();
        if (ignored.isEmpty()) {
            source.getSender().sendMessage(Component.text("No mods ignored").color(NamedTextColor.YELLOW));
            return 1;
        }

        source.getSender().sendMessage(Component.text("Ignored Mods (" + ignored.size() + ")").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
        source.getSender().sendMessage(Component.empty());
        
        for (String mod : ignored) {
            source.getSender().sendMessage(Component.text("  • " + mod).color(NamedTextColor.YELLOW));
        }
        return 1;
    }

    private int showPlayerMods(CommandSourceStack source, String playerName) {
        return showPlayerMods(source, playerName, 1);
    }

    private int showPlayerMods(CommandSourceStack source, String playerName, int pageNum) {
        Player target = plugin.getServer().getPlayerExact(playerName);
        if (target == null) {
            source.getSender().sendMessage(Component.text("Player '" + playerName + "' not found").color(NamedTextColor.RED));
            return 0;
        }

        ClientInfo info = plugin.getClients().get(target.getUniqueId());
        Set<String> mods = info != null ? info.mods() : null;
        if (mods == null || mods.isEmpty()) {
            source.getSender().sendMessage(Component.text("No mod list found for " + target.getName()).color(NamedTextColor.YELLOW));
            return 0;
        }

        List<ModEntry> parsedMods = mods.stream()
            .filter(mod -> !config.isIgnored(mod))
            .map(ModEntry::parse)
            .filter(Objects::nonNull)
            .sorted(Comparator.comparing(ModEntry::modId))
            .collect(Collectors.toList());

        if (parsedMods.isEmpty()) {
            source.getSender().sendMessage(Component.text("No visible mods found for " + target.getName()).color(NamedTextColor.YELLOW));
            return 1;
        }

        int totalMods = parsedMods.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalMods / CommandHelper.PAGE_SIZE));
        if (pageNum < 1 || pageNum > totalPages) {
            source.getSender().sendMessage(Component.text("Invalid page. Total pages: " + totalPages).color(NamedTextColor.RED));
            return 0;
        }

        int start = (pageNum - 1) * CommandHelper.PAGE_SIZE;
        int end = Math.min(start + CommandHelper.PAGE_SIZE, totalMods);

        String title = totalMods + " Mods";
        source.getSender().sendMessage(sectionHeader(title));
        source.getSender().sendMessage(Component.text("player: ").color(NamedTextColor.DARK_GRAY)
            .append(Component.text(target.getName()).color(NamedTextColor.WHITE))
            .append(Component.text(" : Page ").color(NamedTextColor.DARK_GRAY))
            .append(Component.text(pageNum).color(NamedTextColor.WHITE))
            .append(Component.text(" / ").color(NamedTextColor.DARK_GRAY))
            .append(Component.text(totalPages).color(NamedTextColor.WHITE)));
        source.getSender().sendMessage(Component.empty());

        for (int i = start; i < end; i++) {
            ModEntry entry = parsedMods.get(i);
            String modId = entry.modId();
            String version = entry.version() != null ? entry.version() : "unknown";
            String displayName = entry.displayName() != null ? entry.displayName() : CommandHelper.prettyModName(modId);
            source.getSender().sendMessage(Component.text("- ").color(NamedTextColor.GRAY)
                .append(Component.text(displayName).color(NamedTextColor.WHITE)));
            source.getSender().sendMessage(Component.text("  ID: ").color(NamedTextColor.DARK_GRAY)
                .append(Component.text(modId).color(NamedTextColor.GRAY))
                .append(Component.text(" | Version: ").color(NamedTextColor.DARK_GRAY))
                .append(Component.text(version).color(NamedTextColor.GRAY)));
        }

        if (totalPages > 1) {
            source.getSender().sendMessage(Component.empty());
            String quotedPlayer = CommandVisualOperations.quoted(target.getName());
            sendPaginationNavigation(
                source,
                pageNum > 1,
                "/handshakermanage player " + quotedPlayer + " page " + (pageNum - 1),
                pageNum < totalPages,
                "/handshakermanage player " + quotedPlayer + " page " + (pageNum + 1),
                "/handshakermanage player \"" + target.getName() + "\" page <number>"
            );
        }

        source.getSender().sendMessage(sectionFooter(title));

        return 1;
    }

    private int changePlayerMod(CommandSourceStack source, String playerName, String mod, String status, String action) {
        Player target = plugin.getServer().getPlayerExact(playerName);
        if (target == null) {
            source.getSender().sendMessage(Component.text("Player '" + playerName + "' not found").color(NamedTextColor.RED));
            return 0;
        }

        ClientInfo info = plugin.getClients().get(target.getUniqueId());
        Set<String> mods = info != null ? info.mods() : null;
        if (mods == null || mods.isEmpty()) {
            source.getSender().sendMessage(Component.text("No mod list found for " + target.getName()).color(NamedTextColor.YELLOW));
            return 0;
        }

        // Check if player has this mod (with sanitization)
        boolean hasMod = false;
        for (String playerMod : mods) {
            ModEntry entry = ModEntry.parse(playerMod);
            if (entry != null && entry.toDisplayKey().equalsIgnoreCase(mod)) {
                hasMod = true;
                break;
            }
        }

        if (!hasMod) {
            source.getSender().sendMessage(Component.text("Player " + target.getName() + " does not have mod: " + mod).color(NamedTextColor.RED));
            return 0;
        }

        String resolvedAction = action != null ? action : config.getDefaultActionForMode(status);
        config.setModConfig(mod, status, resolvedAction, null);
        config.save();
        source.getSender().sendMessage(Component.text("✓ Set " + mod + " to " + status.toLowerCase()).color(NamedTextColor.GREEN));
        plugin.checkAllPlayers();
        return 1;
    }

    // ===== Suggestion Providers =====

    private CompletableFuture<Suggestions> suggestModes(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        for (String mode : CommandSuggestionOperations.modeSuggestions(builder.getRemaining())) {
            builder.suggest(mode);
        }
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestActions(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        List<String> actions = CommandSuggestionOperations.actionSuggestions(config.getAvailableActions(), false, true);
        for (String action : CommandSuggestionOperations.filterByPrefix(actions, builder.getRemaining())) {
            builder.suggest(action);
        }
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestMods(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        // Suggest from sender's own mods if player, else from database
        if (ctx.getSource().getSender() instanceof Player player) {
            ClientInfo info = plugin.getClients().get(player.getUniqueId());
            Set<String> clientMods = info != null ? info.mods() : null;
            if (clientMods != null) {
                List<String> displays = CommandHelper.sanitizeModSuggestions(clientMods).stream()
                    .map(CommandHelper::toDisplayModToken)
                    .toList();
                for (String suggestion : CommandSuggestionOperations.autoQuotedSuggestions(builder.getRemaining(), displays)) {
                    builder.suggest(suggestion);
                }
                return builder.buildFuture();
            }
        }
        
        PlayerHistoryDatabase db = plugin.getPlayerHistoryDb();
        if (db == null) return builder.buildFuture();
        
        List<String> displays = CommandHelper.sanitizeModSuggestions(db.getModPopularity().keySet()).stream()
            .map(CommandHelper::toDisplayModToken)
            .toList();
        for (String suggestion : CommandSuggestionOperations.autoQuotedSuggestions(builder.getRemaining(), displays)) {
            builder.suggest(suggestion);
        }
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestConfiguredMods(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        for (String suggestion : CommandSuggestionOperations.autoQuotedSuggestions(builder.getRemaining(), config.getModConfigMap().keySet())) {
            builder.suggest(suggestion);
        }
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestAllMods(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        PlayerHistoryDatabase db = plugin.getPlayerHistoryDb();
        if (db == null) return builder.buildFuture();
        
        List<String> displays = CommandHelper.sanitizeModSuggestions(db.getModPopularity().keySet()).stream()
            .map(CommandHelper::toDisplayModToken)
            .toList();
        for (String suggestion : CommandSuggestionOperations.autoQuotedSuggestions(builder.getRemaining(), displays)) {
            builder.suggest(suggestion);
        }
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestPlayers(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        List<String> playerNames = CommandDataOperations.collectPlayerNames(
            plugin.getServer().getOnlinePlayers(),
            Player::getName
        );
        for (String name : CommandSuggestionOperations.filterByPrefix(playerNames, builder.getRemaining())) {
            builder.suggest(name);
        }
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestIgnoredMods(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        for (String suggestion : CommandSuggestionOperations.autoQuotedSuggestions(builder.getRemaining(), config.getIgnoredMods())) {
            builder.suggest(suggestion);
        }
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestModeLists(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        for (String list : CommandSuggestionOperations.modeListSuggestions(builder.getRemaining())) {
            builder.suggest(list);
        }
        return builder.buildFuture();
    }


    private CompletableFuture<Suggestions> suggestConfigParams(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        for (String param : CommandSuggestionOperations.configParamSuggestions(builder.getRemaining())) {
            builder.suggest(param);
        }
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestConfigValues(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String param = null;
        try {
            param = ctx.getArgument("param", String.class);
        } catch (IllegalArgumentException ignored) {
            param = extractConfigParamFromInput(builder);
        }

        List<String> suggestions = CommandSuggestionOperations.configValueSuggestions(
            param != null ? param.toLowerCase(Locale.ROOT) : "",
            config.getAvailableActions()
        );

        for (String value : CommandSuggestionOperations.filterByPrefix(suggestions, builder.getRemaining())) {
            builder.suggest(value);
        }
        return builder.buildFuture();
    }

    private String extractConfigParamFromInput(SuggestionsBuilder builder) {
        String input = builder.getInput();
        if (input == null || input.isBlank()) {
            return null;
        }

        int valueStart = Math.max(0, Math.min(builder.getStart(), input.length()));
        String beforeValue = input.substring(0, valueStart).trim();
        if (beforeValue.isEmpty()) {
            return null;
        }

        String[] parts = beforeValue.split("\\s+");
        if (parts.length < 3) {
            return null;
        }

        if (!"config".equalsIgnoreCase(parts[parts.length - 2])) {
            return parts[parts.length - 1];
        }

        return parts[parts.length - 1];
    }

    private CompletableFuture<Suggestions> suggestPlayerMods(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        try {
            String playerName = ctx.getArgument("playerName", String.class);
            Player target = plugin.getServer().getPlayerExact(playerName);
            if (target != null) {
                ClientInfo info = plugin.getClients().get(target.getUniqueId());
                Set<String> clientMods = info != null ? info.mods() : null;
                if (clientMods != null) {
                    List<String> displays = CommandHelper.sanitizeModSuggestions(clientMods).stream()
                        .map(CommandHelper::toDisplayModToken)
                        .toList();
                    for (String suggestion : CommandSuggestionOperations.autoQuotedSuggestions(builder.getRemaining(), displays)) {
                        builder.suggest(suggestion);
                    }
                }
            }
        } catch (IllegalArgumentException e) {
            // playerName argument not yet filled or parsing context error - silently ignore
        }
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestCurrentModeState(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        try {
            String listName = ctx.getArgument("list", String.class).toLowerCase(Locale.ROOT);
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
        } catch (IllegalArgumentException ignored) {
            for (String state : CommandSuggestionOperations.modeStateSuggestions(null)) {
                builder.suggest(state);
            }
        }
        return builder.buildFuture();
    }


    // ===== Helpers =====

    private void registerModFingerprint(ConfigManager config, String modToken) {
        CommandRuntimeOperations.registerModFingerprint(
            modToken,
            plugin.getPlayerHistoryDb(),
            config.isHashMods(),
            config.isModVersioning(),
            plugin.getClients().values(),
            config.isAsyncDatabaseOperations(),
            command -> plugin.getServer().getAsyncScheduler().runNow(plugin, scheduledTask -> command.run())
        );
    }

    private <T> int executeAsyncDatabase(
        java.util.function.Supplier<T> supplier,
        java.util.function.Consumer<T> onSuccess,
        java.util.function.Consumer<Throwable> onFailure
    ) {
        return CommandRuntimeOperations.executeAsyncDatabase(
            config.isAsyncDatabaseOperations(),
            command -> plugin.getServer().getAsyncScheduler().runNow(plugin, task -> command.run()),
            run -> plugin.getServer().getGlobalRegionScheduler().run(plugin, task -> run.run()),
            supplier,
            onSuccess,
            onFailure,
            1,
            0
        );
    }

    private void renderAllMods(CommandSourceStack source, AllModsAsyncResult data) {
        InfoCommandOperations.PopularityPageResult paged = data.paged();
        if (paged.hasInvalidPage()) {
            source.getSender().sendMessage(Component.text("Invalid page. Total pages: " + paged.totalPages()).color(NamedTextColor.RED));
            return;
        }

        List<Map.Entry<String, Integer>> sortedMods = paged.items();
        CommandHelper.Page<Map.Entry<String, Integer>> page = paged.page();

        String title = "All Mods";
        source.getSender().sendMessage(sectionHeader(title));
        source.getSender().sendMessage(Component.text("page: ").color(NamedTextColor.DARK_GRAY)
            .append(Component.text(page.pageNum()).color(NamedTextColor.WHITE))
            .append(Component.text(" / ").color(NamedTextColor.DARK_GRAY))
            .append(Component.text(page.totalPages()).color(NamedTextColor.WHITE)));
        source.getSender().sendMessage(Component.empty());

        if (!(source.getSender() instanceof Player player)) {
            for (int i = page.startIdx(); i < page.endIdx(); i++) {
                Map.Entry<String, Integer> entry = sortedMods.get(i);
                String modToken = entry.getKey();
                int count = entry.getValue();
                if (config.isIgnored(modToken)) continue;

                ModEntry parsed = ModEntry.parse(modToken);
                String modId = parsed != null ? parsed.modId() : modToken;
                String version = parsed != null && parsed.version() != null ? parsed.version() : "unknown";
                String displayName = parsed != null && parsed.displayName() != null ? parsed.displayName() : CommandHelper.prettyModName(modId);

                ConfigState.ModConfig modCfg = config.getModConfig(modToken);
                source.getSender().sendMessage(modeTagComponent(modCfg)
                    .append(Component.text(" ").color(NamedTextColor.GRAY))
                    .append(Component.text(displayName).color(NamedTextColor.WHITE))
                    .append(Component.text(" x" + count).color(NamedTextColor.GRAY)));
                source.getSender().sendMessage(Component.text("  ID: ").color(NamedTextColor.DARK_GRAY)
                    .append(Component.text(modId).color(NamedTextColor.GRAY))
                    .append(Component.text(" | Version: ").color(NamedTextColor.DARK_GRAY))
                    .append(Component.text(version).color(NamedTextColor.GRAY)));
            }
        } else {
            for (int i = page.startIdx(); i < page.endIdx(); i++) {
                Map.Entry<String, Integer> entry = sortedMods.get(i);
                String modToken = entry.getKey();
                int count = entry.getValue();
                if (config.isIgnored(modToken)) continue;

                ModEntry parsed = ModEntry.parse(modToken);
                String modId = parsed != null ? parsed.modId() : modToken;
                String version = parsed != null && parsed.version() != null ? parsed.version() : "unknown";
                String displayName = parsed != null && parsed.displayName() != null ? parsed.displayName() : CommandHelper.prettyModName(modId);

                ConfigState.ModConfig modCfg = config.getModConfig(modToken);
                NamedTextColor statusColor = modCfg != null && modCfg.isRequired() ? NamedTextColor.GREEN
                    : modCfg != null && modCfg.isBlacklisted() ? NamedTextColor.RED
                    : modCfg != null ? NamedTextColor.YELLOW : NamedTextColor.GRAY;

                String hoverText = modCfg != null ? modCfg.getMode() + "\n\nClick to change mode" : "Detected mod\n\nClick to change mode";
                String preview = data.playerPreviewByMod().get(modToken);
                if (preview != null && !preview.isBlank()) {
                    hoverText = hoverText + "\n\nActive on: " + preview;
                }

                Component modComponent = buildModComponent(displayName, statusColor, hoverText, CommandVisualOperations.infoModCommand("/handshakerv3", modToken));
                player.sendMessage(modeTagComponent(modCfg)
                    .append(Component.text(" ").color(NamedTextColor.GRAY))
                    .append(modComponent)
                    .append(Component.text(" x" + count).color(NamedTextColor.GRAY)));
                player.sendMessage(Component.text("  ID: ").color(NamedTextColor.DARK_GRAY)
                    .append(Component.text(modId).color(NamedTextColor.GRAY))
                    .append(Component.text(" | Version: ").color(NamedTextColor.DARK_GRAY))
                    .append(Component.text(version).color(NamedTextColor.GRAY)));
            }
        }

        if (page.totalPages() > 1) {
            source.getSender().sendMessage(Component.empty());
            sendPaginationNavigation(
                source,
                page.pageNum() > 1,
                "/handshaker info all_mods " + (page.pageNum() - 1),
                page.pageNum() < page.totalPages(),
                "/handshaker info all_mods " + (page.pageNum() + 1),
                "/handshaker info all_mods <page>"
            );
        }
        source.getSender().sendMessage(sectionFooter(title));
    }

    private void renderModInfo(CommandSourceStack source, InfoCommandOperations.ModInfoResult result) {
        if (!result.success()) {
            source.getSender().sendMessage(Component.text(result.error()).color(NamedTextColor.RED));
            return;
        }
        List<PlayerHistoryDatabase.PlayerModInfo> players = result.players();

        if (players.isEmpty()) {
            source.getSender().sendMessage(Component.text("No players found with mod: " + result.displayKey()).color(NamedTextColor.YELLOW));
            return;
        }

        String title = "Mod: " + result.displayKey();
        source.getSender().sendMessage(sectionHeader(title));
        source.getSender().sendMessage(Component.text("Players: " + players.size()).color(NamedTextColor.YELLOW));
        source.getSender().sendMessage(Component.empty());

        if (!(source.getSender() instanceof Player)) {
            List<CommandHelper.ModInfoRow> rows =
                CommandHelper.buildModInfoRows(players, "Active", "Removed");
            for (CommandHelper.ModInfoRow row : rows) {
                source.getSender().sendMessage(Component.text("- ").color(NamedTextColor.GRAY)
                    .append(Component.text(row.playerName()).color(NamedTextColor.WHITE)));
                source.getSender().sendMessage(Component.text("  Status: ").color(NamedTextColor.DARK_GRAY)
                    .append(Component.text(row.statusLabel()).color(
                        row.statusLabel().equals("Active") ? NamedTextColor.GREEN : NamedTextColor.RED))
                    .append(Component.text(" | Since: ").color(NamedTextColor.DARK_GRAY))
                    .append(Component.text(row.firstSeenFormatted()).color(NamedTextColor.GRAY)));
            }
        } else {
            for (PlayerHistoryDatabase.PlayerModInfo playerInfo : players) {
                source.getSender().sendMessage(Component.text("- ").color(NamedTextColor.GRAY)
                    .append(Component.text(playerInfo.currentName()).color(NamedTextColor.WHITE)));
                source.getSender().sendMessage(Component.text("  Status: ").color(NamedTextColor.DARK_GRAY)
                    .append(Component.text(playerInfo.isActive() ? "Active" : "Removed")
                        .color(playerInfo.isActive() ? NamedTextColor.GREEN : NamedTextColor.RED))
                    .append(Component.text(" | Since: ").color(NamedTextColor.DARK_GRAY))
                    .append(Component.text(playerInfo.getFirstSeenFormatted()).color(NamedTextColor.GRAY)));
            }
        }
        source.getSender().sendMessage(sectionFooter(title));
    }

    private void renderPlayerHistory(CommandSourceStack source, String playerName, InfoCommandOperations.PlayerHistoryResult result) {
        if (!result.success()) {
            source.getSender().sendMessage(Component.text(result.error()).color(NamedTextColor.RED));
            return;
        }

        List<PlayerHistoryDatabase.ModHistoryEntry> history = result.history();
        if (history.isEmpty()) {
            source.getSender().sendMessage(Component.text("No mod history found for: " + playerName).color(NamedTextColor.YELLOW));
            return;
        }
        CommandHelper.Page<PlayerHistoryDatabase.ModHistoryEntry> page = result.page();

        String title = "Mod History";
        source.getSender().sendMessage(sectionHeader(title));
        source.getSender().sendMessage(Component.text("player: ").color(NamedTextColor.DARK_GRAY)
            .append(Component.text(playerName).color(NamedTextColor.WHITE))
            .append(Component.text(" : Page ").color(NamedTextColor.DARK_GRAY))
            .append(Component.text(page.pageNum()).color(NamedTextColor.WHITE))
            .append(Component.text(" / ").color(NamedTextColor.DARK_GRAY))
            .append(Component.text(page.totalPages()).color(NamedTextColor.WHITE)));
        source.getSender().sendMessage(Component.empty());

        for (int i = page.startIdx(); i < page.endIdx(); i++) {
            PlayerHistoryDatabase.ModHistoryEntry entry = history.get(i);
            String status = entry.isActive() ? "ACTIVE" : "REMOVED";
            NamedTextColor statusColor = entry.isActive() ? NamedTextColor.GREEN : NamedTextColor.RED;
            source.getSender().sendMessage(Component.text("- ").color(NamedTextColor.GRAY)
                .append(Component.text(entry.modName()).color(NamedTextColor.WHITE)));
            source.getSender().sendMessage(Component.text("  Status: ").color(NamedTextColor.DARK_GRAY)
                .append(Component.text(status).color(statusColor))
                .append(Component.text(" | Added: ").color(NamedTextColor.DARK_GRAY))
                .append(Component.text(entry.getAddedDateFormatted()).color(NamedTextColor.GRAY)));
        }

        if (page.totalPages() > 1) {
            source.getSender().sendMessage(Component.empty());
            String quotedPlayer = CommandVisualOperations.quoted(playerName);
            sendPaginationNavigation(
                source,
                page.pageNum() > 1,
                "/handshaker info player " + quotedPlayer + " " + (page.pageNum() - 1),
                page.pageNum() < page.totalPages(),
                "/handshaker info player " + quotedPlayer + " " + (page.pageNum() + 1),
                "/handshaker info player \"" + playerName + "\" <page>"
            );
        }
        source.getSender().sendMessage(sectionFooter(title));
    }

    private String buildPlayerPreview(List<PlayerHistoryDatabase.PlayerModInfo> playerList) {
        if (playerList == null || playerList.isEmpty()) {
            return "";
        }
        StringBuilder playerNames = new StringBuilder();
        int shown = 0;
        for (PlayerHistoryDatabase.PlayerModInfo playerInfo : playerList) {
            if (shown >= 3) {
                playerNames.append(", ...");
                break;
            }
            if (playerNames.length() > 0) {
                playerNames.append(", ");
            }
            playerNames.append(playerInfo.currentName());
            shown++;
        }
        return playerNames.toString();
    }

    private String rootMessage(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage() != null ? root.getMessage() : root.getClass().getSimpleName();
    }

    private record AllModsAsyncResult(
        InfoCommandOperations.PopularityPageResult paged,
        Map<String, String> playerPreviewByMod
    ) {
    }

    private static String getModToken(CommandContext<CommandSourceStack> ctx, String argumentName) {
        return ctx.getArgument(argumentName, String.class);
    }

    private Component sectionHeader(String title) {
        return Component.text(CommandVisualOperations.sectionHeaderText(title))
            .color(NamedTextColor.GOLD)
            .decorate(TextDecoration.BOLD);
    }

    private Component sectionFooter(String title) {
        return Component.text(CommandVisualOperations.sectionFooterText(title))
            .color(NamedTextColor.GOLD)
            .decorate(TextDecoration.BOLD);
    }

    private void sendPaginationNavigation(
        CommandSourceStack source,
        boolean hasPrevious,
        String previousCommand,
        boolean hasNext,
        String nextCommand,
        String fallbackUsage
    ) {
        if (!(source.getSender() instanceof Player)) {
            source.getSender().sendMessage(Component.text(CommandVisualOperations.navigateUsageText(fallbackUsage)).color(NamedTextColor.DARK_GRAY));
            return;
        }

        Component nav = Component.empty();
        if (hasPrevious) {
            nav = nav.append(Component.text(CommandVisualOperations.previousPageLabel()).color(NamedTextColor.GREEN)
                .clickEvent(ClickEvent.runCommand(previousCommand))
                .hoverEvent(HoverEvent.showText(Component.text(previousCommand))));
        } else {
            nav = nav.append(Component.text(CommandVisualOperations.previousPageLabel()).color(NamedTextColor.DARK_GRAY));
        }

        nav = nav.append(Component.text(" "));

        if (hasNext) {
            nav = nav.append(Component.text(CommandVisualOperations.nextPageLabel()).color(NamedTextColor.GREEN)
                .clickEvent(ClickEvent.runCommand(nextCommand))
                .hoverEvent(HoverEvent.showText(Component.text(nextCommand))));
        } else {
            nav = nav.append(Component.text(CommandVisualOperations.nextPageLabel()).color(NamedTextColor.DARK_GRAY));
        }

        source.getSender().sendMessage(nav);
    }

    private Component modeTagComponent(ConfigState.ModConfig modCfg) {
        String mode = modCfg != null ? modCfg.getMode() : null;
        String label = CommandHelper.modeTagLabel(mode);
        if (mode == null) {
            return Component.text(label).color(NamedTextColor.DARK_GRAY);
        }

        NamedTextColor color = switch (mode) {
            case "required" -> NamedTextColor.GOLD;
            case "blacklisted" -> NamedTextColor.RED;
            case "allowed" -> NamedTextColor.GREEN;
            default -> NamedTextColor.DARK_GRAY;
        };

        return Component.text(label).color(color);
    }

    private Component buildModComponent(String modName, NamedTextColor statusColor, String hoverText, String changeCommand) {
        return Component.text(modName)
            .color(statusColor)
            .hoverEvent(HoverEvent.showText(Component.text(hoverText)))
            .clickEvent(ClickEvent.suggestCommand(changeCommand));
    }

}
