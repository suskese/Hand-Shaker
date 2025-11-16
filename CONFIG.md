# Hand-Shaker Configuration Guide

## Fabric Server Configuration

The Fabric server uses a JSON configuration file located at `config/hand-shaker.json`.

### Example Configuration:

```json
{
  "integrity": "SIGNED",
  "mode": "BLACKLIST",
  "behavior": "STRICT",
  "invalid_signature_kick_message": "Invalid client signature. Please use the official client.",
  "kick_message": "You are using a blacklisted mod: {mod}. Please remove it to join this server.",
  "missing_mod_message": "To connect to this server please download 'Hand-shaker' mod.",
  "missing_whitelist_mod_message": "You are missing required mods: {mod}. Please install them to join this server.",
  "extra_whitelist_mod_message": "You have mods that are not on the whitelist: {mod}. Please remove them to join.",
  "blacklisted_mods": [
    "xraymod",
    "testmod"
  ],
  "whitelisted_mods": [
    "hand-shaker"
  ]
}
```

### Configuration Options:

#### `integrity`
- **`SIGNED`**: Only accepts signed HandShaker copies (recommended for production)
- **`DEV`**: Accepts non-signed copies (for development/testing only)

#### `mode`
- **`BLACKLIST`**: Blocks only blacklisted mods, allows all others
- **`REQUIRE`**: Requires whitelisted mods AND blocks blacklisted mods (hybrid mode)
- **`WHITELIST`**: Only allows whitelisted mods, blocks everything else

#### `behavior`
- **`STRICT`**: Kicks both clients with blacklisted mods AND clients without HandShaker mod
- **`VANILLA`**: Only kicks clients with blacklisted mods, allows vanilla clients

### Kick Messages:

All messages support the `{mod}` placeholder which will be replaced with the mod name(s).

- **`kick_message`**: Shown when a blacklisted mod is detected
- **`missing_mod_message`**: Shown when HandShaker mod is not installed (STRICT mode)
- **`missing_whitelist_mod_message`**: Shown when required mods are missing (WHITELIST/REQUIRE modes)
- **`extra_whitelist_mod_message`**: Shown when non-whitelisted mods are present (WHITELIST mode only)
- **`invalid_signature_kick_message`**: Shown when signature verification fails (SIGNED integrity mode)

---

## Paper Server Configuration

The Paper server uses a YAML configuration file located at `plugins/HandShaker/config.yml`.

### Example Configuration:

```yaml
Behavior: Strict
Integrity: Signed
Operation Mode: blacklist

Kick Message: "You are using a blacklisted mod: {mod}. Please remove it to join this server."
Missing whitelist mod message: "You are missing required mods: {mod}. Please install them to join this server."
Extra whitelist mod message: "You have mods that are not on the whitelist: {mod}. Please remove them to join."
Missing mod message: "To connect to this server please download 'Hand-shaker' mod."
Invalid signature kick message: "Invalid client signature. Please use the official client."

Whitelisted Mods:
- hand-shaker

Blacklisted Mods:
- xraymod
- testmod
- forge
```

### Configuration Options:

Same as Fabric server, but using YAML syntax instead of JSON.

---

## Mode Comparison

| Mode | Requires Whitelisted Mods | Blocks Blacklisted Mods | Allows Other Mods |
|------|--------------------------|-------------------------|-------------------|
| **BLACKLIST** | ❌ | ✅ | ✅ |
| **REQUIRE** | ✅ | ✅ | ✅ |
| **WHITELIST** | ✅ | ✅ | ❌ |

### Example Use Cases:

- **BLACKLIST**: General server that wants to block specific cheating mods
- **REQUIRE**: Modded server that requires specific mods but allows optional client-side mods
- **WHITELIST**: Strict modded server that only allows an exact mod list
