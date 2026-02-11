package me.mklv.handshaker.common.protocols;

import java.util.UUID;

public final class BedrockPlayer {
	public interface LogSink {
		void warn(String message);
	}

	private BedrockPlayer() {
	}

	public static boolean isBedrockPlayer(UUID playerUuid, String playerName, LogSink logger) {
		if (playerUuid == null) {
			return false;
		}

		// Try Floodgate API first.
		try {
			Class<?> floodgateApiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
			Object api = floodgateApiClass.getMethod("getInstance").invoke(null);
			boolean isFloodgate = (boolean) floodgateApiClass.getMethod("isFloodgatePlayer", UUID.class)
				.invoke(api, playerUuid);
			if (isFloodgate) {
				return true;
			}
		} catch (ClassNotFoundException e) {
			// Floodgate not installed.
		} catch (Exception e) {
			if (logger != null) {
				logger.warn("Error checking Floodgate for " + safeName(playerName) + ": " + e.getMessage());
			}
		}

		// Try Geyser API.
		try {
			Class<?> geyserApiClass = Class.forName("org.geysermc.geyser.api.GeyserApi");
			Object geyserApi = geyserApiClass.getMethod("api").invoke(null);

			if (geyserApi != null) {
				Object connection = geyserApiClass.getMethod("connectionByUuid", UUID.class)
					.invoke(geyserApi, playerUuid);
				return connection != null;
			}
		} catch (ClassNotFoundException e) {
			// Geyser not installed.
		} catch (Exception e) {
			if (logger != null) {
				logger.warn("Error checking Geyser for " + safeName(playerName) + ": " + e.getMessage());
			}
		}

		return false;
	}

	private static String safeName(String name) {
		return name == null || name.isEmpty() ? "Unknown" : name;
	}
}
