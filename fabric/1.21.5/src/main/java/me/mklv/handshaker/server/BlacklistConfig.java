package me.mklv.handshaker.server;

import com.google.gson.*;
import com.google.gson.annotations.SerializedName;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;

public class BlacklistConfig {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(Behavior.class, new BehaviorDeserializer())
            .registerTypeAdapter(IntegrityMode.class, new IntegrityModeDeserializer())
            .registerTypeAdapter(ModStatus.class, new ModStatusDeserializer())
            .registerTypeAdapter(DefaultMode.class, new DefaultModeDeserializer())
            .registerTypeAdapter(ModConfig.class, new ModConfigDeserializer())
            .registerTypeAdapter(Action.class, new ActionDeserializer())
            .create();
    private File configFile;
    private ConfigData configData;

    private static class ConfigData {
        String config = "v3"; // Config version marker - v3 for new structure
        IntegrityMode integrity = IntegrityMode.SIGNED;
        Behavior behavior = Behavior.STRICT;
        @SerializedName("invalid_signature_kick_message")
        String invalidSignatureKickMessage = "Invalid client signature. Please use the official client.";
        @SerializedName("kick_message")
        String kickMessage = "You are using a blacklisted mod: {mods}. Please remove it to join this server.";
        @SerializedName("missing_mod_message")
        String noHandshakeKickMessage = "To connect to this server please download 'Hand-shaker' mod.";
        @SerializedName("missing_whitelist_mod_message")
        String missingWhitelistModMessage = "You are missing required mods: {mods}. Please install them to join this server.";
        @SerializedName("default_mode")
        DefaultMode defaultMode = DefaultMode.ALLOWED;
        Map<String, ModConfig> mods = new LinkedHashMap<>();
        Set<String> ignoredMods = new LinkedHashSet<>();

        {
            mods.put("hand-shaker", new ModConfig(ModStatus.REQUIRED, Action.KICK, null));
            mods.put("xraymod", new ModConfig(ModStatus.BLACKLISTED, Action.KICK, null));
            mods.put("testmod", new ModConfig(ModStatus.BLACKLISTED, Action.KICK, null));
            mods.put("forge", new ModConfig(ModStatus.BLACKLISTED, Action.KICK, null));
        }
    }

    public enum Behavior {STRICT, VANILLA}
    public enum IntegrityMode {SIGNED, DEV}
    public enum ModStatus { REQUIRED, ALLOWED, BLACKLISTED }
    public enum DefaultMode { ALLOWED, BLACKLISTED }
    public enum Action { KICK, BAN }

    public static class ModConfig {
        ModStatus mode;
        Action action;
        @SerializedName("warn-message")
        String warnMessage;

        public ModConfig(ModStatus mode, Action action, String warnMessage) {
            this.mode = mode;
            this.action = action != null ? action : Action.KICK;
            this.warnMessage = warnMessage;
        }

        public ModStatus getMode() { return mode; }
        public Action getAction() { return action != null ? action : Action.KICK; }
        public String getWarnMessage() { return warnMessage; }
    }

    public static class IntegrityModeDeserializer implements JsonDeserializer<IntegrityMode> {
        @Override
        public IntegrityMode deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            try {
                return IntegrityMode.valueOf(json.getAsString().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return IntegrityMode.DEV; // default value
            }
        }
    }

    public static class BehaviorDeserializer implements JsonDeserializer<Behavior> {
        @Override
        public Behavior deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            try {
                return Behavior.valueOf(json.getAsString().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return Behavior.VANILLA; // default value
            }
        }
    }

    public static class ModStatusDeserializer implements JsonDeserializer<ModStatus> {
        @Override
        public ModStatus deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            try {
                return ModStatus.valueOf(json.getAsString().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return ModStatus.ALLOWED; // default value
            }
        }
    }

    public static class DefaultModeDeserializer implements JsonDeserializer<DefaultMode> {
        @Override
        public DefaultMode deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            try {
                return DefaultMode.valueOf(json.getAsString().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return DefaultMode.ALLOWED; // default value
            }
        }
    }

    public static class ActionDeserializer implements JsonDeserializer<Action> {
        @Override
        public Action deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            try {
                return Action.valueOf(json.getAsString().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return Action.KICK; // default value
            }
        }
    }

