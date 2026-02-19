package me.mklv.handshaker.common.configs;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;


public final class ConfigFileBootstrap {
    private ConfigFileBootstrap() {
        }

        public interface Logger {
            void info(String message);
            void warn(String message);
            void error(String message, Throwable error);
        }

        public static boolean copyRequired(Path configDir, String filename, Class<?> resourceBase, Logger logger) {
            return copyFromResourcesIfMissing(configDir, filename, resourceBase, logger, true);
        }

        public static boolean copyOptional(Path configDir, String filename, Class<?> resourceBase, Logger logger) {
            return copyFromResourcesIfMissing(configDir, filename, resourceBase, logger, false);
        }

        private static boolean copyFromResourcesIfMissing(Path configDir,
                                                          String filename,
                                                          Class<?> resourceBase,
                                                          Logger logger,
                                                          boolean required) {
            try {
                Files.createDirectories(configDir);
            } catch (IOException e) {
                if (logger != null) {
                    logger.error("Failed to create config directory: " + configDir, e);
                }
                if (required) {
                    throw new RuntimeException("Failed to create config directory: " + configDir, e);
                }
                return false;
            }

            Path targetFile = configDir.resolve(filename);
            if (Files.exists(targetFile)) {
                return false;
            }

            String resourcePath = "/configs/" + filename;
            try (InputStream is = resourceBase.getResourceAsStream(resourcePath)) {
                if (is == null) {
                    String message = "Config file " + resourcePath + " not found in resources";
                    if (logger != null) {
                        logger.warn(message);
                    }
                    if (required) {
                        throw new RuntimeException(message);
                    }
                    return false;
                }
                Files.copy(is, targetFile);
                if (logger != null) {
                    logger.info("Created default " + filename + " from resources");
                }
                return true;
            } catch (IOException e) {
                if (logger != null) {
                    logger.error("Failed to copy " + filename + " from resources", e);
                }
                if (required) {
                    throw new RuntimeException("Failed to copy " + filename + " from resources", e);
            }
            return false;
        }
    }
}
