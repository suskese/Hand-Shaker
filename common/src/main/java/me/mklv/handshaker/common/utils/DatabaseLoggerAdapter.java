package me.mklv.handshaker.common.utils;

import me.mklv.handshaker.common.database.PlayerHistoryDatabase;

/**
 * Unified adapter factory for creating {@link PlayerHistoryDatabase.Logger} implementations
 * from loader-specific logger instances.
 * 
 * Supports both java.util.logging.Logger (Paper) and SLF4J Logger (Fabric/NeoForge).
 */
public class DatabaseLoggerAdapter {
    private DatabaseLoggerAdapter() {
        // Utility class, no instantiation
    }

    /**
     * Converts loader-specific logger to a {@link PlayerHistoryDatabase.Logger}.
     * 
     * Supports:
     * - java.util.logging.Logger (Paper)
     * - org.slf4j.Logger (Fabric, NeoForge)
     * 
     * @param loaderLogger The loader's logger instance
     * @return A PlayerHistoryDatabase.Logger adapter wrapping the provided logger
     */
    public static PlayerHistoryDatabase.Logger fromLoaderLogger(Object loaderLogger) {
        return new PlayerHistoryDatabaseLoggerAdapterImpl(loaderLogger);
    }

    /**
     * Implementation adapter supporting multiple logger types.
     */
    private static class PlayerHistoryDatabaseLoggerAdapterImpl implements PlayerHistoryDatabase.Logger {
        private final Object logger;

        PlayerHistoryDatabaseLoggerAdapterImpl(Object logger) {
            this.logger = logger;
        }

        @Override
        public void info(String message, Object... args) {
            if (isJavaLogger()) {
                getJavaLogger().info(formatString(message, args));
            } else {
                getSLF4JLogger().info(message, args);
            }
        }

        @Override
        public void warn(String message, Object... args) {
            if (isJavaLogger()) {
                getJavaLogger().warning(formatString(message, args));
            } else {
                getSLF4JLogger().warn(message, args);
            }
        }

        @Override
        public void error(String message, Throwable e) {
            String baseMsg = message;
            if (isJavaLogger()) {
                getJavaLogger().severe(baseMsg + ": " + e.getMessage());
            } else {
                getSLF4JLogger().error(baseMsg, e);
            }
        }

        @Override
        public void debug(String message) {
            if (isJavaLogger()) {
                getJavaLogger().fine(message);
            } else {
                getSLF4JLogger().debug(message);
            }
        }

        private boolean isJavaLogger() {
            return logger instanceof java.util.logging.Logger;
        }

        private java.util.logging.Logger getJavaLogger() {
            return (java.util.logging.Logger) logger;
        }

        private org.slf4j.Logger getSLF4JLogger() {
            return (org.slf4j.Logger) logger;
        }

        /**
         * Formats message for java.util.logging compatibility.
         * Converts {} placeholders to %s for String.format().
         */
        private String formatString(String message, Object... args) {
            if (args == null || args.length == 0) {
                return message;
            }
            return String.format(message.replace("{}", "%s"), args);
        }
    }
}
