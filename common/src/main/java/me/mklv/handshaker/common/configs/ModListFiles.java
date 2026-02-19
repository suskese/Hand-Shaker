package me.mklv.handshaker.common.configs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ModListFiles {
    private ModListFiles() {
    }

    public static Path findListFile(Path configDir, String listName) {
        if (configDir == null || listName == null) {
            return null;
        }

        String normalized = normalizeListName(listName);
        if (normalized.isEmpty()) {
            return null;
        }

        String desiredBase = stripExtension(normalized);
        List<Path> candidates = listYamlFiles(configDir);

        // 1) Exact base-name match (case-insensitive)
        for (Path p : candidates) {
            String base = stripExtension(p.getFileName().toString());
            if (base.equalsIgnoreCase(desiredBase)) {
                return p;
            }
        }

        // 2) Try mods- prefix
        String withPrefix = desiredBase.startsWith("mods-") ? desiredBase : ("mods-" + desiredBase);
        for (Path p : candidates) {
            String base = stripExtension(p.getFileName().toString());
            if (base.equalsIgnoreCase(withPrefix)) {
                return p;
            }
        }

        return null;
    }

    public static boolean setListEnabled(Path listFile, boolean enabled, ConfigFileBootstrap.Logger logger) {
        if (listFile == null) {
            return false;
        }

        try {
            List<String> lines = Files.exists(listFile)
                ? Files.readAllLines(listFile, StandardCharsets.UTF_8)
                : new ArrayList<>();

            boolean replaced = false;
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line == null) {
                    continue;
                }

                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }

                // Only match root-level "enabled:" (no leading indentation)
                if (line.startsWith("enabled:")) {
                    lines.set(i, "enabled: " + (enabled ? "true" : "false"));
                    replaced = true;
                    break;
                }
            }

            if (!replaced) {
                int insertAt = 0;
                while (insertAt < lines.size()) {
                    String line = lines.get(insertAt);
                    if (line == null) {
                        insertAt++;
                        continue;
                    }
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                        insertAt++;
                        continue;
                    }
                    break;
                }
                lines.add(insertAt, "enabled: " + (enabled ? "true" : "false"));
                // Add a blank line after for readability (only if there is more content after)
                if (insertAt + 1 < lines.size() && !lines.get(insertAt + 1).trim().isEmpty()) {
                    lines.add(insertAt + 1, "");
                }
            }

            Files.createDirectories(listFile.getParent());
            String newline = detectNewline(lines);
            String out = String.join(newline, lines) + newline;
            Files.writeString(listFile, out, StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            if (logger != null) {
                logger.error("Failed to update list file: " + listFile.getFileName(), e);
            }
            return false;
        }
    }

    private static List<Path> listYamlFiles(Path configDir) {
        List<Path> out = new ArrayList<>();
        try (var stream = Files.list(configDir)) {
            stream
                .filter(p -> Files.isRegularFile(p))
                .filter(p -> {
                    String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                    return name.endsWith(".yml") || name.endsWith(".yaml");
                })
                .forEach(out::add);
        } catch (IOException ignored) {
            // ignore
        }
        return out;
    }

    private static String normalizeListName(String listName) {
        return listName.trim().toLowerCase(Locale.ROOT);
    }

    private static String stripExtension(String filename) {
        if (filename == null) {
            return "";
        }
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".yml")) {
            return filename.substring(0, filename.length() - 4);
        }
        if (lower.endsWith(".yaml")) {
            return filename.substring(0, filename.length() - 5);
        }
        return filename;
    }

    private static String detectNewline(List<String> lines) {
        return System.lineSeparator();
    }
}
