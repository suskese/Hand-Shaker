package me.mklv.handshaker.common.utils;

import me.mklv.handshaker.common.database.PlayerHistoryDatabase;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class ModCache {
	private static final Object LOCK = new Object();
	private static CacheKey cacheKey;
	private static Map<String, String> cachedHashes = Collections.emptyMap();

	private ModCache() {
	}

	public static CacheKey createKey(PlayerHistoryDatabase db,
									 boolean modVersioning,
									 Set<String> ruleKeys) {
		return new CacheKey(db, modVersioning, ruleKeys);
	}

	public static Map<String, String> get(CacheKey key) {
		synchronized (LOCK) {
			if (key == null || cacheKey == null || !cacheKey.equals(key)) {
				return null;
			}
			return cachedHashes;
		}
	}

	public static void put(CacheKey key, Map<String, String> hashes) {
		if (key == null) {
			return;
		}

		Map<String, String> snapshot = hashes == null
			? Collections.emptyMap()
			: Collections.unmodifiableMap(new LinkedHashMap<>(hashes));

		synchronized (LOCK) {
			cacheKey = key;
			cachedHashes = snapshot;
		}
	}

	public static void invalidate() {
		synchronized (LOCK) {
			cacheKey = null;
			cachedHashes = Collections.emptyMap();
		}
	}

	public static final class CacheKey {
		private final PlayerHistoryDatabase db;
		private final boolean modVersioning;
		private final Set<String> ruleKeys;

		private CacheKey(PlayerHistoryDatabase db,
						 boolean modVersioning,
						 Set<String> ruleKeys) {
			this.db = db;
			this.modVersioning = modVersioning;
			this.ruleKeys = ruleKeys == null
				? Collections.emptySet()
				: Collections.unmodifiableSet(new LinkedHashSet<>(ruleKeys));
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (!(obj instanceof CacheKey other)) {
				return false;
			}
			return db == other.db
				&& modVersioning == other.modVersioning
				&& Objects.equals(ruleKeys, other.ruleKeys);
		}

		@Override
		public int hashCode() {
			return Objects.hash(System.identityHashCode(db), modVersioning, ruleKeys);
		}
	}
}
