package me.mklv.handshaker.common.api.local;

import java.time.Instant;
import java.util.List;

public final class ApiModels {
    private ApiModels() {
    }

    public record PlayerSummary(String uuid, String name, int modCount) {
    }

    public record PlayerMod(String modToken, boolean active, String firstSeen, String removedAt) {
    }

    public record ModSummary(String modToken, int players) {
    }

        public record ModHistoryPlayer(String uuid, String name, String firstSeen, boolean active) {
    }

    public record HealthResponse(
        boolean ok,
        boolean databaseEnabled,
        String databaseType,
        int poolActive,
        int poolIdle,
        int poolWaiting,
        int poolMax,
        Instant generatedAt
    ) {
    }

    public record ExportResponse(String format, int rowCount, String data) {
    }

    public record ErrorResponse(String error) {
    }

    public record PlayerListResponse(Instant generatedAt, List<PlayerSummary> players) {
    }

    public record PlayerModsResponse(String playerUuid, Instant generatedAt, List<PlayerMod> mods) {
    }

    public record ModListResponse(Instant generatedAt, List<ModSummary> mods) {
    }

    public record ModHistoryResponse(String modToken, Instant generatedAt, List<ModHistoryPlayer> players) {
    }
}
