package me.mklv.handshaker.common.utils;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.logging.Logger;

public class SignatureVerifier {
    public interface LogSink {
        void info(String message);
        void warn(String message);
    }

    private final PublicKey publicKey;
    private final LogSink logger;

    public SignatureVerifier(PublicKey publicKey, Logger logger) {
        this(publicKey, new LogSink() {
            @Override
            public void info(String message) {
                logger.info(message);
            }

            @Override
            public void warn(String message) {
                logger.warning(message);
            }
        });
    }

    public SignatureVerifier(PublicKey publicKey, LogSink logger) {
        this.publicKey = publicKey;
        this.logger = logger;
    }

    public boolean verifySignature(String jarHash, byte[] signatureBytes) {
        if (publicKey == null || signatureBytes == null || signatureBytes.length == 0) {
            return false;
        }

        try {
            return verifyDetachedSignature(jarHash, signatureBytes);
        } catch (Exception e) {
            logger.warn("Signature verification failed: " + e.getMessage());
            return false;
        }
    }

    private boolean verifyDetachedSignature(String jarHash, byte[] signatureBytes) throws Exception {
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initVerify(publicKey);
        sig.update(jarHash.getBytes(StandardCharsets.UTF_8));

        boolean isValid;
        try {
            isValid = sig.verify(signatureBytes);
        } catch (SignatureException e) {
            logger.warn("Signature verification failed: signature container/bytes not verifiable (" + signatureBytes.length + " bytes)");
            return false;
        }
        if (!isValid) {
            logger.warn("Signature verification failed: signature does not match provided jar hash");
        }
        return isValid;
    }

    public boolean isKeyLoaded() {
        return publicKey != null;
    }

    public boolean containsExpectedSignerCertificate(byte[] signatureBytes) {
        if (publicKey == null || signatureBytes == null || signatureBytes.length == 0) {
            return false;
        }

        try {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            Collection<? extends Certificate> certificates =
                factory.generateCertificates(new ByteArrayInputStream(signatureBytes));

            if (certificates == null || certificates.isEmpty()) {
                return false;
            }

            byte[] expected = publicKey.getEncoded();
            for (Certificate certificate : certificates) {
                if (certificate instanceof X509Certificate x509Certificate) {
                    PublicKey certKey = x509Certificate.getPublicKey();
                    if (certKey != null && Arrays.equals(expected, certKey.getEncoded())) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {
            return false;
        }

        return false;
    }
}
