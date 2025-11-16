# Hand-Shaker v4.0.0 Config System Upgrade - Summary

## Overview
Successfully upgraded the Hand-Shaker config system to support both v1 (legacy) and v2 (new) configuration formats with full backwards compatibility. The system automatically detects which config version is being used and adjusts command availability and functionality accordingly.

## Key Changes

### 1. Config Version Detection
- Added `config: v2` marker at the top of v2 configs (absence means v1)
- Both Paper (YAML) and Fabric (JSON) implementations support both formats
- Runtime detection allows seamless migration

### 2. V2 Config Features

#### Per-Mod Status System
Replaces the old blacklist/whitelist/require modes with granular per-mod control:
- **REQUIRED** - Mod must be present to join
- **ALLOWED** - Mod is permitted but not required  
- **BLACKLISTED** - Mod causes kick if detected

#### Default Mode
- **allowed** - Unlisted mods are permitted (permissive)
- **blacklisted** - Unlisted mods cause kick (restrictive)

#### Example V2 Config (YAML for Paper):
```yaml
config: v2
Behavior: Strict
Integrity: Signed
Default Mode: allowed
Kick Message: "You are using a blacklisted mod: {mod}. Please remove it to join this server."
Missing required mod message: "You are missing required mods: {mod}. Please install them to join this server."
Missing mod message: "To connect to this server please download 'Hand-shaker' mod."
Invalid signature kick message: "Invalid client signature. Please use the official client."

Mods:
  handshaker: required
  xraymod: blacklisted
  testmod: blacklisted
  forge: blacklisted
  sodium: allowed
  lithium: allowed
  iris: allowed
```

### 3. Updated Commands

#### V2 Config Commands (Modern)
- `/handshaker reload` - Reload config
- `/handshaker add <mod> <allowed|required|blacklisted>` - Add/set mod status
- `/handshaker add * <status> <player>` - Add all player's mods with specified status (replaces whitelist_update)
- `/handshaker change <mod> <status>` - Change existing mod status
- `/handshaker remove <mod>` - Remove mod from config
- `/handshaker list [mods|remove]` - List configured mods or show clickable removal interface
- `/handshaker player <player>` - View player's mods with interactive [A] [R] [B] buttons

#### V1 Config Commands (Legacy - Backwards Compatible)
- `/handshaker reload` - Reload config
- `/handshaker mode <blacklist|whitelist|require>` - Set operation mode
- `/handshaker add <mod>` - Add to blacklist
- `/handshaker remove <mod>` - Remove from blacklist
- `/handshaker player <player>` - View player's mods with clickable blacklist option
- `/handshaker whitelist_update <player>` - Update whitelist from player's mods

#### Commands Removed in V2
- `/handshaker mode` - Not needed with per-mod configuration
- `/handshaker whitelist_update` - Replaced by `/handshaker add * required <player>`

### 4. Interactive UI Features (V2)

#### Player Mod Viewer
When using `/handshaker player <player>` in v2 mode:
- Shows each mod with current status (colored: green=required, yellow=allowed, red=blacklisted)
- Three clickable buttons per mod:
  - **[A]** - Set as Allowed (hover: "Set as ALLOWED")
  - **[R]** - Set as Required (hover: "Set as REQUIRED")  
  - **[B]** - Set as Blacklisted (hover: "Set as BLACKLISTED")

#### List Command
- `/handshaker list mods` - Displays all configured mods with color-coded statuses
- `/handshaker list remove` - Shows clickable list for easy removal

### 5. Implementation Details

#### Paper Plugin (Bukkit/Spigot)
- **BlacklistConfig.java** - Enhanced YAML config loader with v2 support
  - New enums: `ModStatus`, `DefaultMode`
  - New methods: `setModStatus()`, `getModStatus()`, `addAllMods()`
  - Maintains v1 compatibility with `blacklistedMods` and `whitelistedMods` sets
  
- **HandShakerCommand.java** - Complete rewrite with dynamic command system
  - Uses Adventure API for rich text components
  - Clickable/hoverable text for interactive UI
  - Tab completion adapts to config version

- **HandShakerPlugin.java** - Updated player checking logic
  - Separate v2 and v1 check paths for clarity
  - V2 checks both required mods and blacklisted mods
  - Maintains all existing v1 behavior

#### Fabric Server Mod
- **BlacklistConfig.java** - Enhanced JSON config loader with v2 support
  - Custom GSON deserializers for new enums
  - JSON structure mirrors Paper's YAML structure
  
- **HandShakerCommand.java** - Brigadier-based command system
  - Complex suggestion providers for dynamic tab completion
  - Native Minecraft text components for rich formatting
  - Helper method `listMods()` for reusable list display

- **HandShakerServer.java** - Added singleton pattern
  - `getInstance()` method for command access
  - No changes to core networking logic

### 6. Backwards Compatibility

The system maintains full backwards compatibility:

1. **Config Auto-Detection**: Checks for `config: v2` field
2. **Graceful Degradation**: V1 configs continue to work exactly as before
3. **Command Availability**: Commands adapt based on config version
4. **Error Messages**: Clear messages when wrong command used for config version
5. **Migration Path**: No forced migration - admins can choose when to upgrade

#### Migration Examples
- V1 command: `/handshaker whitelist_update PlayerName`
- V2 equivalent: `/handshaker add * required PlayerName`

- V1 command: `/handshaker add xraymod` (adds to blacklist)
- V2 equivalent: `/handshaker add xraymod blacklisted`

### 7. File Changes

#### Paper Plugin
- ✅ `paper/src/main/java/me/mklv/handshaker/paper/BlacklistConfig.java` - Enhanced
- ✅ `paper/src/main/java/me/mklv/handshaker/paper/HandShakerCommand.java` - Rewritten
- ✅ `paper/src/main/java/me/mklv/handshaker/paper/HandShakerPlugin.java` - Updated check logic
- ✅ `paper/build.gradle` - Fixed processResources dependency

#### Fabric Server
- ✅ `src/main/java/me/mklv/handshaker/server/BlacklistConfig.java` - Enhanced
- ✅ `src/main/java/me/mklv/handshaker/server/HandShakerCommand.java` - Rewritten
- ✅ `src/main/java/me/mklv/handshaker/server/HandShakerServer.java` - Added getInstance()

### 8. Build Status
✅ Java compilation successful for both Paper and Fabric modules
⚠️ Full build requires keystore for JAR signing (not code-related)

## Testing Recommendations

1. **V1 Config Testing**: Verify existing v1 configs still work
2. **V2 Config Testing**: Test new per-mod configuration system
3. **Migration Testing**: Convert v1 to v2 and verify behavior
4. **Command Testing**: Test all commands in both v1 and v2 modes
5. **UI Testing**: Verify clickable buttons work in-game
6. **Edge Cases**: Test default mode behavior, empty mod lists, etc.

## Future Enhancements

The new architecture makes it easy to:
- Remove v1 support in a future version (all v1 code is isolated with `else` blocks)
- Add more mod status types (e.g., "recommended", "optional")
- Add mod groups or categories
- Import/export mod configurations
- Add mod version checking

## Notes

- The v2 config is simpler and more powerful than v1
- Commands are self-documenting with tab completion
- Interactive UI reduces typing and errors
- The system scales better for servers with many mods
- Easy to clean up legacy code when v1 support is no longer needed
