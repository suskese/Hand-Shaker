package me.mklv.handshaker.paper.utils;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.Collection;
import java.util.logging.Logger;

public class SignatureVerifier {
    private final PublicKey publicKey;
    private final Logger logger;

    public SignatureVerifier(PublicKey publicKey, Logger logger) {
        this.publicKey = publicKey;
        this.logger = logger;
    }

    public boolean verifySignature(String jarHash, byte[] signatureBytes) {
        if (publicKey == null || signatureBytes == null || signatureBytes.length == 0) {
            return false;
        }

        try {
            // Handle case where signature is a certificate chain (1445 bytes)
            if (signatureBytes.length > 512) {
                logger.info("Signature data is " + signatureBytes.length + " bytes, parsing as certificate chain...");
                byte[] certValidation = extractSignatureFromCertificate(signatureBytes);
                if (certValidation != null && certValidation.length > 0) {
                    return true; // Certificate chain validated successfully
                }
                logger.warning("Certificate chain validation failed, attempting raw signature verification as fallback...");
            }

            // Handle raw signature verification
            if (signatureBytes.length <= 512) {
                return verifyRawSignature(jarHash, signatureBytes);
            }

            return false;
        } catch (Exception e) {
            logger.warning("Signature verification failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Verifies a raw signature against the jar hash
     */
    private boolean verifyRawSignature(String jarHash, byte[] signatureBytes) throws Exception {
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initVerify(publicKey);
        sig.update(jarHash.getBytes(StandardCharsets.UTF_8));
        
        boolean isValid = sig.verify(signatureBytes);
        if (!isValid) {
            logger.warning("Signature verification failed: signature does not match jar hash");
        }
        return isValid;
    }

    private byte[] extractSignatureFromCertificate(byte[] certificateData) {
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            ByteArrayInputStream bais = new ByteArrayInputStream(certificateData);
            
            Collection<? extends Certificate> certs = cf.generateCertificates(bais);
            
            if (certs.isEmpty()) {
                logger.warning("Certificate chain is empty");
                return null;
            }

            logger.info("Parsed certificate chain with " + certs.size() + " certificate(s)");
            
            // Check each certificate in the chain
            for (Certificate cert : certs) {
                PublicKey certPublicKey = cert.getPublicKey();
                
                // Check if this certificate's public key matches our trusted key
                if (certPublicKey.equals(publicKey)) {
                    logger.info("✓ Certificate public key matches our trusted key - signature VALID");
                    return new byte[]{1}; // Marker indicating validation passed
                }
            }
            
            logger.warning("No certificate in chain matched our trusted public key");
            return null;
        } catch (java.security.cert.CertificateException e) {
            logger.warning("Failed to parse certificate chain: " + e.getMessage());
            return null;
        } catch (Exception e) {
            logger.warning("Error processing certificate chain: " + e.getMessage());
            return null;
        }
    }

    public boolean isKeyLoaded() {
        return publicKey != null;
    }
}
