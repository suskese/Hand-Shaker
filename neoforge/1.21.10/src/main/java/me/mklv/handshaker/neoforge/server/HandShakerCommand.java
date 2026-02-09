package me.mklv.handshaker.neoforge.server;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import me.mklv.handshaker.common.database.PlayerHistoryDatabase;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@SuppressWarnings("null")
public class HandShakerCommand {

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
                    .executes(HandShakerCommand::showConfiguredMods))
                .then(Commands.literal("all_mods")
                    .executes(HandShakerCommand::showAllMods))
                .then(Commands.literal("mod")
                    .then(Commands.argument("modName", StringArgumentType.word())
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
                .then(Commands.literal("bedrock")
                    .then(Commands.argument("value", StringArgumentType.word())
                        .suggests((ctx, builder) -> builder.suggest("true").suggest("false").buildFuture())
                        .executes(ctx -> setConfigValue(ctx, "bedrock"))))
                .then(Commands.literal("playerdb")
                    .then(Commands.argument("value", StringArgumentType.word())
                        .suggests((ctx, builder) -> builder.suggest("true").suggest("false").buildFuture())
                        .executes(ctx -> setConfigValue(ctx, "playerdb")))))
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
                    .then(Commands.argument("mod", StringArgumentType.word())
                        .suggests(HandShakerCommand::suggestMods)
                        .then(Commands.argument("mode", StringArgumentType.word())
                            .suggests(HandShakerCommand::suggestModes)
                            .executes(HandShakerCommand::addMod)
                            .then(Commands.argument("action", StringArgumentType.word())
                                .suggests(HandShakerCommand::suggestActions)
                                .executes(HandShakerCommand::addModWithAction)))))
                .then(Commands.literal("change")
                    .then(Commands.argument("mod", StringArgumentType.word())
                        .suggests(HandShakerCommand::suggestConfiguredMods)
                        .then(Commands.argument("mode", StringArgumentType.word())
                            .suggests(HandShakerCommand::suggestModes)
                            .executes(HandShakerCommand::changeMod)
                            .then(Commands.argument("action", StringArgumentType.word())
                                .suggests(HandShakerCommand::suggestActions)
                                .executes(HandShakerCommand::changeModWithAction)))))
                .then(Commands.literal("remove")
                    .then(Commands.argument("mod", StringArgumentType.word())
                        .suggests(HandShakerCommand::suggestConfiguredMods)
                        .executes(HandShakerCommand::removeMod)))
                .then(Commands.literal("ignore")
                    .then(Commands.literal("add")
                        .then(Commands.literal("*")
                            .executes(HandShakerCommand::ignoreAllMods))
                        .then(Commands.argument("mod", StringArgumentType.word())
                            .suggests(HandShakerCommand::suggestMods)
                            .executes(HandShakerCommand::ignoreMod)))
                    .then(Commands.literal("remove")
                        .then(Commands.argument("mod", StringArgumentType.word())
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
        ctx.getSource().sendSystemMessage(Component.literal("═══════════════════════════════").withColor(0xFFAA00));
        ctx.getSource().sendSystemMessage(Component.literal("HandShaker v6 Commands").withColor(0xFFAA00).withStyle(ChatFormatting.BOLD));
        ctx.getSource().sendSystemMessage(Component.literal("═══════════════════════════════").withColor(0xFFAA00));
        
        ctx.getSource().sendSystemMessage(Component.literal("Core Commands:").withColor(0xFFFF55).withStyle(ChatFormatting.BOLD));
        ctx.getSource().sendSystemMessage(Component.literal("/handshaker reload").withColor(0xFFFF55)
            .append(Component.literal(" - Reload config").withColor(0xAAAAAA)));
        ctx.getSource().sendSystemMessage(Component.literal("/handshaker info").withColor(0xFFFF55)
            .append(Component.literal(" - Show statistics").withColor(0xAAAAAA)));
        ctx.getSource().sendSystemMessage(Component.literal("/handshaker config [param] [value]").withColor(0xFFFF55)
            .append(Component.literal(" - View/change configuration").withColor(0xAAAAAA)));
        ctx.getSource().sendSystemMessage(Component.literal("/handshaker mode <list> <on|off>").withColor(0xFFFF55)
            .append(Component.literal(" - Toggle mod lists").withColor(0xAAAAAA)));
        
        ctx.getSource().sendSystemMessage(Component.empty());
        ctx.getSource().sendSystemMessage(Component.literal("Mod Management:").withColor(0xFFFF55).withStyle(ChatFormatting.BOLD));
        ctx.getSource().sendSystemMessage(Component.literal("/handshaker manage add <mod> <mode> [action]").withColor(0xFFFF55)
            .append(Component.literal(" - Add mod").withColor(0xAAAAAA)));
        ctx.getSource().sendSystemMessage(Component.literal("/handshaker manage change <mod> <mode> [action]").withColor(0xFFFF55)
            .append(Component.literal(" - Change mod").withColor(0xAAAAAA)));
        ctx.getSource().sendSystemMessage(Component.literal("/handshaker manage remove <mod>").withColor(0xFFFF55)
            .append(Component.literal(" - Remove mod").withColor(0xAAAAAA)));
        ctx.getSource().sendSystemMessage(Component.literal("/handshaker manage ignore add/remove <mod>").withColor(0xFFFF55)
            .append(Component.literal(" - Manage ignored mods").withColor(0xAAAAAA)));
        
        return Command.SINGLE_SUCCESS;
    }

    private static int showInfo(CommandContext<CommandSourceStack> ctx) {
        BlacklistConfig config = HandShakerServerMod.getInstance().getBlacklistConfig();
        ctx.getSource().sendSystemMessage(Component.literal("═══════════════════════════════════").withColor(0xFFAA00));
        ctx.getSource().sendSystemMessage(Component.literal("  HandShaker Statistics").withColor(0xFFAA00).withStyle(ChatFormatting.BOLD));
        ctx.getSource().sendSystemMessage(Component.literal("═══════════════════════════════════").withColor(0xFFAA00));
        
        ctx.getSource().sendSystemMessage(Component.literal("Required Mods: ").withColor(0xFFFF55)
            .append(Component.literal(String.valueOf(config.getRequiredMods().size())).withColor(0xFFFFFF)));
        ctx.getSource().sendSystemMessage(Component.literal("Blacklisted Mods: ").withColor(0xFFFF55)
            .append(Component.literal(String.valueOf(config.getBlacklistedMods().size())).withColor(0xFFFFFF)));
        ctx.getSource().sendSystemMessage(Component.literal("Whitelisted Mods: ").withColor(0xFFFF55)
            .append(Component.literal(String.valueOf(config.getWhitelistedMods().size())).withColor(0xFFFFFF)));
        ctx.getSource().sendSystemMessage(Component.literal("Ignored Mods: ").withColor(0xFFFF55)
            .append(Component.literal(String.valueOf(config.getIgnoredMods().size())).withColor(0xFFFFFF)));
        
        return Command.SINGLE_SUCCESS;
    }

    private static int showConfiguredMods(CommandContext<CommandSourceStack> ctx) {
        BlacklistConfig config = HandShakerServerMod.getInstance().getBlacklistConfig();
        Map<String, BlacklistConfig.ModConfig> mods = config.getModConfigMap();
        
        ctx.getSource().sendSystemMessage(Component.literal("═══════════════════════════════").withColor(0xFFAA00));
        ctx.getSource().sendSystemMessage(Component.literal("Configured Mods (Whitelist: " + (config.isWhitelist() ? "ON" : "OFF") + ")").withColor(0xFFAA00).withStyle(ChatFormatting.BOLD));
        ctx.getSource().sendSystemMessage(Component.literal("═══════════════════════════════").withColor(0xFFAA00));
        
        if (mods.isEmpty()) {
            ctx.getSource().sendSystemMessage(Component.literal("No mods configured").withColor(0xFFFF55));
            return Command.SINGLE_SUCCESS;
        }
        
        for (Map.Entry<String, BlacklistConfig.ModConfig> entry : mods.entrySet()) {
            BlacklistConfig.ModConfig modCfg = entry.getValue();
            String actionStr = !modCfg.getAction().toString().toLowerCase().equals("kick") ? " | " + modCfg.getAction() : "";
            
            ChatFormatting statusColor = switch (modCfg.getMode()) {
                case "required" -> ChatFormatting.GOLD;
                case "blacklisted" -> ChatFormatting.RED;
                default -> ChatFormatting.GREEN;
            };
            
            ctx.getSource().sendSystemMessage(Component.literal(entry.getKey()).withStyle(statusColor)
                .append(Component.literal(" | " + modCfg.getMode() + actionStr).withColor(0xAAAAAA)));
        }
        
        return Command.SINGLE_SUCCESS;
    }

    private static int showAllMods(CommandContext<CommandSourceStack> ctx) {
        return showAllModsWithPageNumber(ctx, 1);
    }

    private static int showAllModsWithPageNumber(CommandContext<CommandSourceStack> ctx, int pageNum) {
        BlacklistConfig config = HandShakerServerMod.getInstance().getBlacklistConfig();
        
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
        
        List<Map.Entry<String, Integer>> sortedMods = new ArrayList<>(popularity.entrySet());
        sortedMods.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        
        int pageSize = 10;
        int totalPages = (int) Math.ceil((double) sortedMods.size() / pageSize);
        
        if (pageNum < 1 || pageNum > totalPages) {
            ctx.getSource().sendFailure(Component.literal("Invalid page. Total pages: " + totalPages));
            return 0;
        }
        
        int startIdx = (pageNum - 1) * pageSize;
        int endIdx = Math.min(startIdx + pageSize, sortedMods.size());
        
        ctx.getSource().sendSystemMessage(Component.literal("═══════════════════════════════").withColor(0xFFAA00));
        ctx.getSource().sendSystemMessage(Component.literal("All Detected Mods (Page " + pageNum + "/" + totalPages + ")").withColor(0xFFAA00).withStyle(ChatFormatting.BOLD));
        ctx.getSource().sendSystemMessage(Component.literal("═══════════════════════════════").withColor(0xFFAA00));
        
        for (int i = startIdx; i < endIdx; i++) {
            Map.Entry<String, Integer> entry = sortedMods.get(i);
            BlacklistConfig.ModConfig modCfg = config.getModConfig(entry.getKey());
            
            ChatFormatting modeColor = ChatFormatting.GRAY;
            if (modCfg != null) {
                String mode = modCfg.getMode();
                if ("required".equals(mode)) {
                    modeColor = ChatFormatting.GOLD;
                } else if ("blacklisted".equals(mode)) {
                    modeColor = ChatFormatting.RED;
                } else if ("allowed".equals(mode)) {
                    modeColor = ChatFormatting.GREEN;
                }
            }
            
            ctx.getSource().sendSystemMessage(Component.literal(entry.getKey() + " (" + entry.getValue() + " players)").withStyle(modeColor));
        }
        
        if (pageNum < totalPages) {
            ctx.getSource().sendSystemMessage(Component.literal("Use /handshaker info all_mods " + (pageNum + 1) + " for next page").withColor(0xAAAAAA));
        }
        
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
        
        ctx.getSource().sendSystemMessage(Component.literal("═══════════════════════════════").withColor(0xFFAA00));
        ctx.getSource().sendSystemMessage(Component.literal("Mod: " + modName).withColor(0xFFAA00).withStyle(ChatFormatting.BOLD));
        ctx.getSource().sendSystemMessage(Component.literal("═══════════════════════════════").withColor(0xFFAA00));
        ctx.getSource().sendSystemMessage(Component.literal("Players: " + players.size()).withColor(0xFFFF55));
        ctx.getSource().sendSystemMessage(Component.empty());
        
        for (PlayerHistoryDatabase.PlayerModInfo player : players) {
            if (player.isActive()) {
                ctx.getSource().sendSystemMessage(Component.literal(player.currentName()).withColor(0x55FFFF)
                    .append(Component.literal(" - ✓ Active").withStyle(ChatFormatting.GREEN))
                    .append(Component.literal(" (Since: " + player.getFirstSeenFormatted() + ")").withColor(0xAAAAAA)));
            } else {
                ctx.getSource().sendSystemMessage(Component.literal(player.currentName()).withColor(0x55FFFF)
                    .append(Component.literal(" - ✗ Removed").withStyle(ChatFormatting.RED))
                    .append(Component.literal(" (Since: " + player.getFirstSeenFormatted() + ")").withColor(0xAAAAAA)));
            }
        }
        
        return Command.SINGLE_SUCCESS;
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        BlacklistConfig config = HandShakerServerMod.getInstance().getBlacklistConfig();
        config.load();
        Component message = Component.literal("✓ HandShaker config reloaded.");
        ctx.getSource().sendSuccess(() -> message, true);
        HandShakerServerMod.getInstance().checkAllPlayers();
        return Command.SINGLE_SUCCESS;
    }

    private static int showConfig(CommandContext<CommandSourceStack> ctx) {
        BlacklistConfig config = HandShakerServerMod.getInstance().getBlacklistConfig();
        ctx.getSource().sendSystemMessage(Component.literal("═══════════════════════════════════").withColor(0xFFAA00));
        ctx.getSource().sendSystemMessage(Component.literal("  HandShaker Configuration").withColor(0xFFAA00).withStyle(ChatFormatting.BOLD));
        ctx.getSource().sendSystemMessage(Component.literal("═══════════════════════════════════").withColor(0xFFAA00));
        ctx.getSource().sendSystemMessage(Component.literal("  Behavior: ").withColor(0xFFFF55).append(Component.literal(config.getBehavior().toString()).withColor(0xFFFFFF)));
        ctx.getSource().sendSystemMessage(Component.literal("  Integrity Mode: ").withColor(0xFFFF55).append(Component.literal(config.getIntegrityMode().toString()).withColor(0xFFFFFF)));
        ctx.getSource().sendSystemMessage(Component.literal("  Whitelist Mode: ").withColor(0xFFFF55).append(Component.literal(config.isWhitelist() ? "ON" : "OFF").withColor(0xFFFFFF)));
        ctx.getSource().sendSystemMessage(Component.literal("  Bedrock Players: ").withColor(0xFFFF55).append(Component.literal(config.isAllowBedrockPlayers() ? "Allowed" : "Blocked").withColor(0xFFFFFF)));
        ctx.getSource().sendSystemMessage(Component.literal("  Player Database: ").withColor(0xFFFF55).append(Component.literal(config.isPlayerdbEnabled() ? "Enabled" : "Disabled").withColor(0xFFFFFF)));
        return Command.SINGLE_SUCCESS;
    }

    private static int setConfigValue(CommandContext<CommandSourceStack> ctx, String param) {
        String value = StringArgumentType.getString(ctx, "value");
        BlacklistConfig config = HandShakerServerMod.getInstance().getBlacklistConfig();
        
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
            case "bedrock" -> {
                boolean bedrock = value.equalsIgnoreCase("true") || value.equalsIgnoreCase("on");
                config.setAllowBedrockPlayers(bedrock);
                ctx.getSource().sendSuccess(() -> Component.literal("✓ Bedrock players " + (bedrock ? "allowed" : "disallowed")), true);
                HandShakerServerMod.getInstance().checkAllPlayers();
            }
            case "playerdb" -> {
                boolean playerdb = value.equalsIgnoreCase("true") || value.equalsIgnoreCase("on");
                config.setPlayerdbEnabled(playerdb);
                ctx.getSource().sendSuccess(() -> Component.literal("✓ Player database " + (playerdb ? "enabled" : "disabled")), true);
            }
            default -> ctx.getSource().sendFailure(Component.literal("Unknown config parameter: " + param));
        }
        
        return Command.SINGLE_SUCCESS;
    }

    private static int setMode(CommandContext<CommandSourceStack> ctx) {
        String list = StringArgumentType.getString(ctx, "list");
        String action = StringArgumentType.getString(ctx, "action");
        BlacklistConfig config = HandShakerServerMod.getInstance().getBlacklistConfig();
        
        boolean enable = action.equalsIgnoreCase("on") || action.equalsIgnoreCase("true");
        
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
            default -> ctx.getSource().sendFailure(Component.literal("Unknown list: " + list + ". Use: mods_required, mods_blacklisted, or mods_whitelisted"));
        }
        
        HandShakerServerMod.getInstance().checkAllPlayers();
        return Command.SINGLE_SUCCESS;
    }

    private static int addMod(CommandContext<CommandSourceStack> ctx) {
        String mod = StringArgumentType.getString(ctx, "mod");
        String mode = StringArgumentType.getString(ctx, "mode");
        BlacklistConfig config = HandShakerServerMod.getInstance().getBlacklistConfig();
        config.setModConfigByString(mod, mode, "kick", null);
        ctx.getSource().sendSuccess(() -> Component.literal("✓ Added mod '" + mod + "' as " + mode), true);
        HandShakerServerMod.getInstance().checkAllPlayers();
        return Command.SINGLE_SUCCESS;
    }

    private static int addModWithAction(CommandContext<CommandSourceStack> ctx) {
        String mod = StringArgumentType.getString(ctx, "mod");
        String mode = StringArgumentType.getString(ctx, "mode");
        String action = StringArgumentType.getString(ctx, "action");
        BlacklistConfig config = HandShakerServerMod.getInstance().getBlacklistConfig();
        config.setModConfigByString(mod, mode, action, null);
        ctx.getSource().sendSuccess(() -> Component.literal("✓ Added mod '" + mod + "' as " + mode + " with action " + action), true);
        HandShakerServerMod.getInstance().checkAllPlayers();
        return Command.SINGLE_SUCCESS;
    }

    private static int changeMod(CommandContext<CommandSourceStack> ctx) {
        String mod = StringArgumentType.getString(ctx, "mod");
        String mode = StringArgumentType.getString(ctx, "mode");
        BlacklistConfig config = HandShakerServerMod.getInstance().getBlacklistConfig();
        config.setModConfigByString(mod, mode, "kick", null);
        ctx.getSource().sendSuccess(() -> Component.literal("✓ Changed mod '" + mod + "' to " + mode), true);
        HandShakerServerMod.getInstance().checkAllPlayers();
        return Command.SINGLE_SUCCESS;
    }

    private static int addAllMods(CommandContext<CommandSourceStack> ctx) {
        String mode = StringArgumentType.getString(ctx, "mode");
        BlacklistConfig config = HandShakerServerMod.getInstance().getBlacklistConfig();
        
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
            config.setModConfigByString(mod, mode, "kick", null);
        }
        
        ctx.getSource().sendSuccess(() -> Component.literal("✓ Added " + modsToAdd.size() + " mods as " + mode), true);
        HandShakerServerMod.getInstance().checkAllPlayers();
        return Command.SINGLE_SUCCESS;
    }

    private static int addAllModsWithAction(CommandContext<CommandSourceStack> ctx) {
        String mode = StringArgumentType.getString(ctx, "mode");
        String action = StringArgumentType.getString(ctx, "action");
        BlacklistConfig config = HandShakerServerMod.getInstance().getBlacklistConfig();
        
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
        }
        
        ctx.getSource().sendSuccess(() -> Component.literal("✓ Added " + modsToAdd.size() + " mods as " + mode + " with action " + action), true);
        HandShakerServerMod.getInstance().checkAllPlayers();
        return Command.SINGLE_SUCCESS;
    }

    private static int changeModWithAction(CommandContext<CommandSourceStack> ctx) {
        String mod = StringArgumentType.getString(ctx, "mod");
        String mode = StringArgumentType.getString(ctx, "mode");
        String action = StringArgumentType.getString(ctx, "action");
        BlacklistConfig config = HandShakerServerMod.getInstance().getBlacklistConfig();
        config.setModConfigByString(mod, mode, action, null);
        ctx.getSource().sendSuccess(() -> Component.literal("✓ Changed mod '" + mod + "' to " + mode + " with action " + action), true);
        HandShakerServerMod.getInstance().checkAllPlayers();
        return Command.SINGLE_SUCCESS;
    }

    private static int removeMod(CommandContext<CommandSourceStack> ctx) {
        String mod = StringArgumentType.getString(ctx, "mod");
        BlacklistConfig config = HandShakerServerMod.getInstance().getBlacklistConfig();
        config.removeModConfig(mod);
        ctx.getSource().sendSuccess(() -> Component.literal("✓ Removed mod '" + mod + "'"), true);
        HandShakerServerMod.getInstance().checkAllPlayers();
        return Command.SINGLE_SUCCESS;
    }

    private static int ignoreMod(CommandContext<CommandSourceStack> ctx) {
        String mod = StringArgumentType.getString(ctx, "mod");
        BlacklistConfig config = HandShakerServerMod.getInstance().getBlacklistConfig();
        config.addIgnoredMod(mod);
        ctx.getSource().sendSuccess(() -> Component.literal("✓ Now ignoring mod '" + mod + "'"), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int ignoreAllMods(CommandContext<CommandSourceStack> ctx) {
        BlacklistConfig config = HandShakerServerMod.getInstance().getBlacklistConfig();
        
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
        
        int added = count[0];
        ctx.getSource().sendSuccess(() -> Component.literal("✓ Added " + added + " mods to ignore list"), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int unignoreMod(CommandContext<CommandSourceStack> ctx) {
        String mod = StringArgumentType.getString(ctx, "mod");
        BlacklistConfig config = HandShakerServerMod.getInstance().getBlacklistConfig();
        config.removeIgnoredMod(mod);
        ctx.getSource().sendSuccess(() -> Component.literal("✓ No longer ignoring mod '" + mod + "'"), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int listIgnoredMods(CommandContext<CommandSourceStack> ctx) {
        BlacklistConfig config = HandShakerServerMod.getInstance().getBlacklistConfig();
        ctx.getSource().sendSystemMessage(Component.literal("═══════════════════════════════════").withColor(0xFFAA00));
        ctx.getSource().sendSystemMessage(Component.literal("  Ignored Mods (" + config.getIgnoredMods().size() + ")").withColor(0xFFAA00).withStyle(ChatFormatting.BOLD));
        ctx.getSource().sendSystemMessage(Component.literal("═══════════════════════════════════").withColor(0xFFAA00));
        for (String mod : config.getIgnoredMods()) {
            ctx.getSource().sendSystemMessage(Component.literal("  • " + mod).withColor(0xFFFF55));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int showPlayerMods(CommandContext<CommandSourceStack> ctx) {
        String playerName = StringArgumentType.getString(ctx, "player");
        ServerPlayer player = ctx.getSource().getServer().getPlayerList().getPlayerByName(playerName);
        if (player == null) {
            ctx.getSource().sendFailure(Component.literal("Player not found: " + playerName));
            return 0;
        }
        Set<String> mods = HandShakerServerMod.getInstance().getClientMods(player.getUUID());
        ctx.getSource().sendSystemMessage(Component.literal("═══════════════════════════════════").withColor(0xFFAA00));
        ctx.getSource().sendSystemMessage(Component.literal("  Mods for " + playerName).withColor(0xFFAA00).withStyle(ChatFormatting.BOLD));
        ctx.getSource().sendSystemMessage(Component.literal("═══════════════════════════════════").withColor(0xFFAA00));
        if (mods == null || mods.isEmpty()) {
            ctx.getSource().sendSystemMessage(Component.literal("  No mods detected").withColor(0xAAAAAA));
        } else {
            for (String mod : mods) {
                ctx.getSource().sendSystemMessage(Component.literal("  • " + mod).withColor(0xFFFF55));
            }
        }
        return Command.SINGLE_SUCCESS;
    }

    // ===== Suggestion Providers =====

    private static CompletableFuture<Suggestions> suggestModes(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return builder.suggest("required").suggest("allowed").suggest("blacklisted").buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestActions(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return builder.suggest("kick").suggest("ban").buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestModeLists(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return builder.suggest("mods_required").suggest("mods_blacklisted").suggest("mods_whitelisted").buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestConfiguredMods(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        BlacklistConfig config = HandShakerServerMod.getInstance().getBlacklistConfig();
        return SharedSuggestionProvider.suggest(config.getModConfigMap().keySet(), builder);
    }

    private static CompletableFuture<Suggestions> suggestIgnoredMods(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        BlacklistConfig config = HandShakerServerMod.getInstance().getBlacklistConfig();
        return SharedSuggestionProvider.suggest(config.getIgnoredMods(), builder);
    }

    private static CompletableFuture<Suggestions> suggestMods(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        Set<String> allMods = new HashSet<>();
        for (ServerPlayer player : ctx.getSource().getServer().getPlayerList().getPlayers()) {
            Set<String> playerMods = HandShakerServerMod.getInstance().getClientMods(player.getUUID());
            if (playerMods != null) {
                allMods.addAll(playerMods);
            }
        }
        return SharedSuggestionProvider.suggest(allMods, builder);
    }

    private static CompletableFuture<Suggestions> suggestPlayers(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(
            ctx.getSource().getServer().getPlayerList().getPlayers().stream().map(p -> p.getName().getString()).collect(Collectors.toList()),
            builder
        );
    }
}
