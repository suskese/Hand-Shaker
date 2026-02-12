package me.mklv.handshaker.paper.utils;

import me.mklv.handshaker.paper.HandShakerPlugin;
import me.mklv.handshaker.common.utils.ClientInfo;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;

public class HandShakerListener implements Listener {
    private final HandShakerPlugin plugin;
    private final Map<UUID, ClientInfo> clients;

    public HandShakerListener(HandShakerPlugin plugin, Map<UUID, ClientInfo> clients) {
        this.plugin = plugin;
        this.clients = clients;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        int timeoutSeconds = plugin.getConfigManager().getHandshakeTimeoutSeconds();
        long delayTicks = Math.max(1, timeoutSeconds) * 20L;
        // Schedule check with configurable delay to allow plugin channel messages to arrive
        plugin.schedulePlayerCheck(player, delayTicks);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        clients.remove(event.getPlayer().getUniqueId());
    }
}
