package me.mklv.handshaker.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Collections;
import java.util.Set;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class HandShakerCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, HandShakerServer server) {
        dispatcher.register(literal("handshaker")
                .requires(source -> source.hasPermissionLevel(2))
                .then(literal("reload")
                        .executes(context -> {
                            server.getBlacklistConfig().load();
                            context.getSource().sendFeedback(() -> Text.of("HandShaker config reloaded."), true);
                            server.checkAllPlayers();
                            return 1;
                        }))
                .then(literal("mode")
                        .then(literal("blacklist")
                                .executes(context -> {
                                    server.getBlacklistConfig().setMode(BlacklistConfig.Mode.BLACKLIST);
                                    context.getSource().sendFeedback(() -> Text.of("HandShaker mode set to blacklist."), true);
                                    server.checkAllPlayers();
                                    return 1;
                                }))
                        .then(literal("whitelist")
                                .executes(context -> {
                                    server.getBlacklistConfig().setMode(BlacklistConfig.Mode.WHITELIST);
                                    context.getSource().sendFeedback(() -> Text.of("HandShaker mode set to whitelist."), true);
                                    server.checkAllPlayers();
                                    return 1;
                                }))
                        .then(literal("require")
                                .executes(context -> {
                                    server.getBlacklistConfig().setMode(BlacklistConfig.Mode.REQUIRE);
                                    context.getSource().sendFeedback(() -> Text.of("HandShaker mode set to require."), true);
                                    server.checkAllPlayers();
                                    return 1;
                                })))
                .then(literal("whitelist_update")
                        .then(argument("player", StringArgumentType.string())
                                .suggests((context, builder) -> CommandSource.suggestMatching(context.getSource().getServer().getPlayerNames(), builder))
                                .executes(context -> {
                                    String playerName = StringArgumentType.getString(context, "player");
                                    ServerPlayerEntity player = context.getSource().getServer().getPlayerManager().getPlayer(playerName);
                                    if (player == null) {
                                        context.getSource().sendFeedback(() -> Text.of("Player not found."), false);
                                        return 0;
                                    }
                                    HandShakerServer.ClientInfo info = server.getClients().get(player.getUuid());
                                    Set<String> mods = info != null ? info.mods() : null;
                                    if (mods == null) {
                                        context.getSource().sendFeedback(() -> Text.of("Mod list for " + playerName + " not found. Make sure they are online."), false);
                                        return 0;
                                    }
                                    server.getBlacklistConfig().setWhitelist(mods);
                                    context.getSource().sendFeedback(() -> Text.of("Whitelist updated with " + playerName + "'s mods. " + mods.size() + " mods added."), true);
                                    server.checkAllPlayers();
                                    return 1;
                                })))
                .then(literal("add")
                        .then(argument("mod", StringArgumentType.string())
                                .suggests((context, builder) -> {
                                    ServerCommandSource source = context.getSource();
                                    if (source.getEntity() instanceof ServerPlayerEntity player) {
                                        HandShakerServer.ClientInfo info = server.getClients().get(player.getUuid());
                                        Set<String> mods = info != null ? info.mods() : null;
                                        if (mods != null) {
                                            return CommandSource.suggestMatching(mods, builder);
                                        }
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    String modId = StringArgumentType.getString(context, "mod");
                                    if (server.getBlacklistConfig().addMod(modId)) {
                                        context.getSource().sendFeedback(() -> Text.of("Added " + modId + " to blacklist."), true);
                                        server.checkAllPlayers();
                                    } else {
                                        context.getSource().sendFeedback(() -> Text.of(modId + " is already in the blacklist."), false);
                                    }
                                    return 1;
                                })))
                .then(literal("remove")
                        .then(argument("mod", StringArgumentType.string())
                                .suggests((context, builder) -> CommandSource.suggestMatching(server.getBlacklistConfig().getBlacklistedMods(), builder))
                                .executes(context -> {
                                    String modId = StringArgumentType.getString(context, "mod");
                                    if (server.getBlacklistConfig().removeMod(modId)) {
                                        context.getSource().sendFeedback(() -> Text.of("Removed " + modId + " from blacklist."), true);
                                    } else {
                                        context.getSource().sendFeedback(() -> Text.of(modId + " not found in blacklist."), false);
                                    }
                                    return 1;
                                })))
                .then(literal("player")
                        .then(argument("player", StringArgumentType.string())
                                .suggests((context, builder) -> CommandSource.suggestMatching(context.getSource().getServer().getPlayerNames(), builder))
                                .executes(context -> {
                                    String playerName = StringArgumentType.getString(context, "player");
                                    ServerPlayerEntity player = context.getSource().getServer().getPlayerManager().getPlayer(playerName);
                                    if (player != null) {
                                        HandShakerServer.ClientInfo info = server.getClients().get(player.getUuid());
                                        Set<String> mods = info != null ? info.mods() : Collections.emptySet();
                                        if (!mods.isEmpty()) {
                                            context.getSource().sendFeedback(() -> Text.of(playerName + "'s mods: " + String.join(", ", mods)), false);
                                        } else {
                                            context.getSource().sendFeedback(() -> Text.of("No mod list found for " + playerName), false);
                                        }
                                    } else {
                                        context.getSource().sendFeedback(() -> Text.of("Player not found."), false);
                                    }
                                    return 1;
                                })
                                .then(argument("mod", StringArgumentType.string())
                                        .suggests((context, builder) -> {
                                            String playerName = StringArgumentType.getString(context, "player");
                                            ServerPlayerEntity player = context.getSource().getServer().getPlayerManager().getPlayer(playerName);
                                            if (player != null) {
                                                HandShakerServer.ClientInfo info = server.getClients().get(player.getUuid());
                                                Set<String> mods = info != null ? info.mods() : null;
                                                if (mods != null) {
                                                    return CommandSource.suggestMatching(mods, builder);
                                                }
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> {
                                            String playerName = StringArgumentType.getString(context, "player");
                                            ServerPlayerEntity player = context.getSource().getServer().getPlayerManager().getPlayer(playerName);
                                            if (player == null) {
                                                context.getSource().sendFeedback(() -> Text.of("Player not found."), false);
                                                return 1;
                                            }

                                            String modId = StringArgumentType.getString(context, "mod");
                                            HandShakerServer.ClientInfo info = server.getClients().get(player.getUuid());
                                            Set<String> mods = info != null ? info.mods() : Collections.emptySet();

                                            if (!mods.contains(modId)) {
                                                context.getSource().sendFeedback(() -> Text.of(playerName + " does not have the mod " + modId), false);
                                                return 1;
                                            }

                                            if (server.getBlacklistConfig().addMod(modId)) {
                                                context.getSource().sendFeedback(() -> Text.of("Added " + modId + " to blacklist."), true);
                                                server.checkAllPlayers();
                                            } else {
                                                context.getSource().sendFeedback(() -> Text.of(modId + " is already in the blacklist."), false);
                                            }
                                            return 1;
                                        })))));
    }
}
