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

    public enum Mode {BLACKLIST, WHITELIST, REQUIRE}
    public enum Behavior { STRICT, VANILLA }
    public enum IntegrityMode { SIGNED, DEV }
    public enum ModStatus { REQUIRED, ALLOWED, BLACKLISTED }
    public enum DefaultMode { ALLOWED, BLACKLISTED }

    private Mode mode = Mode.BLACKLIST;
    private Behavior behavior = Behavior.STRICT;
    private IntegrityMode integrityMode = IntegrityMode.SIGNED;
    private final Set<String> blacklistedMods = new LinkedHashSet<>();
    private final Set<String> whitelistedMods = new LinkedHashSet<>();
    private String kickMessage = "You are using a blacklisted mod: {mod}. Please remove it to join this server.";
    private String noHandshakeKickMessage = "To connect to this server please download 'Hand-shaker' mod.";
    private String missingWhitelistModMessage = "You are missing required mods: {mod}. Please install them to join this server.";
    private String extraWhitelistModMessage = "You have mods that are not on the whitelist: {mod}. Please remove them to join.";
    private String invalidSignatureKickMessage = "Invalid client signature. Please use the official client.";
    
    // V2 config fields
    private boolean isV2Config = false;
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
        blacklistedMods.clear();
        whitelistedMods.clear();
        modStatusMap.clear();
        
        // Check if this is a v2 config
        String configVersion = config.getString("config", "v1");
        isV2Config = configVersion.equalsIgnoreCase("v2");

        // Parse Integrity Mode
        String integrityString = config.getString("Integrity", "signed").toUpperCase(Locale.ROOT);
        integrityMode = integrityString.equals("SIGNED") ? IntegrityMode.SIGNED : IntegrityMode.DEV;

        // Parse Behavior
        String behaviorString = config.getString("Behavior", "strict").toUpperCase(Locale.ROOT);
        if (behaviorString.isEmpty()) {
            // Backwards compatibility
            String oldMode = config.getString("Kick Mode", config.getString("KickMode", "Fabric")).toUpperCase(Locale.ROOT);
            behavior = oldMode.startsWith("ALL") ? Behavior.STRICT : Behavior.VANILLA;
        } else {
            behavior = behaviorString.startsWith("STRICT") ? Behavior.STRICT : Behavior.VANILLA;
        }

        // Parse Kick Messages
        kickMessage = config.getString("Kick Message", "You are using a blacklisted mod: {mod}. Please remove it to join this server.");
        noHandshakeKickMessage = config.getString("Missing mod message", "To connect to this server please download 'Hand-shaker' mod.");
        missingWhitelistModMessage = config.getString("Missing whitelist mod message", 
            config.getString("Missing required mod message", "You are missing required mods: {mod}. Please install them to join this server."));
        extraWhitelistModMessage = config.getString("Extra whitelist mod message", "You have mods that are not on the whitelist: {mod}. Please remove them to join.");
        invalidSignatureKickMessage = config.getString("Invalid signature kick message", "Invalid client signature. Please use the official client.");

        if (isV2Config) {
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
        } else {
            // V1 Config Format (backwards compatibility)
            String modeString = config.getString("Operation Mode", "blacklist").toUpperCase(Locale.ROOT);
            if (modeString.equals("WHITELIST")) {
                mode = Mode.WHITELIST;
            } else if (modeString.equals("REQUIRE")) {
                mode = Mode.REQUIRE;
            } else {
                mode = Mode.BLACKLIST;
            }

            // Parse Blacklisted Mods
            if (config.isList("Blacklisted Mods")) {
                for (Object o : config.getList("Blacklisted Mods")) {
                    if (o != null) blacklistedMods.add(o.toString().toLowerCase(Locale.ROOT));
                }
            }

            // Parse Whitelisted Mods
            if (config.isList("Whitelisted Mods")) {
                for (Object o : config.getList("Whitelisted Mods")) {
                    if (o != null) whitelistedMods.add(o.toString().toLowerCase(Locale.ROOT));
                }
            }
        }
    }

    public Mode getMode() { return mode; }
    public void setMode(Mode mode) { this.mode = mode; save(); }
    public Behavior getBehavior() { return behavior; }
    public IntegrityMode getIntegrityMode() { return integrityMode; }
    public Set<String> getBlacklistedMods() { return Collections.unmodifiableSet(blacklistedMods); }
    public Set<String> getWhitelistedMods() { return Collections.unmodifiableSet(whitelistedMods); }
    public String getKickMessage() { return kickMessage; }
    public String getNoHandshakeKickMessage() { return noHandshakeKickMessage; }
    public String getMissingWhitelistModMessage() { return missingWhitelistModMessage; }
    public String getExtraWhitelistModMessage() { return extraWhitelistModMessage; }
    public String getInvalidSignatureKickMessage() { return invalidSignatureKickMessage; }
    public boolean isV2Config() { return isV2Config; }
    public Map<String, ModStatus> getModStatusMap() { return Collections.unmodifiableMap(modStatusMap); }
    public DefaultMode getDefaultMode() { return defaultMode; }

    // V2 Config methods
    public boolean setModStatus(String modId, ModStatus status) {
        if (!isV2Config) return false;
        modId = modId.toLowerCase(Locale.ROOT);
        ModStatus oldStatus = modStatusMap.get(modId);
        if (oldStatus == status) return false;
        modStatusMap.put(modId, status);
        save();
        return true;
    }

    public boolean removeModStatus(String modId) {
        if (!isV2Config) return false;
        modId = modId.toLowerCase(Locale.ROOT);
        boolean removed = modStatusMap.remove(modId) != null;
        if (removed) save();
        return removed;
    }

    public ModStatus getModStatus(String modId) {
        if (!isV2Config) return null;
        modId = modId.toLowerCase(Locale.ROOT);
        ModStatus status = modStatusMap.get(modId);
        if (status != null) return status;
        return defaultMode == DefaultMode.ALLOWED ? ModStatus.ALLOWED : ModStatus.BLACKLISTED;
    }

    public void addAllMods(Set<String> mods, ModStatus status) {
        if (!isV2Config) return;
        for (String mod : mods) {
            modStatusMap.put(mod.toLowerCase(Locale.ROOT), status);
        }
        save();
    }

    // V1 Config methods (backwards compatibility)
    public boolean addMod(String modId) {
        if (isV2Config) {
            return setModStatus(modId, ModStatus.BLACKLISTED);
        }
        boolean added = blacklistedMods.add(modId.toLowerCase(Locale.ROOT));
        if(added) save();
        return added;
    }
    
    public boolean removeMod(String modId) {
        if (isV2Config) {
            return removeModStatus(modId);
        }
        boolean removed = blacklistedMods.remove(modId.toLowerCase(Locale.ROOT));
        if(removed) save();
        return removed;
    }

    public void setWhitelistedMods(Set<String> mods) {
        if (isV2Config) {
            addAllMods(mods, ModStatus.REQUIRED);
            return;
        }
        whitelistedMods.clear();
        for (String mod : mods) {
            whitelistedMods.add(mod.toLowerCase(Locale.ROOT));
        }
        save();
    }

    public void save() {
        if (isV2Config) {
            // Save V2 format
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
        } else {
            // Save V1 format
            config.set("Integrity", integrityMode == IntegrityMode.SIGNED ? "Signed" : "Dev");
            config.set("Operation Mode", mode == Mode.WHITELIST ? "whitelist" : mode == Mode.REQUIRE ? "require" : "blacklist");
            config.set("Behavior", behavior == Behavior.STRICT ? "Strict" : "Vanilla");
            config.set("Kick Message", kickMessage);
            config.set("Missing mod message", noHandshakeKickMessage);
            config.set("Missing whitelist mod message", missingWhitelistModMessage);
            config.set("Extra whitelist mod message", extraWhitelistModMessage);
            config.set("Invalid signature kick message", invalidSignatureKickMessage);
            config.set("Blacklisted Mods", new ArrayList<>(blacklistedMods));
            config.set("Whitelisted Mods", new ArrayList<>(whitelistedMods));
        }
        try { config.save(file); } catch (IOException e) { plugin.getLogger().severe("Could not save config.yml!"); }
    }
}