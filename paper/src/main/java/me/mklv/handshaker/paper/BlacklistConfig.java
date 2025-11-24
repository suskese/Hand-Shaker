package me.mklv.handshaker.paper;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.ConfigurationSection;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class BlacklistConfig {
    private final HandShakerPlugin plugin;
    private File file;
    private YamlConfiguration config;

    public enum Behavior { STRICT, VANILLA }
    public enum IntegrityMode { SIGNED, DEV }
    public enum ModStatus { REQUIRED, ALLOWED, BLACKLISTED }
    public enum DefaultMode { ALLOWED, BLACKLISTED }

    private Behavior behavior = Behavior.STRICT;
    private IntegrityMode integrityMode = IntegrityMode.SIGNED;
    private String kickMessage = "You are using a blacklisted mod: {mod}. Please remove it to join this server.";
    private String noHandshakeKickMessage = "To connect to this server please download 'Hand-shaker' mod.";
    private String missingWhitelistModMessage = "You are missing required mods: {mod}. Please install them to join this server.";
    private String invalidSignatureKickMessage = "Invalid client signature. Please use the official client.";
    
    private final Map<String, ModStatus> modStatusMap = new LinkedHashMap<>();
    private DefaultMode defaultMode = DefaultMode.ALLOWED;

    public BlacklistConfig(HandShakerPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        if (file == null) {
            file = new File(plugin.getDataFolder(), "config.yml");
        }

        if (!file.exists()) {
            file.getParentFile().mkdirs();
            plugin.saveResource("config.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
        modStatusMap.clear();

        // Parse Integrity Mode
        String integrityString = config.getString("Integrity", "signed").toUpperCase(Locale.ROOT);
        integrityMode = integrityString.equals("SIGNED") ? IntegrityMode.SIGNED : IntegrityMode.DEV;

        // Parse Behavior
        String behaviorString = config.getString("Behavior", "strict").toUpperCase(Locale.ROOT);
        behavior = behaviorString.startsWith("STRICT") ? Behavior.STRICT : Behavior.VANILLA;

        // Parse Kick Messages
        kickMessage = config.getString("Kick Message", "You are using a blacklisted mod: {mod}. Please remove it to join this server.");
        noHandshakeKickMessage = config.getString("Missing mod message", "To connect to this server please download 'Hand-shaker' mod.");
        missingWhitelistModMessage = config.getString("Missing whitelist mod message", 
            config.getString("Missing required mod message", "You are missing required mods: {mod}. Please install them to join this server."));
        invalidSignatureKickMessage = config.getString("Invalid signature kick message", "Invalid client signature. Please use the official client.");

        // V2 Config Format
        String defaultModeString = config.getString("Default Mode", "allowed").toUpperCase(Locale.ROOT);
        defaultMode = defaultModeString.equals("BLACKLISTED") ? DefaultMode.BLACKLISTED : DefaultMode.ALLOWED;
        
        // Parse Mods section
        ConfigurationSection modsSection = config.getConfigurationSection("Mods");
        if (modsSection != null) {
            for (String modId : modsSection.getKeys(false)) {
                String statusString = modsSection.getString(modId, "allowed").toUpperCase(Locale.ROOT);
                ModStatus status;
                switch (statusString) {
                    case "REQUIRED" -> status = ModStatus.REQUIRED;
                    case "BLACKLISTED" -> status = ModStatus.BLACKLISTED;
                    default -> status = ModStatus.ALLOWED;
                }
                modStatusMap.put(modId.toLowerCase(Locale.ROOT), status);
            }
        }
    }

    public Behavior getBehavior() { return behavior; }
    public IntegrityMode getIntegrityMode() { return integrityMode; }
    public String getKickMessage() { return kickMessage; }
    public String getNoHandshakeKickMessage() { return noHandshakeKickMessage; }
    public String getMissingWhitelistModMessage() { return missingWhitelistModMessage; }
    public String getInvalidSignatureKickMessage() { return invalidSignatureKickMessage; }
    public Map<String, ModStatus> getModStatusMap() { return Collections.unmodifiableMap(modStatusMap); }
    public DefaultMode getDefaultMode() { return defaultMode; }

    public boolean setModStatus(String modId, ModStatus status) {
        modId = modId.toLowerCase(Locale.ROOT);
        ModStatus oldStatus = modStatusMap.get(modId);
        if (oldStatus == status) return false;
        modStatusMap.put(modId, status);
        save();
        return true;
    }

    public boolean removeModStatus(String modId) {
        modId = modId.toLowerCase(Locale.ROOT);
        boolean removed = modStatusMap.remove(modId) != null;
        if (removed) save();
        return removed;
    }

    public ModStatus getModStatus(String modId) {
        modId = modId.toLowerCase(Locale.ROOT);
        ModStatus status = modStatusMap.get(modId);
        if (status != null) return status;
        return defaultMode == DefaultMode.ALLOWED ? ModStatus.ALLOWED : ModStatus.BLACKLISTED;
    }

    public void addAllMods(Set<String> mods, ModStatus status) {
        for (String mod : mods) {
            modStatusMap.put(mod.toLowerCase(Locale.ROOT), status);
        }
        save();
    }

    public void save() {
        config.set("config", "v2");
        config.set("Kick Message", kickMessage);
        config.set("Missing required mod message", missingWhitelistModMessage);
        config.set("Missing mod message", noHandshakeKickMessage);
        config.set("Invalid signature kick message", invalidSignatureKickMessage);
        config.set("Behavior", behavior == Behavior.STRICT ? "Strict" : "Vanilla");
        config.set("Integrity", integrityMode == IntegrityMode.SIGNED ? "Signed" : "Dev");
        config.set("Default Mode", defaultMode == DefaultMode.ALLOWED ? "allowed" : "blacklisted");
        
        // Save mods with their status
        config.set("Mods", null); // Clear old section
        for (Map.Entry<String, ModStatus> entry : modStatusMap.entrySet()) {
            String statusString = switch (entry.getValue()) {
                case REQUIRED -> "required";
                case BLACKLISTED -> "blacklisted";
                case ALLOWED -> "allowed";
            };
            config.set("Mods." + entry.getKey(), statusString);
        }
        try { config.save(file); } catch (IOException e) { plugin.getLogger().severe("Could not save config.yml!"); }
    }
}