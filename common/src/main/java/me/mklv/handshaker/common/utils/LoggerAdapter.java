package me.mklv.handshaker.common.utils;

import me.mklv.handshaker.common.configs.ConfigFileBootstrap;

public final class LoggerAdapter {
    private LoggerAdapter() {
    }

    public static ConfigFileBootstrap.Logger fromLoaderLogger(Object loaderLogger) {
        if (loaderLogger instanceof java.util.logging.Logger javaLogger) {
            return new ConfigFileBootstrap.Logger() {
                @Override
                public void info(String message) {
                    javaLogger.info(message);
                }

                @Override
                public void warn(String message) {
                    javaLogger.warning(message);
                }

                @Override
                public void error(String message, Throwable error) {
                    if (error != null) {
                        javaLogger.severe(message + ": " + error.getMessage());
                    } else {
                        javaLogger.severe(message);
                    }
                }
            };
        }

        return new ConfigFileBootstrap.Logger() {
            @Override
            public void info(String message) {
                if (!invoke(loaderLogger, "info", message)) {
                    System.out.println(message);
                }
            }

            @Override
            public void warn(String message) {
                if (!invoke(loaderLogger, "warn", message)
                    && !invoke(loaderLogger, "warning", message)
                    && !invoke(loaderLogger, "info", message)) {
                    System.out.println(message);
                }
            }

            @Override
            public void error(String message, Throwable error) {
                if (!invoke(loaderLogger, "error", message, error)
                    && !invoke(loaderLogger, "error", message)
                    && !invoke(loaderLogger, "severe", message)) {
                    System.err.println(message);
                    if (error != null) {
                        error.printStackTrace(System.err);
                    }
                }
            }
        };
    }

    private static boolean invoke(Object target, String methodName, Object... args) {
        if (target == null) {
            return false;
        }

        Class<?>[] argTypes = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            argTypes[i] = args[i] == null ? Object.class : args[i].getClass();
        }

        try {
            for (var method : target.getClass().getMethods()) {
                if (!method.getName().equals(methodName) || method.getParameterCount() != args.length) {
                    continue;
                }
                method.invoke(target, args);
                return true;
            }
        } catch (ReflectiveOperationException ignored) {
            return false;
        }

        return false;
    }
}
