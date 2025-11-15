package me.mklv.handshaker.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class BlacklistConfig {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(Behavior.class, new BehaviorDeserializer())
            .registerTypeAdapter(Mode.class, new ModeDeserializer())
            .registerTypeAdapter(IntegrityMode.class, new IntegrityModeDeserializer())
            .create();
    private final HandShakerServer server;
    private File configFile;
    private ConfigData configData;

    public BlacklistConfig(HandShakerServer server) {
        this.server = server;
    }

    private static class ConfigData {
        IntegrityMode integrity = IntegrityMode.SIGNED;
        Mode mode = Mode.BLACKLIST;
        Behavior behavior = Behavior.STRICT;
        @SerializedName("invalid_signature_kick_message")
        String invalidSignatureKickMessage = "Invalid client signature. Please use the official client.";
        @SerializedName("kick_message")
        String kickMessage = "You are using a blacklisted mod: {mod}. Please remove it to join this server.";
        @SerializedName("missing_mod_message")
        String noHandshakeKickMessage = "To connect to this server please download 'Hand-shaker' mod.";
        @SerializedName("missing_whitelist_mod_message")
        String missingWhitelistModMessage = "You are missing required mods: {mod}. Please install them to join this server.";
        @SerializedName("extra_whitelist_mod_message")
        String extraWhitelistModMessage = "You have mods that are not on the whitelist: {mod}. Please remove them to join.";
        @SerializedName("blacklisted_mods")
        Set<String> blacklistedMods = new LinkedHashSet<>();
        @SerializedName("whitelisted_mods")
        Set<String> whitelistedMods = new LinkedHashSet<>();

        // For backwards compatibility
        @SerializedName("kickMode")
        KickMode oldKickMode = null;
    }

    public enum Mode {BLACKLIST, WHITELIST, REQUIRE}
    public enum Behavior {STRICT, VANILLA}
    public enum IntegrityMode {SIGNED, DEV}
    public enum KickMode {ALL, FABRIC} // For backwards compatibility

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

    public static class ModeDeserializer implements JsonDeserializer<Mode> {
        @Override
        public Mode deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            try {
                return Mode.valueOf(json.getAsString().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return Mode.BLACKLIST; // default value
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

    public void load() {
        configFile = new File(FabricLoader.getInstance().getConfigDir().toFile(), "hand-shaker.json");
        if (configFile.exists()) {
            try (FileReader reader = new FileReader(configFile)) {
                configData = GSON.fromJson(reader, ConfigData.class);
                if (configData == null) {
                    configData = new ConfigData();
                }
                // Backwards compatibility
                if (configData.oldKickMode != null) {
                    configData.behavior = configData.oldKickMode == KickMode.ALL ? Behavior.STRICT : Behavior.VANILLA;
                    configData.oldKickMode = null; // Don't need it anymore
                    save();
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
    public Mode getMode() { return configData.mode; }
    public Behavior getBehavior() { return configData.behavior; }
    public String getInvalidSignatureKickMessage() { return configData.invalidSignatureKickMessage; }
    public String getKickMessage() { return configData.kickMessage; }
    public String getNoHandshakeKickMessage() { return configData.noHandshakeKickMessage; }
    public String getMissingWhitelistModMessage() { return configData.missingWhitelistModMessage; }
    public String getExtraWhitelistModMessage() { return configData.extraWhitelistModMessage; }
    public Set<String> getBlacklistedMods() { return Collections.unmodifiableSet(configData.blacklistedMods); }
    public Set<String> getWhitelistedMods() { return Collections.unmodifiableSet(configData.whitelistedMods); }

    public void setMode(Mode mode) {
        configData.mode = mode;
        save();
    }

    public boolean addMod(String modId) {
        boolean added = configData.blacklistedMods.add(modId.toLowerCase(Locale.ROOT));
        if (added) save();
        return added;
    }

    public boolean removeMod(String modId) {
        boolean removed = configData.blacklistedMods.remove(modId.toLowerCase(Locale.ROOT));
        if (removed) save();
        return removed;
    }

    public void setWhitelist(Set<String> mods) {
        configData.whitelistedMods = new LinkedHashSet<>();
        for (String mod : mods) {
            configData.whitelistedMods.add(mod.toLowerCase(Locale.ROOT));
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
        if (getMode() == Mode.BLACKLIST) {
            List<String> hits = new ArrayList<>();
            for (String mod : getBlacklistedMods()) {
                if (mods.contains(mod)) {
                    hits.add(mod);
                }
            }
            if (!hits.isEmpty()) {
                String msg = getKickMessage().replace("{mod}", String.join(", ", hits));
                player.networkHandler.disconnect(Text.of(msg));
            }
        } else { // WHITELIST OR REQUIRE
            if (isFabric || !getWhitelistedMods().isEmpty()) {
                Set<String> whitelistedMods = getWhitelistedMods();
                List<String> missing = new ArrayList<>();
                for (String mod : whitelistedMods) {
                    if (!mods.contains(mod)) {
                        missing.add(mod);
                    }
                }
                if (!missing.isEmpty()) {
                    String msg = getMissingWhitelistModMessage().replace("{mod}", String.join(", ", missing));
                    player.networkHandler.disconnect(Text.of(msg));
                    return;
                }

                if (getMode() == Mode.WHITELIST) {    // WHITELIST ONLY
                    
                    List<String> extra = new ArrayList<>();
                    for (String mod : mods) {
                        if (!whitelistedMods.contains(mod)) {
                            extra.add(mod);
                        }
                    }
                    if (!extra.isEmpty()) {
                        String msg = getExtraWhitelistModMessage().replace("{mod}", String.join(", ", extra));
                        player.networkHandler.disconnect(Text.of(msg));
                    }
                }
            }
        }
    }
}
