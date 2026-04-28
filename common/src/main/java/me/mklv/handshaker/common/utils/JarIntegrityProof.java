package me.mklv.handshaker.common.utils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class JarIntegrityProof {
    public static final String HASH_ENTRY = "META-INF/HANDSHAKER.HASH";
    public static final String SIG_ENTRY = "META-INF/HANDSHAKER.SIG";

    public interface LogSink {
        void info(String message);
        void warn(String message);
    }

    public record Proof(byte[] signature, String jarHash) {}

    private JarIntegrityProof() {}

    public static Optional<Proof> buildFromRuntimeJar(Class<?> anchor, LogSink logSink) {
        Optional<Path> jarPath = resolveRuntimeJar(anchor);
        if (jarPath.isEmpty()) {
            logSink.warn("Integrity proof rejected: failed to resolve runtime jar path");
            return Optional.empty();
        }

        Path path = jarPath.get();
        if (!Files.isRegularFile(path)) {
            logSink.warn("Integrity proof rejected: runtime path is not a jar file (" + path + ")");
            return Optional.empty();
        }

        if (!verifyJarSignatureLocally(path, logSink)) {
            logSink.warn("Integrity proof rejected: local jar signature validation failed");
            return Optional.empty();
        }

        Optional<String> computedHash = computeCanonicalJarHash(path);
        Optional<String> embeddedHash = readTextEntry(path, HASH_ENTRY);
        Optional<byte[]> signature = readBinaryEntry(path, SIG_ENTRY);

        if (computedHash.isEmpty() || embeddedHash.isEmpty() || signature.isEmpty()) {
            logSink.warn("Integrity proof rejected: embedded detached integrity files are missing");
            return Optional.empty();
        }

        String expectedHash = embeddedHash.get().trim().toLowerCase(Locale.ROOT);
        if (!computedHash.get().equals(expectedHash)) {
            logSink.warn("Integrity proof rejected: embedded hash mismatch with runtime jar content");
            return Optional.empty();
        }

        return Optional.of(new Proof(signature.get(), computedHash.get()));
    }

    public static Optional<String> computeCanonicalJarHash(Path jarPath) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (JarFile jarFile = new JarFile(jarPath.toFile())) {
                var entries = Collections.list(jarFile.entries()).stream()
                    .filter(entry -> !entry.isDirectory())
                    .filter(entry -> isContentEntry(entry.getName()))
                    .sorted(Comparator.comparing(JarEntry::getName))
                    .toList();

                if (entries.isEmpty()) {
                    return Optional.empty();
                }

                for (JarEntry entry : entries) {
                    try (InputStream input = jarFile.getInputStream(entry)) {
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = input.read(buffer)) > 0) {
                            digest.update(buffer, 0, read);
                        }
                    }
                }
            }

            return Optional.of(HashUtils.toHex(digest.digest()));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    public static boolean verifyJarSignatureLocally(Path jarPath, LogSink logSink) {
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                try (InputStream input = jarFile.getInputStream(entry)) {
                    byte[] buffer = new byte[8192];
                    while (input.read(buffer) > 0) {
                    }
                }
            }
            return true;
        } catch (IOException io) {
            logSink.warn("Jar signature verification failed: " + io.getMessage());
            return false;
        }
    }

    private static Optional<String> readTextEntry(Path jarPath, String name) {
        return readBinaryEntry(jarPath, name)
            .map(bytes -> new String(bytes, StandardCharsets.UTF_8));
    }

    private static Optional<byte[]> readBinaryEntry(Path jarPath, String name) {
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            JarEntry entry = jarFile.getJarEntry(name);
            if (entry == null || entry.isDirectory()) {
                return Optional.empty();
            }
            try (InputStream input = jarFile.getInputStream(entry)) {
                return Optional.of(input.readAllBytes());
            }
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private static Optional<Path> resolveRuntimeJar(Class<?> anchor) {
        try {
            var source = anchor.getProtectionDomain().getCodeSource();
            if (source == null || source.getLocation() == null) {
                return Optional.empty();
            }
            return Optional.of(Path.of(source.getLocation().toURI()));
        } catch (URISyntaxException ignored) {
            return Optional.empty();
        }
    }

    private static boolean isContentEntry(String name) {
        String upper = name.toUpperCase(Locale.ROOT);
        if (!upper.startsWith("META-INF/")) {
            return true;
        }

        return !(upper.endsWith(".SF")
            || upper.endsWith(".RSA")
            || upper.endsWith(".DSA")
            || upper.equals("META-INF/MANIFEST.MF")
            || upper.equals(HASH_ENTRY)
            || upper.equals(SIG_ENTRY));
    }
}
