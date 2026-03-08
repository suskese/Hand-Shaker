package me.mklv.handshaker.neoforge.server;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import me.mklv.handshaker.common.commands.ConfigCommandOperations;
import me.mklv.handshaker.common.commands.InfoCommandOperations;
import me.mklv.handshaker.common.commands.CommandHelper;
import me.mklv.handshaker.common.commands.CommandSuggestionOperations;
import me.mklv.handshaker.common.commands.CommandVisualOperations;
import me.mklv.handshaker.common.commands.CommandModUtil;
import me.mklv.handshaker.common.commands.ModManagementOperations;
import me.mklv.handshaker.common.database.PlayerHistoryDatabase;
import me.mklv.handshaker.common.configs.ConfigTypes.ConfigState;
import me.mklv.handshaker.common.configs.ConfigTypes.ModEntry;
import me.mklv.handshaker.common.utils.LoggerAdapter;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.level.ServerPlayer;
import static net.minecraft.commands.Commands.LEVEL_OWNERS;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.ChatFormatting;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class HandShakerCommand {
    private static final int PAGE_SIZE = 10;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var handshaker = Commands.literal("handshaker")
            .requires(Commands.hasPermission(LEVEL_OWNERS))
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
                .then(Commands.literal("force_handshaker_mod")
                    .then(Commands.argument("value", StringArgumentType.word())
                    .suggests(HandShakerCommand::suggestBooleanValues)
                    .executes(ctx -> setConfigValue(ctx, "force_handshaker_mod"))))
                .then(Commands.literal("compat_modern")
                    .then(Commands.argument("value", StringArgumentType.word())
                    .suggests(HandShakerCommand::suggestBooleanValues)
                    .executes(ctx -> setConfigValue(ctx, "compat_modern"))))
                .then(Commands.literal("compat_hybrid")
                    .then(Commands.argument("value", StringArgumentType.word())
                    .suggests(HandShakerCommand::suggestBooleanValues)
                    .executes(ctx -> setConfigValue(ctx, "compat_hybrid"))))
                .then(Commands.literal("compat_legacy")
                    .then(Commands.argument("value", StringArgumentType.word())
                    .suggests(HandShakerCommand::suggestBooleanValues)
                    .executes(ctx -> setConfigValue(ctx, "compat_legacy"))))
                .then(Commands.literal("compat_unsigned")
                    .then(Commands.argument("value", StringArgumentType.word())
                    .suggests(HandShakerCommand::suggestBooleanValues)
                    .executes(ctx -> setConfigValue(ctx, "compat_unsigned"))))
                .then(Commands.literal("enforce_whitelisted_mod_list")
                    .then(Commands.argument("value", StringArgumentType.word())
                        .suggests(HandShakerCommand::suggestBooleanValues)
                    .executes(ctx -> setConfigValue(ctx, "enforce_whitelisted_mod_list"))))
                .then(Commands.literal("allow_bedrock_players")
                    .then(Commands.argument("value", StringArgumentType.word())
                        .suggests(HandShakerCommand::suggestBooleanValues)
                    .executes(ctx -> setConfigValue(ctx, "allow_bedrock_players"))))
                .then(Commands.literal("player_database_enabled")
                    .then(Commands.argument("value", StringArgumentType.word())
                        .suggests(HandShakerCommand::suggestBooleanValues)
                    .executes(ctx -> setConfigValue(ctx, "player_database_enabled"))))
                .then(Commands.literal("use_hash_for_mods")
                    .then(Commands.argument("value", StringArgumentType.word())
                        .suggests(HandShakerCommand::suggestBooleanValues)
                    .executes(ctx -> setConfigValue(ctx, "use_hash_for_mods"))))
                .then(Commands.literal("mod_versioning")
                    .then(Commands.argument("value", StringArgumentType.word())
                        .suggests(HandShakerCommand::suggestBooleanValues)
                        .executes(ctx -> setConfigValue(ctx, "mod_versioning"))))
                .then(Commands.literal("runtime_cache")
                    .then(Commands.argument("value", StringArgumentType.word())
                        .suggests(HandShakerCommand::suggestBooleanValues)
                        .executes(ctx -> setConfigValue(ctx, "runtime_cache"))))
                .then(Commands.literal("required_modpack_hash")
                    .then(Commands.argument("value", StringArgumentType.word())
                        .suggests(HandShakerCommand::suggestRequiredModpackHashValues)
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
                        .executes(HandShakerCommand::showPlayerMods)
                        .then(Commands.literal("page")
                            .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(HandShakerCommand::showPlayerModsWithPageArg))))));

        dispatcher.register(handshaker);
    }

    private static int showHelp(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSystemMessage(Component.literal("HandShaker v6 Commands").withColor(0xFFAA00).withStyle(ChatFormatting.BOLD));
        ctx.getSource().sendSystemMessage(Component.empty());
        
        // Loop through help sections and render with NeoForge Component API
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
        
        if (mods.isEmpty()) {
            ctx.getSource().sendSystemMessage(Component.literal("No mods configured").withColor(0xFFFF55));
            ctx.getSource().sendSystemMessage(sectionFooter(title));
            return Command.SINGLE_SUCCESS;
        }

        InfoCommandOperations.ConfiguredModsPageResult paged =
            InfoCommandOperations.loadConfiguredModsPage(mods, pageNum, PAGE_SIZE);
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
                    .withClickEvent(new ClickEvent.SuggestCommand(changeCommand))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal(hoverText))));

                MutableComponent removeBtn = Component.literal("[✕]").withStyle(style -> style
                    .withColor(ChatFormatting.RED)
                    .withClickEvent(new ClickEvent.RunCommand(removeCommand))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal(CommandVisualOperations.removeHoverText()))));

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
        return showAllModsWithPageNumber(ctx, 1);
    }

    private static int showAllModsWithPage(CommandContext<CommandSourceStack> ctx) {
        int page = IntegerArgumentType.getInteger(ctx, "page");
        return showAllModsWithPageNumber(ctx, page);
    }

    private static int showAllModsWithPageNumber(CommandContext<CommandSourceStack> ctx, int pageNum) {
        ConfigManager config = HandShakerServerMod.getInstance().getBlacklistConfig();

        List<Set<String>> allPlayersMods = new ArrayList<>();
        for (ServerPlayer player : ctx.getSource().getServer().getPlayerList().getPlayers()) {
            Set<String> mods = HandShakerServerMod.getInstance().getClientMods(player.getUUID());
            allPlayersMods.add(mods);
        }

        Map<String, Integer> popularity = InfoCommandOperations.collectPopularity(allPlayersMods);
        InfoCommandOperations.PopularityPageResult paged =
            InfoCommandOperations.loadPopularityPage(popularity, pageNum, PAGE_SIZE);
        if (paged.hasInvalidPage()) {
            ctx.getSource().sendFailure(Component.literal("Invalid page. Total pages: " + paged.totalPages()));
            return 0;
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
            String displayName = parsed != null && parsed.displayName() != null ? parsed.displayName() : prettyModName(modId);

            if (ctx.getSource().getEntity() instanceof ServerPlayer) {
                String modInfoCommand = CommandVisualOperations.infoModCommand("/handshaker", modToken);
                MutableComponent modComponent = Component.literal(displayName).withStyle(style -> style
                    .withColor(ChatFormatting.WHITE)
                    .withClickEvent(new ClickEvent.RunCommand(modInfoCommand))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal(CommandVisualOperations.showPlayersUsingModHoverText()))));

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
        
        return Command.SINGLE_SUCCESS;
    }

    private static int showModInfo(CommandContext<CommandSourceStack> ctx) {
        String modName = StringArgumentType.getString(ctx, "modName");
        PlayerHistoryDatabase db = HandShakerServerMod.getInstance().getPlayerHistoryDb();

        InfoCommandOperations.ModInfoResult result =
            InfoCommandOperations.loadModInfo(db, modName, false);
        if (!result.success()) {
            ctx.getSource().sendFailure(Component.literal(result.error()));
            return 0;
        }

        List<PlayerHistoryDatabase.PlayerModInfo> players = result.players();
        
        if (players.isEmpty()) {
            ctx.getSource().sendSystemMessage(Component.literal("No players found with mod: " + result.displayKey()).withColor(0xFFFF55));
            return Command.SINGLE_SUCCESS;
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
        ServerPlayer online = ctx.getSource().getServer().getPlayerList().getPlayerByName(playerName);
        UUID onlineUuid = online != null ? online.getUUID() : null;

        InfoCommandOperations.PlayerHistoryResult result =
            InfoCommandOperations.loadPlayerHistory(db, playerName, onlineUuid, pageNum, PAGE_SIZE);
        if (!result.success()) {
            ctx.getSource().sendFailure(Component.literal(result.error()));
            return 0;
        }

        List<PlayerHistoryDatabase.ModHistoryEntry> history = result.history();
        if (history.isEmpty()) {
            ctx.getSource().sendSystemMessage(Component.literal("No mod history found for: " + playerName).withColor(0xFFFF55));
            return Command.SINGLE_SUCCESS;
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
            ctx.getSource().sendSystemMessage(Component.literal(field.label()).withColor(0xFFFF55)
                .append(Component.literal(": " + field.value()).withColor(0xFFFFFF)));
        }
        ctx.getSource().sendSystemMessage(sectionFooter(title));
        return Command.SINGLE_SUCCESS;
    }

    private static int setConfigValue(CommandContext<CommandSourceStack> ctx, String param) {
        String value = StringArgumentType.getString(ctx, "value");
        ConfigManager config = HandShakerServerMod.getInstance().getBlacklistConfig();
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

        ctx.getSource().sendSuccess(() -> Component.literal("✓ " + result.message()), true);
        
        return Command.SINGLE_SUCCESS;
    }

    private static int setMode(CommandContext<CommandSourceStack> ctx) {
        String list = StringArgumentType.getString(ctx, "list");
        String action = StringArgumentType.getString(ctx, "action");
        ConfigManager config = HandShakerServerMod.getInstance().getBlacklistConfig();

        ConfigCommandOperations.MutationResult result = ConfigCommandOperations.applyModeToggle(
            config,
            config.getConfigDirPath(),
            LoggerAdapter.fromLoaderLogger(HandShakerServerMod.LOGGER),
            list,
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

        ctx.getSource().sendSuccess(() -> Component.literal("✓ " + result.message()), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int addMod(CommandContext<CommandSourceStack> ctx) {
        String mod = StringArgumentType.getString(ctx, "mod");
        String mode = StringArgumentType.getString(ctx, "mode");
        ConfigManager config = HandShakerServerMod.getInstance().getBlacklistConfig();
        config.setModConfigByString(mod, mode, config.getDefaultActionForMode(mode), null);
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
        config.setModConfigByString(mod, mode, config.getDefaultActionForMode(mode), null);
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
            allMods.addAll(HandShakerServerMod.getInstance().getClientMods(player.getUUID()));
        }
        
        int added = ModManagementOperations.applyToMods(allMods, config::isModIgnored, mod -> {
            config.setModConfigByString(mod, mode, config.getDefaultActionForMode(mode), null);
            registerModFingerprint(config, mod);
        });

        ctx.getSource().sendSuccess(() -> Component.literal("✓ Added " + added + " mods as " + mode), true);
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
            allMods.addAll(HandShakerServerMod.getInstance().getClientMods(player.getUUID()));
        }
        
        int added = ModManagementOperations.applyToMods(allMods, config::isModIgnored, mod -> {
            config.setModConfigByString(mod, mode, action, null);
            registerModFingerprint(config, mod);
        });

        ctx.getSource().sendSuccess(() -> Component.literal("✓ Added " + added + " mods as " + mode + " with action " + action), true);
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
            allMods.addAll(HandShakerServerMod.getInstance().getClientMods(player.getUUID()));
        }
        
        int added = ModManagementOperations.applyToMods(allMods, config::isModIgnored, config::addIgnoredMod);

        config.save();

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
        ctx.getSource().sendSystemMessage(Component.literal("Ignored Mods (" + config.getIgnoredMods().size() + ")").withColor(0xFFAA00).withStyle(ChatFormatting.BOLD));
        ctx.getSource().sendSystemMessage(Component.empty());
        for (String mod : config.getIgnoredMods()) {
            ctx.getSource().sendSystemMessage(Component.literal("  • " + mod).withColor(0xFFFF55));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static void registerModFingerprint(ConfigManager config, String modToken) {
        CommandModUtil.registerFromCommand(
            modToken,
            HandShakerServerMod.getInstance().getPlayerHistoryDb(),
            config.isHashMods(),
            config.isModVersioning(),
            HandShakerServerMod.getInstance().getClients().values()
        );
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
        if (mods == null || mods.isEmpty()) {
            ctx.getSource().sendSystemMessage(Component.literal("No mod list found for " + playerName).withColor(0xFFFF55));
            return Command.SINGLE_SUCCESS;
        }

        List<ModEntry> parsedMods = mods.stream()
            .map(ModEntry::parse)
            .filter(Objects::nonNull)
            .sorted(Comparator.comparing(ModEntry::modId))
            .toList();

        if (parsedMods.isEmpty()) {
            ctx.getSource().sendSystemMessage(Component.literal("No visible mods found for " + playerName).withColor(0xFFFF55));
            return Command.SINGLE_SUCCESS;
        }

        int totalMods = parsedMods.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalMods / PAGE_SIZE));
        if (pageNum < 1 || pageNum > totalPages) {
            ctx.getSource().sendFailure(Component.literal("Invalid page. Total pages: " + totalPages));
            return 0;
        }

        int start = (pageNum - 1) * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, totalMods);

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
            String displayName = entry.displayName() != null ? entry.displayName() : prettyModName(modId);

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
            ctx.getSource().sendSystemMessage(Component.literal("Use " + fallbackUsage + " to navigate").withColor(0xAAAAAA));
            return;
        }

        MutableComponent prev = hasPrevious
            ? Component.literal("[ < Previous page ]").withStyle(style -> style
                .withColor(ChatFormatting.GREEN)
                .withClickEvent(new ClickEvent.RunCommand(previousCommand))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(previousCommand))))
            : Component.literal("[ < Previous page ]").withColor(0x555555);

        MutableComponent next = hasNext
            ? Component.literal("[ Next page > ]").withStyle(style -> style
                .withColor(ChatFormatting.GREEN)
                .withClickEvent(new ClickEvent.RunCommand(nextCommand))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(nextCommand))))
            : Component.literal("[ Next page > ]").withColor(0x555555);

        ctx.getSource().sendSystemMessage(prev.append(Component.literal(" ")).append(next));
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

    // ===== Suggestion Providers =====

    private static CompletableFuture<Suggestions> suggestBooleanValues(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        for (String value : CommandSuggestionOperations.booleanSuggestions(builder.getRemaining())) {
            builder.suggest(value);
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestRequiredModpackHashValues(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        List<String> values = CommandSuggestionOperations.configValueSuggestions("required_modpack_hash", null);
        for (String value : CommandSuggestionOperations.filterByPrefix(values, builder.getRemaining())) {
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

    private static CompletableFuture<Suggestions> suggestActions(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        for (String action : CommandSuggestionOperations.actionSuggestions(null, true, true)) {
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
            allMods.addAll(HandShakerServerMod.getInstance().getClientMods(player.getUUID()));
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