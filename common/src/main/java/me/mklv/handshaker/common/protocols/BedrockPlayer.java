package me.mklv.handshaker.common.protocols;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class BedrockPlayer {
	public interface LogSink {
		void warn(String message);
	}

	private BedrockPlayer() {
	}

	public static boolean isBedrockPlayer(UUID playerUuid, String playerName, LogSink logger) {
		return isBedrockPlayer(playerUuid, playerName, logger, null);
	}

	public static boolean isBedrockPlayer(UUID playerUuid, String playerName, LogSink logger, Collection<ClassLoader> additionalClassLoaders) {
		if (playerUuid == null) {
			return false;
		}

		Set<ClassLoader> classLoaders = new LinkedHashSet<>();
		if (additionalClassLoaders != null) {
			for (ClassLoader classLoader : additionalClassLoaders) {
				if (classLoader != null) {
					classLoaders.add(classLoader);
				}
			}
		}

		ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
		if (contextClassLoader != null) {
			classLoaders.add(contextClassLoader);
		}

		ClassLoader selfClassLoader = BedrockPlayer.class.getClassLoader();
		if (selfClassLoader != null) {
			classLoaders.add(selfClassLoader);
		}

		try {
			ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();
			if (systemClassLoader != null) {
				classLoaders.add(systemClassLoader);
			}
		} catch (Exception ignored) {
		}

		if (isFloodgatePlayer(playerUuid, playerName, logger, classLoaders)) {
			return true;
		}

		if (isGeyserPlayer(playerUuid, playerName, logger, classLoaders)) {
			return true;
		}

		return playerUuid.version() == 0;
	}

	private static boolean isFloodgatePlayer(UUID playerUuid, String playerName, LogSink logger, Set<ClassLoader> classLoaders) {
		Exception lastError = null;

		// Try Floodgate API first.
		for (ClassLoader classLoader : classLoaders) {
			try {
				Class<?> floodgateApiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi", true, classLoader);
				Object api = floodgateApiClass.getMethod("getInstance").invoke(null);
				boolean isFloodgate = (boolean) floodgateApiClass.getMethod("isFloodgatePlayer", UUID.class)
					.invoke(api, playerUuid);
				if (isFloodgate) {
					return true;
				}
			} catch (ClassNotFoundException e) {
				// Floodgate not available from this classloader.
			} catch (Exception e) {
				lastError = e;
			}
		}

		if (lastError != null && logger != null) {
			logger.warn("Error checking Floodgate for " + safeName(playerName) + ": " + lastError.getMessage());
		}

		return false;
	}

	private static boolean isGeyserPlayer(UUID playerUuid, String playerName, LogSink logger, Set<ClassLoader> classLoaders) {
		Exception lastError = null;

		// Try Geyser API.
		for (ClassLoader classLoader : classLoaders) {
			try {
				Class<?> geyserApiClass = Class.forName("org.geysermc.geyser.api.GeyserApi", true, classLoader);
				Object geyserApi = geyserApiClass.getMethod("api").invoke(null);

				if (geyserApi != null) {
					Object connection = geyserApiClass.getMethod("connectionByUuid", UUID.class)
						.invoke(geyserApi, playerUuid);
					if (connection != null) {
						return true;
					}
				}
			} catch (ClassNotFoundException e) {
				// Geyser not available from this classloader.
			} catch (Exception e) {
				lastError = e;
			}
		}

		if (lastError != null && logger != null) {
			logger.warn("Error checking Geyser for " + safeName(playerName) + ": " + lastError.getMessage());
		}

		return false;
	}

	private static String safeName(String name) {
		return name == null || name.isEmpty() ? "Unknown" : name;
	}
}
