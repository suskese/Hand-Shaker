package me.mklv.handshaker.paper.listener;

import me.mklv.handshaker.paper.HandShakerPlugin;
import me.mklv.handshaker.paper.utils.ClientInfo;
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
        
        // Schedule check with delay (5 seconds = 100 ticks)
        // This allows time for plugin channel messages to arrive
        plugin.schedulePlayerCheck(player, 100L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        clients.remove(event.getPlayer().getUniqueId());
    }
}
