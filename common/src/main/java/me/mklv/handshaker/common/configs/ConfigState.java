package me.mklv.handshaker.common.configs;

import java.util.Locale;

public final class ConfigState {
	public static final String MODE_REQUIRED = "required";
	public static final String MODE_BLACKLISTED = "blacklisted";
	public static final String MODE_ALLOWED = "allowed";
	public static final String MODE_WHITELISTED = "whitelisted";

	private ConfigState() {
	}

	public enum Behavior { STRICT, VANILLA }
	public enum IntegrityMode { SIGNED, DEV }

	public enum Action {
		KICK, BAN;

		public static Action fromString(String str) {
			if (str == null) return KICK;
			return switch (str.toUpperCase(Locale.ROOT)) {
				case "BAN" -> BAN;
				default -> KICK;
			};
		}
	}

	public static class ModConfig {
		private String mode;
		private String action;
		private String warnMessage;

		public ModConfig(String mode, String action, String warnMessage) {
			this.mode = mode != null ? mode : MODE_ALLOWED;
			this.action = action != null ? action : "kick";
			this.warnMessage = warnMessage;
		}

		public String getMode() { return mode; }
		public void setMode(String mode) { this.mode = mode; }
		public Action getAction() { return Action.fromString(action); }
		public String getActionName() { return action; }
		public void setAction(String action) { this.action = action; }
		public String getWarnMessage() { return warnMessage; }
		public void setWarnMessage(String warnMessage) { this.warnMessage = warnMessage; }

		public boolean isRequired() { return MODE_REQUIRED.equalsIgnoreCase(mode); }
		public boolean isBlacklisted() { return MODE_BLACKLISTED.equalsIgnoreCase(mode); }
		public boolean isAllowed() { return MODE_ALLOWED.equalsIgnoreCase(mode); }
	}
}
