package me.mklv.handshaker.common.commands;

import java.util.List;

public final class CommandSuggestionData {
    public static final List<String> ROOT_COMMANDS = List.of("reload", "info", "config", "mode", "manage");
    public static final List<String> INFO_SUBCOMMANDS = List.of("configured_mods", "all_mods", "mod", "player");
    public static final List<String> CONFIG_PARAMS = List.of("behavior", "integrity", "whitelist", "allow_bedrock", "playerdb_enabled", "handshake_timeout", "hash_mods", "mod_versioning");
    public static final List<String> MODE_LISTS = List.of("mods_required", "mods_blacklisted", "mods_whitelisted");
    public static final List<String> MANAGE_SUBCOMMANDS = List.of("add", "change", "remove", "ignore", "player");
    public static final List<String> MOD_MODES = List.of("allowed", "required", "blacklisted", "optional");
    public static final List<String> BOOLEAN_VALUES = List.of("true", "false");
    public static final List<String> INTEGRITY_MODES = List.of("SIGNED", "DEV");
    public static final List<String> BEHAVIOR_MODES = List.of("STRICT", "VANILLA");
    public static final List<String> IGNORE_SUBCOMMANDS = List.of("add", "remove", "list");
    public static final List<String> DEFAULT_ACTIONS = List.of("kick", "ban", "none");

    private CommandSuggestionData() {
    }
}
