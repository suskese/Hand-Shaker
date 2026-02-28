package me.mklv.handshaker.common.utils;

import me.mklv.handshaker.common.configs.ConfigTypes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ModpackHashing {
    private ModpackHashing() {
    }

    public static String compute(Set<String> mods, boolean includeFileHashes) {
        if (mods == null || mods.isEmpty()) {
            return HashUtils.sha256Hex("");
        }

        List<String> normalized = new ArrayList<>();
        for (String raw : mods) {
            ConfigTypes.ModEntry entry = ConfigTypes.ModEntry.parse(raw);
            if (entry == null) {
                continue;
            }

            StringBuilder token = new StringBuilder(entry.modId());
            if (entry.version() != null && !entry.version().isBlank()) {
                token.append(':').append(entry.version());
            }

            if (includeFileHashes && entry.hash() != null && !entry.hash().isBlank() && !"null".equals(entry.hash())) {
                token.append(':').append(entry.hash());
            }

            normalized.add(token.toString().toLowerCase(Locale.ROOT));
        }

        Collections.sort(normalized);
        return HashUtils.sha256Hex(String.join(",", normalized));
    }
}