    public static class ModConfigDeserializer implements JsonDeserializer<ModConfig> {
        @Override
        public ModConfig deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (json.isJsonPrimitive()) {
                // Backward compatibility: old format was just a status string
                String statusStr = json.getAsString().toUpperCase(Locale.ROOT);
                try {
                    ModStatus status = ModStatus.valueOf(statusStr);
                    return new ModConfig(status, Action.KICK, null);
                } catch (IllegalArgumentException e) {
                    return new ModConfig(ModStatus.ALLOWED, Action.KICK, null);
                }
            } else if (json.isJsonObject()) {
                // New format: object with mode, action, warn-message
                JsonObject obj = json.getAsJsonObject();
                ModStatus mode = ModStatus.ALLOWED;
                Action action = Action.KICK;
                String warnMessage = null;

                if (obj.has("mode")) {
                    try {
                        mode = ModStatus.valueOf(obj.get("mode").getAsString().toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException e) {
                        mode = ModStatus.ALLOWED;
                    }
                }

                if (obj.has("action")) {
                    try {
                        action = Action.valueOf(obj.get("action").getAsString().toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException e) {
                        action = Action.KICK;
                    }
                }

                if (obj.has("warn-message")) {
                    warnMessage = obj.get("warn-message").getAsString();
                }

                return new ModConfig(mode, action, warnMessage);
            }
            return new ModConfig(ModStatus.ALLOWED, Action.KICK, null);
        }
    }

    public void load() {
        configFile = new File(FabricLoader.getInstance().getConfigDir().toFile(), "hand-shaker.json");
        if (configFile.exists()) {
            try (FileReader reader = new FileReader(configFile)) {
                configData = GSON.fromJson(reader, ConfigData.class);

                if (configData.config == null || configData.config.equals("v2")) {
                    configData.config = "v3";
                    save(); // Save the migrated config
                }
            } catch (IOException e) {
                HandShakerServer.LOGGER.error("Failed to read blacklist config", e);
                configData = new ConfigData();
            }
        } else {
            configData = new ConfigData();
            save();
        }
    }

    public void save() {
        try (FileWriter writer = new FileWriter(configFile)) {
            GSON.toJson(configData, writer);
        } catch (IOException e) {
            HandShakerServer.LOGGER.error("Failed to save blacklist config", e);
        }
    }

    public IntegrityMode getIntegrityMode() { return configData.integrity; }
    public Behavior getBehavior() { return configData.behavior; }
    public String getInvalidSignatureKickMessage() { return configData.invalidSignatureKickMessage; }
    public String getKickMessage() { return configData.kickMessage; }
    public String getNoHandshakeKickMessage() { return configData.noHandshakeKickMessage; }
    public String getMissingWhitelistModMessage() { return configData.missingWhitelistModMessage; }
    public boolean isV3Config() { return "v3".equalsIgnoreCase(configData.config); }
    public Map<String, ModConfig> getModConfigMap() { return Collections.unmodifiableMap(configData.mods); }
    public Set<String> getIgnoredMods() { return Collections.unmodifiableSet(configData.ignoredMods); }
    public DefaultMode getDefaultMode() { return configData.defaultMode; }

    public boolean setModConfig(String modId, ModStatus status, Action action, String warnMessage) {
        modId = modId.toLowerCase(Locale.ROOT);
        ModConfig oldConfig = configData.mods.get(modId);
        ModConfig newConfig = new ModConfig(status, action != null ? action : Action.KICK, warnMessage);
        
        if (oldConfig != null && oldConfig.getMode() == status && 
            oldConfig.getAction() == newConfig.getAction() &&
            Objects.equals(oldConfig.getWarnMessage(), warnMessage)) {
            return false; // No change
        }
        
        configData.mods.put(modId, newConfig);
        save();
        return true;
    }

    public boolean setModStatus(String modId, ModStatus status) {
        return setModConfig(modId, status, Action.KICK, null);
    }

    public boolean removeModStatus(String modId) {
        modId = modId.toLowerCase(Locale.ROOT);
        boolean removed = configData.mods.remove(modId) != null;
        if (removed) save();
        return removed;
    }

    public ModStatus getModStatus(String modId) {
        modId = modId.toLowerCase(Locale.ROOT);
        ModConfig config = configData.mods.get(modId);
        if (config != null) return config.getMode();
        return configData.defaultMode == DefaultMode.ALLOWED ? ModStatus.ALLOWED : ModStatus.BLACKLISTED;
    }

    public ModConfig getModConfig(String modId) {
        modId = modId.toLowerCase(Locale.ROOT);
        ModConfig config = configData.mods.get(modId);
        if (config != null) return config;
        ModStatus defaultStatus = configData.defaultMode == DefaultMode.ALLOWED ? ModStatus.ALLOWED : ModStatus.BLACKLISTED;
        return new ModConfig(defaultStatus, Action.KICK, null);
    }

