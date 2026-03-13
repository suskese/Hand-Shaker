package me.mklv.handshaker.common.server;

import me.mklv.handshaker.common.api.local.ApiDataProvider;
import me.mklv.handshaker.common.api.local.ApiModels;
import me.mklv.handshaker.common.database.PlayerHistoryDatabase;
import me.mklv.handshaker.common.utils.ClientInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ServerApiProviderFactory {
    private ServerApiProviderFactory() {
    }

    public interface LivePlayer {
        UUID uuid();

        String name();
    }

    public interface LivePlayerSource {
        List<LivePlayer> getOnlinePlayers();
    }

    public static ApiDataProvider create(PlayerHistoryDatabase playerHistoryDb,
                                         Map<UUID, ClientInfo> clients,
                                         LivePlayerSource playerSource) {
        return new ApiDataProvider() {
            @Override
            public List<ApiModels.PlayerSummary> getActivePlayers() {
                Map<UUID, ApiModels.PlayerSummary> merged = new LinkedHashMap<>();
                if (playerHistoryDb != null && playerHistoryDb.isEnabled()) {
                    for (PlayerHistoryDatabase.PlayerSummaryInfo entry : playerHistoryDb.getPlayersWithActiveMods()) {
                        merged.put(entry.uuid(), new ApiModels.PlayerSummary(
                            entry.uuid().toString(),
                            entry.currentName(),
                            entry.modCount()
                        ));
                    }
                }

                for (LivePlayer online : playerSource.getOnlinePlayers()) {
                    ClientInfo info = clients.get(online.uuid());
                    int modCount = info != null && info.mods() != null ? info.mods().size() : 0;
                    merged.put(online.uuid(), new ApiModels.PlayerSummary(
                        online.uuid().toString(),
                        online.name(),
                        modCount
                    ));
                }

                return new ArrayList<>(merged.values());
            }

            @Override
            public List<ApiModels.PlayerMod> getPlayerMods(String playerUuid) {
                List<ApiModels.PlayerMod> mods = new ArrayList<>();
                UUID uuid;
                try {
                    uuid = UUID.fromString(playerUuid);
                } catch (Exception ignored) {
                    return mods;
                }

                Map<String, ApiModels.PlayerMod> merged = new LinkedHashMap<>();
                if (playerHistoryDb != null && playerHistoryDb.isEnabled()) {
                    for (PlayerHistoryDatabase.ModHistoryEntry entry : playerHistoryDb.getPlayerHistory(uuid)) {
                        merged.put(entry.modName(), new ApiModels.PlayerMod(
                            entry.modName(),
                            entry.isActive(),
                            entry.getAddedDateFormatted(),
                            entry.getRemovedDateFormatted()
                        ));
                    }
                }

                ClientInfo live = clients.get(uuid);
                if (live != null && live.mods() != null) {
                    for (String mod : live.mods()) {
                        merged.put(mod, new ApiModels.PlayerMod(mod, true, null, null));
                    }
                }

                mods.addAll(merged.values());
                return mods;
            }

            @Override
            public List<ApiModels.ModSummary> getAllMods() {
                List<ApiModels.ModSummary> mods = new ArrayList<>();
                if (playerHistoryDb != null && playerHistoryDb.isEnabled()) {
                    for (Map.Entry<String, Integer> entry : playerHistoryDb.getModPopularity().entrySet()) {
                        mods.add(new ApiModels.ModSummary(entry.getKey(), entry.getValue()));
                    }
                    return mods;
                }

                Map<String, Integer> counts = new LinkedHashMap<>();
                for (ClientInfo info : clients.values()) {
                    if (info == null || info.mods() == null) {
                        continue;
                    }
                    for (String mod : info.mods()) {
                        counts.put(mod, counts.getOrDefault(mod, 0) + 1);
                    }
                }
                for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                    mods.add(new ApiModels.ModSummary(entry.getKey(), entry.getValue()));
                }
                return mods;
            }

            @Override
            public List<ApiModels.ModHistoryPlayer> getModHistoryPlayers(String modToken) {
                if (playerHistoryDb == null || !playerHistoryDb.isEnabled()) {
                    return Collections.emptyList();
                }
                return playerHistoryDb.getPlayersWithMod(modToken).stream()
                    .map(p -> new ApiModels.ModHistoryPlayer(
                        p.uuid().toString(),
                        p.currentName(),
                        p.firstSeen().toString(),
                        p.isActive()
                    ))
                    .toList();
            }

            @Override
            public PlayerHistoryDatabase.PoolStats getPoolStats() {
                if (playerHistoryDb == null || !playerHistoryDb.isEnabled()) {
                    return new PlayerHistoryDatabase.PoolStats(0, 0, 0, -1);
                }
                return playerHistoryDb.getPoolStats();
            }

            @Override
            public boolean isDatabaseEnabled() {
                return playerHistoryDb != null && playerHistoryDb.isEnabled();
            }

            @Override
            public String getDatabaseType() {
                return playerHistoryDb != null ? playerHistoryDb.getClass().getSimpleName() : "none";
            }
        };
    }
}