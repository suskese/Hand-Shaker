package me.mklv.handshaker.paper;

import me.mklv.handshaker.paper.utils.PlayerHistoryDatabase;
import me.mklv.handshaker.common.commands.CommandSuggestionData;
import me.mklv.handshaker.common.commands.CommandModUtil;
import me.mklv.handshaker.common.configs.ConfigTypes.ModEntry;
import me.mklv.handshaker.common.configs.ConfigFileBootstrap;
import me.mklv.handshaker.common.configs.ConfigTypes.ConfigState;
import me.mklv.handshaker.common.configs.ModListFiles;
import me.mklv.handshaker.common.utils.ClientInfo;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.*;
import java.util.stream.Collectors;

public class HandShakerCommand {
    private final HandShakerPlugin plugin;

    private static final int PAGE_SIZE = 10;

    public HandShakerCommand(HandShakerPlugin plugin) {
        this.plugin = plugin;
    }

    public static void register(HandShakerPlugin plugin) {
        HandShakerCommand cmdHandler = new HandShakerCommand(plugin);
        
        // Create command executor
        org.bukkit.command.CommandExecutor executor = (sender, cmd, label, args) -> cmdHandler.onCommand(sender, cmd, label, args);
        org.bukkit.command.TabCompleter completer = (sender, cmd, alias, args) -> cmdHandler.onTabComplete(sender, cmd, alias, args);
        
        // Register with Paper using CommandMap (this is the Paper way)
        org.bukkit.command.SimpleCommandMap cmdMap = null;
        try {
            java.lang.reflect.Field f = org.bukkit.Bukkit.getServer().getClass().getDeclaredField("commandMap");
            f.setAccessible(true);
            cmdMap = (org.bukkit.command.SimpleCommandMap) f.get(Bukkit.getServer());
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to get CommandMap: " + e.getMessage());
            return;
        }
        
        if (cmdMap != null) {
            org.bukkit.command.Command handShakerCmd = new org.bukkit.command.Command("handshaker") {
                @Override
                public boolean execute(CommandSender sender, String label, String[] args) {
                    return executor.onCommand(sender, this, label, args);
                }

                @Override
                public java.util.List<String> tabComplete(CommandSender sender, String alias, String[] args) {
                    return completer.onTabComplete(sender, this, alias, args);
                }
            };
            
            handShakerCmd.setDescription("HandShaker admin commands");
            handShakerCmd.setPermission("handshaker.admin");
            handShakerCmd.setUsage("/handshaker help");
            
            cmdMap.register("handshaker", "handshaker", handShakerCmd);
        }
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("handshaker.admin")) {
            sender.sendMessage("§cYou do not have permission to use this command.");
            return true;
        }
        
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }
        
        ConfigManager config = plugin.getConfigManager();
        String subcommand = args[0].toLowerCase(Locale.ROOT);
        
        switch (subcommand) {
            case "reload" -> {
                config.load();
                sender.sendMessage("§aHandShaker config reloaded. Re-checking all online players.");
                plugin.checkAllPlayers();
            }
            case "info" -> handleInfo(sender, args);
            case "config" -> handleConfig(sender, args, config);
            case "mode" -> handleMode(sender, args, config);
            case "manage" -> {
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /handshaker manage <add | change | remove | ignore | player>");
                    return true;
                }
                String manageCmd = args[1].toLowerCase(Locale.ROOT);
                switch (manageCmd) {
                    case "add" -> handleAdd(sender, args, config);
                    case "change" -> handleChange(sender, args, config);
                    case "remove" -> handleRemove(sender, args, config);
                    case "ignore" -> handleIgnore(sender, args, config);
                    case "player" -> handlePlayer(sender, args, config);
                    default -> sender.sendMessage("§cUsage: /handshaker manage <add | change | remove | ignore | player>");
                }
            }
            default -> sendUsage(sender);
        }
        return true;
    }

    private void handleRemove(CommandSender sender, String[] args, ConfigManager config) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /handshaker manage remove <mod>");
            return;
        }
        boolean removed = config.removeModConfig(args[2]);
        if (removed) {
            config.save();
        }
        sender.sendMessage(removed ? "§aRemoved " + args[2] : "§e" + args[2] + " not found.");
        plugin.checkAllPlayers();
    }

    private void handleConfig(CommandSender sender, String[] args, ConfigManager config) {
        if (args.length < 2) {
            if (sender instanceof Player player) {
                player.sendMessage(Component.text("═══════════════════════════════════").color(NamedTextColor.GOLD));
                player.sendMessage(Component.text("  HandShaker Configuration").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
                player.sendMessage(Component.text("═══════════════════════════════════").color(NamedTextColor.GOLD));
                player.sendMessage(Component.text("  Behavior: ").color(NamedTextColor.YELLOW).append(Component.text(config.getBehavior().toString()).color(NamedTextColor.WHITE)));
                player.sendMessage(Component.text("  Integrity Mode: ").color(NamedTextColor.YELLOW).append(Component.text(config.getIntegrityMode().toString()).color(NamedTextColor.WHITE)));
                player.sendMessage(Component.text("  Whitelist Mode: ").color(NamedTextColor.YELLOW).append(Component.text(config.isWhitelist() ? "ON" : "OFF").color(NamedTextColor.WHITE)));
                player.sendMessage(Component.text("  Bedrock Players: ").color(NamedTextColor.YELLOW).append(Component.text(config.isAllowBedrockPlayers() ? "Allowed" : "Blocked").color(NamedTextColor.WHITE)));
                player.sendMessage(Component.empty());
                player.sendMessage(Component.text("  Kick Messages:").color(NamedTextColor.GRAY).decorate(TextDecoration.ITALIC));
                player.sendMessage(Component.text("    Kick: ").color(NamedTextColor.GRAY).append(Component.text(config.getKickMessage()).color(NamedTextColor.DARK_GRAY)));
                player.sendMessage(Component.text("    No Handshake: ").color(NamedTextColor.GRAY).append(Component.text(config.getNoHandshakeKickMessage()).color(NamedTextColor.DARK_GRAY)));
                player.sendMessage(Component.text("    Invalid Signature: ").color(NamedTextColor.GRAY).append(Component.text(config.getInvalidSignatureKickMessage()).color(NamedTextColor.DARK_GRAY)));
                player.sendMessage(Component.text("    Missing Whitelist: ").color(NamedTextColor.GRAY).append(Component.text(config.getMissingWhitelistModMessage()).color(NamedTextColor.DARK_GRAY)));
                player.sendMessage(Component.empty());
                player.sendMessage(Component.text("Usage: ").color(NamedTextColor.GOLD).append(Component.text("/handshaker config <param> <value>").color(NamedTextColor.YELLOW)));
            } else {
                sender.sendMessage("§6§l=== HandShaker Configuration ===");
                sender.sendMessage("§eBehavior: §f" + config.getBehavior());
                sender.sendMessage("§eIntegrity Mode: §f" + config.getIntegrityMode());
                sender.sendMessage("§eWhitelist Mode: §f" + (config.isWhitelist() ? "ON" : "OFF"));
                sender.sendMessage("§eAllow Bedrock Players: §f" + (config.isAllowBedrockPlayers() ? "Yes" : "No"));
                sender.sendMessage("");
                sender.sendMessage("§6Usage: §e/handshaker config <param> <value>");
            }
            return;
        }
        
        String param = args[1].toLowerCase(Locale.ROOT);
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /handshaker config <param> <value>");
            return;
        }
        
        String value = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        
        switch (param) {
            case "behavior" -> {
                if (!value.equalsIgnoreCase("STRICT") && !value.equalsIgnoreCase("VANILLA")) {
                    sender.sendMessage("§cBehavior must be STRICT or VANILLA");
                    return;
                }
                config.setBehavior(value);
                sender.sendMessage("§aSet behavior to " + value.toUpperCase());
                plugin.checkAllPlayers();
            }
            case "integrity" -> {
                if (!value.equalsIgnoreCase("SIGNED") && !value.equalsIgnoreCase("DEV")) {
                    sender.sendMessage("§cIntegrity must be SIGNED or DEV");
                    return;
                }
                config.setIntegrityMode(value);
                sender.sendMessage("§aSet integrity mode to " + value.toUpperCase());
            }
            case "default" -> {
                if (!value.equalsIgnoreCase("ALLOWED") && !value.equalsIgnoreCase("BLACKLISTED")) {
                    sender.sendMessage("§cDefault mode must be ALLOWED or BLACKLISTED");
                    return;
                }
                config.setDefaultMode(value);
                sender.sendMessage("§aSet default mode to " + value.toUpperCase());
                plugin.checkAllPlayers();
            }
            case "whitelist" -> {
                boolean whitelist = value.equalsIgnoreCase("true");
                config.setWhitelist(whitelist);
                sender.sendMessage("§aSet whitelist mode to " + (whitelist ? "ON" : "OFF"));
                plugin.checkAllPlayers();
            }
            case "allow_bedrock" -> {
                boolean bedrock = value.equalsIgnoreCase("true");
                config.setAllowBedrockPlayers(bedrock);
                sender.sendMessage("§aSet bedrock players to " + (bedrock ? "allowed" : "disallowed"));
                plugin.checkAllPlayers();
            }
            case "playerdb_enabled" -> {
                boolean enabled = value.equalsIgnoreCase("true");
                config.setPlayerdbEnabled(enabled);
                sender.sendMessage("§aSet playerdb to " + (enabled ? "enabled" : "disabled"));
            }
            case "hash_mods" -> {
                boolean enabled = value.equalsIgnoreCase("true");
                config.setHashMods(enabled);
                sender.sendMessage("§aSet hash-mods to " + enabled);
            }
            case "mod_versioning" -> {
                boolean enabled = value.equalsIgnoreCase("true");
                config.setModVersioning(enabled);
                sender.sendMessage("§aSet mod-versioning to " + enabled);
            }
            case "handshake_timeout" -> {
                try {
                    int seconds = Integer.parseInt(value.trim());
                    config.setHandshakeTimeoutSeconds(seconds);
                    sender.sendMessage("§aSet handshake timeout to " + Math.max(1, seconds) + " seconds");
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cHandshake timeout must be a number of seconds");
                }
            }
            case "kick-message" -> {
                config.setKickMessage(value);
                sender.sendMessage("§aUpdated kick message");
            }
            case "no-handshake-message" -> {
                config.setNoHandshakeKickMessage(value);
                sender.sendMessage("§aUpdated no-handshake message");
            }
            case "invalid-signature-message" -> {
                config.setInvalidSignatureKickMessage(value);
                sender.sendMessage("§aUpdated invalid-signature message");
            }
            case "missing-whitelist-message" -> {
                config.setMissingWhitelistModMessage(value);
                sender.sendMessage("§aUpdated missing-whitelist message");
            }
            default -> sender.sendMessage("§cUnknown parameter: " + param);
        }
        
        config.save();
    }

    private void handleAdd(CommandSender sender, String[] args, ConfigManager config) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /handshaker manage add <mod | *> <allowed | required | blacklisted | optional> [action] [warn-message]");
            return;
        }
        
        String modId = args[2];
        if (modId.equals("*")) {
            if (!(sender instanceof Player senderPlayer)) {
                sender.sendMessage("§cThis command can only be used by players.");
                return;
            }
            
            ClientInfo info = plugin.getClients().get(senderPlayer.getUniqueId());
            Set<String> mods = info != null ? info.mods() : null;
            if (mods == null || mods.isEmpty()) {
                sender.sendMessage("§cNo mod list found for you. Make sure you're using a modded client.");
                return;
            }
            
            String mode = args[3];
            String action = args.length > 4 ? args[4] : CommandModUtil.defaultActionForMode(mode);
            String warnMessage = args.length > 5 ? String.join(" ", Arrays.copyOfRange(args, 5, args.length)) : null;
            
            int added = 0;
            for (String mod : mods) {
                if (!config.isIgnored(mod)) {
                    config.setModConfig(mod, mode, action, warnMessage);
                    registerModFingerprint(config, mod);
                    added++;
                }
            }

            if (added > 0) {
                config.save();
            }
            
            sender.sendMessage("§aAdded " + added + " of your mods as " + mode.toLowerCase());
            plugin.checkAllPlayers();
        } else {
            if (args.length < 4) {
                sender.sendMessage("§cUsage: /handshaker manage add <mod> <allowed|required|blacklisted|optional> [action] [warn-message]");
                return;
            }
            
            String mode = args[3];
            String action = args.length > 4 ? args[4] : CommandModUtil.defaultActionForMode(mode);
            String warnMessage = args.length > 5 ? String.join(" ", Arrays.copyOfRange(args, 5, args.length)) : null;
            
            config.setModConfig(modId, mode, action, warnMessage);
            registerModFingerprint(config, modId);
            config.save();
            sender.sendMessage("§aSet " + modId + " to " + mode.toLowerCase());
            plugin.checkAllPlayers();
        }
    }

    private void handleChange(CommandSender sender, String[] args, ConfigManager config) {
        if (args.length < 4) {
            sender.sendMessage("§cUsage: /handshaker manage change <mod> <allowed|required|blacklisted|optional> [action] [warn-message]");
            return;
        }
        
        String modId = args[2];
        String mode = args[3];
        String action = args.length > 4 ? args[4] : null;
        String warnMessage = args.length > 5 ? String.join(" ", Arrays.copyOfRange(args, 5, args.length)) : null;
        
        config.setModConfig(modId, mode, action, warnMessage);
        config.save();
        sender.sendMessage("§aChanged " + modId + " to " + mode.toLowerCase());
        plugin.checkAllPlayers();
    }

    private void registerModFingerprint(ConfigManager config, String modToken) {
        if (!config.isHashMods()) {
            return;
        }

        PlayerHistoryDatabase db = plugin.getPlayerHistoryDb();
        if (db == null) {
            return;
        }

        ModEntry requested = ModEntry.parse(modToken);
        if (requested == null) {
            return;
        }

        String requestedVersion = config.isModVersioning() ? requested.version() : null;
        String resolvedHash = CommandModUtil.normalizeHash(requested.hash());

        if (resolvedHash == null) {
            resolvedHash = CommandModUtil.resolveHashFromConnectedClients(plugin.getClients().values(), requested.modId(), requestedVersion);
        }

        db.registerModFingerprint(requested.modId(), requestedVersion, resolvedHash);
    }

    private void handleIgnore(CommandSender sender, String[] args, ConfigManager config) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /handshaker manage ignore <add | remove | list> [mod | *]");
            return;
        }
        
        switch (args[2].toLowerCase(Locale.ROOT)) {
            case "add" -> {
                if (args.length < 4) {
                    sender.sendMessage("§cUsage: /handshaker manage ignore add <mod | *>");
                    return;
                }
                
                if (args[3].equals("*")) {
                    if (!(sender instanceof Player senderPlayer)) {
                        sender.sendMessage("§cThis command can only be used by players.");
                        return;
                    }
                    
                    ClientInfo info = plugin.getClients().get(senderPlayer.getUniqueId());
                    Set<String> mods = info != null ? info.mods() : null;
                    if (mods == null || mods.isEmpty()) {
                        sender.sendMessage("§cNo mod list found for you.");
                        return;
                    }
                    
                    int added = 0;
                    for (String mod : mods) {
                        if (config.addIgnoredMod(mod)) {
                            added++;
                        }
                    }
                    if (added > 0) {
                        config.save();
                    }
                    sender.sendMessage("§aAdded " + added + " of your mods to ignore list");
                } else {
                    boolean added = config.addIgnoredMod(args[3]);
                    if (added) {
                        config.save();
                    }
                    sender.sendMessage(added ? "§aAdded " + args[3] + " to ignore list" : "§e" + args[3] + " already in ignore list");
                }
            }
            case "remove" -> {
                if (args.length < 4) {
                    sender.sendMessage("§cUsage: /handshaker manage ignore remove <mod>");
                    return;
                }
                boolean removed = config.removeIgnoredMod(args[3]);
                if (removed) {
                    config.save();
                }
                sender.sendMessage(removed ? "§aRemoved " + args[3] + " from ignore list" : "§e" + args[3] + " not in ignore list");
            }
            case "list" -> {
                Set<String> ignored = config.getIgnoredMods();
                if (ignored.isEmpty()) {
                    sender.sendMessage("§eNo mods in ignore list");
                    return;
                }
                sender.sendMessage("§6=== Ignored Mods ===");
                for (String mod : ignored) {
                    sender.sendMessage("§7  " + mod);
                }
            }
            default -> sender.sendMessage("§cUsage: /handshaker manage ignore <add | remove | list> [mod | *]");
        }
    }

    private void handleInfo(CommandSender sender, String[] args) {
        PlayerHistoryDatabase db = plugin.getPlayerHistoryDb();
        ConfigManager config = plugin.getConfigManager();
        if (db == null) {
            sender.sendMessage("§cPlayer history database not available");
            return;
        }
        
        if (args.length < 2) {
            showInfoSummary(sender, db, config);
            return;
        }
        
        String subcommand = args[1].toLowerCase(Locale.ROOT);
        switch (subcommand) {
            case "configured_mods" -> {
                int page = 1;
                if (args.length > 2) {
                    try {
                        page = Integer.parseInt(args[2]);
                    } catch (NumberFormatException e) {
                        sender.sendMessage("§cInvalid page number");
                        return;
                    }
                }
                showConfiguredMods(sender, config, page);
            }
            case "all_mods" -> {
                int page = 1;
                if (args.length > 2) {
                    try {
                        page = Integer.parseInt(args[2]);
                    } catch (NumberFormatException e) {
                        sender.sendMessage("§cInvalid page number");
                        return;
                    }
                }
                showAllMods(sender, db, config, page);
            }
            case "player" -> {
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /handshaker info player <player> [page]");
                    return;
                }
                int page = 1;
                if (args.length > 3) {
                    try {
                        page = Integer.parseInt(args[3]);
                    } catch (NumberFormatException e) {
                        sender.sendMessage("§cInvalid page number");
                        return;
                    }
                }
                showPlayerHistory(sender, db, args[2], page);
            }
            case "mod" -> {
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /handshaker info mod <modname>");
                    return;
                }
                showModInfo(sender, db, args[2]);
            }
            default -> sender.sendMessage("§cUsage: /handshaker info [configured_mods [page] | all_mods [page] | mod <modname> | player <player> [page]]");
        }
    }

    private void showInfoSummary(CommandSender sender, PlayerHistoryDatabase db, ConfigManager config) {
        Map<String, Integer> popularity = db.getModPopularity();
        int uniqueMods = popularity.size();
        int configuredMods = config.getModConfigMap().size();
        int activePlayers = db.getUniqueActivePlayers();
        
        sender.sendMessage("§6§l=== HandShaker Statistics ===");
        sender.sendMessage("§eUnique Mods Detected: §f" + uniqueMods);
        sender.sendMessage("§eConfigured Mods: §f" + configuredMods);
        sender.sendMessage("§eActive Players: §f" + activePlayers);
        sender.sendMessage("");
        sender.sendMessage("§6§l=== HandShaker Status ===");
        sender.sendMessage("§eBehavior: §f" + config.getBehavior());
        sender.sendMessage("§eIntegrity Mode: §f" + config.getIntegrityMode());
        sender.sendMessage("§eWhitelist Mode: §f" + (config.isWhitelist() ? "ON" : "OFF"));
        sender.sendMessage("§eHandshake Timeout: §f" + config.getHandshakeTimeoutSeconds() + "s");
        sender.sendMessage("§eMod Lists: §frequired=" + (config.areModsRequiredEnabled() ? "on" : "off")
            + ", blacklisted=" + (config.areModsBlacklistedEnabled() ? "on" : "off")
            + ", whitelisted=" + (config.areModsWhitelistedEnabled() ? "on" : "off"));
        sender.sendMessage("");
        sender.sendMessage("§eUse §f/handshaker info configured_mods [page] §eto list configured mods");
        sender.sendMessage("§eUse §f/handshaker info all_mods [page] §eto see all detected mods");
        sender.sendMessage("§eUse §f/handshaker info player <player> [page] §eto see player history");
    }

    private void showConfiguredMods(CommandSender sender, ConfigManager config, int pageNum) {
        Map<String, ConfigState.ModConfig> mods = config.getModConfigMap();
        if (mods.isEmpty()) {
            sender.sendMessage("§eNo mods configured. Whitelist mode: " + (config.isWhitelist() ? "ON" : "OFF"));
            return;
        }

        List<Map.Entry<String, ConfigState.ModConfig>> modList = new ArrayList<>(mods.entrySet());
        modList.sort(Map.Entry.comparingByKey());
        int totalPages = (int) Math.ceil((double) modList.size() / PAGE_SIZE);
        if (pageNum < 1 || pageNum > totalPages) {
            sender.sendMessage("§cInvalid page. Total pages: " + totalPages);
            return;
        }
        int startIdx = (pageNum - 1) * PAGE_SIZE;
        int endIdx = Math.min(startIdx + PAGE_SIZE, modList.size());

        sender.sendMessage("§6=== Configured Mods (Page " + pageNum + "/" + totalPages + ", Whitelist: "
            + (config.isWhitelist() ? "ON" : "OFF") + ") ===");
        
        if (!(sender instanceof Player player)) {
            for (int i = startIdx; i < endIdx; i++) {
                Map.Entry<String, ConfigState.ModConfig> entry = modList.get(i);
                ConfigState.ModConfig modCfg = entry.getValue();
                String actionStr = modCfg.getAction() != ConfigState.Action.KICK
                    ? " §7[" + modCfg.getAction().toString().toLowerCase(Locale.ROOT) + "]"
                    : "";
                sender.sendMessage(modeTagText(modCfg) + " §e" + entry.getKey() + actionStr);
            }
        } else {
            for (int i = startIdx; i < endIdx; i++) {
                Map.Entry<String, ConfigState.ModConfig> entry = modList.get(i);
                ConfigState.ModConfig modCfg = entry.getValue();
                NamedTextColor statusColor = modCfg.isRequired() ? NamedTextColor.GREEN 
                    : modCfg.isBlacklisted() ? NamedTextColor.RED 
                    : NamedTextColor.YELLOW;
                
                String actionStr = modCfg.getAction() != ConfigState.Action.KICK ? " " + modCfg.getAction() : "";
                String hoverText = modCfg.getMode() + actionStr + "\n\nClick to change mode";
                
                Component modComponent = buildModComponentWithRemove(entry.getKey(), statusColor, hoverText);
                Component line = modeTagComponent(modCfg)
                    .append(Component.text(" ").color(NamedTextColor.GRAY))
                    .append(modComponent);
                if (modCfg.getAction() != ConfigState.Action.KICK) {
                    line = line.append(Component.text(" [" + modCfg.getAction().toString().toLowerCase(Locale.ROOT) + "]")
                        .color(NamedTextColor.DARK_GRAY));
                }
                player.sendMessage(line);
            }
        }

        if (totalPages > 1) {
            sender.sendMessage("§7Use §f/handshaker info configured_mods <page> §7to navigate");
        }
    }

    private void showAllMods(CommandSender sender, PlayerHistoryDatabase db, ConfigManager config, int pageNum) {
        Map<String, Integer> popularity = db.getModPopularity();
        List<Map.Entry<String, Integer>> sortedMods = new ArrayList<>(popularity.entrySet());
        sortedMods.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        
        int totalPages = (int) Math.ceil((double) sortedMods.size() / PAGE_SIZE);
        
        if (pageNum < 1 || pageNum > totalPages) {
            sender.sendMessage("§cInvalid page. Total pages: " + totalPages);
            return;
        }
        
        int startIdx = (pageNum - 1) * PAGE_SIZE;
        int endIdx = Math.min(startIdx + PAGE_SIZE, sortedMods.size());
        
        sender.sendMessage("§6=== All Detected Mods (Page " + pageNum + "/" + totalPages + ") ===");
        
        if (!(sender instanceof Player player)) {
            for (int i = startIdx; i < endIdx; i++) {
                Map.Entry<String, Integer> entry = sortedMods.get(i);
                String modName = entry.getKey();
                int count = entry.getValue();
                
                if (config.isIgnored(modName)) continue;
                
                ConfigState.ModConfig modCfg = config.getModConfig(modName);
                sender.sendMessage(modeTagText(modCfg) + " §e" + modName + " §7x" + count);
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
                Component modComponent = buildModComponent(modName, statusColor, hoverText, "/handshaker manage change " + modName);
                Component line = modeTagComponent(modCfg)
                    .append(Component.text(" ").color(NamedTextColor.GRAY))
                    .append(modComponent)
                    .append(Component.text(" x" + count).color(NamedTextColor.GRAY));
                player.sendMessage(line);
            }
        }
        
        if (totalPages > 1) {
            sender.sendMessage("§7Use §f/handshaker info all_mods <page> §7to navigate");
        }
    }

    private void showPlayerHistory(CommandSender sender, PlayerHistoryDatabase db, String playerName, int pageNum) {
        UUID uuid = null;
        Player online = Bukkit.getPlayerExact(playerName);
        if (online != null) {
            uuid = online.getUniqueId();
        } else {
            uuid = db.getPlayerUuidByName(playerName).orElse(null);
        }

        if (uuid == null) {
            sender.sendMessage("§cNo player history found for: " + playerName);
            return;
        }

        List<PlayerHistoryDatabase.ModHistoryEntry> history = db.getPlayerHistory(uuid);
        if (history.isEmpty()) {
            sender.sendMessage("§eNo mod history found for: " + playerName);
            return;
        }

        int totalPages = (int) Math.ceil((double) history.size() / PAGE_SIZE);
        if (pageNum < 1 || pageNum > totalPages) {
            sender.sendMessage("§cInvalid page. Total pages: " + totalPages);
            return;
        }
        int startIdx = (pageNum - 1) * PAGE_SIZE;
        int endIdx = Math.min(startIdx + PAGE_SIZE, history.size());

        sender.sendMessage("§6=== Mod History: " + playerName + " (Page " + pageNum + "/" + totalPages + ") ===");
        for (int i = startIdx; i < endIdx; i++) {
            PlayerHistoryDatabase.ModHistoryEntry entry = history.get(i);
            String status = entry.isActive() ? "ACTIVE" : "REMOVED";
            String dates = "Added: " + entry.getAddedDateFormatted();
            if (!entry.isActive() && entry.getRemovedDateFormatted() != null) {
                dates += " | Removed: " + entry.getRemovedDateFormatted();
            }
            sender.sendMessage("§e" + entry.modName() + " §7[" + status + "] §8" + dates);
        }

        if (totalPages > 1) {
            sender.sendMessage("§7Use §f/handshaker info player " + playerName + " <page> §7to navigate");
        }
    }

    private void showModInfo(CommandSender sender, PlayerHistoryDatabase db, String modName) {
        List<PlayerHistoryDatabase.PlayerModInfo> players = db.getPlayersWithMod(modName);
        
        if (players.isEmpty()) {
            sender.sendMessage("§eNo players found with mod: " + modName);
            return;
        }
        
        sender.sendMessage("§6=== Mod: " + modName + " ===");
        sender.sendMessage("§eTotal Players: §f" + players.size());
        sender.sendMessage("");
        
        if (!(sender instanceof Player player)) {
            for (PlayerHistoryDatabase.PlayerModInfo info : players) {
                String status = info.isActive() ? "Active" : "Removed";
                sender.sendMessage("§f" + info.currentName() + " §7[" + status + "] §8(Since: " + info.getFirstSeenFormatted() + ")");
            }
        } else {
            for (PlayerHistoryDatabase.PlayerModInfo info : players) {
                player.sendMessage(buildPlayerInfoComponent(info));
            }
        }
    }

    private void handlePlayer(CommandSender sender, String[] args, ConfigManager config) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /handshaker manage player <player> [mod] [status]");
            return;
        }
        
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            sender.sendMessage("§cPlayer '" + args[2] + "' not found.");
            return;
        }
        
        ClientInfo info = plugin.getClients().get(target.getUniqueId());
        Set<String> mods = info != null ? info.mods() : null;
        if (mods == null || mods.isEmpty()) {
            sender.sendMessage("§cNo mod list found for " + target.getName() + ".");
            return;
        }
        
        if (args.length >= 5) {
            String modId = args[3];
            if (!mods.contains(modId)) {
                sender.sendMessage("§cPlayer " + target.getName() + " does not have mod: " + modId);
                return;
            }
            
            String mode = args[4];
            config.setModConfig(modId, mode, null, null);
            config.save();
            sender.sendMessage("§aSet " + modId + " to " + mode.toLowerCase());
            plugin.checkAllPlayers();
            return;
        }
        
        if (!(sender instanceof Player player)) {
            List<String> filteredMods = mods.stream()
                .filter(mod -> !config.isIgnored(mod))
                .collect(Collectors.toList());
            sender.sendMessage(target.getName() + "'s mods: " + String.join(", ", filteredMods));
            return;
        }
        
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
        
        player.sendMessage(Component.text("=== " + target.getName() + "'s Mods ===").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
        
        for (String mod : mods) {
            if (config.isIgnored(mod)) continue;
            
            ConfigState.ModConfig modCfg = config.getModConfig(mod);
            NamedTextColor statusColor = modCfg.isRequired() ? NamedTextColor.GREEN 
                : modCfg.isBlacklisted() ? NamedTextColor.RED 
                : NamedTextColor.YELLOW;
            
            Component modName = Component.text("  " + mod).color(NamedTextColor.WHITE);
            Component currentStatusComp = Component.text(" [" + modCfg.getMode() + "] ").color(statusColor);
            
            Component allowedBtn = Component.text("[A]")
                .color(NamedTextColor.YELLOW)
                .hoverEvent(HoverEvent.showText(Component.text("Set as ALLOWED")))
                .clickEvent(ClickEvent.suggestCommand("/handshaker manage change " + mod + " allowed"));
            
            Component requiredBtn = Component.text("[R]")
                .color(NamedTextColor.GREEN)
                .hoverEvent(HoverEvent.showText(Component.text("Set as REQUIRED")))
                .clickEvent(ClickEvent.suggestCommand("/handshaker manage change " + mod + " required"));
            
            Component blacklistedBtn = Component.text("[B]")
                .color(NamedTextColor.RED)
                .hoverEvent(HoverEvent.showText(Component.text("Set as BLACKLISTED")))
                .clickEvent(ClickEvent.suggestCommand("/handshaker manage change " + mod + " blacklisted"));
            
            Component fullLine = modName.append(currentStatusComp).append(allowedBtn).append(Component.text(" ")).append(requiredBtn).append(Component.text(" ")).append(blacklistedBtn);
            
            if (historyMap.containsKey(mod)) {
                PlayerHistoryDatabase.ModHistoryEntry entry = historyMap.get(mod);
                String hoverText = "Added: " + entry.getAddedDateFormatted();
                String removedDate = entry.getRemovedDateFormatted();
                if (removedDate != null) {
                    hoverText += "\nRemoved: " + removedDate;
                }
                fullLine = fullLine.hoverEvent(HoverEvent.showText(Component.text(hoverText).color(NamedTextColor.GRAY)));
            }
            
            player.sendMessage(fullLine);
        }
    }

    private void handleMode(CommandSender sender, String[] args, ConfigManager config) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /handshaker mode <list> <on|off|true|false>");
            return;
        }

        String listName = args[1].toLowerCase(Locale.ROOT);
        String action = args[2].toLowerCase(Locale.ROOT);

        Boolean enable = parseEnableFlag(action);
        if (enable == null) {
            sender.sendMessage("§cAction must be on/off/true/false");
            return;
        }

        switch (listName) {
            case "mods_required" -> {
                config.setModsRequiredEnabledState(enable);
                sender.sendMessage("§aRequired Mods turned " + (enable ? "ON" : "OFF"));
                config.save();
                plugin.checkAllPlayers();
            }
            case "mods_blacklisted" -> {
                config.setModsBlacklistedEnabledState(enable);
                sender.sendMessage("§aBlacklisted Mods turned " + (enable ? "ON" : "OFF"));
                config.save();
                plugin.checkAllPlayers();
            }
            case "mods_whitelisted" -> {
                config.setModsWhitelistedEnabledState(enable);
                sender.sendMessage("§aWhitelisted Mods turned " + (enable ? "ON" : "OFF"));
                config.save();
                plugin.checkAllPlayers();
            }
            default -> {
                var logger = new ConfigFileBootstrap.Logger() {
                    @Override public void info(String message) { plugin.getLogger().info(message); }
                    @Override public void warn(String message) { plugin.getLogger().warning(message); }
                    @Override public void error(String message, Throwable error) { plugin.getLogger().severe(message + ": " + error.getMessage()); }
                };

                java.nio.file.Path configDir = plugin.getDataFolder().toPath();
                java.nio.file.Path listFile = ModListFiles.findListFile(configDir, listName);
                if (listFile == null) {
                    sender.sendMessage("§cUnknown list file: " + listName + " (expected <name>.yml in plugin folder)");
                    return;
                }

                if (!ModListFiles.setListEnabled(listFile, enable, logger)) {
                    sender.sendMessage("§cFailed to update list file: " + listFile.getFileName());
                    return;
                }

                config.load();
                sender.sendMessage("§a" + listFile.getFileName() + " enabled=" + (enable ? "true" : "false"));
                plugin.checkAllPlayers();
            }
        }
    }

    private static Boolean parseEnableFlag(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim().toLowerCase(Locale.ROOT);
        return switch (v) {
            case "on", "true", "yes", "1" -> Boolean.TRUE;
            case "off", "false", "no", "0" -> Boolean.FALSE;
            default -> null;
        };
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage("§6=== HandShaker v6 Commands ===");
        sender.sendMessage("§e§lCore Commands:");
        sender.sendMessage("§e/handshaker reload §7 | §7Reload config");
        sender.sendMessage("§e/handshaker info [configured_mods [page]|all_mods [page]|mod <modname>|player <player> [page]] §7 | §7Show stats or lists");
        sender.sendMessage("§e/handshaker config [param] [value] §7 | §7View/change configuration");
        sender.sendMessage("§e/handshaker mode <mods_required|mods_blacklisted|mods_whitelisted> <on|off> §7 | §7Toggle mod lists");
        sender.sendMessage("");
        sender.sendMessage("§e§lMod Management (/handshaker manage):");
        sender.sendMessage("§e/handshaker manage add <mod | *> <status> [action] [warn-message] §7 | §7Add/set mod status");
        sender.sendMessage("§e/handshaker manage change <mod> <status> [action] [warn-message] §7 | §7Change mod status");
        sender.sendMessage("§e/handshaker manage remove <mod> §7 | §7Remove mod from config");
        sender.sendMessage("§e/handshaker manage ignore <add | remove | list> [mod | *] §7 | §7Manage ignored mods");
        sender.sendMessage("§e/handshaker manage player <player> [mod] [status] §7 | §7View/set player's mods");
    }

    private Component buildModComponent(String modName, NamedTextColor statusColor, String hoverText, String changeCommand) {
        return Component.text(modName)
            .color(statusColor)
            .hoverEvent(HoverEvent.showText(Component.text(hoverText)))
            .clickEvent(ClickEvent.suggestCommand(changeCommand));
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
    
    private Component buildModComponentWithRemove(String modName, NamedTextColor statusColor, String hoverText) {
        return buildModComponent(modName, statusColor, hoverText, "/handshaker manage change " + modName)
            .append(Component.text(" § ").color(NamedTextColor.DARK_GRAY))
            .append(Component.text("[Remove]")
                .color(NamedTextColor.RED)
                .hoverEvent(HoverEvent.showText(Component.text("Click to remove")))
                .clickEvent(ClickEvent.runCommand("/handshaker manage remove " + modName)));
    }

    private Component buildPlayerInfoComponent(PlayerHistoryDatabase.PlayerModInfo info) {
        String status = info.isActive() ? "✓ Active" : "✗ Removed";
        NamedTextColor statusColor = info.isActive() ? NamedTextColor.GREEN : NamedTextColor.RED;
        
        return Component.text(info.currentName())
            .color(NamedTextColor.AQUA)
            .append(Component.text(" - " + status).color(statusColor))
            .append(Component.text(" (Since: " + info.getFirstSeenFormatted() + ")").color(NamedTextColor.DARK_GRAY));
    }

    private List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return StringUtil.copyPartialMatches(args[0], CommandSuggestionData.ROOT_COMMANDS, new ArrayList<>());
        }

        if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "info" -> { return StringUtil.copyPartialMatches(args[1], CommandSuggestionData.INFO_SUBCOMMANDS, new ArrayList<>()); }
                case "config" -> { return StringUtil.copyPartialMatches(args[1], CommandSuggestionData.CONFIG_PARAMS, new ArrayList<>()); }
                case "mode" -> { return StringUtil.copyPartialMatches(args[1], CommandSuggestionData.MODE_LISTS, new ArrayList<>()); }
                case "manage" -> { return StringUtil.copyPartialMatches(args[1], CommandSuggestionData.MANAGE_SUBCOMMANDS, new ArrayList<>()); }
            }
        }
        if (args.length == 3 )
            {
                if (args[0].equalsIgnoreCase("mode") && (!args[1].isEmpty())) {
                    return StringUtil.copyPartialMatches(args[2], CommandSuggestionData.BOOLEAN_VALUES, new ArrayList<>());
                }


            }
        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("info") && args[1].equalsIgnoreCase("mod")) {
                PlayerHistoryDatabase db = plugin.getPlayerHistoryDb();
                if (db != null) {
                    Map<String, Integer> popularity = db.getModPopularity();
                    return StringUtil.copyPartialMatches(args[2], sanitizeModSuggestions(popularity.keySet()), new ArrayList<>());
                }
                return new ArrayList<>();
            }
            if (args[0].equalsIgnoreCase("info") && args[1].equalsIgnoreCase("player")) {
                List<String> playerNames = Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
                return StringUtil.copyPartialMatches(args[2], playerNames, new ArrayList<>());
            }
            if (args[0].equalsIgnoreCase("config") && !args[1].isEmpty()) {
                String param = args[1].toLowerCase(Locale.ROOT);
                switch (param) {
                    case "behavior" -> { return StringUtil.copyPartialMatches(args[2], CommandSuggestionData.BEHAVIOR_MODES, new ArrayList<>()); }
                    case "integrity" -> { return StringUtil.copyPartialMatches(args[2], CommandSuggestionData.INTEGRITY_MODES, new ArrayList<>()); }
                    case "whitelist", "allow_bedrock", "playerdb_enabled", "hash_mods", "mod_versioning" -> { return StringUtil.copyPartialMatches(args[2], CommandSuggestionData.BOOLEAN_VALUES, new ArrayList<>()); }
                }
            }
        }

        if (args.length >= 3 && args[0].equalsIgnoreCase("manage")) {
            String manageCmd = args[1].toLowerCase(Locale.ROOT);
            
            if (args.length == 3) {
                switch (manageCmd) {
                    case "remove", "change" -> { return StringUtil.copyPartialMatches(args[2], plugin.getConfigManager().getModConfigMap().keySet(), new ArrayList<>()); }
                    case "add" -> {
                        List<String> suggestions = new ArrayList<>(Arrays.asList("*"));
                        if (sender instanceof Player p) {
                            ClientInfo info = plugin.getClients().get(p.getUniqueId());
                            Set<String> clientMods = info != null ? info.mods() : null;
                            if (clientMods != null) suggestions.addAll(sanitizeModSuggestions(clientMods));
                        }
                        return StringUtil.copyPartialMatches(args[2], suggestions, new ArrayList<>());
                    }
                    case "player" -> {
                        List<String> playerNames = Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
                        return StringUtil.copyPartialMatches(args[2], playerNames, new ArrayList<>());
                    }
                    case "ignore" -> { return StringUtil.copyPartialMatches(args[2], CommandSuggestionData.IGNORE_SUBCOMMANDS, new ArrayList<>()); }
                }
            }

            if (args.length == 4) {
                if ((manageCmd.equals("add") || manageCmd.equals("change"))) {
                    return StringUtil.copyPartialMatches(args[3], CommandSuggestionData.MOD_MODES, new ArrayList<>());
                }
                if (manageCmd.equals("player")) {
                    Player target = Bukkit.getPlayer(args[2]);
                    if (target != null) {
                        ClientInfo info = plugin.getClients().get(target.getUniqueId());
                        Set<String> clientMods = info != null ? info.mods() : null;
                        if (clientMods != null) {
                            return StringUtil.copyPartialMatches(args[3], sanitizeModSuggestions(clientMods), new ArrayList<>());
                        }
                    }
                }
                if (manageCmd.equals("ignore") && args[2].equalsIgnoreCase("add")) {
                    List<String> suggestions = new ArrayList<>(Arrays.asList("*"));
                    if (sender instanceof Player p) {
                        ClientInfo info = plugin.getClients().get(p.getUniqueId());
                        Set<String> clientMods = info != null ? info.mods() : null;
                        if (clientMods != null) suggestions.addAll(sanitizeModSuggestions(clientMods));
                    }
                    return StringUtil.copyPartialMatches(args[3], suggestions, new ArrayList<>());
                }
                if (manageCmd.equals("ignore") && args[2].equalsIgnoreCase("remove")) {
                    return StringUtil.copyPartialMatches(args[3], plugin.getConfigManager().getIgnoredMods(), new ArrayList<>());
                }
            }

            if (args.length == 5 && (manageCmd.equals("add") || manageCmd.equals("change"))) {
                List<String> availableActions = new ArrayList<>(plugin.getConfigManager().getAvailableActions());
                return StringUtil.copyPartialMatches(args[4], availableActions, new ArrayList<>());
            }
        }
        
        return Collections.emptyList();
    }

    private Set<String> sanitizeModSuggestions(Collection<String> rawMods) {
        Set<String> result = new LinkedHashSet<>();
        if (rawMods == null) {
            return result;
        }

        for (String rawMod : rawMods) {
            ModEntry entry = ModEntry.parse(rawMod);
            if (entry != null) {
                result.add(entry.toDisplayKey());
            } else if (rawMod != null && !rawMod.isBlank()) {
                result.add(rawMod);
            }
        }
        return result;
    }
}