    public void addAllMods(Set<String> mods, ModStatus status) {
        for (String mod : mods) {
            configData.mods.put(mod.toLowerCase(Locale.ROOT), new ModConfig(status, Action.KICK, null));
        }
        save();
    }

    public boolean addIgnoredMod(String modId) {
        modId = modId.toLowerCase(Locale.ROOT);
        boolean added = configData.ignoredMods.add(modId);
        if (added) save();
        return added;
    }

    public boolean removeIgnoredMod(String modId) {
        modId = modId.toLowerCase(Locale.ROOT);
        boolean removed = configData.ignoredMods.remove(modId);
        if (removed) save();
        return removed;
    }

    public boolean isModIgnored(String modId) {
        return configData.ignoredMods.contains(modId.toLowerCase(Locale.ROOT));
    }

    public void checkPlayer(ServerPlayerEntity player, HandShakerServer.ClientInfo info) {
        if (info == null) return; // Should not happen, but safeguard

        // Check for bypass permission
        if (Permissions.check(player, "handshaker.bypass", 2)) {
            return; // Player has bypass permission
        }

        boolean isFabric = !info.mods().isEmpty();
        if (getBehavior() == Behavior.STRICT && !isFabric) {
            player.networkHandler.disconnect(Text.of(getNoHandshakeKickMessage()));
            return;
        }

        // If behavior is VANILLA and client doesn't have the mod, skip all checks
        if (getBehavior() == Behavior.VANILLA && !isFabric) {
            return; // Client without mod is allowed in VANILLA mode
        }

        // Integrity Check (only if client has the mod)
        if (isFabric && getIntegrityMode() == IntegrityMode.SIGNED && !info.signatureVerified()) {
            player.networkHandler.disconnect(Text.of(getInvalidSignatureKickMessage()));
            return;
        }

        Set<String> mods = info.mods();

        List<String> requiredMissing = new ArrayList<>();
        Map<String, ModConfig> blacklistedPresent = new LinkedHashMap<>(); // mod -> config

        // Check all player's mods
        for (String mod : mods) {
            ModConfig config = getModConfig(mod);
            if (config.getMode() == ModStatus.BLACKLISTED) {
                blacklistedPresent.put(mod, config);
            }
        }

        // Check all required mods
        for (Map.Entry<String, ModConfig> entry : getModConfigMap().entrySet()) {
            if (entry.getValue().getMode() == ModStatus.REQUIRED) {
                if (!mods.contains(entry.getKey())) {
                    requiredMissing.add(entry.getKey());
                }
            }
        }

        if (!requiredMissing.isEmpty()) {
            String msg = getMissingWhitelistModMessage().replace("{mods}", String.join(", ", requiredMissing));
            player.networkHandler.disconnect(Text.of(msg));
            return;
        }

        if (!blacklistedPresent.isEmpty()) {
            // Handle different actions for blacklisted mods
            // Group by action type - prioritize BAN > KICK
            boolean shouldBan = false;
            boolean shouldKick = false;
            
            for (Map.Entry<String, ModConfig> entry : blacklistedPresent.entrySet()) {
                ModConfig config = entry.getValue();
                Action action = config.getAction();
                
                if (action == Action.BAN) {
                    shouldBan = true;
                } else if (action == Action.KICK) {
                    shouldKick = true;
                }
            }
            
            if (shouldBan) {
                // Ban the player - get server from HandShakerServer instance
                String banReason = "Using blacklisted mods: " + String.join(", ", blacklistedPresent.keySet());
                String msg = getKickMessage().replace("{mods}", String.join(", ", blacklistedPresent.keySet()));
                
                MinecraftServer serverInstance = HandShakerServer.getInstance().getServer();
                if (serverInstance != null) {
                    com.mojang.authlib.GameProfile profile = player.getGameProfile();
                    net.minecraft.server.BannedPlayerEntry banEntry = new net.minecraft.server.BannedPlayerEntry(
                        profile, null, "HandShaker", null, banReason
                    );
                    serverInstance.getPlayerManager().getUserBanList().add(banEntry);
                }
                player.networkHandler.disconnect(Text.of(msg + " (Banned)"));
                return;
            }
            
            if (shouldKick) {
                String msg = getKickMessage().replace("{mods}", String.join(", ", blacklistedPresent.keySet()));
                player.networkHandler.disconnect(Text.of(msg));
                return;
            }
        }
    }
}
