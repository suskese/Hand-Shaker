package me.mklv.handshaker.paper;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.*;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.mklv.handshaker.common.commands.CommandSuggestionData;
import me.mklv.handshaker.common.commands.CommandModUtil;
import me.mklv.handshaker.common.commands.ModFingerprintRegistrar;
import me.mklv.handshaker.common.commands.ModListToggler;
import me.mklv.handshaker.common.database.PlayerHistoryDatabase;
import me.mklv.handshaker.common.configs.ConfigTypes.ConfigState;
import me.mklv.handshaker.common.configs.ConfigTypes.ModEntry;
import me.mklv.handshaker.common.utils.ClientInfo;
import me.mklv.handshaker.common.utils.LoggerAdapter;
import me.mklv.handshaker.common.utils.ModpackHashing;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class HandShakerCommandV2 {
    private static final int PAGE_SIZE = 10;
    private final HandShakerPlugin plugin;
    private final ConfigManager config;

    public HandShakerCommandV2(HandShakerPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
    }

    public static void register(HandShakerPlugin plugin, Commands commands) {
        try {
            HandShakerCommandV2 handler = new HandShakerCommandV2(plugin);
            
            // Build the main command tree
            var builder = Commands.literal("handshakerv2")
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
                    java.util.List.of("hsv2", "hs-v2")
            );
            
            plugin.getLogger().info("✓ Registered HandShaker v2 Brigadier command (/handshakerv2)");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to register Brigadier command: " + e.getMessage());
            plugin.getLogger().info("Ensure this is called from LifecycleEvents.COMMANDS handler");
            e.printStackTrace();
        }
    }
    // ===== Command Tree Builders =====

    private LiteralArgumentBuilder<CommandSourceStack> buildInfoCommand() {
        return Commands.literal("info")
            .executes(ctx -> showInfoSummary(ctx.getSource()))
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
                    .executes(ctx -> showModInfo(ctx.getSource(), getModToken(ctx, "modName")))));
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
                    .suggests(this::suggestBool)
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
                    .then(Commands.argument("mod", StringArgumentType.string())
                        .suggests(this::suggestPlayerMods)
                        .then(Commands.argument("status", StringArgumentType.string())
                            .suggests(this::suggestModes)
                            .executes(ctx -> changePlayerMod(ctx.getSource(), 
                                ctx.getArgument("playerName", String.class),
                                getModToken(ctx, "mod"),
                                ctx.getArgument("status", String.class),
                                null))
                            .then(Commands.argument("action", StringArgumentType.string())
                                .suggests(this::suggestActions)
                                .executes(ctx -> changePlayerMod(ctx.getSource(), 
                                    ctx.getArgument("playerName", String.class),
                                    getModToken(ctx, "mod"),
                                    ctx.getArgument("status", String.class),
                                    ctx.getArgument("action", String.class))))))));
    }

    // ===== Command Handlers =====

    private int showHelp(CommandSourceStack source) {
        source.getSender().sendMessage(Component.text("═══════════════════════════════════").color(NamedTextColor.GOLD));
        source.getSender().sendMessage(Component.text("  HandShaker v6 Brigadier Commands").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
        source.getSender().sendMessage(Component.text("═══════════════════════════════════").color(NamedTextColor.GOLD));
        source.getSender().sendMessage(Component.empty());
        
        source.getSender().sendMessage(Component.text("Core Commands:").color(NamedTextColor.YELLOW).decorate(TextDecoration.BOLD));
        source.getSender().sendMessage(Component.text("/handshaker reload").color(NamedTextColor.YELLOW).append(Component.text(" - Reload config").color(NamedTextColor.GRAY)));
        source.getSender().sendMessage(Component.text("/handshaker info [configured_mods|all_mods|mod|player] [page]").color(NamedTextColor.YELLOW).append(Component.text(" - Show info").color(NamedTextColor.GRAY)));
        source.getSender().sendMessage(Component.text("/handshaker config <param> <value>").color(NamedTextColor.YELLOW).append(Component.text(" - Modify config").color(NamedTextColor.GRAY)));
        source.getSender().sendMessage(Component.text("/handshaker mode <list> <on|off>").color(NamedTextColor.YELLOW).append(Component.text(" - Toggle mod lists").color(NamedTextColor.GRAY)));
        source.getSender().sendMessage(Component.empty());
        
        source.getSender().sendMessage(Component.text("Mod Management:").color(NamedTextColor.YELLOW).decorate(TextDecoration.BOLD));
        source.getSender().sendMessage(Component.text("/handshaker manage add <mod|*> <mode> [action]").color(NamedTextColor.YELLOW).append(Component.text(" - Add mod").color(NamedTextColor.GRAY)));
        source.getSender().sendMessage(Component.text("/handshaker manage change <mod> <mode> [action]").color(NamedTextColor.YELLOW).append(Component.text(" - Change mod").color(NamedTextColor.GRAY)));
        source.getSender().sendMessage(Component.text("/handshaker manage remove <mod>").color(NamedTextColor.YELLOW).append(Component.text(" - Remove mod").color(NamedTextColor.GRAY)));
        source.getSender().sendMessage(Component.text("/handshaker manage ignore <add|remove|list> [mod|*]").color(NamedTextColor.YELLOW).append(Component.text(" - Manage ignores").color(NamedTextColor.GRAY)));
        source.getSender().sendMessage(Component.text("/handshaker manage player <player>").color(NamedTextColor.YELLOW).append(Component.text(" - View player mods").color(NamedTextColor.GRAY)));
        
        return 1;
    }

    private int reload(CommandSourceStack source) {
        config.load();
        source.getSender().sendMessage(Component.text("✓ HandShaker config reloaded").color(NamedTextColor.GREEN));
        plugin.checkAllPlayers();
        return 1;
    }

    private int showInfoSummary(CommandSourceStack source) {
        PlayerHistoryDatabase db = plugin.getPlayerHistoryDb();
        if (db == null) {
            source.getSender().sendMessage(Component.text("Player history database not available").color(NamedTextColor.RED));
            return 0;
        }
        
        Map<String, Integer> popularity = db.getModPopularity();
        int uniqueMods = popularity.size();
        int configuredMods = config.getModConfigMap().size();
        int activePlayers = db.getUniqueActivePlayers();
        
        source.getSender().sendMessage(Component.text("═══════════════════════════════════").color(NamedTextColor.GOLD));
        source.getSender().sendMessage(Component.text("  HandShaker Statistics").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
        source.getSender().sendMessage(Component.text("═══════════════════════════════════").color(NamedTextColor.GOLD));
        source.getSender().sendMessage(Component.text("Unique Mods Detected: ").color(NamedTextColor.YELLOW).append(Component.text(uniqueMods + "").color(NamedTextColor.WHITE)));
        source.getSender().sendMessage(Component.text("Configured Mods: ").color(NamedTextColor.YELLOW).append(Component.text(configuredMods + "").color(NamedTextColor.WHITE)));
        source.getSender().sendMessage(Component.text("Active Players: ").color(NamedTextColor.YELLOW).append(Component.text(activePlayers + "").color(NamedTextColor.WHITE)));
        source.getSender().sendMessage(Component.empty());
        
        source.getSender().sendMessage(Component.text("═══════════════════════════════════").color(NamedTextColor.GOLD));
        source.getSender().sendMessage(Component.text("  HandShaker Status").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
        source.getSender().sendMessage(Component.text("═══════════════════════════════════").color(NamedTextColor.GOLD));
        source.getSender().sendMessage(Component.text("Behavior: ").color(NamedTextColor.YELLOW).append(Component.text(config.getBehavior().toString()).color(NamedTextColor.WHITE)));
        source.getSender().sendMessage(Component.text("Integrity Mode: ").color(NamedTextColor.YELLOW).append(Component.text(config.getIntegrityMode().toString()).color(NamedTextColor.WHITE)));
        source.getSender().sendMessage(Component.text("Whitelist Mode: ").color(NamedTextColor.YELLOW).append(Component.text(config.isWhitelist() ? "ON" : "OFF").color(NamedTextColor.WHITE)));
        source.getSender().sendMessage(Component.text("Handshake Timeout: ").color(NamedTextColor.YELLOW).append(Component.text(config.getHandshakeTimeoutSeconds() + "s").color(NamedTextColor.WHITE)));
        
        return 1;
    }

    private int showConfiguredMods(CommandSourceStack source, int pageNum) {
        Map<String, ConfigState.ModConfig> mods = config.getModConfigMap();
        if (mods.isEmpty()) {
            source.getSender().sendMessage(Component.text("No mods configured.").color(NamedTextColor.YELLOW));
            return 1;
        }

        List<Map.Entry<String, ConfigState.ModConfig>> modList = new ArrayList<>(mods.entrySet());
        modList.sort(Map.Entry.comparingByKey());
        int totalPages = (int) Math.ceil((double) modList.size() / PAGE_SIZE);
        
        if (pageNum < 1 || pageNum > totalPages) {
            source.getSender().sendMessage(Component.text("Invalid page. Total pages: " + totalPages).color(NamedTextColor.RED));
            return 0;
        }
        
        int startIdx = (pageNum - 1) * PAGE_SIZE;
        int endIdx = Math.min(startIdx + PAGE_SIZE, modList.size());

        source.getSender().sendMessage(Component.text("═══════════════════════════════════").color(NamedTextColor.GOLD));
        source.getSender().sendMessage(Component.text("Configured Mods (Page " + pageNum + "/" + totalPages + ")").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
        source.getSender().sendMessage(Component.text("═══════════════════════════════════").color(NamedTextColor.GOLD));
        
        if (!(source.getSender() instanceof Player player)) {
            for (int i = startIdx; i < endIdx; i++) {
                Map.Entry<String, ConfigState.ModConfig> entry = modList.get(i);
                ConfigState.ModConfig modCfg = entry.getValue();
                String actionStr = modCfg.getAction() != ConfigState.Action.KICK
                    ? " [" + modCfg.getAction().toString().toLowerCase(Locale.ROOT) + "]"
                    : "";
                source.getSender().sendMessage(modeTagText(modCfg) + " " + entry.getKey() + actionStr);
            }
        } else {
            for (int i = startIdx; i < endIdx; i++) {
                Map.Entry<String, ConfigState.ModConfig> entry = modList.get(i);
                ConfigState.ModConfig modCfg = entry.getValue();
                NamedTextColor statusColor = modCfg.isRequired() ? NamedTextColor.GREEN : modCfg.isBlacklisted() ? NamedTextColor.RED : NamedTextColor.YELLOW;
                
                String actionStr = modCfg.getAction() != ConfigState.Action.KICK ? " " + modCfg.getAction() : "";
                String hoverText = modCfg.getMode() + actionStr + "\n\nClick to change mode";
                
                Component modComponent = buildModComponent(entry.getKey(), statusColor, hoverText, "/handshakerv2 manage change " + entry.getKey());
                Component removeBtn = Component.text(" [Remove]")
                    .color(NamedTextColor.RED)
                    .hoverEvent(HoverEvent.showText(Component.text("Click to remove")))
                    .clickEvent(ClickEvent.runCommand("/handshakerv2 manage remove " + entry.getKey()));
                
                Component line = modeTagComponent(modCfg)
                    .append(Component.text(" ").color(NamedTextColor.GRAY))
                    .append(modComponent);
                    
                if (modCfg.getAction() != ConfigState.Action.KICK) {
                    line = line.append(Component.text(" [" + modCfg.getAction().toString().toLowerCase(Locale.ROOT) + "]")
                        .color(NamedTextColor.DARK_GRAY));
                }
                
                line = line.append(removeBtn);
                player.sendMessage(line);
            }
        }

        if (totalPages > 1) {
            source.getSender().sendMessage(Component.text("Use /handshakerv2 info configured_mods <page> to navigate").color(NamedTextColor.DARK_GRAY));
        }
        
        return 1;
    }

    private int showAllMods(CommandSourceStack source, int pageNum) {
        PlayerHistoryDatabase db = plugin.getPlayerHistoryDb();
        if (db == null) {
            source.getSender().sendMessage(Component.text("Player history database not available").color(NamedTextColor.RED));
            return 0;
        }
        
        Map<String, Integer> popularity = db.getModPopularity();
        List<Map.Entry<String, Integer>> sortedMods = new ArrayList<>(popularity.entrySet());
        sortedMods.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        
        int totalPages = (int) Math.ceil((double) sortedMods.size() / PAGE_SIZE);
        if (pageNum < 1 || pageNum > totalPages) {
            source.getSender().sendMessage(Component.text("Invalid page. Total pages: " + totalPages).color(NamedTextColor.RED));
            return 0;
        }
        
        int startIdx = (pageNum - 1) * PAGE_SIZE;
        int endIdx = Math.min(startIdx + PAGE_SIZE, sortedMods.size());

        source.getSender().sendMessage(Component.text("═══════════════════════════════════").color(NamedTextColor.GOLD));
        source.getSender().sendMessage(Component.text("All Detected Mods (Page " + pageNum + "/" + totalPages + ")").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
        source.getSender().sendMessage(Component.text("═══════════════════════════════════").color(NamedTextColor.GOLD));
        
        if (!(source.getSender() instanceof Player player)) {
            for (int i = startIdx; i < endIdx; i++) {
                Map.Entry<String, Integer> entry = sortedMods.get(i);
                String modName = entry.getKey();
                int count = entry.getValue();
                
                if (config.isIgnored(modName)) continue;
                
                ConfigState.ModConfig modCfg = config.getModConfig(modName);
                String tag = modCfg == null ? "[DET]" 
                    : modCfg.getMode().equals("required") ? "[REQ]"
                    : modCfg.getMode().equals("blacklisted") ? "[BLK]"
                    : "[ALW]";
                source.getSender().sendMessage(Component.text(tag + " " + modName + " x" + count).color(NamedTextColor.YELLOW));
            }
        } else {
            for (int i = startIdx; i < endIdx; i++) {
                Map.Entry<String, Integer> entry = sortedMods.get(i);
                String modName = entry.getKey();
                int count = entry.getValue();
                
                if (config.isIgnored(modName)) continue;
                
                ConfigState.ModConfig modCfg = config.getModConfig(modName);
                NamedTextColor statusColor = modCfg != null && modCfg.isRequired() ? NamedTextColor.GREEN 
                    : modCfg != null && modCfg.isBlacklisted() ? NamedTextColor.RED 
                    : modCfg != null ? NamedTextColor.YELLOW : NamedTextColor.GRAY;
                
                String hoverText = modCfg != null ? modCfg.getMode() + "\n\nClick to change mode" : "Detected mod\n\nClick to change mode";
                
                List<PlayerHistoryDatabase.PlayerModInfo> playerList = db.getPlayersWithMod(modName);
                if (!playerList.isEmpty()) {
                    StringBuilder playerNames = new StringBuilder();
                    for (PlayerHistoryDatabase.PlayerModInfo playerInfo : playerList) {
                        if (playerNames.length() > 0) playerNames.append(", ");
                        playerNames.append(playerInfo.currentName());
                    }
                    hoverText = hoverText + "\n\nActive on: " + playerNames;
                }
                
                Component modComponent = buildModComponent(modName, statusColor, hoverText, "/handshakerv2 manage change " + modName);
                Component line = modeTagComponent(modCfg)
                    .append(Component.text(" ").color(NamedTextColor.GRAY))
                    .append(modComponent)
                    .append(Component.text(" x" + count).color(NamedTextColor.GRAY));
                player.sendMessage(line);
            }
        }

        if (totalPages > 1) {
            source.getSender().sendMessage(Component.text("Use /handshakerv2 info all_mods <page> to navigate").color(NamedTextColor.DARK_GRAY));
        }
        
        return 1;
    }

    private int showModInfo(CommandSourceStack source, String modName) {
        PlayerHistoryDatabase db = plugin.getPlayerHistoryDb();
        if (db == null) {
            source.getSender().sendMessage(Component.text("Player history database not available").color(NamedTextColor.RED));
            return 0;
        }

        // Parse mod:version if provided
        ModEntry entry = ModEntry.parse(modName);
        String displayKey = entry != null ? entry.toDisplayKey() : modName;
        String searchModId = entry != null && entry.modId() != null ? entry.modId() : modName;
        
        // Collect all players - if no version specified, search for all versions
        List<PlayerHistoryDatabase.PlayerModInfo> players = new ArrayList<>();
        if (entry != null && entry.version() != null) {
            // Specific version requested
            players = db.getPlayersWithMod(displayKey);
        } else {
            // No version - search for all versions of this mod
            Set<String> foundMods = new LinkedHashSet<>();
            Map<String, Integer> popularity = db.getModPopularity();
            for (String mod : popularity.keySet()) {
                ModEntry checking = ModEntry.parse(mod);
                if (checking != null && checking.modId().equalsIgnoreCase(searchModId)) {
                    foundMods.add(mod);
                }
            }
            
            // Get players for all matching mod versions
            Map<String, PlayerHistoryDatabase.PlayerModInfo> uniquePlayers = new LinkedHashMap<>();
            for (String mod : foundMods) {
                List<PlayerHistoryDatabase.PlayerModInfo> versionPlayers = db.getPlayersWithMod(mod);
                for (PlayerHistoryDatabase.PlayerModInfo p : versionPlayers) {
                    uniquePlayers.put(p.currentName() + ":" + mod, p);
                }
            }
            players = new ArrayList<>(uniquePlayers.values());
        }
        
        if (players.isEmpty()) {
            source.getSender().sendMessage(Component.text("No players found with mod: " + displayKey).color(NamedTextColor.YELLOW));
            return 1;
        }

        source.getSender().sendMessage(Component.text("═══════════════════════════════════").color(NamedTextColor.GOLD));
        source.getSender().sendMessage(Component.text("Mod: " + displayKey).color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
        source.getSender().sendMessage(Component.text("═══════════════════════════════════").color(NamedTextColor.GOLD));
        source.getSender().sendMessage(Component.text("Players: " + players.size()).color(NamedTextColor.YELLOW));
        source.getSender().sendMessage(Component.empty());
        
        if (!(source.getSender() instanceof Player)) {
            for (PlayerHistoryDatabase.PlayerModInfo playerInfo : players) {
                String status = playerInfo.isActive() ? "Active" : "Removed";
                source.getSender().sendMessage(Component.text("  " + playerInfo.currentName() + " [" + status + "] (Since: " + playerInfo.getFirstSeenFormatted() + ")").color(NamedTextColor.WHITE));
            }
        } else {
            for (PlayerHistoryDatabase.PlayerModInfo playerInfo : players) {
                source.getSender().sendMessage(buildPlayerInfoComponent(playerInfo));
            }
        }
        
        return 1;
    }

    private int showPlayerHistory(CommandSourceStack source, String playerName, int pageNum) {
        PlayerHistoryDatabase db = plugin.getPlayerHistoryDb();
        if (db == null) {
            source.getSender().sendMessage(Component.text("Player history database not available").color(NamedTextColor.RED));
            return 0;
        }

        UUID uuid = null;
        Player online = plugin.getServer().getPlayerExact(playerName);
        if (online != null) {
            uuid = online.getUniqueId();
        } else {
            uuid = db.getPlayerUuidByName(playerName).orElse(null);
        }

        if (uuid == null) {
            source.getSender().sendMessage(Component.text("No player history found for: " + playerName).color(NamedTextColor.RED));
            return 0;
        }

        List<PlayerHistoryDatabase.ModHistoryEntry> history = db.getPlayerHistory(uuid);
        if (history.isEmpty()) {
            source.getSender().sendMessage(Component.text("No mod history found for: " + playerName).color(NamedTextColor.YELLOW));
            return 1;
        }

        int totalPages = (int) Math.ceil((double) history.size() / PAGE_SIZE);
        if (pageNum < 1 || pageNum > totalPages) {
            source.getSender().sendMessage(Component.text("Invalid page. Total pages: " + totalPages).color(NamedTextColor.RED));
            return 0;
        }
        
        int startIdx = (pageNum - 1) * PAGE_SIZE;
        int endIdx = Math.min(startIdx + PAGE_SIZE, history.size());

        source.getSender().sendMessage(Component.text("═══════════════════════════════════").color(NamedTextColor.GOLD));
        source.getSender().sendMessage(Component.text("Mod History: " + playerName + " (Page " + pageNum + "/" + totalPages + ")").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
        source.getSender().sendMessage(Component.text("═══════════════════════════════════").color(NamedTextColor.GOLD));
        
        for (int i = startIdx; i < endIdx; i++) {
            PlayerHistoryDatabase.ModHistoryEntry entry = history.get(i);
            String status = entry.isActive() ? "ACTIVE" : "REMOVED";
            NamedTextColor statusColor = entry.isActive() ? NamedTextColor.GREEN : NamedTextColor.RED;
            source.getSender().sendMessage(Component.text("  " + entry.modName()).color(NamedTextColor.YELLOW)
                .append(Component.text(" [" + status + "]").color(statusColor))
                .append(Component.text(" (added " + entry.getAddedDateFormatted() + ")").color(NamedTextColor.DARK_GRAY)));
        }

        if (totalPages > 1) {
            source.getSender().sendMessage(Component.text("Use /handshaker info player " + playerName + " <page> to navigate").color(NamedTextColor.DARK_GRAY));
        }
        
        return 1;
    }

    private int showConfig(CommandSourceStack source) {
        source.getSender().sendMessage(Component.text("═══════════════════════════════════").color(NamedTextColor.GOLD));
        source.getSender().sendMessage(Component.text("  HandShaker Configuration").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
        source.getSender().sendMessage(Component.text("═══════════════════════════════════").color(NamedTextColor.GOLD));
        source.getSender().sendMessage(Component.text("  Behavior: ").color(NamedTextColor.YELLOW).append(Component.text(config.getBehavior().toString()).color(NamedTextColor.WHITE)));
        source.getSender().sendMessage(Component.text("  Integrity Mode: ").color(NamedTextColor.YELLOW).append(Component.text(config.getIntegrityMode().toString()).color(NamedTextColor.WHITE)));
        source.getSender().sendMessage(Component.text("  Whitelist Mode: ").color(NamedTextColor.YELLOW).append(Component.text(config.isWhitelist() ? "ON" : "OFF").color(NamedTextColor.WHITE)));
        source.getSender().sendMessage(Component.text("  Bedrock Players: ").color(NamedTextColor.YELLOW).append(Component.text(config.isAllowBedrockPlayers() ? "Allowed" : "Blocked").color(NamedTextColor.WHITE)));
        source.getSender().sendMessage(Component.text("  Player Database: ").color(NamedTextColor.YELLOW).append(Component.text(config.isPlayerdbEnabled() ? "Enabled" : "Disabled").color(NamedTextColor.WHITE)));
        String requiredModpackHash = config.getRequiredModpackHash();
        source.getSender().sendMessage(Component.text("  Required Modpack Hash: ").color(NamedTextColor.YELLOW).append(Component.text(requiredModpackHash != null ? requiredModpackHash : "OFF").color(NamedTextColor.WHITE)));
        return 1;
    }

    private int setConfigValue(CommandSourceStack source, String param, String value) {
        switch (param.toLowerCase(Locale.ROOT)) {
            case "behavior" -> {
                if (!value.equalsIgnoreCase("STRICT") && !value.equalsIgnoreCase("VANILLA")) {
                    source.getSender().sendMessage(Component.text("Invalid behavior. Use STRICT or VANILLA").color(NamedTextColor.RED));
                    return 0;
                }
                config.setBehavior(value);
                source.getSender().sendMessage(Component.text("✓ Set behavior to " + value.toUpperCase()).color(NamedTextColor.GREEN));
                plugin.checkAllPlayers();
            }
            case "integrity" -> {
                if (!value.equalsIgnoreCase("SIGNED") && !value.equalsIgnoreCase("DEV")) {
                    source.getSender().sendMessage(Component.text("Invalid integrity mode. Use SIGNED or DEV").color(NamedTextColor.RED));
                    return 0;
                }
                config.setIntegrityMode(value);
                source.getSender().sendMessage(Component.text("✓ Set integrity mode to " + value.toUpperCase()).color(NamedTextColor.GREEN));
            }
            case "whitelist" -> {
                boolean whitelist = value.equalsIgnoreCase("true");
                config.setWhitelist(whitelist);
                source.getSender().sendMessage(Component.text("✓ Set whitelist mode to " + (whitelist ? "ON" : "OFF")).color(NamedTextColor.GREEN));
                plugin.checkAllPlayers();
            }
            case "allow_bedrock" -> {
                boolean bedrock = value.equalsIgnoreCase("true");
                config.setAllowBedrockPlayers(bedrock);
                source.getSender().sendMessage(Component.text("✓ Set bedrock players to " + (bedrock ? "allowed" : "disallowed")).color(NamedTextColor.GREEN));
                plugin.checkAllPlayers();
            }
            case "playerdb_enabled" -> {
                boolean enabled = value.equalsIgnoreCase("true");
                config.setPlayerdbEnabled(enabled);
                source.getSender().sendMessage(Component.text("✓ Set playerdb to " + (enabled ? "enabled" : "disabled")).color(NamedTextColor.GREEN));
            }
            case "hash_mods" -> {
                boolean enabled = value.equalsIgnoreCase("true");
                config.setHashMods(enabled);
                source.getSender().sendMessage(Component.text("✓ Set hash-mods to " + enabled).color(NamedTextColor.GREEN));
            }
            case "mod_versioning" -> {
                boolean enabled = value.equalsIgnoreCase("true");
                config.setModVersioning(enabled);
                source.getSender().sendMessage(Component.text("✓ Set mod-versioning to " + enabled).color(NamedTextColor.GREEN));
            }
            case "runtime_cache" -> {
                boolean enabled = value.equalsIgnoreCase("true");
                config.setRuntimeCache(enabled);
                source.getSender().sendMessage(Component.text("✓ Set runtime-cache to " + enabled).color(NamedTextColor.GREEN));
            }
            case "handshake_timeout" -> {
                try {
                    int seconds = Integer.parseInt(value.trim());
                    config.setHandshakeTimeoutSeconds(seconds);
                    source.getSender().sendMessage(Component.text("✓ Set handshake timeout to " + Math.max(1, seconds) + " seconds").color(NamedTextColor.GREEN));
                } catch (NumberFormatException e) {
                    source.getSender().sendMessage(Component.text("Handshake timeout must be a number of seconds").color(NamedTextColor.RED));
                    return 0;
                }
            }
            case "required_modpack_hash" -> {
                if (value.equalsIgnoreCase("current")) {
                    if (!(source.getSender() instanceof Player player)) {
                        source.getSender().sendMessage(Component.text("'current' can only be used by an in-game player").color(NamedTextColor.RED));
                        return 0;
                    }

                    ClientInfo info = plugin.getClients().get(player.getUniqueId());
                    if (info == null || info.mods() == null || info.mods().isEmpty()) {
                        source.getSender().sendMessage(Component.text("No client mod list available. Join with HandShaker client first.").color(NamedTextColor.RED));
                        return 0;
                    }

                    String computed = computeModpackHash(info.mods(), config.isHashMods());
                    config.setRequiredModpackHash(computed);
                    source.getSender().sendMessage(Component.text("✓ Set required_modpack_hash to current client hash: " + computed).color(NamedTextColor.GREEN));
                    plugin.checkAllPlayers();
                    break;
                }

                String normalized = normalizeRequiredModpackHash(value);
                if (normalized == null && !value.equalsIgnoreCase("off") && !value.equalsIgnoreCase("none") && !value.equalsIgnoreCase("null")) {
                    source.getSender().sendMessage(Component.text("required_modpack_hash must be 64-char SHA-256, 'off', or 'current'").color(NamedTextColor.RED));
                    return 0;
                }

                config.setRequiredModpackHash(normalized);
                source.getSender().sendMessage(Component.text("✓ Set required_modpack_hash to " + (normalized == null ? "OFF" : normalized)).color(NamedTextColor.GREEN));
                plugin.checkAllPlayers();
            }
            default -> {
                source.getSender().sendMessage(Component.text("Unknown config parameter: " + param).color(NamedTextColor.RED));
                return 0;
            }
        }
        config.save();
        return 1;
    }

    private int setMode(CommandSourceStack source, String listName, String action) {
        Boolean enable = parseEnableFlag(action);
        if (enable == null) {
            source.getSender().sendMessage(Component.text("Action must be on/off/true/false").color(NamedTextColor.RED));
            return 0;
        }

        switch (listName.toLowerCase(Locale.ROOT)) {
            case "mods_required" -> {
                config.setModsRequiredEnabledState(enable);
                source.getSender().sendMessage(Component.text("✓ Required Mods turned " + (enable ? "ON" : "OFF")).color(NamedTextColor.GREEN));
                config.save();
            }
            case "mods_blacklisted" -> {
                config.setModsBlacklistedEnabledState(enable);
                source.getSender().sendMessage(Component.text("✓ Blacklisted Mods turned " + (enable ? "ON" : "OFF")).color(NamedTextColor.GREEN));
                config.save();
            }
            case "mods_whitelisted" -> {
                config.setModsWhitelistedEnabledState(enable);
                source.getSender().sendMessage(Component.text("✓ Whitelisted Mods turned " + (enable ? "ON" : "OFF")).color(NamedTextColor.GREEN));
                config.save();
            }
            default -> {
                var logger = LoggerAdapter.fromLoaderLogger(plugin.getLogger());
                var toggleResult = ModListToggler.toggleListDetailed(plugin.getDataFolder().toPath(), listName, enable, logger);
                if (toggleResult.status() == ModListToggler.ToggleStatus.NOT_FOUND) {
                    source.getSender().sendMessage(Component.text("List not found: " + listName).color(NamedTextColor.RED));
                    return 0;
                }
                source.getSender().sendMessage(Component.text("✓ " + toggleResult.listFile().getFileName() + " enabled=" + enable).color(NamedTextColor.GREEN));
                config.load();
            }
        }
        plugin.checkAllPlayers();
        return 1;
    }

    private int addMod(CommandSourceStack source, String mod, String mode, String action) {
        String resolvedAction = action != null ? action : CommandModUtil.defaultActionForMode(mode);
        config.setModConfig(mod, mode, resolvedAction, null);
        registerModFingerprint(config, mod);
        config.save();
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

        String resolvedAction = action != null ? action : CommandModUtil.defaultActionForMode(mode);
        int added = 0;
        for (String mod : mods) {
            if (!config.isIgnored(mod)) {
                config.setModConfig(mod, mode, resolvedAction, null);
                registerModFingerprint(config, mod);
                added++;
            }
        }

        config.save();
        source.getSender().sendMessage(Component.text("✓ Added " + added + " of your mods as " + mode.toLowerCase()).color(NamedTextColor.GREEN));
        plugin.checkAllPlayers();
        return 1;
    }

    private int changeMod(CommandSourceStack source, String mod, String mode, String action) {
        String resolvedAction = action != null ? action : CommandModUtil.defaultActionForMode(mode);
        config.setModConfig(mod, mode, resolvedAction, null);
        config.save();
        source.getSender().sendMessage(Component.text("✓ Changed " + mod + " to " + mode.toLowerCase()).color(NamedTextColor.GREEN));
        plugin.checkAllPlayers();
        return 1;
    }

    private int removeMod(CommandSourceStack source, String mod) {
        boolean removed = config.removeModConfig(mod);
        if (removed) {
            config.save();
            source.getSender().sendMessage(Component.text("✓ Removed " + mod).color(NamedTextColor.GREEN));
        } else {
            source.getSender().sendMessage(Component.text(mod + " not found").color(NamedTextColor.YELLOW));
        }
        plugin.checkAllPlayers();
        return 1;
    }

    private int addIgnore(CommandSourceStack source, String mod) {
        config.addIgnoredMod(mod);
        config.save();
        source.getSender().sendMessage(Component.text("✓ Added " + mod + " to ignore list").color(NamedTextColor.GREEN));
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

        int added = 0;
        for (String mod : mods) {
            if (!config.isIgnored(mod)) {
                config.addIgnoredMod(mod);
                added++;
            }
        }
        config.save();
        source.getSender().sendMessage(Component.text("✓ Added " + added + " mods to ignore list").color(NamedTextColor.GREEN));
        return 1;
    }

    private int removeIgnore(CommandSourceStack source, String mod) {
        boolean removed = config.removeIgnoredMod(mod);
        if (removed) {
            config.save();
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

        source.getSender().sendMessage(Component.text("═══════════════════════════════════").color(NamedTextColor.GOLD));
        source.getSender().sendMessage(Component.text("  Ignored Mods (" + ignored.size() + ")").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
        source.getSender().sendMessage(Component.text("═══════════════════════════════════").color(NamedTextColor.GOLD));
        
        for (String mod : ignored) {
            source.getSender().sendMessage(Component.text("  • " + mod).color(NamedTextColor.YELLOW));
        }
        return 1;
    }

    private int showPlayerMods(CommandSourceStack source, String playerName) {
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

        if (!(source.getSender() instanceof Player)) {
            List<String> filteredMods = mods.stream()
                .filter(mod -> !config.isIgnored(mod))
                .collect(Collectors.toList());
            source.getSender().sendMessage(Component.text(target.getName() + "'s mods: ").color(NamedTextColor.YELLOW)
                .append(Component.text(String.join(", ", filteredMods)).color(NamedTextColor.WHITE)));
            return 1;
        }

        // Get mod history for additional info
        PlayerHistoryDatabase db = plugin.getPlayerHistoryDb();
        Map<String, PlayerHistoryDatabase.ModHistoryEntry> historyMap = new HashMap<>();
        if (db != null) {
            List<PlayerHistoryDatabase.ModHistoryEntry> history = db.getPlayerHistory(target.getUniqueId());
            for (PlayerHistoryDatabase.ModHistoryEntry entry : history) {
                if (entry.isActive()) {
                    historyMap.put(entry.modName(), entry);
                }
            }
        }

        source.getSender().sendMessage(Component.text("=== " + target.getName() + "'s Mods ===")
            .color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD));

        for (String mod : mods) {
            if (config.isIgnored(mod)) continue;
            
            ConfigState.ModConfig modCfg = config.getModConfig(mod);
            NamedTextColor statusColor = modCfg != null && modCfg.isRequired() ? NamedTextColor.GREEN 
                : modCfg != null && modCfg.isBlacklisted() ? NamedTextColor.RED 
                : NamedTextColor.YELLOW;
            
            Component modName = Component.text("  " + mod).color(NamedTextColor.WHITE);
            Component currentStatus = Component.text(" [" + (modCfg != null ? modCfg.getMode() : "unset") + "] ")
                .color(statusColor);
            
            // Action buttons
            Component allowedBtn = Component.text("[A]")
                .color(NamedTextColor.YELLOW)
                .hoverEvent(HoverEvent.showText(Component.text("Set as ALLOWED")))
                .clickEvent(ClickEvent.suggestCommand("/handshakerv2 manage change " + mod + " allowed"));
            
            Component requiredBtn = Component.text("[R]")
                .color(NamedTextColor.GREEN)
                .hoverEvent(HoverEvent.showText(Component.text("Set as REQUIRED")))
                .clickEvent(ClickEvent.suggestCommand("/handshakerv2 manage change " + mod + " required"));
            
            Component blacklistBtn = Component.text("[B]")
                .color(NamedTextColor.RED)
                .hoverEvent(HoverEvent.showText(Component.text("Set as BLACKLISTED")))
                .clickEvent(ClickEvent.suggestCommand("/handshakerv2 manage change " + mod + " blacklisted"));
            
            Component ignoreBtn = Component.text("[I]")
                .color(NamedTextColor.GRAY)
                .hoverEvent(HoverEvent.showText(Component.text("Add to IGNORE list")))
                .clickEvent(ClickEvent.suggestCommand("/handshakerv2 manage ignore add " + mod));
            
            Component line = modName.append(currentStatus)
                .append(allowedBtn).append(Component.text(" "))
                .append(requiredBtn).append(Component.text(" "))
                .append(blacklistBtn).append(Component.text(" "))
                .append(ignoreBtn);
            
            // Add timestamp if available
            if (historyMap.containsKey(mod)) {
                PlayerHistoryDatabase.ModHistoryEntry entry = historyMap.get(mod);
                line = line.append(Component.text(" §8(since " + entry.getAddedDateFormatted() + ")"));
            }
            
            source.getSender().sendMessage(line);
        }
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

        String resolvedAction = action != null ? action : CommandModUtil.defaultActionForMode(status);
        config.setModConfig(mod, status, resolvedAction, null);
        config.save();
        source.getSender().sendMessage(Component.text("✓ Set " + mod + " to " + status.toLowerCase()).color(NamedTextColor.GREEN));
        plugin.checkAllPlayers();
        return 1;
    }

    // ===== Suggestion Providers =====

    private CompletableFuture<Suggestions> suggestModes(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        for (String mode : CommandSuggestionData.MOD_MODES) {
            if (mode.toLowerCase().startsWith(builder.getRemaining().toLowerCase())) {
                builder.suggest(mode);
            }
        }
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestActions(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        List<String> actions = new ArrayList<>(CommandSuggestionData.DEFAULT_ACTIONS);
        actions.addAll(config.getAvailableActions());
        for (String action : actions) {
            if (action.toLowerCase().startsWith(builder.getRemaining().toLowerCase())) {
                builder.suggest(action);
            }
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
                for (String mod : sanitizeModSuggestions(clientMods)) {
                    suggestWithAutoQuote(builder, mod);
                }
                return builder.buildFuture();
            }
        }
        
        PlayerHistoryDatabase db = plugin.getPlayerHistoryDb();
        if (db == null) return builder.buildFuture();
        
        for (String mod : sanitizeModSuggestions(db.getModPopularity().keySet())) {
            suggestWithAutoQuote(builder, mod);
        }
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestConfiguredMods(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        for (String mod : config.getModConfigMap().keySet()) {
            suggestWithAutoQuote(builder, mod);
        }
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestAllMods(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        PlayerHistoryDatabase db = plugin.getPlayerHistoryDb();
        if (db == null) return builder.buildFuture();
        
        for (String mod : sanitizeModSuggestions(db.getModPopularity().keySet())) {
            suggestWithAutoQuote(builder, mod);
        }
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestPlayers(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getName().toLowerCase().startsWith(builder.getRemaining().toLowerCase())) {
                builder.suggest(player.getName());
            }
        }
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestIgnoredMods(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        for (String mod : config.getIgnoredMods()) {
            suggestWithAutoQuote(builder, mod);
        }
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestModeLists(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        for (String list : CommandSuggestionData.MODE_LISTS) {
            if (list.toLowerCase().startsWith(builder.getRemaining().toLowerCase())) {
                builder.suggest(list);
            }
        }
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestBool(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        for (String bool : CommandSuggestionData.BOOLEAN_VALUES) {
            if (bool.toLowerCase().startsWith(builder.getRemaining().toLowerCase())) {
                builder.suggest(bool);
            }
        }
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestConfigParams(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        for (String param : CommandSuggestionData.CONFIG_PARAMS) {
            if (param.toLowerCase().startsWith(builder.getRemaining().toLowerCase())) {
                builder.suggest(param);
            }
        }
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestConfigValues(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String param = ctx.getArgument("param", String.class).toLowerCase();
        List<String> suggestions = switch (param) {
            case "behavior" -> CommandSuggestionData.BEHAVIOR_MODES;
            case "integrity" -> CommandSuggestionData.INTEGRITY_MODES;
            case "whitelist", "allow_bedrock", "playerdb_enabled", "hash_mods", "mod_versioning", "runtime_cache" -> CommandSuggestionData.BOOLEAN_VALUES;
            case "required_modpack_hash" -> List.of("off", "current");
            case "default" -> {
                List<String> combined = new ArrayList<>(CommandSuggestionData.DEFAULT_ACTIONS);
                combined.addAll(config.getAvailableActions());
                yield combined;
            }
            default -> List.of();
        };
        
        for (String value : suggestions) {
            if (value.toLowerCase().startsWith(builder.getRemaining().toLowerCase())) {
                builder.suggest(value);
            }
        }
        return builder.buildFuture();
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
                    for (String mod : sanitizeModSuggestions(clientMods)) {
                        suggestWithAutoQuote(builder, mod);
                    }
                }
            }
        } catch (IllegalArgumentException e) {
            // playerName argument not yet filled or parsing context error - silently ignore
        }
        return builder.buildFuture();
    }

    private Set<String> sanitizeModSuggestions(Collection<String> rawMods) {
        Set<String> result = new LinkedHashSet<>();
        if (rawMods == null) {
            return result;
        }

        for (String rawMod : rawMods) {
            ModEntry entry = ModEntry.parse(rawMod);
            String displayKey = entry != null ? entry.toDisplayKey() : rawMod;
            
            if (displayKey != null && !displayKey.isBlank()) {
                result.add(displayKey);
            }
        }
        return result;
    }

    private void suggestWithAutoQuote(SuggestionsBuilder builder, String value) {
        String remaining = builder.getRemaining();
        boolean needsQuotes = value.contains(":") || value.contains("+") || value.contains(" ") || value.contains("!");
        
        // If user started with a quote, or value needs quotes, suggest with quotes
        if (remaining.startsWith("\"") || needsQuotes) {
            String quotedValue = "\"" + value + "\"";
            // Check against remaining with or without starting quote
            String checkAgainst = remaining.startsWith("\"") ? remaining.substring(1) : remaining;
            if (value.toLowerCase().startsWith(checkAgainst.toLowerCase())) {
                builder.suggest(quotedValue);
            }
        } else {
            // User hasn't typed quote and value is clean - suggest unquoted
            if (value.toLowerCase().startsWith(remaining.toLowerCase())) {
                builder.suggest(value);
            }
        }
    }

    // ===== Helpers =====

    private void registerModFingerprint(ConfigManager config, String modToken) {
        ModFingerprintRegistrar.registerFromCommand(
            modToken,
            plugin.getPlayerHistoryDb(),
            config.isHashMods(),
            config.isModVersioning(),
            plugin.getClients().values()
        );
    }

    private static Boolean parseEnableFlag(String value) {
        if (value == null) return null;
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "on", "true", "yes", "1" -> Boolean.TRUE;
            case "off", "false", "no", "0" -> Boolean.FALSE;
            default -> null;
        };
    }

    private static String normalizeRequiredModpackHash(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.equals("off") || normalized.equals("none") || normalized.equals("null")) {
            return null;
        }

        if (!normalized.matches("[0-9a-f]{64}")) {
            return null;
        }

        return normalized;
    }

    private static String computeModpackHash(Set<String> mods, boolean includeFileHashes) {
        return ModpackHashing.compute(mods, includeFileHashes);
    }

    private static String getModToken(CommandContext<CommandSourceStack> ctx, String argumentName) {
        return ctx.getArgument(argumentName, String.class);
    }

    private Component modeTagComponent(ConfigState.ModConfig modCfg) {
        if (modCfg == null) {
            return Component.text("[DET]").color(NamedTextColor.DARK_GRAY);
        }

        return switch (modCfg.getMode()) {
            case "required" -> Component.text("[REQ]").color(NamedTextColor.GOLD);
            case "blacklisted" -> Component.text("[BLK]").color(NamedTextColor.RED);
            case "allowed" -> Component.text("[ALW]").color(NamedTextColor.GREEN);
            default -> Component.text("[UNK]").color(NamedTextColor.DARK_GRAY);
        };
    }

    private String modeTagText(ConfigState.ModConfig modCfg) {
        if (modCfg == null) {
            return "§8[DET]";
        }

        return switch (modCfg.getMode()) {
            case "required" -> "§6[REQ]";
            case "blacklisted" -> "§c[BLK]";
            case "allowed" -> "§a[ALW]";
            default -> "§8[UNK]";
        };
    }

    private Component buildModComponent(String modName, NamedTextColor statusColor, String hoverText, String changeCommand) {
        return Component.text(modName)
            .color(statusColor)
            .hoverEvent(HoverEvent.showText(Component.text(hoverText)))
            .clickEvent(ClickEvent.suggestCommand(changeCommand));
    }

    private Component buildPlayerInfoComponent(PlayerHistoryDatabase.PlayerModInfo info) {
        String status = info.isActive() ? "✓ Active" : "✗ Removed";
        NamedTextColor statusColor = info.isActive() ? NamedTextColor.GREEN : NamedTextColor.RED;
        
        return Component.text(info.currentName())
            .color(NamedTextColor.AQUA)
            .append(Component.text(" - " + status).color(statusColor))
            .append(Component.text(" (Since: " + info.getFirstSeenFormatted() + ")").color(NamedTextColor.DARK_GRAY));
    }
}
