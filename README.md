# 🤝 HandShaker 6.0.0 (Beta)

>[!WARNING]
> **This project requires setup on both client and server sides.**

[![Modrinth](https://raw.githubusercontent.com/intergrav/devins-badges/c7fd18efdadd1c3f12ae56b49afd834640d2d797/assets/cozy/available/modrinth_vector.svg)](https://modrinth.com/plugin/hand-shaker)
[![CurseForge](https://raw.githubusercontent.com/intergrav/devins-badges/c7fd18efdadd1c3f12ae56b49afd834640d2d797/assets/cozy/available/curseforge_vector.svg)](https://www.curseforge.com/minecraft/mc-mods/hand-shaker)
[![Paper](https://raw.githubusercontent.com/intergrav/devins-badges/c7fd18efdadd1c3f12ae56b49afd834640d2d797/assets/cozy/supported/paper_vector.svg)](https://papermc.io/)
[![Purpur](https://raw.githubusercontent.com/intergrav/devins-badges/c7fd18efdadd1c3f12ae56b49afd834640d2d797/assets/cozy/supported/purpur_vector.svg)](https://purpurmc.org/)
[![Wiki](https://raw.githubusercontent.com/intergrav/devins-badges/c7fd18efdadd1c3f12ae56b49afd834640d2d797/assets/cozy/documentation/generic_vector.svg)](https://github.com/suskese/Hand-Shaker/wiki)
[![Ko-fi](https://raw.githubusercontent.com/intergrav/devins-badges/c7fd18efdadd1c3f12ae56b49afd834640d2d797/assets/cozy/donate/kofi-plural-alt_vector.svg)](https://ko-fi.com/icevallish)
[![Fabric API](https://raw.githubusercontent.com/intergrav/devins-badges/c7fd18efdadd1c3f12ae56b49afd834640d2d797/assets/cozy/requires/fabric-api_vector.svg)](https://modrinth.com/mod/fabric-api)
[![Neo Forge](https://raw.githubusercontent.com/Hyperbole-Devs/vectors/8494ec1ac495cfb481dc7e458356325510933eb0/assets/cozy/supported/neoforge_vector.svg)]()

---

## 📋 What is HandShaker?

HandShaker is a **cross-platform mod/plugin verification system** for Minecraft servers and clients. It enables servers to detect which mods players are running and enforce mod restrictions with customizable policies.

### 🎯 Core Functionality

- **Fabric/Neoforge Client** → Sends your active mod list to the server upon join
- **Paper/Fabric/NeoForge Server** → Validates mod lists against configured rules and enforces restrictions
- **Multi-platform Support** → Works across Fabric, Paper, and NeoForge
- **Cryptographic Integrity** → Prevents tampered or self-compiled HandShaker mods
- **Flexible Configuration** → Per-mod rules: Required, Allowed, or Blacklisted

---

## ✨ Key Features

| Feature                         | Description                                                         |
| ------------------------------- | ------------------------------------------------------------------- |
| 🔐 **Per-Mod Configuration**    | Individually set mod allowance to Required, Allowed, or Blacklisted |
| 🚫 **Customizable Enforcement** | Configurable kick messages and auto-ban capabilities                |
| 📊 **Player History Database**  | Store and query player mod histories                                |
| 👀 **Player Mod Viewing**       | Administrators can see other players' mod lists                     |
| ✔️ **Integrity Verification**   | Cryptographic signatures prevent spoofed mod reports                |
| **🔐 Custom Actions**           | Allows to setup custom events (commands) for triggered mods         |

---

## 🏗️ Platform Comparison

✅ Supported/Working
⚠️ Issues/Unsupported
❌ Unsupported/Broken

| Features                   | Paper (1.x - 6.x)            | Fabric (2.x - 6.x)      | NeoForge (6.x+) |
| -------------------------- |:----------------------------:|:-----------------------:|:---------------:|
| **Integrity Checking**     | ✅ 6.x+<br/>⚠️ 3.x - 5.x<br/> | ✅ 6.x+<br/>⚠️ 3.x - 5.x | ✅ 6.0.0+        |
| **Configurable Rules**     | ✅ 6.0.0+                     | ✅ 6.0.0+                | ✅ 6.0.0+        |
| **Database Storage**       | ✅ 5.0.0+                     | ✅ 5.0.0+                | ✅ 6.0.0+        |
| **GeyserMC/Floodgate**     | ✅ 5.2.0+                     | ✅ 5.2.0+                | ❌               |
| **Permissions**            | ✅ 5.0.0+                     | ✅ 5.0.0+                | ❌               |
| **Folia Compatibility**    | ✅ 6.0.0+                     | ❌                       | ❌               |
| **Clickable text in chat** | ✅                            | ❌                       | ❌               |

---

## 🚀 Quick Start

### Installation

1. **Download** the appropriate mod/plugin for your platform:
   
   - **Fabric Client**: Place JAR in `mods/` folder
   - **Paper Server**: Place JAR in `plugins/` folder
   - **NeoForge Client**: Place JAR in `mods/` folder

2. **Configure** the mod/plugin

### Basic Configuration

```yaml
config: v4

# Behavior: "strict" - Force requires client-side mod or "vanilla" allow also non-mod clients
behavior: strict

# Integrity Mode: "signed" or "dev" - allow unsigned mods (if you are building own client/server fork)
integrity-mode: signed

# Whitelist mode: true = only allowed mods (inside whitelisted.yml), false = allowed by default.
# To work properly set "mods-whitelisted-enabled" must be true
whitelist: false

# Allow Bedrock players
allow-bedrock-players: false

# Player Database: Store and track player mod history (requires playerdb to be enabled)
playerdb-enabled: false

# Mod List Toggles: Enable/disable each mod list without losing configuration
mods-required-enabled: true
mods-blacklisted-enabled: true
mods-whitelisted-enabled: true

# Kick Messages - customize as needed (use {mod} for mod name)
messages:
  kick: "You are using a blacklisted mod: {mod}. Please remove it to join this server."
  no-handshake: "To connect to this server please download 'Hand-shaker' mod."
  missing-whitelist: "You are missing required mods: {mod}. Please install them to join this server."
  invalid-signature: "Invalid client signature. Please use the official HandShaker client mod."

  ban: "You have been banned for using a blacklisted mod: {mod}."
  bedrock: "Bedrock players are not allowed on this server."
  # custom messages for actions can be added here
  # placeholders are {mod} - mod which triggered, {player}
  test_action: "Hi, {player}! You are using {mod}! Thanks for using it!"


```

```yaml
actions:
# System actions (Uses hardcoded functions, not recomended to copy these)
  kick:
    commands:
      - "kick {player} {messages.kick}"

  ban:
    commands:
      - "ban {player} {messages.ban}"
    log: true
    
  log:
    missing: "{player} tried to join but missing required mod/mods: {mod}"
    blacklisted: "{player} tried to join with blacklisted mod/mods: {mod}"

# Examples of custom actions
# placeholders are {mod} - mod which triggered, {player}, {messages.name_from_config.yml}

  test_action:
    commands:
      - "msg {player} {messages.test_action}"
      - "give {player} minecraft:diamond 1"
      - "say Say Hello to {player}, who is using {mod}!"

  watchdog:
    commands:
      - "ac increase_alert_level {player} 1000"


```

---

## 📝 Permissions

| Permission          | Description              | Default  |
| ------------------- | ------------------------ | -------- |
| `handshaker.admin`  | Access to admin commands | Operator |
| `handshaker.bypass` | Bypass mod restrictions  | False    |

---

## 🎮 Supported Versions

| Loader       | Versions                | Status      | Versions | Status  |
| ------------ | ----------------------- | ----------- | -------- | ------- |
| **Fabric**   | 1.21 - 1.21.10, 1.21.11 | ✅ Supported | N/A      | N/A     |
| **Paper**    | 1.21+                   | ✅ Supported | N/A      | N/A     |
| **NeoForge** | 1.21 - 1.21.10, 1.21.11 | ✅ Supported | 1.20.1   | Planned |

---

## 📚 Documentation

- 📖 [**Full Wiki**](https://github.com/suskese/Hand-Shaker/wiki)
- ⬇️ [**Installation Guide**](https://github.com/suskese/Hand-Shaker/wiki/Installation)
- ⚙️ [**Configuration Guide**](https://github.com/suskese/Hand-Shaker/wiki/Configuration)
- 💬 [**Commands Reference**](https://github.com/suskese/Hand-Shaker/wiki/Commands)
- 🔨 [**Building from Source**](https://github.com/suskese/Hand-Shaker/wiki/Self%E2%80%90building)
- 📋 [**Changelog**](https://github.com/suskese/hand-shaker/releases)

---

## 🤝 Contributing

Issues, pull requests, and suggestions are welcome! Check the [GitHub repository](https://github.com/suskese/Hand-Shaker) for contribution guidelines.

---

<div align="center">

### 

If you enjoy HandShaker, consider supporting development:

[![Ko-fi](https://github.com/Hyperbole-Devs/vectors/blob/neoforge_badges/assets/compact/donate/kofi-singular_46h.png?raw=true)](https://ko-fi.com/icevallish)

</div>
