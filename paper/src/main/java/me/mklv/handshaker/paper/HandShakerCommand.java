package me.mklv.handshaker.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.*;
import java.util.stream.Collectors;

public class HandShakerCommand implements TabExecutor {
    private final HandShakerPlugin plugin;

    public HandShakerCommand(HandShakerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Check admin permission
        if (!sender.hasPermission("handshaker.admin")) {
            sender.sendMessage("§cYou do not have permission to use this command.");
            return true;
        }
        
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }
        
        BlacklistConfig config = plugin.getBlacklistConfig();
        
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                config.load();
                sender.sendMessage("§aHandShaker config reloaded. Re-checking all online players.");
                plugin.checkAllPlayers();
            }
            case "add" -> handleAdd(sender, args, config);
            case "change" -> handleChange(sender, args, config);
            case "remove" -> {
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /handshaker remove <mod>");
                    return true;
                }
                boolean removed = config.removeModConfig(args[1]);
                sender.sendMessage(removed ? "§aRemoved " + args[1] : "§e" + args[1] + " not found.");
            }
            case "list" -> handleList(sender, config);
            case "ignore" -> handleIgnore(sender, args, config);
            case "info" -> handleInfo(sender, args);
            case "player" -> handlePlayer(sender, args, config);
            default -> sendUsage(sender);
        }
        return true;
    }

    private void handleAdd(CommandSender sender, String[] args, BlacklistConfig config) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /handshaker add <mod|*> <allowed|required|blacklisted> [action] [warn-message]");
            return;
        }
        
        String modId = args[1];
        
        if (modId.equals("*")) {
            if (args.length < 3) {
                sender.sendMessage("§cUsage: /handshaker add * <allowed|required|blacklisted> [action] [warn-message]");
                return;
            }
            
            if (!(sender instanceof Player senderPlayer)) {
                sender.sendMessage("§cThis command can only be used by players.");
                return;
            }
            
            Set<String> mods = plugin.getClientMods(senderPlayer.getUniqueId());
            if (mods == null || mods.isEmpty()) {
                sender.sendMessage("§cNo mod list found for you. Make sure you're using a modded client.");
                return;
            }
            
            String mode = args[2];
            String action = args.length > 3 ? args[3] : "kick";
            String warnMessage = args.length > 4 ? String.join(" ", Arrays.copyOfRange(args, 4, args.length)) : null;
            
            int added = 0;
            for (String mod : mods) {
                if (!config.isIgnored(mod)) {
                    config.setModConfig(mod, mode, action, warnMessage);
                    added++;
                }
            }
            
            sender.sendMessage("§aAdded " + added + " of your mods as " + mode.toLowerCase());
            plugin.checkAllPlayers();
        } else {
            if (args.length < 3) {
                sender.sendMessage("§cUsage: /handshaker add <mod> <allowed|required|blacklisted> [action] [warn-message]");
                return;
            }
            
            String mode = args[2];
            String action = args.length > 3 ? args[3] : "kick";
            String warnMessage = args.length > 4 ? String.join(" ", Arrays.copyOfRange(args, 4, args.length)) : null;
            
            config.setModConfig(modId, mode, action, warnMessage);
            sender.sendMessage("§aSet " + modId + " to " + mode.toLowerCase());
            plugin.checkAllPlayers();
        }
    }

    private void handleChange(CommandSender sender, String[] args, BlacklistConfig config) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /handshaker change <mod> <allowed|required|blacklisted> [action] [warn-message]");
            return;
        }
        
        String modId = args[1];
        String mode = args[2];
        String action = args.length > 3 ? args[3] : null;
        String warnMessage = args.length > 4 ? String.join(" ", Arrays.copyOfRange(args, 4, args.length)) : null;
        
        config.setModConfig(modId, mode, action, warnMessage);
        sender.sendMessage("§aChanged " + modId + " to " + mode.toLowerCase());
        plugin.checkAllPlayers();
    }

    private void handleList(CommandSender sender, BlacklistConfig config) {
        Map<String, BlacklistConfig.ModConfig> mods = config.getModConfigMap();
        if (mods.isEmpty()) {
            sender.sendMessage("§eNo mods configured. Default mode: " + config.getDefaultMode());
            return;
        }
        
        sender.sendMessage("§6=== Configured Mods (Default: " + config.getDefaultMode() + ") ===");
        
        if (!(sender instanceof Player player)) {
            for (Map.Entry<String, BlacklistConfig.ModConfig> entry : mods.entrySet()) {
                BlacklistConfig.ModConfig modCfg = entry.getValue();
                String actionStr = modCfg.getAction() != BlacklistConfig.Action.KICK ? " [" + modCfg.getAction() + "]" : "";
                sender.sendMessage("§e" + entry.getKey() + " §7- §f" + modCfg.getMode() + actionStr);
            }
        } else {
            for (Map.Entry<String, BlacklistConfig.ModConfig> entry : mods.entrySet()) {
                BlacklistConfig.ModConfig modCfg = entry.getValue();
                NamedTextColor statusColor = modCfg.isRequired() ? NamedTextColor.GREEN 
                    : modCfg.isBlacklisted() ? NamedTextColor.RED 
                    : NamedTextColor.YELLOW;
                
                String actionStr = modCfg.getAction() != BlacklistConfig.Action.KICK ? " [" + modCfg.getAction() + "]" : "";
                Component modComponent = Component.text(entry.getKey())
                    .color(statusColor)
                    .append(Component.text(" - " + modCfg.getMode() + actionStr).color(NamedTextColor.WHITE))
                    .append(Component.text(" [Remove]")
                        .color(NamedTextColor.GRAY)
                        .hoverEvent(HoverEvent.showText(Component.text("Click to remove")))
                        .clickEvent(ClickEvent.runCommand("/handshaker remove " + entry.getKey())));
                player.sendMessage(modComponent);
            }
        }
    }

    private void handleIgnore(CommandSender sender, String[] args, BlacklistConfig config) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /handshaker ignore <add|remove|list> [mod|*]");
            return;
        }
        
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "add" -> {
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /handshaker ignore add <mod|*>");
                    return;
                }
                
                if (args[2].equals("*")) {
                    if (!(sender instanceof Player senderPlayer)) {
                        sender.sendMessage("§cThis command can only be used by players.");
                        return;
                    }
                    
                    Set<String> mods = plugin.getClientMods(senderPlayer.getUniqueId());
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
                    sender.sendMessage("§aAdded " + added + " of your mods to ignore list");
                } else {
                    boolean added = config.addIgnoredMod(args[2]);
                    sender.sendMessage(added ? "§aAdded " + args[2] + " to ignore list" : "§e" + args[2] + " already in ignore list");
                }
            }
            case "remove" -> {
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /handshaker ignore remove <mod>");
                    return;
                }
                boolean removed = config.removeIgnoredMod(args[2]);
                sender.sendMessage(removed ? "§aRemoved " + args[2] + " from ignore list" : "§e" + args[2] + " not in ignore list");
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
            default -> sender.sendMessage("§cUsage: /handshaker ignore <add|remove|list> [mod|*]");
        }
    }

    private void handleInfo(CommandSender sender, String[] args) {
        PlayerHistoryDatabase db = plugin.getPlayerHistoryDb();
        BlacklistConfig config = plugin.getBlacklistConfig();
        if (db == null) {
            sender.sendMessage("§cPlayer history database not available");
            return;
        }
        
        if (args.length < 2) {
            // Show popularity list
            Map<String, Integer> popularity = db.getModPopularity();
            if (popularity.isEmpty()) {
                sender.sendMessage("§eNo mod usage data available yet");
                return;
            }
            
            sender.sendMessage("§6§l=== Mod Popularity ===");
            int count = 0;
            int totalMods = 0;
            for (Map.Entry<String, Integer> entry : popularity.entrySet()) {
                if (config.isIgnored(entry.getKey())) continue; // Skip ignored mods
                totalMods++;
                if (count < 20) {
                    sender.sendMessage("§e" + entry.getKey() + " §7- §f" + entry.getValue() + " player(s)");
                    count++;
                }
            }
            if (totalMods > 20) {
                sender.sendMessage("§7... and " + (totalMods - 20) + " more");
            }
        } else {
            // Show players with specific mod
            String modName = args[1];
            List<PlayerHistoryDatabase.PlayerModInfo> players = db.getPlayersWithMod(modName);
            if (players.isEmpty()) {
                sender.sendMessage("§eNo players found using mod: " + modName);
                return;
            }
            
            sender.sendMessage("§6§l=== Players with " + modName + " ===");
            for (PlayerHistoryDatabase.PlayerModInfo info : players) {
                String statusMark = info.isActive() ? "●" : "○";
                String color = info.isActive() ? "§a" : "§7";
                String playerName = info.currentName() != null ? info.currentName() : info.uuid().toString();
                sender.sendMessage(color + statusMark + " " + playerName + " §7(first seen: " + info.getFirstSeenFormatted() + ")");
            }
        }
    }

    private void handlePlayer(CommandSender sender, String[] args, BlacklistConfig config) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /handshaker player <player> [mod] [status]");
            return;
        }
        
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cPlayer '" + args[1] + "' not found.");
            return;
        }
        
        Set<String> mods = plugin.getClientMods(target.getUniqueId());
        if (mods == null || mods.isEmpty()) {
            sender.sendMessage("§cNo mod list found for " + target.getName() + ".");
            return;
        }
        
        if (args.length >= 4) {
            // Set specific mod status
            String modId = args[2];
            if (!mods.contains(modId)) {
                sender.sendMessage("§cPlayer " + target.getName() + " does not have mod: " + modId);
                return;
            }
            
            String mode = args[3];
            config.setModConfig(modId, mode, null, null);
            sender.sendMessage("§aSet " + modId + " to " + mode.toLowerCase());
            plugin.checkAllPlayers();
            return;
        }
        
        // Show player's mods
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
            
            BlacklistConfig.ModConfig modCfg = config.getModConfig(mod);
            NamedTextColor statusColor = modCfg.isRequired() ? NamedTextColor.GREEN 
                : modCfg.isBlacklisted() ? NamedTextColor.RED 
                : NamedTextColor.YELLOW;
            
            Component modName = Component.text("  " + mod).color(NamedTextColor.WHITE);
            Component currentStatusComp = Component.text(" [" + modCfg.getMode() + "] ").color(statusColor);
            
            Component allowedBtn = Component.text("[A]")
                .color(NamedTextColor.YELLOW)
                .hoverEvent(HoverEvent.showText(Component.text("Set as ALLOWED")))
                .clickEvent(ClickEvent.runCommand("/handshaker change " + mod + " allowed"));
            
            Component requiredBtn = Component.text("[R]")
                .color(NamedTextColor.GREEN)
                .hoverEvent(HoverEvent.showText(Component.text("Set as REQUIRED")))
                .clickEvent(ClickEvent.runCommand("/handshaker change " + mod + " required"));
            
            Component blacklistedBtn = Component.text("[B]")
                .color(NamedTextColor.RED)
                .hoverEvent(HoverEvent.showText(Component.text("Set as BLACKLISTED")))
                .clickEvent(ClickEvent.runCommand("/handshaker change " + mod + " blacklisted"));
            
            Component fullLine = modName.append(currentStatusComp).append(allowedBtn).append(Component.text(" ")).append(requiredBtn).append(Component.text(" ")).append(blacklistedBtn);
            
            // Add hover text with dates
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

    private void sendUsage(CommandSender sender) {
        sender.sendMessage("§6=== HandShaker Commands ===");
        sender.sendMessage("§e/handshaker reload §7- Reload config");
        sender.sendMessage("§e/handshaker add <mod|*> <status> [action] [warn-message] §7- Add/set mod status");
        sender.sendMessage("§e/handshaker change <mod> <status> [action] [warn-message] §7- Change mod status");
        sender.sendMessage("§e/handshaker remove <mod> §7- Remove mod from config");
        sender.sendMessage("§e/handshaker list §7- List configured mods");
        sender.sendMessage("§e/handshaker ignore <add|remove|list> [mod|*] §7- Manage ignored mods");
        sender.sendMessage("§e/handshaker info [mod] §7- Show mod popularity or specific mod details");
        sender.sendMessage("§e/handshaker player <player> [mod] [status] §7- View/set player's mods");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> commands = Arrays.asList("reload", "add", "change", "remove", "list", "ignore", "info", "player");
            return StringUtil.copyPartialMatches(args[0], commands, new ArrayList<>());
        }

        if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "remove", "change" -> {
                    return StringUtil.copyPartialMatches(args[1], plugin.getBlacklistConfig().getModConfigMap().keySet(), new ArrayList<>());
                }
                case "add" -> {
                    List<String> suggestions = new ArrayList<>(Arrays.asList("*"));
                    if (sender instanceof Player p) {
                        Set<String> clientMods = plugin.getClientMods(p.getUniqueId());
                        if (clientMods != null) suggestions.addAll(clientMods);
                    }
                    return StringUtil.copyPartialMatches(args[1], suggestions, new ArrayList<>());
                }
                case "player" -> {
                    List<String> playerNames = Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
                    return StringUtil.copyPartialMatches(args[1], playerNames, new ArrayList<>());
                }
                case "ignore" -> {
                    return StringUtil.copyPartialMatches(args[1], Arrays.asList("add", "remove", "list"), new ArrayList<>());
                }
                case "info" -> {
                    PlayerHistoryDatabase db = plugin.getPlayerHistoryDb();
                    BlacklistConfig cfg = plugin.getBlacklistConfig();
                    if (db != null) {
                        List<String> nonIgnoredMods = new ArrayList<>();
                        for (String mod : db.getModPopularity().keySet()) {
                            if (!cfg.isIgnored(mod)) {
                                nonIgnoredMods.add(mod);
                            }
                        }
                        return StringUtil.copyPartialMatches(args[1], nonIgnoredMods, new ArrayList<>());
                    }
                }
            }
        }

        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("change")) {
                return StringUtil.copyPartialMatches(args[2], Arrays.asList("allowed", "required", "blacklisted"), new ArrayList<>());
            }
            if (args[0].equalsIgnoreCase("player")) {
                Player target = Bukkit.getPlayer(args[1]);
                if (target != null) {
                    Set<String> clientMods = plugin.getClientMods(target.getUniqueId());
                    if (clientMods != null) {
                        return StringUtil.copyPartialMatches(args[2], clientMods, new ArrayList<>());
                    }
                }
            }
            if (args[0].equalsIgnoreCase("ignore") && args[1].equalsIgnoreCase("add")) {
                List<String> suggestions = new ArrayList<>(Arrays.asList("*"));
                if (sender instanceof Player p) {
                    Set<String> clientMods = plugin.getClientMods(p.getUniqueId());
                    if (clientMods != null) suggestions.addAll(clientMods);
                }
                return StringUtil.copyPartialMatches(args[2], suggestions, new ArrayList<>());
            }
            if (args[0].equalsIgnoreCase("ignore") && args[1].equalsIgnoreCase("remove")) {
                return StringUtil.copyPartialMatches(args[2], plugin.getBlacklistConfig().getIgnoredMods(), new ArrayList<>());
            }
        }
        
        if (args.length == 4) {
            if (args[0].equalsIgnoreCase("player")) {
                return StringUtil.copyPartialMatches(args[3], Arrays.asList("allowed", "required", "blacklisted"), new ArrayList<>());
            }
            if (args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("change")) {
                return StringUtil.copyPartialMatches(args[3], Arrays.asList("kick", "ban"), new ArrayList<>());
            }
        }
        
        return Collections.emptyList();
    }
}
