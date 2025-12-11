package me.mklv.handshaker.paper;

import com.google.gson.*;
import org.bukkit.entity.Player;
import java.io.*;
import java.nio.file.Files;
import java.time.Duration;
import java.util.*;

public class BlacklistConfig {
    private final HandShakerPlugin plugin;
    private File file;
    private JsonObject config;

    public enum Behavior { STRICT, VANILLA }
    public enum IntegrityMode { SIGNED, DEV }
    public enum DefaultMode { ALLOWED, BLACKLISTED }
    
    public enum Action {
        KICK, BAN;
        
        public static Action fromString(String str) {
            if (str == null) return KICK;
            return switch (str.toUpperCase(Locale.ROOT)) {
                case "BAN" -> BAN;
                default -> KICK;
            };
        }
    }

    public static class ModConfig {
        private String mode;
        private String action;
        private String warnMessage;

        public ModConfig(String mode, String action, String warnMessage) {
            this.mode = mode != null ? mode : "allowed";
            this.action = action != null ? action : "kick";
            this.warnMessage = warnMessage;
        }

        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
        public Action getAction() { return Action.fromString(action); }
        public void setAction(String action) { this.action = action; }
        public String getWarnMessage() { return warnMessage; }
        public void setWarnMessage(String warnMessage) { this.warnMessage = warnMessage; }
        
        public boolean isRequired() { return "required".equalsIgnoreCase(mode); }
        public boolean isBlacklisted() { return "blacklisted".equalsIgnoreCase(mode); }
        public boolean isAllowed() { return "allowed".equalsIgnoreCase(mode); }
    }

    private Behavior behavior = Behavior.STRICT;
    private IntegrityMode integrityMode = IntegrityMode.SIGNED;
    private String kickMessage = "You are using a blacklisted mod: {mod}. Please remove it to join this server.";
    private String noHandshakeKickMessage = "To connect to this server please download 'Hand-shaker' mod.";
    private String missingWhitelistModMessage = "You are missing required mods: {mod}. Please install them to join this server.";
    private String invalidSignatureKickMessage = "Invalid client signature. Please use the official client.";
    
    private final Map<String, ModConfig> modConfigMap = new LinkedHashMap<>();
    private DefaultMode defaultMode = DefaultMode.ALLOWED;
    private final Set<String> ignoredMods = new HashSet<>();

    public BlacklistConfig(HandShakerPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        if (file == null) {
            file = new File(plugin.getDataFolder(), "hand-shaker.json");
        }

        if (!file.exists()) {
            try {
                Files.copy(
                    Objects.requireNonNull(plugin.getClass().getResourceAsStream("/hand-shaker-example.json")),
                    file.toPath()
                );
            } catch (IOException | NullPointerException e) {
                plugin.getLogger().warning("Could not create default hand-shaker.json");
            }
        }

        try (FileReader reader = new FileReader(file)) {
            config = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException | JsonSyntaxException e) {
            plugin.getLogger().severe("Failed to load hand-shaker.json: " + e.getMessage());
            config = new JsonObject();
        }

        modConfigMap.clear();
        ignoredMods.clear();

        String version = config.has("config") ? config.get("config").getAsString() : "v2";
        
        integrityMode = config.has("Integrity") && config.get("Integrity").getAsString().equalsIgnoreCase("dev") 
            ? IntegrityMode.DEV : IntegrityMode.SIGNED;
        
        String behaviorStr = config.has("Behavior") ? config.get("Behavior").getAsString() : "strict";
        behavior = behaviorStr.toLowerCase(Locale.ROOT).startsWith("strict") ? Behavior.STRICT : Behavior.VANILLA;

        kickMessage = config.has("Kick Message") ? config.get("Kick Message").getAsString() : kickMessage;
        noHandshakeKickMessage = config.has("Missing mod message") ? config.get("Missing mod message").getAsString() : noHandshakeKickMessage;
        missingWhitelistModMessage = config.has("Missing whitelist mod message") 
            ? config.get("Missing whitelist mod message").getAsString()
            : (config.has("Missing required mod message") ? config.get("Missing required mod message").getAsString() : missingWhitelistModMessage);
        invalidSignatureKickMessage = config.has("Invalid signature kick message") 
            ? config.get("Invalid signature kick message").getAsString() : invalidSignatureKickMessage;

        String defaultModeStr = config.has("Default Mode") ? config.get("Default Mode").getAsString() : "allowed";
        defaultMode = defaultModeStr.equalsIgnoreCase("blacklisted") ? DefaultMode.BLACKLISTED : DefaultMode.ALLOWED;

        if (config.has("Ignored Mods") && config.get("Ignored Mods").isJsonArray()) {
            for (JsonElement elem : config.getAsJsonArray("Ignored Mods")) {
                ignoredMods.add(elem.getAsString().toLowerCase(Locale.ROOT));
            }
        }

        if (config.has("Mods")) {
            JsonObject modsObj = config.getAsJsonObject("Mods");
            
            if (version.equals("v3")) {
                for (String modId : modsObj.keySet()) {
                    JsonElement elem = modsObj.get(modId);
                    if (elem.isJsonObject()) {
                        JsonObject modObj = elem.getAsJsonObject();
                        String mode = modObj.has("mode") ? modObj.get("mode").getAsString() : "allowed";
                        String action = modObj.has("action") ? modObj.get("action").getAsString() : "kick";
                        String warnMsg = modObj.has("warn-message") ? modObj.get("warn-message").getAsString() : null;
                        modConfigMap.put(modId.toLowerCase(Locale.ROOT), new ModConfig(mode, action, warnMsg));
                    } else {
                        String mode = elem.getAsString();
                        modConfigMap.put(modId.toLowerCase(Locale.ROOT), new ModConfig(mode, "kick", null));
                    }
                }
            } else {
                for (String modId : modsObj.keySet()) {
                    String mode = modsObj.get(modId).getAsString();
                    modConfigMap.put(modId.toLowerCase(Locale.ROOT), new ModConfig(mode, "kick", null));
                }
                config.addProperty("config", "v3");
                save();
            }
        }
    }

