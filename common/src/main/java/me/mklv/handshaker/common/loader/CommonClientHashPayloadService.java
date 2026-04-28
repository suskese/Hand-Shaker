package me.mklv.handshaker.common.loader;

import me.mklv.handshaker.common.utils.HashUtils;
import me.mklv.handshaker.common.utils.JarIntegrityProof;
import me.mklv.handshaker.common.utils.PayloadCompression;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;

public final class CommonClientHashPayloadService {
    public record ModDescriptor(String id, String displayName, String version, Path modPath) {
    }

    public record ModListData(String transportPayload, String modListHash) {
    }

    public record IntegrityData(byte[] signature, String jarHash) {
    }

    public interface LogSink {
        void info(String message);

        void warn(String message);
    }

    public ModListData buildModListData(Collection<ModDescriptor> mods,
                                        String loaderModId,
                                        String wireModId,
                                        boolean includeDisplayName) {
        String payload = mods.stream()
            .map(mod -> toPayloadEntry(mod, loaderModId, wireModId, includeDisplayName))
            .sorted()
            .reduce((a, b) -> a + "," + b)
            .orElse("");

        String modListHash = HashUtils.sha256Hex(payload);
        String transportPayload = PayloadCompression.compressToEnvelope(payload);
        return new ModListData(transportPayload, modListHash);
    }

    public IntegrityData buildIntegrityData(Class<?> runtimeAnchor, LogSink logger) {
        Optional<JarIntegrityProof.Proof> proof = JarIntegrityProof.buildFromRuntimeJar(
            runtimeAnchor,
            new JarIntegrityProof.LogSink() {
                @Override
                public void info(String message) {
                    logger.info(message);
                }

                @Override
                public void warn(String message) {
                    logger.warn(message);
                }
            }
        );

        if (proof.isPresent()) {
            JarIntegrityProof.Proof integrityProof = proof.get();
            return new IntegrityData(integrityProof.signature().clone(), integrityProof.jarHash());
        }

        logger.warn("Could not build runtime integrity proof. Using empty payload.");
        return new IntegrityData(new byte[0], "");
    }

    private String toPayloadEntry(ModDescriptor mod,
                                  String loaderModId,
                                  String wireModId,
                                  boolean includeDisplayName) {
        String normalizedId = normalizeModId(mod.id(), loaderModId, wireModId);
        String version = nonBlankOrDefault(mod.version(), "unknown");
        String modHash = HashUtils.sha256FileHex(mod.modPath()).orElse("null");

        if (!includeDisplayName) {
            return normalizedId + ":" + version + ":" + modHash;
        }

        String encodedDisplayName = encodeDisplayName(mod.displayName());
        return normalizedId + "~" + encodedDisplayName + ":" + version + ":" + modHash;
    }

    private String normalizeModId(String id, String loaderModId, String wireModId) {
        String safeId = nonBlankOrDefault(id, "unknown");
        if (loaderModId != null && loaderModId.equals(safeId)) {
            return nonBlankOrDefault(wireModId, safeId);
        }
        return safeId;
    }

    private String encodeDisplayName(String name) {
        if (name == null || name.isBlank()) {
            return "null";
        }
        return URLEncoder.encode(name, StandardCharsets.UTF_8);
    }

    private String nonBlankOrDefault(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }
}