package me.mklv.handshaker.common.server;

import me.mklv.handshaker.common.protocols.CertLoader;
import me.mklv.handshaker.common.utils.SignatureVerifier;

import java.security.PublicKey;

public final class ServerSecurityWebhookSupport {
    private ServerSecurityWebhookSupport() {
    }

    public interface Logger {
        void info(String message);
        void warn(String message);
    }

    public record SecurityMaterial(PublicKey publicKey, SignatureVerifier signatureVerifier) {
    }

    public static SecurityMaterial loadSecurityMaterial(ClassLoader classLoader,
                                                        String certificateName,
                                                        boolean debugMode,
                                                        Logger logger) {
        PublicKey publicKey = CertLoader.loadPublicKey(classLoader, certificateName, new CertLoader.LogSink() {
            @Override
            public void info(String message) {
                if (debugMode) {
                    logger.info(message);
                }
            }

            @Override
            public void warn(String message) {
                logger.warn(message);
            }
        });

        SignatureVerifier verifier = null;
        if (publicKey != null) {
            verifier = new SignatureVerifier(publicKey, new SignatureVerifier.LogSink() {
                @Override
                public void info(String message) {
                    if (debugMode) {
                        logger.info(message);
                    }
                }

                @Override
                public void warn(String message) {
                    logger.warn(message);
                }
            });
        }

        return new SecurityMaterial(publicKey, verifier);
    }
}
