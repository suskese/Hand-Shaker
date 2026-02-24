package me.mklv.handshaker.common.protocols;

import me.mklv.handshaker.common.configs.ConfigManager;
import me.mklv.handshaker.common.database.PlayerHistoryDatabase;
import me.mklv.handshaker.common.utils.ModCache;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

public final class CollectKnownHashes {
	private CollectKnownHashes() {
	}

	public static Map<String, String> collect(boolean hashMods,
										  boolean runtimeCache,
											  PlayerHistoryDatabase db,
											  boolean modVersioning,
											  Set<String> required,
											  Set<String> blacklisted,
											  Set<String> whitelisted,
											  Set<String> optional) {
		if (!hashMods || db == null) {
			return Collections.emptyMap();
		}

		Set<String> ruleKeys = ConfigManager.collectRuleKeys(required, blacklisted, whitelisted, optional);
		if (ruleKeys.isEmpty()) {
			return Collections.emptyMap();
		}

		if (!runtimeCache) {
			return db.getRegisteredHashes(ruleKeys, modVersioning);
		}

		ModCache.CacheKey cacheKey = ModCache.createKey(db, modVersioning, ruleKeys);
		Map<String, String> cached = ModCache.get(cacheKey);
		if (cached != null) {
			return cached;
		}

		Map<String, String> knownHashes = db.getRegisteredHashes(ruleKeys, modVersioning);
		ModCache.put(cacheKey, knownHashes);
		return knownHashes;
	}
}
