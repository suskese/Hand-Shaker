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
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }
        
        BlacklistConfig config = plugin.getBlacklistConfig();
        
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                config.load();
                sender.sendMessage("HandShaker config reloaded. Re-checking all online players.");
                plugin.checkAllPlayers();
            }
            case "add" -> {
                if (args.length < 2) {
                    sender.sendMessage("Usage: /handshaker add <mod|*> <allowed|required|blacklisted>");
                    return true;
                }
                
                String modId = args[1];
                
                if (modId.equals("*")) {
                    // Add all mods from the command sender - needs status only
                    if (args.length < 3) {
                        sender.sendMessage("§cUsage: /handshaker add * <allowed|required|blacklisted>");
                        return true;
                    }
                    
                    if (!(sender instanceof Player)) {
                        sender.sendMessage("§cThis command can only be used by players.");
                        return true;
                    }
                    
                    BlacklistConfig.ModStatus status;
                    switch (args[2].toLowerCase(Locale.ROOT)) {
                        case "required" -> status = BlacklistConfig.ModStatus.REQUIRED;
                        case "allowed" -> status = BlacklistConfig.ModStatus.ALLOWED;
                        case "blacklisted" -> status = BlacklistConfig.ModStatus.BLACKLISTED;
                        default -> {
                            sender.sendMessage("§cInvalid status. Use: allowed, required, or blacklisted");
                            return true;
                        }
                    }
                    
                    Player senderPlayer = (Player) sender;
                    Set<String> mods = plugin.getClientMods(senderPlayer.getUniqueId());
                    if (mods == null || mods.isEmpty()) {
                        sender.sendMessage("§cNo mod list found for you. Make sure you're using a modded client.");
                        return true;
                    }
                    config.addAllMods(mods, status);
                    sender.sendMessage("§aAdded " + mods.size() + " of your mods as " + args[2].toLowerCase());
                    plugin.checkAllPlayers();
                } else {
                    // Add single mod
                    if (args.length < 3) {
                        sender.sendMessage("§cUsage: /handshaker add <mod> <allowed|required|blacklisted>");
                        return true;
                    }
                    
                    BlacklistConfig.ModStatus status;
                    switch (args[2].toLowerCase(Locale.ROOT)) {
                        case "required" -> status = BlacklistConfig.ModStatus.REQUIRED;
                        case "allowed" -> status = BlacklistConfig.ModStatus.ALLOWED;
                        case "blacklisted" -> status = BlacklistConfig.ModStatus.BLACKLISTED;
                        default -> {
                            sender.sendMessage("§cInvalid status. Use: allowed, required, or blacklisted");
                            return true;
                        }
                    }
                    
                    config.setModStatus(modId, status);
                    sender.sendMessage("§aSet " + modId + " to " + args[2].toLowerCase());
                    plugin.checkAllPlayers();
                }
            }
            case "change" -> {
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /handshaker change <mod> <allowed|required|blacklisted>");
                    return true;
                }
                
                String modId = args[1];
                BlacklistConfig.ModStatus status;
                switch (args[2].toLowerCase(Locale.ROOT)) {
                    case "required" -> status = BlacklistConfig.ModStatus.REQUIRED;
                    case "allowed" -> status = BlacklistConfig.ModStatus.ALLOWED;
                    case "blacklisted" -> status = BlacklistConfig.ModStatus.BLACKLISTED;
                    default -> {
                        sender.sendMessage("§cInvalid status. Use: allowed, required, or blacklisted");
                        return true;
                    }
                }
                
                config.setModStatus(modId, status);
                sender.sendMessage("§aChanged " + modId + " to " + args[2].toLowerCase());
                plugin.checkAllPlayers();
            }
            case "remove" -> {
                if (args.length < 2) {
                    sender.sendMessage("Usage: /handshaker remove <mod>");
                    return true;
                }
                boolean removed = config.removeModStatus(args[1]);
                sender.sendMessage(removed ? "Removed " + args[1] : args[1] + " not found.");
            }
            case "list" -> {
                Map<String, BlacklistConfig.ModStatus> mods = config.getModStatusMap();
                if (mods.isEmpty()) {
                    sender.sendMessage("§eNo mods configured. Default mode: " + config.getDefaultMode());
                    return true;
                }
                
                sender.sendMessage("§6=== Configured Mods (Default: " + config.getDefaultMode() + ") ===");
                
                if (!(sender instanceof Player player)) {
                    for (Map.Entry<String, BlacklistConfig.ModStatus> entry : mods.entrySet()) {
                        String statusColor = switch (entry.getValue()) {
                            case REQUIRED -> "§a";
                            case ALLOWED -> "§e";
                            case BLACKLISTED -> "§c";
                        };
                        sender.sendMessage(statusColor + entry.getKey() + " §7- §f" + entry.getValue());
                    }
                } else {
                    for (Map.Entry<String, BlacklistConfig.ModStatus> entry : mods.entrySet()) {
                        NamedTextColor statusColor = switch (entry.getValue()) {
                            case REQUIRED -> NamedTextColor.GREEN;
                            case ALLOWED -> NamedTextColor.YELLOW;
                            case BLACKLISTED -> NamedTextColor.RED;
                        };
                        
                        Component modComponent = Component.text(entry.getKey())
                            .color(statusColor)
                            .append(Component.text(" - " + entry.getValue()).color(NamedTextColor.WHITE))
                            .append(Component.text(" [Remove]")
                                .color(NamedTextColor.GRAY)
                                .hoverEvent(HoverEvent.showText(Component.text("Click to remove")))
                                .clickEvent(ClickEvent.runCommand("/handshaker remove " + entry.getKey())));
                        player.sendMessage(modComponent);
                    }
                }
            }
            case "player" -> {
                if (args.length < 2) {
                    sender.sendMessage("Usage: /handshaker player <player> [mod] [status]");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage("Player '" + args[1] + "' not found.");
                    return true;
                }
                Set<String> mods = plugin.getClientMods(target.getUniqueId());
                if (mods == null || mods.isEmpty()) {
                    sender.sendMessage("No mod list found for " + target.getName() + ".");
                    return true;
                }
                
                if (args.length >= 4) {
                    String modId = args[2];
                    if (!mods.contains(modId)) {
                        sender.sendMessage("§cPlayer " + target.getName() + " does not have mod: " + modId);
                        return true;
                    }
                    
                    BlacklistConfig.ModStatus status;
                    switch (args[3].toLowerCase(Locale.ROOT)) {
                        case "required" -> status = BlacklistConfig.ModStatus.REQUIRED;
                        case "allowed" -> status = BlacklistConfig.ModStatus.ALLOWED;
                        case "blacklisted" -> status = BlacklistConfig.ModStatus.BLACKLISTED;
                        default -> {
                            sender.sendMessage("§cInvalid status. Use: allowed, required, or blacklisted");
                            return true;
                        }
                    }
                    
                    config.setModStatus(modId, status);
                    sender.sendMessage("§aSet " + modId + " to " + args[3].toLowerCase());
                    plugin.checkAllPlayers();
                    return true;
                }

                if (!(sender instanceof Player player)) {
                    sender.sendMessage(target.getName() + "'s mods: " + String.join(", ", mods));
                    return true;
                }
                
                player.sendMessage(Component.text("=== " + target.getName() + "'s Mods ===").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
                    for (String mod : mods) {
                        BlacklistConfig.ModStatus currentStatus = config.getModStatus(mod);
                        String statusStr = currentStatus.toString().toLowerCase();
                        NamedTextColor statusColor = switch (currentStatus) {
                            case REQUIRED -> NamedTextColor.GREEN;
                            case ALLOWED -> NamedTextColor.YELLOW;
                            case BLACKLISTED -> NamedTextColor.RED;
                        };
                        
                        Component modName = Component.text("  " + mod).color(NamedTextColor.WHITE);
                        Component currentStatusComp = Component.text(" [" + statusStr + "] ").color(statusColor);
                        
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
                        
                        player.sendMessage(modName.append(currentStatusComp).append(allowedBtn).append(Component.text(" ")).append(requiredBtn).append(Component.text(" ")).append(blacklistedBtn));
                    }
            }
            default -> sendUsage(sender);
        }
        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage("§6=== HandShaker Commands ===");
        sender.sendMessage("§e/handshaker reload §7- Reload config");
        sender.sendMessage("§e/handshaker add <mod> <allowed|required|blacklisted> §7- Add/set mod status");
        sender.sendMessage("§e/handshaker add * <status> §7- Add all your mods");
        sender.sendMessage("§e/handshaker change <mod> <status> §7- Change mod status");
        sender.sendMessage("§e/handshaker remove <mod> §7- Remove mod from config");
        sender.sendMessage("§e/handshaker list §7- List configured mods");
        sender.sendMessage("§e/handshaker player <player> [mod] [status] §7- View/set player's mods");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> commands = Arrays.asList("reload", "add", "change", "remove", "list", "player");
            return StringUtil.copyPartialMatches(args[0], commands, new ArrayList<>());
        }

        if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "remove", "change" -> {
                    return StringUtil.copyPartialMatches(args[1], plugin.getBlacklistConfig().getModStatusMap().keySet(), new ArrayList<>());
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
            }
        }

        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("change")) {
                return StringUtil.copyPartialMatches(args[2], Arrays.asList("allowed", "required", "blacklisted"), new ArrayList<>());
            }
            // Tab-complete mods for /handshaker player <player> <mod>
            if (args[0].equalsIgnoreCase("player")) {
                Player target = Bukkit.getPlayer(args[1]);
                if (target != null) {
                    Set<String> clientMods = plugin.getClientMods(target.getUniqueId());
                    if (clientMods != null) {
                        return StringUtil.copyPartialMatches(args[2], clientMods, new ArrayList<>());
                    }
                }
            }
        }
        
        if (args.length == 4) {
            // Tab-complete status for /handshaker player <player> <mod> <status>
            if (args[0].equalsIgnoreCase("player")) {
                return StringUtil.copyPartialMatches(args[3], Arrays.asList("allowed", "required", "blacklisted"), new ArrayList<>());
            }
        }
        
        return Collections.emptyList();
    }
}
