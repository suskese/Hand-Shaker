package me.mklv.handshaker.common.commands;

import me.mklv.handshaker.common.configs.ConfigRuntime.CommonConfigManagerBase;
import me.mklv.handshaker.common.database.PlayerHistoryDatabase;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class DiagnosticCommand {
	private DiagnosticCommand() {
	}

	public record FileExportResult(Path output, String errorMessage) {
		public static FileExportResult success(Path output) {
			return new FileExportResult(output, null);
		}

		public static FileExportResult failure(String errorMessage) {
			return new FileExportResult(null, errorMessage);
		}

		public boolean success() {
			return errorMessage == null;
		}
	}

	public static List<String> buildDisplayLines(CommonConfigManagerBase config, PlayerHistoryDatabase db) {
		return buildDisplayLines(config, db, null, null, null);
	}

	public static List<String> buildDisplayLines(
		CommonConfigManagerBase config,
		PlayerHistoryDatabase db,
		String handShakerVersion,
		String serverInfo,
		String minecraftInfo
	) {
		int uniqueMods = 0;
		int activePlayers = 0;
		int registeredHashes = 0;
		String poolLine = "Database disabled";

		if (db != null && db.isEnabled()) {
			uniqueMods = db.getModPopularity().size();
			activePlayers = db.getUniqueActivePlayers();
			registeredHashes = db.getRegisteredHashes(config.getModConfigMap().keySet(), config.isModVersioning()).size();
			PlayerHistoryDatabase.PoolStats stats = db.getPoolStats();
			poolLine = "Pool active: " + stats.activeConnections()
				+ ", idle: " + stats.idleConnections()
				+ ", waiting: " + stats.awaitingThreads()
				+ ", max: " + stats.maxPoolSize();
		}

		List<String> lines = new ArrayList<>();
		if (handShakerVersion != null && !handShakerVersion.isBlank()) {
			lines.add("HandShaker: " + handShakerVersion);
		}
		if (serverInfo != null && !serverInfo.isBlank()) {
			lines.add("Server: " + serverInfo);
		}
		if (minecraftInfo != null && !minecraftInfo.isBlank()) {
			lines.add("Minecraft: " + minecraftInfo);
		}

		lines.addAll(List.of(
			"Detected mods (historical): " + uniqueMods,
			"Configured rules: " + config.getModConfigMap().size(),
			"Active players (historical): " + activePlayers,
			"Handshake enforcement: " + config.getBehavior(),
			"Client verification mode: " + config.getDisplayedIntegrityMode(),
			"Compatibility: modern=" + (config.isModernCompatibilityEnabled() ? "on" : "off")
				+ ", hybrid=" + (config.isHybridCompatibilityEnabled() ? "on" : "off")
				+ ", legacy=" + (config.isLegacyCompatibilityEnabled() ? "on" : "off")
				+ ", unsigned/dev=" + (config.isUnsignedCompatibilityEnabled() ? "on" : "off"),
			"Whitelist enforcement: " + (config.isWhitelist() ? "enabled" : "disabled"),
			"Handshake timeout: " + config.getHandshakeTimeoutSeconds() + "s",
			"Rule lists: required=" + (config.areModsRequiredEnabled() ? "on" : "off")
				+ ", blacklisted=" + (config.areModsBlacklistedEnabled() ? "on" : "off")
				+ ", whitelisted=" + (config.areModsWhitelistedEnabled() ? "on" : "off"),
			"Hash checks: " + (config.isHashMods() ? "enabled" : "disabled"),
			"Mod versioning: " + (config.isModVersioning() ? "enabled" : "disabled"),
			"Runtime cache: " + (config.isRuntimeCache() ? "enabled" : "disabled"),
			"Bedrock players: " + (config.isAllowBedrockPlayers() ? "allowed" : "blocked"),
			"Player database: " + (config.isPlayerdbEnabled() ? "enabled" : "disabled"),
			"Required modpack hashes: " + (config.getRequiredModpackHashes().isEmpty() ? "OFF" : String.join(", ", config.getRequiredModpackHashes())),
			"DB pool target: " + config.getDatabasePoolSize(),
			"DB idle timeout: " + config.getDatabaseIdleTimeoutMs() + "ms",
			"DB max lifetime: " + config.getDatabaseMaxLifetimeMs() + "ms",
			"Rate limit: " + config.getRateLimitPerMinute() + "/min",
			"Compression: " + (config.isPayloadCompressionEnabled() ? "enabled" : "disabled"),
			"Database async: " + (config.isAsyncDatabaseOperations() ? "enabled" : "disabled"),
			"Diagnostic command: " + (config.isDiagnosticCommandEnabled() ? "enabled" : "disabled"),
			poolLine,
			"Registered hashes: " + registeredHashes,
			"Active nonce cache: internal"
		));

		return lines;
	}

	public static FileExportResult writeDiagnosticReport(CommonConfigManagerBase config, PlayerHistoryDatabase db, Path exportDir, long now) {
		return writeDiagnosticReport(config, db, exportDir, now, null, null, null);
	}

	public static FileExportResult writeDiagnosticReport(
		CommonConfigManagerBase config,
		PlayerHistoryDatabase db,
		Path exportDir,
		long now,
		String handShakerVersion,
		String serverInfo,
		String minecraftInfo
	) {
		int uniqueMods = 0;
		int activePlayers = 0;
		int registeredHashes = 0;
		String poolLine = "database=disabled";

		if (db != null && db.isEnabled()) {
			uniqueMods = db.getModPopularity().size();
			activePlayers = db.getUniqueActivePlayers();
			registeredHashes = db.getRegisteredHashes(config.getModConfigMap().keySet(), config.isModVersioning()).size();
			PlayerHistoryDatabase.PoolStats stats = db.getPoolStats();
			poolLine = "pool active=" + stats.activeConnections()
				+ ", idle=" + stats.idleConnections()
				+ ", waiting=" + stats.awaitingThreads()
				+ ", max=" + stats.maxPoolSize();
		}

		try {
			Files.createDirectories(exportDir);
			Path output = exportDir.resolve("handshaker_diagnostic_" + now + ".txt");

			List<String> lines = new ArrayList<>();
			lines.add("HandShaker Diagnostic");
			lines.add("generated-at=" + now);
			if (handShakerVersion != null && !handShakerVersion.isBlank()) {
				lines.add("handshaker-version=" + handShakerVersion);
			}
			if (serverInfo != null && !serverInfo.isBlank()) {
				lines.add("server=" + serverInfo);
			}
			if (minecraftInfo != null && !minecraftInfo.isBlank()) {
				lines.add("minecraft-version=" + minecraftInfo);
			}
			lines.addAll(List.of(
				"detected-mods-historical=" + uniqueMods,
				"configured-rules=" + config.getModConfigMap().size(),
				"active-players-historical=" + activePlayers,
				"strict-handshake-mode=" + config.getBehavior(),
				"client-verification-mode=" + config.getDisplayedIntegrityMode(),
				"compatibility-modern=" + config.isModernCompatibilityEnabled(),
				"compatibility-hybrid=" + config.isHybridCompatibilityEnabled(),
				"compatibility-legacy=" + config.isLegacyCompatibilityEnabled(),
				"compatibility-unsigned-dev=" + config.isUnsignedCompatibilityEnabled(),
				"whitelist-enforcement=" + config.isWhitelist(),
				"timeout-seconds=" + config.getHandshakeTimeoutSeconds(),
				"rule-lists-required=" + config.areModsRequiredEnabled(),
				"rule-lists-blacklisted=" + config.areModsBlacklistedEnabled(),
				"rule-lists-whitelisted=" + config.areModsWhitelistedEnabled(),
				"hash-checks=" + config.isHashMods(),
				"mod-versioning=" + config.isModVersioning(),
				"runtime-cache=" + config.isRuntimeCache(),
				"bedrock-players-allowed=" + config.isAllowBedrockPlayers(),
				"player-database-enabled=" + config.isPlayerdbEnabled(),
				"modpack-hashes=" + (config.getRequiredModpackHashes().isEmpty() ? "OFF" : String.join(",", config.getRequiredModpackHashes())),
				"database-pool-size=" + config.getDatabasePoolSize(),
				"database-idle-timeout-ms=" + config.getDatabaseIdleTimeoutMs(),
				"database-max-lifetime-ms=" + config.getDatabaseMaxLifetimeMs(),
				"rate-limit-per-minute=" + config.getRateLimitPerMinute(),
				"payload-compression-enabled=" + config.isPayloadCompressionEnabled(),
				"database-async-enabled=" + config.isAsyncDatabaseOperations(),
				"diagnostic-command-enabled=" + config.isDiagnosticCommandEnabled(),
				poolLine,
				"registered-hashes=" + registeredHashes,
				"active-nonce-cache=internal"
			));

			Files.write(output, lines, StandardCharsets.UTF_8);
			return FileExportResult.success(output);
		} catch (Exception e) {
			return FileExportResult.failure(e.getMessage());
		}
	}

	public static FileExportResult writeSnapshot(CommonConfigManagerBase config, PlayerHistoryDatabase db, Path exportDir, long now) {
		return writeSnapshot(config, db, exportDir, now, null, null, null);
	}

	public static FileExportResult writeSnapshot(
		CommonConfigManagerBase config,
		PlayerHistoryDatabase db,
		Path exportDir,
		long now,
		String handShakerVersion,
		String serverInfo,
		String minecraftInfo
	) {
		if (db == null || !db.isEnabled()) {
			return FileExportResult.failure("Database is not enabled");
		}

		try {
			Map<String, Integer> popularity = db.getModPopularity();
			Files.createDirectories(exportDir);
			Path output = exportDir.resolve("handshaker_export_" + now + ".json");
			StringBuilder json = new StringBuilder();
			json.append("{\n  \"generatedAt\": ").append(now);
			if (handShakerVersion != null && !handShakerVersion.isBlank()) {
				json.append(",\n  \"handShakerVersion\": \"")
					.append(escapeJson(handShakerVersion))
					.append("\"");
			}
			if (serverInfo != null && !serverInfo.isBlank()) {
				json.append(",\n  \"server\": \"")
					.append(escapeJson(serverInfo))
					.append("\"");
			}
			if (minecraftInfo != null && !minecraftInfo.isBlank()) {
				json.append(",\n  \"minecraftVersion\": \"")
					.append(escapeJson(minecraftInfo))
					.append("\"");
			}
			json.append(",\n  \"mods\": [\n");
			int i = 0;
			for (Map.Entry<String, Integer> entry : popularity.entrySet()) {
				if (i++ > 0) {
					json.append(",\n");
				}
				json.append("    {\"mod\":\"")
					.append(escapeJson(entry.getKey()))
					.append("\",\"players\":")
					.append(entry.getValue())
					.append("}");
			}
			json.append("\n  ]\n}\n");
			Files.writeString(output, json.toString(), StandardCharsets.UTF_8);
			return FileExportResult.success(output);
		} catch (Exception e) {
			return FileExportResult.failure(e.getMessage());
		}
	}

	private static String escapeJson(String value) {
		return value
			.replace("\\", "\\\\")
			.replace("\"", "\\\"")
			.replace("\n", "\\n")
			.replace("\r", "\\r")
			.replace("\t", "\\t");
	}
}
