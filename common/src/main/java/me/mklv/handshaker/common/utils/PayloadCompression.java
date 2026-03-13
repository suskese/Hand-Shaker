package me.mklv.handshaker.common.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class PayloadCompression {
    private static final String MARKER = "gz1:";

    private PayloadCompression() {
    }

    public static String compressToEnvelope(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
                gzip.write(value.getBytes(StandardCharsets.UTF_8));
            }
            String encoded = Base64.getEncoder().encodeToString(output.toByteArray());
            String wrapped = MARKER + encoded;
            if (wrapped.length() >= value.length()) {
                return value;
            }
            return wrapped;
        } catch (Exception ignored) {
            return value;
        }
    }

    public static Optional<String> decodeEnvelope(String value) {
        if (value == null) {
            return Optional.empty();
        }
        if (!value.startsWith(MARKER)) {
            return Optional.of(value);
        }

        String encoded = value.substring(MARKER.length());
        if (encoded.isEmpty()) {
            return Optional.empty();
        }

        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int read;
                while ((read = gzip.read(buffer)) >= 0) {
                    out.write(buffer, 0, read);
                }
                return Optional.of(out.toString(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    public static boolean isCompressedEnvelope(String value) {
        return value != null && value.startsWith(MARKER);
    }
}
