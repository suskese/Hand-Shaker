package me.mklv.handshaker.server;

import com.google.gson.*;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
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
            .create();
    private final HandShakerServer server;
    private File configFile;
    private ConfigData configData;

    public BlacklistConfig(HandShakerServer server) {
        this.server = server;
    }

    private static class ConfigData {
        String config = "v2"; // Config version marker - default to v2
        IntegrityMode integrity = IntegrityMode.SIGNED;
        Behavior behavior = Behavior.STRICT;
        @SerializedName("invalid_signature_kick_message")
        String invalidSignatureKickMessage = "Invalid client signature. Please use the official client.";
        @SerializedName("kick_message")
        String kickMessage = "You are using a blacklisted mod: {mod}. Please remove it to join this server.";
        @SerializedName("missing_mod_message")
        String noHandshakeKickMessage = "To connect to this server please download 'Hand-shaker' mod.";
        @SerializedName("missing_whitelist_mod_message")
        String missingWhitelistModMessage = "You are missing required mods: {mod}. Please install them to join this server.";
        @SerializedName("default_mode")
        DefaultMode defaultMode = DefaultMode.ALLOWED;
        Map<String, ModStatus> mods = new LinkedHashMap<>();

        {
            mods.put("hand-shaker", ModStatus.REQUIRED);
            mods.put("xraymod", ModStatus.BLACKLISTED);
            mods.put("testmod", ModStatus.BLACKLISTED);
            mods.put("forge", ModStatus.BLACKLISTED);
        }
    }

    public enum Behavior {STRICT, VANILLA}
    public enum IntegrityMode {SIGNED, DEV}
    public enum ModStatus { REQUIRED, ALLOWED, BLACKLISTED }
    public enum DefaultMode { ALLOWED, BLACKLISTED }

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

    public void load() {
        configFile = new File(FabricLoader.getInstance().getConfigDir().toFile(), "hand-shaker.json");
        if (configFile.exists()) {
            try (FileReader reader = new FileReader(configFile)) {
                configData = GSON.fromJson(reader, ConfigData.class);
                if (configData == null) {
                    configData = new ConfigData();
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
    public boolean isV2Config() { return "v2".equalsIgnoreCase(configData.config); }
    public Map<String, ModStatus> getModStatusMap() { return Collections.unmodifiableMap(configData.mods); }
    public DefaultMode getDefaultMode() { return configData.defaultMode; }


    public boolean setModStatus(String modId, ModStatus status) {
        modId = modId.toLowerCase(Locale.ROOT);
        ModStatus oldStatus = configData.mods.get(modId);
        if (oldStatus == status) return false;
        configData.mods.put(modId, status);
        save();
        return true;
    }

    public boolean removeModStatus(String modId) {
        modId = modId.toLowerCase(Locale.ROOT);
        boolean removed = configData.mods.remove(modId) != null;
        if (removed) save();
        return removed;
    }

    public ModStatus getModStatus(String modId) {
        modId = modId.toLowerCase(Locale.ROOT);
        ModStatus status = configData.mods.get(modId);
        if (status != null) return status;
        return configData.defaultMode == DefaultMode.ALLOWED ? ModStatus.ALLOWED : ModStatus.BLACKLISTED;
    }

    public void addAllMods(Set<String> mods, ModStatus status) {
        for (String mod : mods) {
            configData.mods.put(mod.toLowerCase(Locale.ROOT), status);
        }
        save();
    }


    public void checkPlayer(ServerPlayerEntity player, HandShakerServer.ClientInfo info) {
        if (info == null) return; // Should not happen, but safeguard

        boolean isFabric = !info.mods().isEmpty();
        if (getBehavior() == Behavior.STRICT && !isFabric) {
            player.networkHandler.disconnect(Text.of(getNoHandshakeKickMessage()));
            return;
        }

        // Integrity Check
        if (getIntegrityMode() == IntegrityMode.SIGNED && !info.signatureVerified()) {
            player.networkHandler.disconnect(Text.of(getInvalidSignatureKickMessage()));
            return;
        }

        Set<String> mods = info.mods();

        List<String> requiredMissing = new ArrayList<>();
        List<String> blacklistedPresent = new ArrayList<>();

        // Check all player's mods
        for (String mod : mods) {
            ModStatus status = getModStatus(mod);
            if (status == ModStatus.BLACKLISTED) {
                blacklistedPresent.add(mod);
            }
        }

        // Check all required mods
        for (Map.Entry<String, ModStatus> entry : getModStatusMap().entrySet()) {
            if (entry.getValue() == ModStatus.REQUIRED) {
                if (!mods.contains(entry.getKey())) {
                    requiredMissing.add(entry.getKey());
                }
            }
        }

        if (!requiredMissing.isEmpty()) {
            String msg = getMissingWhitelistModMessage().replace("{mod}", String.join(", ", requiredMissing));
            player.networkHandler.disconnect(Text.of(msg));
            return;
        }

        if (!blacklistedPresent.isEmpty()) {
            String msg = getKickMessage().replace("{mod}", String.join(", ", blacklistedPresent));
            player.networkHandler.disconnect(Text.of(msg));
            return;
        }
    }
}
