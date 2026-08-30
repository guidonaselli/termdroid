# Termdroid 🤖📱

> **Native AI Agent & PTY Terminal for Android** — Zero-friction mobile agent, full Unix terminal emulator, Node.js toolchain, and local ADB device management.

---

## 🌟 Overview

**Termdroid** brings powerful AI coding and automation tools directly to your Android phone without needing a cloud VM or remote desktop.

It provides two parallel workflows that do not compete:
1. **Interactive Terminal (CLI)**: Run `@anthropic-ai/claude-code` or `codex` directly inside an interactive PTY shell. Authenticate via your regular browser with Claude Pro/Team OAuth without paying per API token.
2. **Native Mobile Agent (Chat)**: A fast, battery-efficient mobile assistant with rich interactive tool cards, diff previews, speech dictation, and Android system tools.

---

## 🔒 Sandbox & Device Control

### 1. Isolated Private Storage & Clean Uninstall
All rootfs binaries, Node.js runtimes, npm packages, config files (`~/.claude.json`), and temporary caches live strictly inside the app's sandboxed private storage:
```text
/data/data/com.termdroid/files/
  ├── usr/          # bin, lib, node_modules, node runtime
  └── home/         # ~/.claude.json, configuration, workspace
```
- **100% Clean Uninstall**: When you uninstall Termdroid (or tap "Clear Data" in Android settings), the Android OS completely and atomically wipes the entire directory. Zero orphan files in `/sdcard` or shared storage.
- **Manual Reset**: Run `termdroid reset` in the terminal to wipe and rebuild the user environment at any time.

### 2. Device Control Capabilities

| Level | Capability | What it enables | How it is activated |
|---|---|---|---|
| **App Sandbox** (Default) | Private Filesystem, Unix Tools | Shell commands, file manipulation, Node.js, `claude` CLI, `rg`, `jaq` | Always available |
| **Special Access (JIT)** | App Usage & Notifications | Read app usage stats (`app_usage`), inspect notifications | Requested on-demand only when a tool asks for it |
| **Wireless Debugging (`shell_priv`)** | Full Device Shell (`UID 2000`) | Install/uninstall apps (`pm`), inject touch/keys (`input tap`), settings (`settings put`), screen captures | Wireless debugging enabled in Developer Options |
| **Root (`su`)** | Full Kernel/OS Control | Direct access to all partitions and system files | Available automatically if device is rooted |

> [!IMPORTANT]
> Any action executed through privileged tools (`shell_priv`) **always requires explicit human approval** on screen regardless of the autonomy mode.

---

## 🌐 Languages / Idiomas

Termdroid is fully bilingual out of the box:
- 🇬🇧 **English** (default)
- 🇪🇸 **Español** (detectado automáticamente según el idioma de tu dispositivo)

---

## 🚀 Getting Started

### Prerequisites
- Android 8.0+ (API 26+)
- Recommended: Android 11+ for Wireless Debugging loopback (`shell_priv`)

### Building from Source

```bash
# Clone the repository
git clone https://github.com/your-org/termdroid.git
cd termdroid

# Run unit tests across all 12 modules
./gradlew testDebugUnitTest

# Assemble debug APKs
./gradlew assembleDebug
```

### Installation

```bash
# Install universal debug APK to connected device
adb install -r app/build/outputs/apk/debug/app-universal-debug.apk
```

---

## 🛠️ Architecture

```text
:app           ──> UI Compose (Chat, Terminal, Quick Settings, Service)
:agent         ──> Autonomous agent loop, tool dispatch & approval state machine
:terminal      ──> ANSI/VT parser, terminal buffer grid, PTY session wrapper
:rootfs        ──> Unix environment manager, Node.js & Claude CLI wrappers
:exec          ──> Native PTY fork & dynamic linker executor (C++ / bionic)
:probe         ──> Dynamic device capability probe (SELinux / W^X / Linker)
:adb           ──> Embedded loopback ADB client & RSA authentication
:tools-unix    ──> File & shell tools (ripgrep, jaq, bash)
:tools-android ──> Android OS tools (list_apps, app_usage, notifications, shell_priv)
:core          ──> Domain models & shared contracts
```

---

## 🔒 Privacy & Security

Termdroid is 100% local-first and collects zero user telemetry. Read the full [Privacy Policy](PRIVACY_POLICY.md).

---

## 📄 License

Termdroid is open source software licensed under the [Apache License, Version 2.0](LICENSE).
