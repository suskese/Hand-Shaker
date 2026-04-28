package me.mklv.handshaker.common.utils;

import me.mklv.handshaker.common.configs.ConfigFileBootstrap;
import me.mklv.handshaker.common.database.PlayerHistoryDatabase;

import java.util.logging.Level;

public final class LoggerAdapter {
    private static final java.util.logging.Logger FALLBACK_LOGGER = java.util.logging.Logger.getLogger("HandShaker");

    private LoggerAdapter() {
    }

    public static ConfigFileBootstrap.Logger fromLoaderLogger(Object loaderLogger) {
        return new ConfigFileBootstrap.Logger() {
            @Override
            public void info(String message) {
                if (!logInfo(loaderLogger, message)) {
                    FALLBACK_LOGGER.info(message);
                }
            }

            @Override
            public void warn(String message) {
                if (!logWarn(loaderLogger, message)) {
                    FALLBACK_LOGGER.warning(message);
                }
            }

            @Override
            public void error(String message, Throwable error) {
                if (!logError(loaderLogger, message, error)) {
                    FALLBACK_LOGGER.log(Level.SEVERE, message, error);
                }
            }
        };
    }

    public static PlayerHistoryDatabase.Logger fromLoaderDatabaseLogger(Object loaderLogger) {
        return new PlayerHistoryDatabase.Logger() {
            @Override
            public void info(String message, Object... args) {
                String formatted = formatWithArgs(message, args);
                if (!logInfo(loaderLogger, formatted)) {
                    FALLBACK_LOGGER.info(formatted);
                }
            }

            @Override
            public void warn(String message, Object... args) {
                String formatted = formatWithArgs(message, args);
                if (!logWarn(loaderLogger, formatted)) {
                    FALLBACK_LOGGER.warning(formatted);
                }
            }

            @Override
            public void error(String message, Throwable e) {
                if (!logError(loaderLogger, message, e)) {
                    FALLBACK_LOGGER.log(Level.SEVERE, message, e);
                }
            }

            @Override
            public void debug(String message) {
                if (!invoke(loaderLogger, "debug", message)
                    && !invoke(loaderLogger, "fine", message)
                    && !invoke(loaderLogger, "trace", message)) {
                    FALLBACK_LOGGER.fine(message);
                }
            }
        };
    }

    private static boolean logInfo(Object logger, String message) {
        return invoke(logger, "info", message)
            || invoke(logger, "log", "INFO", message)
            || invoke(logger, "log", Level.INFO, message);
    }

    private static boolean logWarn(Object logger, String message) {
        return invoke(logger, "warn", message)
            || invoke(logger, "warning", message)
            || invoke(logger, "log", "WARN", message)
            || invoke(logger, "log", Level.WARNING, message)
            || invoke(logger, "info", message);
    }

    private static boolean logError(Object logger, String message, Throwable error) {
        return invoke(logger, "error", message, error)
            || invoke(logger, "error", message)
            || invoke(logger, "severe", message)
            || invoke(logger, "log", "ERROR", message, error)
            || invoke(logger, "log", Level.SEVERE, message, error)
            || invoke(logger, "log", Level.SEVERE, message);
    }

    private static String formatWithArgs(String message, Object... args) {
        if (message == null) {
            return "";
        }
        if (args == null || args.length == 0) {
            return message;
        }

        String pattern = message.replace("{}", "%s");
        try {
            return String.format(pattern, args);
        } catch (Exception ignored) {
            return message;
        }
    }

    private static boolean invoke(Object target, String methodName, Object... args) {
        if (target == null) {
            return false;
        }

        try {
            for (var method : target.getClass().getMethods()) {
                if (!method.getName().equals(methodName) || method.getParameterCount() != args.length) {
                    continue;
                }
                if (!canAccept(method.getParameterTypes(), args)) {
                    continue;
                }
                method.setAccessible(true);
                method.invoke(target, adaptArgs(method.getParameterTypes(), args));
                return true;
            }
        } catch (ReflectiveOperationException ignored) {
            return false;
        }

        return false;
    }

    private static boolean canAccept(Class<?>[] parameterTypes, Object[] args) {
        for (int i = 0; i < parameterTypes.length; i++) {
            Object arg = args[i];
            if (arg == null) {
                continue;
            }
            Class<?> expected = wrapPrimitive(parameterTypes[i]);
            if (!expected.isAssignableFrom(arg.getClass())
                && !(expected == Level.class && arg instanceof String)) {
                return false;
            }
        }
        return true;
    }

    private static Object[] adaptArgs(Class<?>[] parameterTypes, Object[] args) {
        Object[] adapted = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            if (arg instanceof String text && parameterTypes[i] == Level.class) {
                adapted[i] = parseLevel(text);
            } else {
                adapted[i] = arg;
            }
        }
        return adapted;
    }

    private static Level parseLevel(String value) {
        if (value == null) {
            return Level.INFO;
        }
        return switch (value.toUpperCase()) {
            case "WARN", "WARNING" -> Level.WARNING;
            case "ERROR", "SEVERE" -> Level.SEVERE;
            case "DEBUG", "FINE" -> Level.FINE;
            default -> Level.INFO;
        };
    }

    private static Class<?> wrapPrimitive(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == char.class) return Character.class;
        return type;
    }
}
