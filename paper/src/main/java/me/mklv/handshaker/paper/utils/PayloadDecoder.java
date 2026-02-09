package me.mklv.handshaker.paper.utils;

import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

public class PayloadDecoder {
    private final Logger logger;

    public PayloadDecoder(Logger logger) {
        this.logger = logger;
    }

    public byte[] decodeByteArray(byte[] data) {
        DecodeResult result = decodeLengthPrefixedByteArrayWithOffset(data, 0);
        return result != null ? (byte[]) result.value : null;
    }

    public String decodeString(byte[] data) {
        return decodeLengthPrefixedString(data);
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
            int idx = startOffset;
            int numRead = 0;
            int result = 0;
            byte read;
            do {
                if (idx >= data.length) return null;
                read = data[idx++];
                int value = (read & 0b01111111);
                result |= (value << (7 * numRead));
                numRead++;
                if (numRead > 5) return null;
            } while ((read & 0b10000000) != 0);
            
            int length = result;
            if (length < 0 || idx + length > data.length) return null;
            
            byte[] bytes = new byte[length];
            System.arraycopy(data, idx, bytes, 0, length);
            return new DecodeResult(idx + length, bytes);
        } catch (Exception e) {
            logger.warning("Failed to decode byte array payload: " + e.getMessage());
            return null;
        }
    }

    private DecodeResult decodeLengthPrefixedStringWithOffset(byte[] data, int startOffset) {
        try {
            int idx = startOffset;
            int numRead = 0;
            int result = 0;
            byte read;
            do {
                if (idx >= data.length) return null;
                read = data[idx++];
                int value = (read & 0b01111111);
                result |= (value << (7 * numRead));
                numRead++;
                if (numRead > 5) return null;
            } while ((read & 0b10000000) != 0);
            
            int length = result;
            if (length < 0 || idx + length > data.length) return null;
            
            String str = new String(data, idx, length, StandardCharsets.UTF_8);
            return new DecodeResult(idx + length, str);
        } catch (Exception e) {
            logger.warning("Failed to decode string payload: " + e.getMessage());
            return null;
        }
    }

    private String decodeLengthPrefixedString(byte[] data) {
        try {
            int idx = 0;
            int numRead = 0;
            int result = 0;
            byte read;
            do {
                read = data[idx++];
                int value = (read & 0b01111111);
                result |= (value << (7 * numRead));
                numRead++;
                if (numRead > 5) return null;
            } while ((read & 0b10000000) != 0);
            
            int length = result;
            if (length < 0 || idx + length > data.length) return null;
            return new String(data, idx, length, StandardCharsets.UTF_8);
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
