package me.mklv.handshaker.paper.utils;

import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

public class PayloadDecoder {
    private final Logger logger;

    public PayloadDecoder(Logger logger) {
        this.logger = logger;
    }

    public DecodeResult decodeByteArrayWithOffset(byte[] data, int startOffset) {
        return decodeLengthPrefixedByteArrayWithOffset(data, startOffset);
    }

    public DecodeResult decodeStringWithOffset(byte[] data, int startOffset) {
        return decodeLengthPrefixedStringWithOffset(data, startOffset);
    }

    // Internal implementation methods

    private DecodeResult decodeLengthPrefixedByteArrayWithOffset(byte[] data, int startOffset) {
        try {
            int pos = startOffset;
            int bytesRead = 0;
            int decodedLength = 0;
            byte read;
            do {
                if (pos >= data.length) return null;
                read = data[pos++];
                int value = (read & 0b01111111);
                decodedLength |= (value << (7 * bytesRead));
                bytesRead++;
                if (bytesRead > 5) return null;
            } while ((read & 0b10000000) != 0);
            
            int length = decodedLength;
            if (length < 0 || pos + length > data.length) return null;
            
            byte[] bytes = new byte[length];
            System.arraycopy(data, pos, bytes, 0, length);
            return new DecodeResult(pos + length, bytes);
        } catch (Exception e) {
            logger.warning("Failed to decode byte array payload: " + e.getMessage());
            return null;
        }
    }

    private DecodeResult decodeLengthPrefixedStringWithOffset(byte[] data, int startOffset) {
        try {
            int pos = startOffset;
            int bytesRead = 0;
            int decodedLength = 0;
            byte read;
            do {
                if (pos >= data.length) return null;
                read = data[pos++];
                int value = (read & 0b01111111);
                decodedLength |= (value << (7 * bytesRead));
                bytesRead++;
                if (bytesRead > 5) return null;
            } while ((read & 0b10000000) != 0);
            
            int length = decodedLength;
            if (length < 0 || pos + length > data.length) return null;
            
            String str = new String(data, pos, length, StandardCharsets.UTF_8);
            return new DecodeResult(pos + length, str);
        } catch (Exception e) {
            logger.warning("Failed to decode string payload: " + e.getMessage());
            return null;
        }
    }

    public static class DecodeResult {
        public final int offset;
        public final Object value;

        public DecodeResult(int offset, Object value) {
            this.offset = offset;
            this.value = value;
        }
    }
}
