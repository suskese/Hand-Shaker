package me.mklv.handshaker.common.commands;

import me.mklv.handshaker.common.configs.ConfigTypes;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class OptionalList {
    private OptionalList() {
    }

    public static final class OptionalModsSnapshot {
        private final Map<String, OptionalModStatus> optionalModsByType;

        public OptionalModsSnapshot(Map<String, OptionalModStatus> optionalModsByType) {
            this.optionalModsByType = optionalModsByType != null
                ? new HashMap<>(optionalModsByType)
                : new HashMap<>();
        }

        public OptionalModsSnapshot() {
            this(new HashMap<>());
        }

        public OptionalModStatus getStatus(String modId) {
            if (modId == null) {
                return null;
            }
            return optionalModsByType.get(modId.toLowerCase(Locale.ROOT));
        }

        public boolean isOptional(String modId) {
            return getStatus(modId) != null;
        }

        public boolean isOptionalIn(String modId, String listType) {
            OptionalModStatus status = getStatus(modId);
            return status != null && status.isOptionalIn(listType);
        }

        public Set<String> getModsOptionalIn(String listType) {
            Set<String> result = new LinkedHashSet<>();
            for (Map.Entry<String, OptionalModStatus> entry : optionalModsByType.entrySet()) {
                if (entry.getValue().isOptionalIn(listType)) {
                    result.add(entry.getKey());
                }
            }
            return result;
        }

        public Set<String> getAllOptionalMods() {
            return Collections.unmodifiableSet(new LinkedHashSet<>(optionalModsByType.keySet()));
        }

        public int getOptionalModCount() {
            return optionalModsByType.size();
        }

        public void add(String modId, String listType, ConfigTypes.ModEntry entry) {
            if (modId == null || listType == null) {
                return;
            }
            String normalizedId = modId.toLowerCase(Locale.ROOT);
            OptionalModStatus status = optionalModsByType.computeIfAbsent(
                normalizedId,
                k -> new OptionalModStatus(modId, entry)
            );
            status.addListType(listType);
        }

        public void clear() {
            optionalModsByType.clear();
        }

        public Map<String, OptionalModStatus> getSnapshot() {
            return Collections.unmodifiableMap(new HashMap<>(optionalModsByType));
        }
    }

    public static final class OptionalModStatus {
        private final String modKey;
        private final ConfigTypes.ModEntry entry;
        private final Set<String> listTypes;

        public OptionalModStatus(String modKey, ConfigTypes.ModEntry entry) {
            this.modKey = modKey;
            this.entry = entry;
            this.listTypes = new LinkedHashSet<>();
        }

        public String getModKey() {
            return modKey;
        }

        public ConfigTypes.ModEntry getEntry() {
            return entry;
        }

        public String getModId() {
            return entry != null ? entry.modId() : null;
        }

        public String getVersion() {
            return entry != null ? entry.version() : null;
        }

        public boolean isOptionalIn(String listType) {
            return listType != null && listTypes.contains(listType.toLowerCase(Locale.ROOT));
        }

        public Set<String> getListTypes() {
            return Collections.unmodifiableSet(new LinkedHashSet<>(listTypes));
        }

        protected void addListType(String listType) {
            if (listType != null) {
                listTypes.add(listType.toLowerCase(Locale.ROOT));
            }
        }

        public boolean isOptionalInMultiple() {
            return listTypes.size() > 1;
        }

        public int getListTypeCount() {
            return listTypes.size();
        }
    }

    public static final class SnapshotBuilder {
        private final Map<String, OptionalModStatus> mods = new HashMap<>();

        public SnapshotBuilder addOptionalModsFromList(Set<String> optionalMods,
                                                       String listType,
                                                       Map<String, ConfigTypes.ConfigState.ModConfig> modMap) {
            if (optionalMods == null || optionalMods.isEmpty() || listType == null) {
                return this;
            }

            for (String modKey : optionalMods) {
                if (modKey == null || modKey.trim().isEmpty()) {
                    continue;
                }

                ConfigTypes.ModEntry entry = ConfigTypes.ModEntry.parse(modKey);
                if (entry == null || entry.modId() == null) {
                    continue;
                }

                String normalizedId = entry.modId().toLowerCase(Locale.ROOT);
                OptionalModStatus status = mods.computeIfAbsent(
                    normalizedId,
                    k -> new OptionalModStatus(modKey, entry)
                );
                status.addListType(listType);
            }

            return this;
        }

        public SnapshotBuilder clear() {
            mods.clear();
            return this;
        }

        public OptionalModsSnapshot build() {
            return new OptionalModsSnapshot(new HashMap<>(mods));
        }
    }

    public static boolean isOptionalAndWhitelisted(String modId,
                                                   OptionalModsSnapshot optionalSnapshot,
                                                   Set<String> whitelistedMods) {
        if (modId == null || optionalSnapshot == null || whitelistedMods == null) {
            return false;
        }

        return optionalSnapshot.isOptionalIn(modId, "whitelisted")
            && whitelistedMods.contains(modId);
    }

    public static ConflictResolution resolveListConflict(String modId,
                                                         OptionalModsSnapshot optionalSnapshot,
                                                         Set<String> requiredMods,
                                                         Set<String> blacklistedMods) {
        if (modId == null || optionalSnapshot == null) {
            return ConflictResolution.NO_CONFLICT;
        }

        boolean isRequired = requiredMods != null && requiredMods.contains(modId);
        boolean isBlacklisted = blacklistedMods != null && blacklistedMods.contains(modId);
        boolean isOptional = optionalSnapshot.isOptional(modId);

        if (!isOptional) {
            return ConflictResolution.NO_CONFLICT;
        }

        if (isRequired) {
            return ConflictResolution.REQUIRED_TAKES_PRECEDENCE;
        }

        if (isBlacklisted) {
            return ConflictResolution.BLACKLISTED_TAKES_PRECEDENCE;
        }

        return ConflictResolution.OPTIONAL_ONLY;
    }

    public enum ConflictResolution {
        NO_CONFLICT("No conflict"),
        OPTIONAL_ONLY("Mod is optional only"),
        REQUIRED_TAKES_PRECEDENCE("Required list takes precedence over optional"),
        BLACKLISTED_TAKES_PRECEDENCE("Blacklisted list takes precedence over optional");

        private final String description;

        ConflictResolution(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public static String formatOptionalModsList(Set<String> optionalMods) {
        if (optionalMods == null || optionalMods.isEmpty()) {
            return "(none)";
        }
        return String.join(", ", optionalMods);
    }

    public static ValidationResult validate(OptionalModsSnapshot optionalSnapshot,
                                           Set<String> requiredMods,
                                           Set<String> blacklistedMods,
                                           Set<String> whitelistedMods) {
        ValidationResult result = new ValidationResult();

        if (optionalSnapshot == null || optionalSnapshot.getOptionalModCount() == 0) {
            return result;
        }

        for (String optionalModId : optionalSnapshot.getAllOptionalMods()) {
            ConflictResolution conflict = resolveListConflict(optionalModId, optionalSnapshot, requiredMods, blacklistedMods);

            if (conflict == ConflictResolution.REQUIRED_TAKES_PRECEDENCE) {
                result.addConflict(optionalModId, "Optional mod is also in required list (required takes precedence)");
            } else if (conflict == ConflictResolution.BLACKLISTED_TAKES_PRECEDENCE) {
                result.addConflict(optionalModId, "Optional mod is also in blacklisted list (blacklisted takes precedence)");
            }
        }

        return result;
    }

    public static final class ValidationResult {
        private final Map<String, String> conflicts = new LinkedHashMap<>();
        private boolean hasIssues = false;

        public void addConflict(String modId, String message) {
            conflicts.put(modId, message);
            hasIssues = true;
        }

        public boolean hasConflicts() {
            return hasIssues;
        }

        public Map<String, String> getConflicts() {
            return Collections.unmodifiableMap(new HashMap<>(conflicts));
        }

        public int getConflictCount() {
            return conflicts.size();
        }

        @Override
        public String toString() {
            if (!hasConflicts()) {
                return "ValidationResult: No conflicts";
            }
            StringBuilder sb = new StringBuilder("ValidationResult: ").append(conflicts.size()).append(" conflict(s)\n");
            for (Map.Entry<String, String> entry : conflicts.entrySet()) {
                sb.append("  - ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
            return sb.toString();
        }
    }
}