    public Behavior getBehavior() { return behavior; }
    public IntegrityMode getIntegrityMode() { return integrityMode; }
    public String getKickMessage() { return kickMessage; }
    public String getNoHandshakeKickMessage() { return noHandshakeKickMessage; }
    public String getMissingWhitelistModMessage() { return missingWhitelistModMessage; }
    public String getInvalidSignatureKickMessage() { return invalidSignatureKickMessage; }
    public Map<String, ModConfig> getModConfigMap() { return Collections.unmodifiableMap(modConfigMap); }
    public DefaultMode getDefaultMode() { return defaultMode; }
    public Set<String> getIgnoredMods() { return Collections.unmodifiableSet(ignoredMods); }

    public boolean addIgnoredMod(String modId) {
        if (ignoredMods.add(modId.toLowerCase(Locale.ROOT))) {
            save();
            return true;
        }
        return false;
    }

    public boolean removeIgnoredMod(String modId) {
        if (ignoredMods.remove(modId.toLowerCase(Locale.ROOT))) {
            save();
            return true;
        }
        return false;
    }

    public boolean isIgnored(String modId) {
        return ignoredMods.contains(modId.toLowerCase(Locale.ROOT));
    }

    public boolean setModConfig(String modId, String mode, String action, String warnMessage) {
        modId = modId.toLowerCase(Locale.ROOT);
        ModConfig existing = modConfigMap.get(modId);
        
        if (existing != null) {
            if (mode != null) existing.setMode(mode);
            if (action != null) existing.setAction(action);
            if (warnMessage != null) existing.setWarnMessage(warnMessage);
        } else {
            modConfigMap.put(modId, new ModConfig(mode, action, warnMessage));
        }
        
        save();
        return true;
    }

    public boolean removeModConfig(String modId) {
        modId = modId.toLowerCase(Locale.ROOT);
        boolean removed = modConfigMap.remove(modId) != null;
        if (removed) save();
        return removed;
    }

    public ModConfig getModConfig(String modId) {
        modId = modId.toLowerCase(Locale.ROOT);
        ModConfig cfg = modConfigMap.get(modId);
        if (cfg != null) return cfg;
        String defaultModeStr = defaultMode == DefaultMode.ALLOWED ? "allowed" : "blacklisted";
        return new ModConfig(defaultModeStr, "kick", null);
    }

    public void addAllMods(Set<String> mods, String mode, String action, String warnMessage) {
        for (String mod : mods) {
            modConfigMap.put(mod.toLowerCase(Locale.ROOT), new ModConfig(mode, action, warnMessage));
        }
        save();
    }

    public String checkPlayer(Player player, Set<String> clientMods) {
        // Check for bypass permission
        if (player.hasPermission("handshaker.bypass")) {
            return null; // Player has bypass permission
        }
        
        Set<String> missingRequired = new HashSet<>();
        Set<String> blacklistedFound = new HashSet<>();

        for (Map.Entry<String, ModConfig> entry : modConfigMap.entrySet()) {
            String modId = entry.getKey();
            ModConfig cfg = entry.getValue();
            boolean hasM = clientMods.contains(modId);

            if (cfg.isRequired() && !hasM) {
                missingRequired.add(modId);
            } else if (cfg.isBlacklisted() && hasM) {
                Action act = cfg.getAction();
                blacklistedFound.add(modId);
                
                if (act == Action.BAN) {
                    String reason = kickMessage.replace("{mod}", modId);
                    player.ban(reason, (Duration) null, null);
                }
            }
        }

        if (!missingRequired.isEmpty()) {
            return missingWhitelistModMessage.replace("{mod}", String.join(", ", missingRequired));
        }
        if (!blacklistedFound.isEmpty()) {
            return kickMessage.replace("{mod}", String.join(", ", blacklistedFound));
        }

        return null;
    }

    public void save() {
        config.addProperty("config", "v3");
        config.addProperty("Kick Message", kickMessage);
        config.addProperty("Missing required mod message", missingWhitelistModMessage);
        config.addProperty("Missing mod message", noHandshakeKickMessage);
        config.addProperty("Invalid signature kick message", invalidSignatureKickMessage);
        config.addProperty("Behavior", behavior == Behavior.STRICT ? "Strict" : "Vanilla");
        config.addProperty("Integrity", integrityMode == IntegrityMode.SIGNED ? "Signed" : "Dev");
        config.addProperty("Default Mode", defaultMode == DefaultMode.ALLOWED ? "allowed" : "blacklisted");

        JsonArray ignoredArray = new JsonArray();
        for (String mod : ignoredMods) {
            ignoredArray.add(mod);
        }
        config.add("Ignored Mods", ignoredArray);

        JsonObject modsObj = new JsonObject();
        for (Map.Entry<String, ModConfig> entry : modConfigMap.entrySet()) {
            ModConfig cfg = entry.getValue();
            JsonObject modObj = new JsonObject();
            modObj.addProperty("mode", cfg.getMode());
            modObj.addProperty("action", cfg.action);
            if (cfg.getWarnMessage() != null) {
                modObj.addProperty("warn-message", cfg.getWarnMessage());
            }
            modsObj.add(entry.getKey(), modObj);
        }
        config.add("Mods", modsObj);

        try (FileWriter writer = new FileWriter(file)) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            gson.toJson(config, writer);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save hand-shaker.json!");
        }
    }
}