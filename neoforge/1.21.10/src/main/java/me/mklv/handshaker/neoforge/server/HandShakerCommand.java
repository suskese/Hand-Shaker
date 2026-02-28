package me.mklv.handshaker.neoforge.server;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import me.mklv.handshaker.common.commands.CommandSuggestionData;
import me.mklv.handshaker.common.commands.CommandSuggestionOperations;
import me.mklv.handshaker.common.commands.CommandHelper;
import me.mklv.handshaker.common.commands.CommandVisualOperations;
import me.mklv.handshaker.common.commands.ModFingerprintRegistrar;
import me.mklv.handshaker.common.commands.ModListToggler;
import me.mklv.handshaker.common.database.PlayerHistoryDatabase;
import me.mklv.handshaker.common.configs.ConfigTypes.ConfigState;
import me.mklv.handshaker.common.configs.ConfigTypes.ModEntry;
import me.mklv.handshaker.common.utils.LoggerAdapter;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.ChatFormatting;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@SuppressWarnings("null")
public class HandShakerCommand {
    private static final int PAGE_SIZE = 10;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var handshaker = Commands.literal("handshaker")
            .requires(source -> source.hasPermission(4))
            .executes(HandShakerCommand::showHelp)
            // Core Commands
            .then(Commands.literal("reload")
                .executes(HandShakerCommand::reload))
            .then(Commands.literal("info")
                .executes(HandShakerCommand::showInfo)
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
                        .suggests(HandShakerCommand::suggestMods)
                        .executes(HandShakerCommand::showModInfo))))
            .then(Commands.literal("config")
                .executes(HandShakerCommand::showConfig)
                .then(Commands.literal("behavior")
                    .then(Commands.argument("value", StringArgumentType.word())
                        .suggests((ctx, builder) -> builder.suggest("STRICT").suggest("VANILLA").buildFuture())
                        .executes(ctx -> setConfigValue(ctx, "behavior"))))
                .then(Commands.literal("integrity")
                    .then(Commands.argument("value", StringArgumentType.word())
                        .suggests((ctx, builder) -> builder.suggest("SIGNED").suggest("DEV").buildFuture())
                        .executes(ctx -> setConfigValue(ctx, "integrity"))))
                .then(Commands.literal("whitelist")
                    .then(Commands.argument("value", StringArgumentType.word())
                        .suggests((ctx, builder) -> builder.suggest("true").suggest("false").buildFuture())
                        .executes(ctx -> setConfigValue(ctx, "whitelist"))))
                .then(Commands.literal("allow_bedrock")
                    .then(Commands.argument("value", StringArgumentType.word())
                        .suggests((ctx, builder) -> builder.suggest("true").suggest("false").buildFuture())
                    .executes(ctx -> setConfigValue(ctx, "allow_bedrock"))))
                .then(Commands.literal("playerdb_enabled")
                    .then(Commands.argument("value", StringArgumentType.word())
                        .suggests((ctx, builder) -> builder.suggest("true").suggest("false").buildFuture())
                        .executes(ctx -> setConfigValue(ctx, "playerdb_enabled"))))
                .then(Commands.literal("hash_mods")
                    .then(Commands.argument("value", StringArgumentType.word())
                        .suggests((ctx, builder) -> builder.suggest("true").suggest("false").buildFuture())
                        .executes(ctx -> setConfigValue(ctx, "hash_mods"))))
                .then(Commands.literal("mod_versioning")
                    .then(Commands.argument("value", StringArgumentType.word())
                        .suggests((ctx, builder) -> builder.suggest("true").suggest("false").buildFuture())
                        .executes(ctx -> setConfigValue(ctx, "mod_versioning"))))
                .then(Commands.literal("runtime_cache")
                    .then(Commands.argument("value", StringArgumentType.word())
                        .suggests((ctx, builder) -> builder.suggest("true").suggest("false").buildFuture())
                        .executes(ctx -> setConfigValue(ctx, "runtime_cache"))))
                .then(Commands.literal("required_modpack_hash")
                    .then(Commands.argument("value", StringArgumentType.word())
                        .suggests((ctx, builder) -> builder.suggest("off").suggest("current").buildFuture())
                        .executes(ctx -> setConfigValue(ctx, "required_modpack_hash")))))
            .then(Commands.literal("mode")
                .then(Commands.argument("list", StringArgumentType.word())
                    .suggests(HandShakerCommand::suggestModeLists)
                    .then(Commands.argument("action", StringArgumentType.word())
                        .suggests((ctx, builder) -> builder.suggest("on").suggest("off").buildFuture())
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
                            .executes(HandShakerCommand::ignoreAllMods))
                        .then(Commands.argument("mod", StringArgumentType.string())
                            .suggests(HandShakerCommand::suggestMods)
                            .executes(HandShakerCommand::ignoreMod)))
                    .then(Commands.literal("remove")
                        .then(Commands.argument("mod", StringArgumentType.string())
                            .suggests(HandShakerCommand::suggestIgnoredMods)
                            .executes(HandShakerCommand::unignoreMod)))
                    .then(Commands.literal("list")
                        .executes(HandShakerCommand::listIgnoredMods)))
                .then(Commands.literal("player")
                    .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(HandShakerCommand::suggestPlayers)
                        .executes(HandShakerCommand::showPlayerMods))));

        dispatcher.register(handshaker);
    }

    private static int showHelp(CommandContext<CommandSourceStack> ctx) {
        String title = "HandShaker v6 Commands";
        ctx.getSource().sendSystemMessage(sectionHeader(title));
        
        // Loop through help sections and render with NeoForge Component API
        for (CommandHelper.HelpSection section : CommandHelper.getHelpSections()) {
            ctx.getSource().sendSystemMessage(Component.literal(section.title()).withColor(0xFFFF55).withStyle(ChatFormatting.BOLD));
            for (CommandHelper.HelpEntry entry : section.entries()) {
                ctx.getSource().sendSystemMessage(Component.literal(entry.command()).withColor(0xFFFF55)
                    .append(Component.literal(" - " + entry.description()).withColor(0xAAAAAA)));
            }
            ctx.getSource().sendSystemMessage(Component.empty());
        }

        ctx.getSource().sendSystemMessage(sectionFooter(title));
        
        return Command.SINGLE_SUCCESS;
    }

    private static int showInfo(CommandContext<CommandSourceStack> ctx) {
        ConfigManager config = HandShakerServerMod.getInstance().getBlacklistConfig();
        String statsTitle = "HandShaker Statistics";
        ctx.getSource().sendSystemMessage(sectionHeader(statsTitle));
        
        ctx.getSource().sendSystemMessage(Component.literal("Required Mods: ").withColor(0xFFFF55)
            .append(Component.literal(String.valueOf(config.getRequiredMods().size())).withColor(0xFFFFFF)));
        ctx.getSource().sendSystemMessage(Component.literal("Blacklisted Mods: ").withColor(0xFFFF55)
            .append(Component.literal(String.valueOf(config.getBlacklistedMods().size())).withColor(0xFFFFFF)));
        ctx.getSource().sendSystemMessage(Component.literal("Whitelisted Mods: ").withColor(0xFFFF55)
            .append(Component.literal(String.valueOf(config.getWhitelistedMods().size())).withColor(0xFFFFFF)));
        ctx.getSource().sendSystemMessage(Component.literal("Ignored Mods: ").withColor(0xFFFF55)
            .append(Component.literal(String.valueOf(config.getIgnoredMods().size())).withColor(0xFFFFFF)));

        ctx.getSource().sendSystemMessage(sectionFooter(statsTitle));

        ctx.getSource().sendSystemMessage(Component.literal(""));
        String statusTitle = "HandShaker Status";
        ctx.getSource().sendSystemMessage(sectionHeader(statusTitle));
        ctx.getSource().sendSystemMessage(Component.literal("Behavior: ").withColor(0xFFFF55)
            .append(Component.literal(config.getBehavior().toString()).withColor(0xFFFFFF)));
        ctx.getSource().sendSystemMessage(Component.literal("Integrity Mode: ").withColor(0xFFFF55)
            .append(Component.literal(config.getIntegrityMode().toString()).withColor(0xFFFFFF)));
        ctx.getSource().sendSystemMessage(Component.literal("Whitelist Mode: ").withColor(0xFFFF55)
            .append(Component.literal(config.isWhitelist() ? "ON" : "OFF").withColor(0xFFFFFF)));
        ctx.getSource().sendSystemMessage(Component.literal("Handshake Timeout: ").withColor(0xFFFF55)
            .append(Component.literal(config.getHandshakeTimeoutSeconds() + "s").withColor(0xFFFFFF)));

        ctx.getSource().sendSystemMessage(sectionFooter(statusTitle));

        ctx.getSource().sendSystemMessage(Component.literal(""));
        ctx.getSource().sendSystemMessage(Component.literal("Use /handshaker info configured_mods [page] to list configured mods").withColor(0xAAAAAA));
        ctx.getSource().sendSystemMessage(Component.literal("Use /handshaker info all_mods [page] to see all detected mods").withColor(0xAAAAAA));
        ctx.getSource().sendSystemMessage(Component.literal("Use /handshaker info player <player> [page] to see player history").withColor(0xAAAAAA));
        
        return Command.SINGLE_SUCCESS;
    }

    private static int showConfiguredMods(CommandContext<CommandSourceStack> ctx) {
        return showConfiguredModsWithPageNumber(ctx, 1);
    }

    private static int showConfiguredModsWithPage(CommandContext<CommandSourceStack> ctx) {
        int page = IntegerArgumentType.getInteger(ctx, "page");
        return showConfiguredModsWithPageNumber(ctx, page);
    }

    private static int showConfiguredModsWithPageNumber(CommandContext<CommandSourceStack> ctx, int pageNum) {
        ConfigManager config = HandShakerServerMod.getInstance().getBlacklistConfig();
        Map<String, ConfigState.ModConfig> mods = config.getModConfigMap();

        String title = "Configured Mods";
        ctx.getSource().sendSystemMessage(sectionHeader(title));
        ctx.getSource().sendSystemMessage(Component.literal("whitelist: ").withColor(0xAAAAAA)
            .append(Component.literal(config.isWhitelist() ? "ON" : "OFF").withColor(0xFFFFFF)));

        if (mods.isEmpty()) {
            ctx.getSource().sendSystemMessage(Component.literal(CommandVisualOperations.noModsConfiguredText()).withColor(0xFFFF55));
            ctx.getSource().sendSystemMessage(sectionFooter(title));
            return Command.SINGLE_SUCCESS;
        }

        CommandHelper.PagedList<Map.Entry<String, ConfigState.ModConfig>> paged =
            CommandHelper.configuredModsPage(mods, pageNum, PAGE_SIZE);
        List<Map.Entry<String, ConfigState.ModConfig>> modList = paged.items();
        CommandHelper.Page<Map.Entry<String, ConfigState.ModConfig>> page = paged.page();
        if (page == null) {
            int totalPages = CommandHelper.totalPages(modList.size(), PAGE_SIZE);
            ctx.getSource().sendFailure(Component.literal("Invalid page. Total pages: " + totalPages));
            return 0;
        }

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
                    .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, changeCommand))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hoverText))));

                MutableComponent removeBtn = Component.literal("[✕]").withStyle(style -> style
                    .withColor(ChatFormatting.RED)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, removeCommand))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(CommandVisualOperations.removeHoverText()))));

                ctx.getSource().sendSystemMessage(Component.literal("- ").withColor(0xAAAAAA)
                    .append(modComponent)
                    .append(Component.literal(" ").withColor(0xAAAAAA))
                    .append(modeTag(modCfg != null ? modCfg.getMode() : null))
                    .append(Component.literal(CommandVisualOperations.actionLabelPrefix()).withColor(0x555555))
                    .append(Component.literal(actionLabel).withColor(0xAAAAAA))
                    .append(Component.literal(" | ").withColor(0x555555))
                    .append(removeBtn));
            } else {
                MutableComponent line = modeTag(modCfg != null ? modCfg.getMode() : null)
                    .append(Component.literal(" " + entry.getKey()).withStyle(ChatFormatting.WHITE));
                if (modCfg != null && modCfg.getAction() != null && modCfg.getAction() != ConfigState.Action.KICK) {
                    line = line.append(Component.literal(" [" + CommandVisualOperations.actionDisplayLabel(modCfg) + "]").withStyle(ChatFormatting.GRAY));
                }
                ctx.getSource().sendSystemMessage(line);
            }
        }

        if (page.totalPages() > 1) {
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
        return showAllModsWithPageNumber(ctx, 1);
    }

    private static int showAllModsWithPage(CommandContext<CommandSourceStack> ctx) {
        int page = IntegerArgumentType.getInteger(ctx, "page");
        return showAllModsWithPageNumber(ctx, page);
    }

    private static int showAllModsWithPageNumber(CommandContext<CommandSourceStack> ctx, int pageNum) {
        ConfigManager config = HandShakerServerMod.getInstance().getBlacklistConfig();
        
        // Collect all mods from connected players
        Map<String, Integer> popularity = new HashMap<>();
        for (ServerPlayer player : ctx.getSource().getServer().getPlayerList().getPlayers()) {
            Set<String> mods = HandShakerServerMod.getInstance().getClientMods(player.getUUID());
            if (mods != null) {
                for (String mod : mods) {
                    popularity.put(mod, popularity.getOrDefault(mod, 0) + 1);
                }
            }
        }
        
        CommandHelper.PagedList<Map.Entry<String, Integer>> paged =
            CommandHelper.popularityPage(popularity, pageNum, PAGE_SIZE);
        List<Map.Entry<String, Integer>> sortedMods = paged.items();
        CommandHelper.Page<Map.Entry<String, Integer>> page = paged.page();
        if (page == null) {
            int totalPages = CommandHelper.totalPages(sortedMods.size(), PAGE_SIZE);
            ctx.getSource().sendFailure(Component.literal("Invalid page. Total pages: " + totalPages));
            return 0;
        }
        
        String title = "All Mods (Page " + page.pageNum() + "/" + page.totalPages() + ")";
        ctx.getSource().sendSystemMessage(sectionHeader(title));
        
        for (int i = page.startIdx(); i < page.endIdx(); i++) {
            Map.Entry<String, Integer> entry = sortedMods.get(i);
            ConfigState.ModConfig modCfg = config.getModConfig(entry.getKey());
            String modToken = entry.getKey();
            ModEntry parsed = ModEntry.parse(modToken);
            String modId = parsed != null ? parsed.modId() : modToken;
            String version = parsed != null && parsed.version() != null ? parsed.version() : "unknown";
            String displayName = parsed != null && parsed.displayName() != null ? parsed.displayName() : prettyModName(modId);

            if (ctx.getSource().getEntity() instanceof ServerPlayer) {
                String modInfoCommand = CommandVisualOperations.infoModCommand("/handshaker", modToken);
                MutableComponent modComponent = Component.literal(displayName).withStyle(style -> style
                    .withColor(ChatFormatting.WHITE)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, modInfoCommand))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(CommandVisualOperations.showPlayersUsingModHoverText()))));

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
        
        return Command.SINGLE_SUCCESS;
    }

    private static int showPlayerHistory(CommandContext<CommandSourceStack> ctx) {
        return showPlayerHistoryWithPageNumber(ctx, 1);
    }

    private static int showPlayerHistoryWithPage(CommandContext<CommandSourceStack> ctx) {
        int page = IntegerArgumentType.getInteger(ctx, "page");
        return showPlayerHistoryWithPageNumber(ctx, page);
    }

    private static int showPlayerHistoryWithPageNumber(CommandContext<CommandSourceStack> ctx, int pageNum) {
        String playerName = StringArgumentType.getString(ctx, "playerName");
        PlayerHistoryDatabase db = HandShakerServerMod.getInstance().getPlayerHistoryDb();

        if (db == null || !db.isEnabled()) {
            ctx.getSource().sendFailure(Component.literal("Player history database not available"));
            return 0;
        }

        UUID uuid = null;
        ServerPlayer online = ctx.getSource().getServer().getPlayerList().getPlayerByName(playerName);
        if (online != null) {
            uuid = online.getUUID();
        } else {
            uuid = db.getPlayerUuidByName(playerName).orElse(null);
        }

        if (uuid == null) {
            ctx.getSource().sendFailure(Component.literal("No player history found for: " + playerName));
            return 0;
        }

        List<PlayerHistoryDatabase.ModHistoryEntry> history = db.getPlayerHistory(uuid);
        if (history.isEmpty()) {
            ctx.getSource().sendSystemMessage(Component.literal("No mod history found for: " + playerName).withColor(0xFFFF55));
            return Command.SINGLE_SUCCESS;
        }

        CommandHelper.PagedList<PlayerHistoryDatabase.ModHistoryEntry> paged =
            CommandHelper.historyPage(history, pageNum, PAGE_SIZE);
        CommandHelper.Page<PlayerHistoryDatabase.ModHistoryEntry> page = paged.page();
        if (page == null) {
            int totalPages = CommandHelper.totalPages(history.size(), PAGE_SIZE);
            ctx.getSource().sendFailure(Component.literal("Invalid page. Total pages: " + totalPages));
            return 0;
        }

        String title = "Mod History: " + playerName + " (Page " + page.pageNum() + "/" + page.totalPages() + ")";
        ctx.getSource().sendSystemMessage(sectionHeader(title));

        for (int i = page.startIdx(); i < page.endIdx(); i++) {
            PlayerHistoryDatabase.ModHistoryEntry entry = history.get(i);
            String status = CommandHelper.historyStatusLabel(entry.isActive());
            ChatFormatting statusColor = entry.isActive() ? ChatFormatting.GREEN : ChatFormatting.RED;
            String dates = CommandHelper.historyDates(entry);

            ctx.getSource().sendSystemMessage(Component.literal(entry.modName()).withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(" [" + status + "] ").withStyle(statusColor))
                .append(Component.literal(dates).withColor(0x666666)));
        }

        if (page.totalPages() > 1) {
            ctx.getSource().sendSystemMessage(Component.literal("Use /handshaker info player " + playerName + " <page> to navigate").withColor(0xAAAAAA));
        }

        ctx.getSource().sendSystemMessage(sectionFooter(title));

        return Command.SINGLE_SUCCESS;
    }

    private static int showModInfo(CommandContext<CommandSourceStack> ctx) {
        String modName = StringArgumentType.getString(ctx, "modName");
        PlayerHistoryDatabase db = HandShakerServerMod.getInstance().getPlayerHistoryDb();
        
        if (db == null) {
            ctx.getSource().sendFailure(Component.literal("Player history database not available"));
            return 0;
        }
        
        List<PlayerHistoryDatabase.PlayerModInfo> players = db.getPlayersWithMod(modName);
        
        if (players.isEmpty()) {
            ctx.getSource().sendSystemMessage(Component.literal("No players found with mod: " + modName).withColor(0xFFFF55));
            return Command.SINGLE_SUCCESS;
        }
        
        String title = "Mod: " + modName;
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
        
        return Command.SINGLE_SUCCESS;
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        ConfigManager config = HandShakerServerMod.getInstance().getBlacklistConfig();
        config.load();
        Component message = Component.literal("✓ HandShaker config reloaded.");
        ctx.getSource().sendSuccess(() -> message, true);
        HandShakerServerMod.getInstance().checkAllPlayers();
        return Command.SINGLE_SUCCESS;
    }

    private static int showConfig(CommandContext<CommandSourceStack> ctx) {
        ConfigManager config = HandShakerServerMod.getInstance().getBlacklistConfig();
        String title = "HandShaker Configuration";
        ctx.getSource().sendSystemMessage(sectionHeader(title));
        
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
            ctx.getSource().sendSystemMessage(Component.literal("  " + field.label()).withColor(0xFFFF55)
                .append(Component.literal(": " + field.value()).withColor(0xFFFFFF)));
        }
        ctx.getSource().sendSystemMessage(sectionFooter(title));
        return Command.SINGLE_SUCCESS;
    }

    private static int setConfigValue(CommandContext<CommandSourceStack> ctx, String param) {
        String value = StringArgumentType.getString(ctx, "value");
        ConfigManager config = HandShakerServerMod.getInstance().getBlacklistConfig();
        
        switch (param) {
            case "behavior" -> {
                if (!value.equalsIgnoreCase("STRICT") && !value.equalsIgnoreCase("VANILLA")) {
                    ctx.getSource().sendFailure(Component.literal("Behavior must be STRICT or VANILLA"));
                    return 0;
                }
                config.setBehavior(value);
                ctx.getSource().sendSuccess(() -> Component.literal("✓ Set behavior to " + value.toUpperCase()), true);
                HandShakerServerMod.getInstance().checkAllPlayers();
            }
            case "integrity" -> {
                if (!value.equalsIgnoreCase("SIGNED") && !value.equalsIgnoreCase("DEV")) {
                    ctx.getSource().sendFailure(Component.literal("Integrity must be SIGNED or DEV"));
                    return 0;
                }
                config.setIntegrityMode(value);
                ctx.getSource().sendSuccess(() -> Component.literal("✓ Set integrity mode to " + value.toUpperCase()), true);
            }
            case "whitelist" -> {
                boolean whitelist = value.equalsIgnoreCase("true") || value.equalsIgnoreCase("on");
                config.setWhitelist(whitelist);
                ctx.getSource().sendSuccess(() -> Component.literal("✓ Whitelist mode " + (whitelist ? "enabled" : "disabled")), true);
                HandShakerServerMod.getInstance().checkAllPlayers();
            }
            case "allow_bedrock" -> {
                boolean bedrock = value.equalsIgnoreCase("true") || value.equalsIgnoreCase("on");
                config.setAllowBedrockPlayers(bedrock);
                ctx.getSource().sendSuccess(() -> Component.literal("✓ Bedrock players " + (bedrock ? "allowed" : "disallowed")), true);
                HandShakerServerMod.getInstance().checkAllPlayers();
            }
            case "playerdb_enabled" -> {
                boolean playerdb = value.equalsIgnoreCase("true") || value.equalsIgnoreCase("on");
                config.setPlayerdbEnabled(playerdb);
                ctx.getSource().sendSuccess(() -> Component.literal("✓ Player database " + (playerdb ? "enabled" : "disabled")), true);
            }
            case "hash_mods" -> {
                boolean enabled = value.equalsIgnoreCase("true") || value.equalsIgnoreCase("on");
                config.setHashMods(enabled);
                ctx.getSource().sendSuccess(() -> Component.literal("✓ hash_mods " + (enabled ? "enabled" : "disabled")), true);
            }
            case "mod_versioning" -> {
                boolean enabled = value.equalsIgnoreCase("true") || value.equalsIgnoreCase("on");
                config.setModVersioning(enabled);
                ctx.getSource().sendSuccess(() -> Component.literal("✓ mod_versioning " + (enabled ? "enabled" : "disabled")), true);
            }
            case "runtime_cache" -> {
                boolean enabled = value.equalsIgnoreCase("true") || value.equalsIgnoreCase("on");
                config.setRuntimeCache(enabled);
                ctx.getSource().sendSuccess(() -> Component.literal("✓ runtime_cache " + (enabled ? "enabled" : "disabled")), true);
            }
            case "required_modpack_hash" -> {
                if (value.equalsIgnoreCase("current")) {
                    ServerPlayer player = ctx.getSource().getEntity() instanceof ServerPlayer serverPlayer
                        ? serverPlayer
                        : null;
                    if (player == null) {
                        ctx.getSource().sendFailure(Component.literal("'current' can only be used by an in-game player"));
                        return 0;
                    }

                    Set<String> mods = HandShakerServerMod.getInstance().getClientMods(player.getUUID());
                    if (mods == null || mods.isEmpty()) {
                        ctx.getSource().sendFailure(Component.literal("No client mod list available. Join with HandShaker client first."));
                        return 0;
                    }

                    String computed = CommandHelper.computeModpackHash(mods, config.isHashMods());
                    config.setRequiredModpackHash(computed);
                    ctx.getSource().sendSuccess(() -> Component.literal("✓ required_modpack_hash set to current client hash: " + computed), true);
                    HandShakerServerMod.getInstance().checkAllPlayers();
                    break;
                }

                String normalized = CommandHelper.normalizeRequiredModpackHash(value);
                if (normalized == null && !value.equalsIgnoreCase("off") && !value.equalsIgnoreCase("none") && !value.equalsIgnoreCase("null")) {
                    ctx.getSource().sendFailure(Component.literal("required_modpack_hash must be 64-char SHA-256, 'off', or 'current'"));
                    return 0;
                }

                config.setRequiredModpackHash(normalized);
                ctx.getSource().sendSuccess(() -> Component.literal("✓ required_modpack_hash " + (normalized == null ? "disabled" : "set")), true);
                HandShakerServerMod.getInstance().checkAllPlayers();
            }
            default -> ctx.getSource().sendFailure(Component.literal("Unknown config parameter: " + param));
        }

        config.save();
        
        return Command.SINGLE_SUCCESS;
    }

    private static int setMode(CommandContext<CommandSourceStack> ctx) {
        String list = StringArgumentType.getString(ctx, "list");
        String action = StringArgumentType.getString(ctx, "action");
        ConfigManager config = HandShakerServerMod.getInstance().getBlacklistConfig();
        
        boolean enable = action.equalsIgnoreCase("on") || action.equalsIgnoreCase("true");

        boolean shouldSaveConfig = true;
        
        switch (list.toLowerCase(Locale.ROOT)) {
            case "mods_required" -> {
                config.setModsRequiredEnabledState(enable);
                ctx.getSource().sendSuccess(() -> Component.literal("✓ Required mods " + (enable ? "enabled" : "disabled")), true);
            }
            case "mods_blacklisted" -> {
                config.setModsBlacklistedEnabledState(enable);
                ctx.getSource().sendSuccess(() -> Component.literal("✓ Blacklisted mods " + (enable ? "enabled" : "disabled")), true);
            }
            case "mods_whitelisted" -> {
                config.setModsWhitelistedEnabledState(enable);
                ctx.getSource().sendSuccess(() -> Component.literal("✓ Whitelisted mods " + (enable ? "enabled" : "disabled")), true);
            }
            default -> {
                shouldSaveConfig = false;
                var logger = LoggerAdapter.fromLoaderLogger(HandShakerServerMod.LOGGER);
                var toggleResult = ModListToggler.toggleListDetailed(config.getConfigDirPath(), list, enable, logger);
                if (toggleResult.status() == ModListToggler.ToggleStatus.NOT_FOUND) {
                    ctx.getSource().sendFailure(Component.literal("Unknown list file: " + list + " (expected <name>.yml in config/HandShaker)"));
                    break;
                }
                if (toggleResult.status() == ModListToggler.ToggleStatus.UPDATE_FAILED) {
                    ctx.getSource().sendFailure(Component.literal("Failed to update list file: " + toggleResult.listFile().getFileName()));
                    break;
                }
                config.load();
                ctx.getSource().sendSuccess(() -> Component.literal("✓ " + toggleResult.listFile().getFileName() + " enabled=" + (enable ? "true" : "false")), true);
            }
        }

        if (shouldSaveConfig) {
            config.save();
        }
        
        HandShakerServerMod.getInstance().checkAllPlayers();
        return Command.SINGLE_SUCCESS;
    }

    private static int addMod(CommandContext<CommandSourceStack> ctx) {
        String mod = StringArgumentType.getString(ctx, "mod");
        String mode = StringArgumentType.getString(ctx, "mode");
        ConfigManager config = HandShakerServerMod.getInstance().getBlacklistConfig();
        config.setModConfigByString(mod, mode, CommandHelper.defaultActionForMode(mode), null);
        registerModFingerprint(config, mod);
        ctx.getSource().sendSuccess(() -> Component.literal("✓ Added mod '" + mod + "' as " + mode), true);
        HandShakerServerMod.getInstance().checkAllPlayers();
        return Command.SINGLE_SUCCESS;
    }

    private static int addModWithAction(CommandContext<CommandSourceStack> ctx) {
        String mod = StringArgumentType.getString(ctx, "mod");
        String mode = StringArgumentType.getString(ctx, "mode");
        String action = StringArgumentType.getString(ctx, "action");
        ConfigManager config = HandShakerServerMod.getInstance().getBlacklistConfig();
        config.setModConfigByString(mod, mode, action, null);
        registerModFingerprint(config, mod);
        ctx.getSource().sendSuccess(() -> Component.literal("✓ Added mod '" + mod + "' as " + mode + " with action " + action), true);
        HandShakerServerMod.getInstance().checkAllPlayers();
        return Command.SINGLE_SUCCESS;
    }

    private static int changeMod(CommandContext<CommandSourceStack> ctx) {
        String mod = StringArgumentType.getString(ctx, "mod");
        String mode = StringArgumentType.getString(ctx, "mode");
        ConfigManager config = HandShakerServerMod.getInstance().getBlacklistConfig();
        config.setModConfigByString(mod, mode, CommandHelper.defaultActionForMode(mode), null);
        ctx.getSource().sendSuccess(() -> Component.literal("✓ Changed mod '" + mod + "' to " + mode), true);
        HandShakerServerMod.getInstance().checkAllPlayers();
        return Command.SINGLE_SUCCESS;
    }

    private static int addAllMods(CommandContext<CommandSourceStack> ctx) {
        String mode = StringArgumentType.getString(ctx, "mode");
        ConfigManager config = HandShakerServerMod.getInstance().getBlacklistConfig();
        
        // Collect all mods from all connected players
        Set<String> allMods = new HashSet<>();
        for (ServerPlayer player : ctx.getSource().getServer().getPlayerList().getPlayers()) {
            Set<String> playerMods = HandShakerServerMod.getInstance().getClientMods(player.getUUID());
            if (playerMods != null) {
                allMods.addAll(playerMods);
            }
        }
        
        // Filter out ignored mods
        Set<String> modsToAdd = new HashSet<>();
        for (String mod : allMods) {
            if (!config.isModIgnored(mod)) {
                modsToAdd.add(mod);
            }
        }
        
        // Add all mods with the specified mode
        for (String mod : modsToAdd) {
            config.setModConfigByString(mod, mode, CommandHelper.defaultActionForMode(mode), null);
            registerModFingerprint(config, mod);
        }
        
        ctx.getSource().sendSuccess(() -> Component.literal("✓ Added " + modsToAdd.size() + " mods as " + mode), true);
        HandShakerServerMod.getInstance().checkAllPlayers();
        return Command.SINGLE_SUCCESS;
    }

    private static int addAllModsWithAction(CommandContext<CommandSourceStack> ctx) {
        String mode = StringArgumentType.getString(ctx, "mode");
        String action = StringArgumentType.getString(ctx, "action");
        ConfigManager config = HandShakerServerMod.getInstance().getBlacklistConfig();
        
        // Collect all mods from all connected players
        Set<String> allMods = new HashSet<>();
        for (ServerPlayer player : ctx.getSource().getServer().getPlayerList().getPlayers()) {
            Set<String> playerMods = HandShakerServerMod.getInstance().getClientMods(player.getUUID());
            if (playerMods != null) {
                allMods.addAll(playerMods);
            }
        }
        
        // Filter out ignored mods
        Set<String> modsToAdd = new HashSet<>();
        for (String mod : allMods) {
            if (!config.isModIgnored(mod)) {
                modsToAdd.add(mod);
            }
        }
        
        // Add all mods with the specified mode and action
        for (String mod : modsToAdd) {
            config.setModConfigByString(mod, mode, action, null);
            registerModFingerprint(config, mod);
        }
        
        ctx.getSource().sendSuccess(() -> Component.literal("✓ Added " + modsToAdd.size() + " mods as " + mode + " with action " + action), true);
        HandShakerServerMod.getInstance().checkAllPlayers();
        return Command.SINGLE_SUCCESS;
    }

    private static int changeModWithAction(CommandContext<CommandSourceStack> ctx) {
        String mod = StringArgumentType.getString(ctx, "mod");
        String mode = StringArgumentType.getString(ctx, "mode");
        String action = StringArgumentType.getString(ctx, "action");
        ConfigManager config = HandShakerServerMod.getInstance().getBlacklistConfig();
        config.setModConfigByString(mod, mode, action, null);
        ctx.getSource().sendSuccess(() -> Component.literal("✓ Changed mod '" + mod + "' to " + mode + " with action " + action), true);
        HandShakerServerMod.getInstance().checkAllPlayers();
        return Command.SINGLE_SUCCESS;
    }

    private static int removeMod(CommandContext<CommandSourceStack> ctx) {
        String mod = StringArgumentType.getString(ctx, "mod");
        ConfigManager config = HandShakerServerMod.getInstance().getBlacklistConfig();
        config.removeModConfig(mod);
        ctx.getSource().sendSuccess(() -> Component.literal("✓ Removed mod '" + mod + "'"), true);
        HandShakerServerMod.getInstance().checkAllPlayers();
        return Command.SINGLE_SUCCESS;
    }

    private static int ignoreMod(CommandContext<CommandSourceStack> ctx) {
        String mod = StringArgumentType.getString(ctx, "mod");
        ConfigManager config = HandShakerServerMod.getInstance().getBlacklistConfig();
        config.addIgnoredMod(mod);
        config.save();
        ctx.getSource().sendSuccess(() -> Component.literal("✓ Now ignoring mod '" + mod + "'"), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int ignoreAllMods(CommandContext<CommandSourceStack> ctx) {
        ConfigManager config = HandShakerServerMod.getInstance().getBlacklistConfig();
        
        // Collect all mods from all connected players
        Set<String> allMods = new HashSet<>();
        for (ServerPlayer player : ctx.getSource().getServer().getPlayerList().getPlayers()) {
            Set<String> playerMods = HandShakerServerMod.getInstance().getClientMods(player.getUUID());
            if (playerMods != null) {
                allMods.addAll(playerMods);
            }
        }
        
        // Add all mods to ignore list
        final int[] count = {0};
        for (String mod : allMods) {
            if (config.addIgnoredMod(mod)) {
                count[0]++;
            }
        }

        config.save();
        
        int added = count[0];
        ctx.getSource().sendSuccess(() -> Component.literal("✓ Added " + added + " mods to ignore list"), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int unignoreMod(CommandContext<CommandSourceStack> ctx) {
        String mod = StringArgumentType.getString(ctx, "mod");
        ConfigManager config = HandShakerServerMod.getInstance().getBlacklistConfig();
        config.removeIgnoredMod(mod);
        config.save();
        ctx.getSource().sendSuccess(() -> Component.literal("✓ No longer ignoring mod '" + mod + "'"), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int listIgnoredMods(CommandContext<CommandSourceStack> ctx) {
        ConfigManager config = HandShakerServerMod.getInstance().getBlacklistConfig();
        String title = "Ignored Mods (" + config.getIgnoredMods().size() + ")";
        ctx.getSource().sendSystemMessage(sectionHeader(title));
        for (String mod : config.getIgnoredMods()) {
            ctx.getSource().sendSystemMessage(Component.literal("  • " + mod).withColor(0xFFFF55));
        }
        ctx.getSource().sendSystemMessage(sectionFooter(title));
        return Command.SINGLE_SUCCESS;
    }

    private static void registerModFingerprint(ConfigManager config, String modToken) {
        ModFingerprintRegistrar.registerFromCommand(
            modToken,
            HandShakerServerMod.getInstance().getPlayerHistoryDb(),
            config.isHashMods(),
            config.isModVersioning(),
            HandShakerServerMod.getInstance().getClients().values()
        );
    }

    private static int showPlayerMods(CommandContext<CommandSourceStack> ctx) {
        String playerName = StringArgumentType.getString(ctx, "player");
        ServerPlayer player = ctx.getSource().getServer().getPlayerList().getPlayerByName(playerName);
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("Player not found: " + playerName));
            return 0;
        }
        Set<String> mods = HandShakerServerMod.getInstance().getClientMods(player.getUUID());
        String title = "Mods for " + playerName;
        ctx.getSource().sendSystemMessage(sectionHeader(title));
        if (mods == null || mods.isEmpty()) {
            ctx.getSource().sendSystemMessage(Component.literal("  " + CommandVisualOperations.noModsDetectedText()).withColor(0xAAAAAA));
        } else {
            for (String mod : mods) {
                ConfigState.ModConfig modCfg = HandShakerServerMod.getInstance().getBlacklistConfig().getModConfig(mod);
                ctx.getSource().sendSystemMessage(modeTag(modCfg != null ? modCfg.getMode() : null)
                    .append(Component.literal(" " + mod).withStyle(ChatFormatting.WHITE)));
            }
        }
        ctx.getSource().sendSystemMessage(sectionFooter(title));
        return Command.SINGLE_SUCCESS;
    }

    private static MutableComponent modeTag(String mode) {
        String label = CommandHelper.modeTagLabel(mode);
        if (mode == null) {
            return Component.literal(label).withStyle(ChatFormatting.DARK_GRAY);
        }

        ChatFormatting color = switch (mode) {
            case "required" -> ChatFormatting.GOLD;
            case "blacklisted" -> ChatFormatting.RED;
            case "allowed" -> ChatFormatting.GREEN;
            default -> ChatFormatting.DARK_GRAY;
        };

        return Component.literal(label).withStyle(color);
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

    private static MutableComponent sectionHeader(String title) {
        return Component.literal(CommandVisualOperations.sectionHeaderText(title)).withColor(0xFFAA00).withStyle(ChatFormatting.BOLD);
    }

    private static MutableComponent sectionFooter(String title) {
        return Component.literal(CommandVisualOperations.sectionFooterText(title)).withColor(0xFFAA00).withStyle(ChatFormatting.BOLD);
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
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, previousCommand))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(previousCommand))))
            : Component.literal(CommandVisualOperations.previousPageLabel()).withColor(0x555555);

        MutableComponent next = hasNext
            ? Component.literal(CommandVisualOperations.nextPageLabel()).withStyle(style -> style
                .withColor(ChatFormatting.GREEN)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, nextCommand))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(nextCommand))))
            : Component.literal(CommandVisualOperations.nextPageLabel()).withColor(0x555555);

        ctx.getSource().sendSystemMessage(prev.append(Component.literal(" ")).append(next));
    }

    // ===== Suggestion Providers =====

    private static CompletableFuture<Suggestions> suggestModes(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase();
        for (String mode : CommandSuggestionData.MOD_MODES) {
            if (mode.startsWith(remaining)) {
                builder.suggest(mode);
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestActions(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        for (String action : CommandSuggestionData.DEFAULT_ACTIONS) {
            builder.suggest(action);
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestModeLists(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase();
        for (String list : CommandSuggestionData.MODE_LISTS) {
            if (list.startsWith(remaining)) {
                builder.suggest(list);
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestConfiguredMods(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        ConfigManager config = HandShakerServerMod.getInstance().getBlacklistConfig();
        for (String mod : CommandHelper.sanitizeModSuggestions(config.getModConfigMap().keySet())) {
            String suggestion = CommandSuggestionOperations.autoQuotedSuggestion(builder.getRemaining(), mod);
            if (suggestion != null) {
                builder.suggest(suggestion);
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestIgnoredMods(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        ConfigManager config = HandShakerServerMod.getInstance().getBlacklistConfig();
        for (String mod : config.getIgnoredMods()) {
            String suggestion = CommandSuggestionOperations.autoQuotedSuggestion(builder.getRemaining(), mod);
            if (suggestion != null) {
                builder.suggest(suggestion);
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestMods(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        Set<String> allMods = new HashSet<>();
        for (ServerPlayer player : ctx.getSource().getServer().getPlayerList().getPlayers()) {
            Set<String> playerMods = HandShakerServerMod.getInstance().getClientMods(player.getUUID());
            if (playerMods != null) {
                allMods.addAll(playerMods);
            }
        }
        for (String mod : CommandHelper.sanitizeModSuggestions(allMods)) {
            String suggestion = CommandSuggestionOperations.autoQuotedSuggestion(builder.getRemaining(), mod);
            if (suggestion != null) {
                builder.suggest(suggestion);
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestPlayers(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(
            ctx.getSource().getServer().getPlayerList().getPlayers().stream().map(p -> p.getName().getString()).collect(Collectors.toList()),
            builder
        );
    }

}
