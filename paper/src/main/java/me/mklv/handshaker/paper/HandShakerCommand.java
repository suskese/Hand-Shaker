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
        boolean isV2 = config.isV2Config();
        
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                config.load();
                sender.sendMessage("HandShaker config reloaded. Re-checking all online players.");
                plugin.checkAllPlayers();
            }
            case "mode" -> {
                if (isV2) {
                    sender.sendMessage("§cThe 'mode' command is not available in v2 config. Use per-mod configuration instead.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("Usage: /handshaker mode <blacklist|whitelist|require>");
                    return true;
                }
                switch (args[1].toLowerCase(Locale.ROOT)) {
                    case "blacklist" -> {
                        config.setMode(BlacklistConfig.Mode.BLACKLIST);
                        sender.sendMessage("HandShaker mode set to blacklist.");
                        plugin.checkAllPlayers();
                    }
                    case "whitelist" -> {
                        config.setMode(BlacklistConfig.Mode.WHITELIST);
                        sender.sendMessage("HandShaker mode set to whitelist.");
                        plugin.checkAllPlayers();
                    }
                    case "require" -> {
                        config.setMode(BlacklistConfig.Mode.REQUIRE);
                        sender.sendMessage("HandShaker mode set to require.");
                        plugin.checkAllPlayers();
                    }
                    default -> sender.sendMessage("Unknown mode. Use blacklist, whitelist, or require.");
                }
            }
            case "add" -> {
                if (args.length < 2) {
                    sender.sendMessage("Usage: /handshaker add <mod|*> [allowed|required|blacklisted]");
                    return true;
                }
                
                String modId = args[1];
                
                if (isV2) {
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
                } else {
                    // V1 config - add to blacklist
                    boolean added = config.addMod(modId);
                    sender.sendMessage(added ? "Added " + modId : modId + " already in blacklist.");
                    if (added) plugin.checkAllPlayers();
                }
            }
            case "change" -> {
                if (!isV2) {
                    sender.sendMessage("§cThe 'change' command is only available in v2 config.");
                    return true;
                }
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
                boolean removed = config.removeMod(args[1]);
                sender.sendMessage(removed ? "Removed " + args[1] : args[1] + " not found.");
            }
            case "list" -> {
                if (!isV2) {
                    sender.sendMessage("§cThe 'list' command is only available in v2 config.");
                    return true;
                }
                
                Map<String, BlacklistConfig.ModStatus> mods = config.getModStatusMap();
                if (mods.isEmpty()) {
                    sender.sendMessage("§eNo mods configured. Default mode: " + config.getDefaultMode());
                    return true;
                }
                
                sender.sendMessage("§6=== Configured Mods (Default: " + config.getDefaultMode() + ") ===");
                
                if (!(sender instanceof Player player)) {
                    // Console - simple list
                    for (Map.Entry<String, BlacklistConfig.ModStatus> entry : mods.entrySet()) {
                        String statusColor = switch (entry.getValue()) {
                            case REQUIRED -> "§a";
                            case ALLOWED -> "§e";
                            case BLACKLISTED -> "§c";
                        };
                        sender.sendMessage(statusColor + entry.getKey() + " §7- §f" + entry.getValue());
                    }
                } else {
                    // Player - clickable list to remove
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
                    sender.sendMessage("Usage: /handshaker player <player>");
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

                if (!(sender instanceof Player player)) {
                    // Console fallback
                    sender.sendMessage(target.getName() + "'s mods: " + String.join(", ", mods));
                    return true;
                }
                
                if (isV2) {
                    // V2 - Show interactive buttons
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
                } else {
                    // V1 - Show simple list with option to add to blacklist
                    player.sendMessage(Component.text("=== " + target.getName() + "'s Mods ===").color(NamedTextColor.GOLD));
                    for (String mod : mods) {
                        boolean isBlacklisted = config.getBlacklistedMods().contains(mod);
                        Component modComponent;
                        if (isBlacklisted) {
                            modComponent = Component.text("  • " + mod + " [BLACKLISTED]").color(NamedTextColor.RED);
                        } else {
                            modComponent = Component.text("  • " + mod)
                                .color(NamedTextColor.WHITE)
                                .append(Component.text(" [Add to Blacklist]")
                                    .color(NamedTextColor.GRAY)
                                    .hoverEvent(HoverEvent.showText(Component.text("Click to blacklist")))
                                    .clickEvent(ClickEvent.runCommand("/handshaker add " + mod)));
                        }
                        player.sendMessage(modComponent);
                    }
                }
            }
            case "whitelist_update" -> {
                if (isV2) {
                    sender.sendMessage("§cThis command is deprecated in v2 config. Use: /handshaker add * required <player>");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("Usage: /handshaker whitelist_update <player>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage("Player not found.");
                    return true;
                }
                Set<String> mods = plugin.getClientMods(target.getUniqueId());
                if (mods == null) {
                    sender.sendMessage("Mod list for " + target.getName() + " not found. Make sure they are online and using Fabric.");
                    return true;
                }
                config.setWhitelistedMods(mods);
                sender.sendMessage("Whitelist updated with " + target.getName() + "'s mods. " + mods.size() + " mods added.");
                plugin.checkAllPlayers();
            }
            default -> sendUsage(sender);
        }
        return true;
    }

    private void sendUsage(CommandSender sender) {
        boolean isV2 = plugin.getBlacklistConfig().isV2Config();
        if (isV2) {
            sender.sendMessage("§6=== HandShaker Commands (v2) ===");
            sender.sendMessage("§e/handshaker reload §7- Reload config");
            sender.sendMessage("§e/handshaker add <mod> <allowed|required|blacklisted> §7- Add/set mod status");
            sender.sendMessage("§e/handshaker add * <status> §7- Add all your mods");
            sender.sendMessage("§e/handshaker change <mod> <status> §7- Change mod status");
            sender.sendMessage("§e/handshaker remove <mod> §7- Remove mod from config");
            sender.sendMessage("§e/handshaker list §7- List configured mods");
            sender.sendMessage("§e/handshaker player <player> §7- View player's mods (interactive)");
        } else {
            sender.sendMessage("§6=== HandShaker Commands (v1) ===");
            sender.sendMessage("§e/handshaker reload §7- Reload config");
            sender.sendMessage("§e/handshaker mode <blacklist|whitelist|require> §7- Set mode");
            sender.sendMessage("§e/handshaker add <mod> §7- Add to blacklist");
            sender.sendMessage("§e/handshaker remove <mod> §7- Remove from blacklist");
            sender.sendMessage("§e/handshaker player <player> §7- View player's mods");
            sender.sendMessage("§e/handshaker whitelist_update <player> §7- Update whitelist");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean isV2 = plugin.getBlacklistConfig().isV2Config();
        
        if (args.length == 1) {
            List<String> commands = new ArrayList<>(Arrays.asList("reload", "add", "remove", "player"));
            if (isV2) {
                commands.addAll(Arrays.asList("change", "list"));
            } else {
                commands.addAll(Arrays.asList("mode", "whitelist_update"));
            }
            return StringUtil.copyPartialMatches(args[0], commands, new ArrayList<>());
        }

        if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "remove" -> {
                    if (isV2) {
                        return StringUtil.copyPartialMatches(args[1], plugin.getBlacklistConfig().getModStatusMap().keySet(), new ArrayList<>());
                    } else {
                        return StringUtil.copyPartialMatches(args[1], plugin.getBlacklistConfig().getBlacklistedMods(), new ArrayList<>());
                    }
                }
                case "change" -> {
                    if (isV2) {
                        return StringUtil.copyPartialMatches(args[1], plugin.getBlacklistConfig().getModStatusMap().keySet(), new ArrayList<>());
                    }
                }
                case "add" -> {
                    if (isV2) {
                        List<String> suggestions = new ArrayList<>(Arrays.asList("*"));
                        if (sender instanceof Player p) {
                            Set<String> clientMods = plugin.getClientMods(p.getUniqueId());
                            if (clientMods != null) suggestions.addAll(clientMods);
                        }
                        return StringUtil.copyPartialMatches(args[1], suggestions, new ArrayList<>());
                    } else {
                        if (sender instanceof Player p) {
                            Set<String> clientMods = plugin.getClientMods(p.getUniqueId());
                            if (clientMods != null) {
                                List<String> suggestions = new ArrayList<>(clientMods);
                                suggestions.removeAll(plugin.getBlacklistConfig().getBlacklistedMods());
                                return StringUtil.copyPartialMatches(args[1], suggestions, new ArrayList<>());
                            }
                        }
                    }
                }
                case "player", "whitelist_update" -> {
                    List<String> playerNames = Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
                    return StringUtil.copyPartialMatches(args[1], playerNames, new ArrayList<>());
                }
                case "mode" -> {
                    if (!isV2) {
                        return StringUtil.copyPartialMatches(args[1], Arrays.asList("blacklist", "whitelist", "require"), new ArrayList<>());
                    }
                }
            }
        }

        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("change")) {
                if (isV2) {
                    return StringUtil.copyPartialMatches(args[2], Arrays.asList("allowed", "required", "blacklisted"), new ArrayList<>());
                }
            }
        }
        
        return Collections.emptyList();
    }
}
