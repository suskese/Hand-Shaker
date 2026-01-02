package me.mklv.handshaker;

import me.mklv.handshaker.BlacklistConfig.IntegrityMode;
import org.bukkit.Bukkit;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.PluginCommand;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class HandShakerPlugin extends JavaPlugin implements Listener {
    public static final String MODS_CHANNEL = "hand-shaker:mods";
    public static final String INTEGRITY_CHANNEL = "hand-shaker:integrity";
    public static final String VELTON_CHANNEL = "velton:signature";

    private final Map<UUID, ClientInfo> clients = new ConcurrentHashMap<>();
    private BlacklistConfig blacklistConfig;
    private PlayerHistoryDatabase playerHistoryDb;
    private byte[] serverCertificate;

    @Override
    public void onEnable() {
        blacklistConfig = new BlacklistConfig(this);
        blacklistConfig.load();
        
        playerHistoryDb = new PlayerHistoryDatabase(getDataFolder(), getLogger());

        loadServerCertificate();

        Bukkit.getMessenger().registerIncomingPluginChannel(this, MODS_CHANNEL, (channel, player, message) -> handleModList(player, message));
        Bukkit.getMessenger().registerIncomingPluginChannel(this, INTEGRITY_CHANNEL, (channel, player, message) -> handleIntegrityPayload(player, message));
        Bukkit.getMessenger().registerIncomingPluginChannel(this, VELTON_CHANNEL, (channel, player, message) -> handleVeltonPayload(player, message));
        Bukkit.getMessenger().registerOutgoingPluginChannel(this, MODS_CHANNEL);
        Bukkit.getMessenger().registerOutgoingPluginChannel(this, INTEGRITY_CHANNEL);
        Bukkit.getMessenger().registerOutgoingPluginChannel(this, VELTON_CHANNEL);

        getServer().getPluginManager().registerEvents(this, this);
        PluginCommand cmd = getCommand("handshaker");
        if (cmd != null) cmd.setExecutor(new HandShakerCommand(this));
        getLogger().info("HandShaker plugin enabled");
    }

    private void loadServerCertificate() {
        try (InputStream is = getResource("public.cer")) {
            if (is == null) {
                if (blacklistConfig.getIntegrityMode() == IntegrityMode.SIGNED) {
                    getLogger().severe("Could not find 'public.cer' in the plugin JAR. Integrity checking will fail.");
                }
                this.serverCertificate = new byte[0];
                return;
            }
            this.serverCertificate = is.readAllBytes();
            getLogger().info("Successfully loaded embedded server certificate (" + this.serverCertificate.length + " bytes)");
        } catch (IOException e) {
            getLogger().severe("Failed to load embedded server certificate: " + e.getMessage());
            this.serverCertificate = new byte[0];
        }
    }

    @Override
    public void onDisable() {
        Bukkit.getMessenger().unregisterIncomingPluginChannel(this, MODS_CHANNEL);
        Bukkit.getMessenger().unregisterIncomingPluginChannel(this, INTEGRITY_CHANNEL);
        Bukkit.getMessenger().unregisterIncomingPluginChannel(this, VELTON_CHANNEL);
        if (playerHistoryDb != null) {
            playerHistoryDb.close();
        }
        clients.clear();
    }

    private void handleModList(Player player, byte[] data) {
        try {
            String payload = decodeLengthPrefixedString(data);
            if (payload == null) {
                getLogger().warning("Failed to decode mod list from " + player.getName() + ". Rejecting.");
                player.kick(Component.text("Corrupted handshake data").color(net.kyori.adventure.text.format.NamedTextColor.RED));
                return;
            }
            String nonce = decodeLengthPrefixedString(data, payload.length());
            if (nonce == null || nonce.isEmpty()) {
                getLogger().warning("Received mod list from " + player.getName() + " with invalid/missing nonce. Rejecting.");
                player.kick(Component.text("Invalid handshake: missing nonce").color(net.kyori.adventure.text.format.NamedTextColor.RED));
                return;
            }
            Set<String> mods = new HashSet<>();
            if (!payload.isBlank()) {
                for (String s : payload.split(",")) {
                    if (!s.isBlank()) mods.add(s.trim().toLowerCase(Locale.ROOT));
                }
            }
            getLogger().info("Received mod list from " + player.getName() + " with nonce: " + nonce);
            
            // Sync with database
            if (playerHistoryDb != null) {
                try {
                    playerHistoryDb.syncPlayerMods(player.getUniqueId(), player.getName(), mods);
                } catch (Exception dbEx) {
                    getLogger().warning("Failed to sync player mods to database: " + dbEx.getMessage());
                }
            }
            
            clients.compute(player.getUniqueId(), (uuid, oldInfo) -> oldInfo == null
                    ? new ClientInfo(true, mods, false, false, nonce, null, null)
                    : new ClientInfo(true, mods, oldInfo.signatureVerified(), oldInfo.veltonVerified(), nonce, oldInfo.integrityNonce(), oldInfo.veltonNonce()));
        } catch (Exception e) {
            getLogger().severe("Failed to decode mod list from " + player.getName() + ". Terminating connection: " + e.getMessage());
            player.kick(Component.text("Corrupted handshake data").color(net.kyori.adventure.text.format.NamedTextColor.RED));
        }
    }

    private void handleIntegrityPayload(Player player, byte[] data) {
        try {
            byte[] clientCertificate = decodeLengthPrefixedByteArray(data);
            if (clientCertificate == null) {
                getLogger().warning("Failed to decode integrity payload from " + player.getName() + ". Rejecting.");
                player.kick(Component.text("Corrupted handshake data").color(net.kyori.adventure.text.format.NamedTextColor.RED));
                return;
            }
            String nonce = decodeLengthPrefixedString(data, clientCertificate.length);
            if (nonce == null || nonce.isEmpty()) {
                getLogger().warning("Received integrity payload from " + player.getName() + " with invalid/missing nonce. Rejecting.");
                player.kick(Component.text("Invalid handshake: missing nonce").color(net.kyori.adventure.text.format.NamedTextColor.RED));
                return;
            }
            boolean verified = false;
            if (clientCertificate.length > 0 && this.serverCertificate.length > 0) {
                verified = Arrays.equals(clientCertificate, this.serverCertificate);
            }
            getLogger().info("Integrity check for " + player.getName() + " with nonce " + nonce + ": " + (verified ? "PASSED" : "FAILED"));

            final boolean finalVerified = verified;
            clients.compute(player.getUniqueId(), (uuid, oldInfo) -> oldInfo == null
                    ? new ClientInfo(false, Collections.emptySet(), finalVerified, false, null, nonce, null)
                    : new ClientInfo(oldInfo.fabric(), oldInfo.mods(), finalVerified, oldInfo.veltonVerified(), oldInfo.modListNonce(), nonce, oldInfo.veltonNonce()));
            check(player);
        } catch (Exception e) {
            getLogger().severe("Failed to decode integrity payload from " + player.getName() + ". Terminating connection: " + e.getMessage());
            player.kick(Component.text("Corrupted handshake data").color(net.kyori.adventure.text.format.NamedTextColor.RED));
        }
    }

    private void handleVeltonPayload(Player player, byte[] data) {
        try {
            String signatureHash = decodeLengthPrefixedString(data);
            if (signatureHash == null) {
                getLogger().warning("Failed to decode Velton payload from " + player.getName() + ". Rejecting.");
                player.kick(Component.text("Corrupted handshake data").color(net.kyori.adventure.text.format.NamedTextColor.RED));
                return;
            }
            String nonce = decodeLengthPrefixedString(data, signatureHash.length());
            if (nonce == null || nonce.isEmpty()) {
                getLogger().warning("Received Velton payload from " + player.getName() + " with invalid/missing nonce. Rejecting.");
                player.kick(Component.text("Invalid handshake: missing nonce").color(net.kyori.adventure.text.format.NamedTextColor.RED));
                return;
            }

            boolean verified = signatureHash != null && !signatureHash.isEmpty();
            getLogger().info("Velton check for " + player.getName() + " with nonce " + nonce + ": " + (verified ? "PASSED" : "FAILED"));

            // Kick player if Velton signature is invalid/missing
            if (!verified) {
                getLogger().warning("Kicking " + player.getName() + " - Velton signature verification failed");
                player.kick(Component.text("Anti-cheat verification failed").color(net.kyori.adventure.text.format.NamedTextColor.RED));
                return;
            }

            final boolean finalVerified = verified;
            clients.compute(player.getUniqueId(), (uuid, oldInfo) -> oldInfo == null
                    ? new ClientInfo(false, Collections.emptySet(), false, finalVerified, null, null, nonce)
                    : new ClientInfo(oldInfo.fabric(), oldInfo.mods(), oldInfo.signatureVerified(), finalVerified, oldInfo.modListNonce(), oldInfo.integrityNonce(), nonce));
            check(player);
        } catch (Exception e) {
            getLogger().severe("Failed to decode Velton payload from " + player.getName() + ". Terminating connection: " + e.getMessage());
            player.kick(Component.text("Corrupted handshake data").color(net.kyori.adventure.text.format.NamedTextColor.RED));
        }
    }

    private void check(Player player) {
        ClientInfo info = clients.get(player.getUniqueId());
        if (info == null) return; // Player data not yet received, do nothing.

        // Handshake presence check
        if (blacklistConfig.getBehavior() == BlacklistConfig.Behavior.STRICT && !info.fabric()) {
            player.kick(Component.text(blacklistConfig.getNoHandshakeKickMessage()).color(net.kyori.adventure.text.format.NamedTextColor.RED));
            return;
        }

        // Integrity Check
        if (blacklistConfig.getIntegrityMode() == IntegrityMode.SIGNED) {
            if (!info.signatureVerified()) {
                player.kick(Component.text(blacklistConfig.getInvalidSignatureKickMessage()).color(net.kyori.adventure.text.format.NamedTextColor.RED));
                return;
            }
        }

        Set<String> mods = info.mods();
        
        // Use new checkPlayer method which handles all logic including bans/kicks/warns
        String kickReason = blacklistConfig.checkPlayer(player, mods);
        if (kickReason != null) {
            player.kick(Component.text(kickReason).color(net.kyori.adventure.text.format.NamedTextColor.RED));
            return;
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            ClientInfo info = clients.putIfAbsent(e.getPlayer().getUniqueId(), new ClientInfo(false, Collections.emptySet(), false, false, null, null, null));
            if (info == null) {
                info = clients.get(e.getPlayer().getUniqueId());
            }

            check(e.getPlayer());
        }, 100L); // 5 seconds
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        clients.remove(e.getPlayer().getUniqueId());
    }

    public BlacklistConfig getBlacklistConfig() { return blacklistConfig; }
    
    public PlayerHistoryDatabase getPlayerHistoryDb() {
        return playerHistoryDb;
    }

    public Set<String> getClientMods(UUID uuid) {
        ClientInfo info = clients.get(uuid);
        return info != null ? info.mods : null;
    }

    public void checkAllPlayers() {
        getLogger().info("Re-checking all online players...");
        for (Player player : Bukkit.getOnlinePlayers()) {
            check(player);
        }
    }

    private byte[] decodeLengthPrefixedByteArray(byte[] data) {
        try {
            int idx = 0;
            int numRead = 0;
            int result = 0;
            byte read;
            do {
                read = data[idx++];
                int value = (read & 0b01111111);
                result |= (value << (7 * numRead));
                numRead++;
                if (numRead > 5) return null; // VarInt too big
            } while ((read & 0b10000000) != 0);
            int length = result;
            if (length < 0 || idx + length > data.length) return null;
            byte[] bytes = new byte[length];
            System.arraycopy(data, idx, bytes, 0, length);
            return bytes;
        } catch (Exception e) {
            getLogger().warning("Failed to decode byte array payload: " + e.getMessage());
            return null;
        }
    }

    private String decodeLengthPrefixedString(byte[] data) {
        try {
            int idx = 0;
            int numRead = 0;
            int result = 0;
            byte read;
            do {
                read = data[idx++];
                int value = (read & 0b01111111);
                result |= (value << (7 * numRead));
                numRead++;
                if (numRead > 5) return null; // VarInt too big
            } while ((read & 0b10000000) != 0);
            int length = result;
            if (length < 0 || idx + length > data.length) return null;
            return new String(data, idx, length, StandardCharsets.UTF_8);
        } catch (Exception e) {
            getLogger().warning("Failed to decode mods payload: " + e.getMessage());
            return null;
        }
    }

    private String decodeLengthPrefixedString(byte[] data, int previousDataLength) {
        try {
            // Calculate offset: varint size for previous data + previous data length
            int offset = 0;
            int numRead = 0;
            byte read;
            do {
                read = data[offset++];
                numRead++;
                if (numRead > 5) return null;
            } while ((read & 0b10000000) != 0);
            offset += previousDataLength; // Skip the previous data
            
            int idx = offset;
            numRead = 0;
            int result = 0;
            do {
                if (idx >= data.length) return null;
                read = data[idx++];
                int value = (read & 0b01111111);
                result |= (value << (7 * numRead));
                numRead++;
                if (numRead > 5) return null;
            } while ((read & 0b10000000) != 0);
            int length = result;
            if (length < 0 || idx + length > data.length) return null;
            return new String(data, idx, length, StandardCharsets.UTF_8);
        } catch (Exception e) {
            getLogger().warning("Failed to decode string at offset: " + e.getMessage());
            return null;
        }
    }

    private record ClientInfo(boolean fabric, Set<String> mods, boolean signatureVerified, boolean veltonVerified, String modListNonce, String integrityNonce, String veltonNonce) {}
}
