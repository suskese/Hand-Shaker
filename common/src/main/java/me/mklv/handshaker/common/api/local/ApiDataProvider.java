package me.mklv.handshaker.common.api.local;

import me.mklv.handshaker.common.database.PlayerHistoryDatabase;

import java.util.List;
import java.util.Optional;

public interface ApiDataProvider {
    List<ApiModels.PlayerSummary> getActivePlayers();

    List<ApiModels.PlayerMod> getPlayerMods(String playerUuid);

    List<ApiModels.ModSummary> getAllMods();

    List<ApiModels.ModHistoryPlayer> getModHistoryPlayers(String modToken);

    PlayerHistoryDatabase.PoolStats getPoolStats();

    boolean isDatabaseEnabled();

    String getDatabaseType();

    default Optional<ApiModels.ExportResponse> export(String format) {
        return Optional.empty();
    }
}
