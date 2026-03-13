package me.mklv.handshaker.common.server;

import me.mklv.handshaker.common.api.discord.WebhookConfig;
import me.mklv.handshaker.common.api.discord.WebhookDispatcher;
import me.mklv.handshaker.common.api.discord.WebhookEventType;
import me.mklv.handshaker.common.configs.ConfigRuntime.CommonConfigManagerBase;
import me.mklv.handshaker.common.protocols.CertLoader;
import me.mklv.handshaker.common.utils.SignatureVerifier;

import java.security.PublicKey;
import java.util.EnumSet;

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

    public static WebhookDispatcher createWebhookDispatcher(CommonConfigManagerBase config,
                                                            org.slf4j.Logger logger) {
        if (config == null || !config.isWebhookEnabled()) {
            return null;
        }

        EnumSet<WebhookEventType> events = EnumSet.noneOf(WebhookEventType.class);
        if (config.isWebhookNotifyOnKick()) {
            events.add(WebhookEventType.PLAYER_KICKED);
        }
        if (config.isWebhookNotifyOnBan()) {
            events.add(WebhookEventType.PLAYER_BANNED);
        }

        return new WebhookDispatcher(
            new WebhookConfig(true, config.getWebhookUrl(), "", events),
            logger
        );
    }

    public static void publishKick(WebhookDispatcher dispatcher, String playerName, String reason, String mod) {
        if (dispatcher != null) {
            dispatcher.publish(WebhookEventType.PLAYER_KICKED, playerName, mod, reason);
        }
    }

    public static void publishBan(WebhookDispatcher dispatcher, String playerName, String reason, String mod) {
        if (dispatcher != null) {
            dispatcher.publish(WebhookEventType.PLAYER_BANNED, playerName, mod, reason);
        }
    }
}