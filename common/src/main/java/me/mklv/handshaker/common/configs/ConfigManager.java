package me.mklv.handshaker.common.configs;

import java.util.LinkedHashSet;
import java.util.Set;

public final class ConfigManager {
	private ConfigManager() {
	}

	public static Set<String> collectRuleKeys(Set<String> required,
											  Set<String> blacklisted,
											  Set<String> whitelisted,
											  Set<String> optional) {
		Set<String> ruleKeys = new LinkedHashSet<>();
		ruleKeys.addAll(required);
		ruleKeys.addAll(blacklisted);
		ruleKeys.addAll(whitelisted);
		ruleKeys.addAll(optional);
		return ruleKeys;
	}
}
