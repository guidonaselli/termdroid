# Privacy Policy for Termdroid

**Last updated:** August 30, 2026

Termdroid is an open-source, local-first mobile terminal and AI agent application developed by Guido Naselli.

## 1. Zero Data Collection
Termdroid does **not** collect, store, transmit, or share any personal information, telemetry, usage statistics, or device identifiers to any external server owned or operated by Termdroid developers.

## 2. Local Storage and Data Isolation
All application files, configurations, shell session logs, and package files reside exclusively within the application's sandboxed internal private storage (`/data/data/com.termdroid/`).
- Uninstalling the application completely, atomically, and permanently wipes all application data.

## 3. Third-Party AI Services (Optional)
If you choose to use the built-in AI Agent with an Anthropic API key, requests and prompt context are sent directly and securely via HTTPS (TLS 1.3) from your device to the official Anthropic API (`api.anthropic.com`).
- Your API key is encrypted locally using the Android Keystore system.
- Termdroid developers never have access to your API keys, prompts, or LLM conversations.

## 4. Android System Permissions
Termdroid requests system permissions only for user-initiated terminal and agent functionality:
- **Notifications / Foreground Service**: To keep background terminal sessions and agent tasks alive.
- **Microphone / Record Audio**: Exclusively for local on-device speech-to-text dictation when the user presses the voice input button.
- **Vibration**: For haptic feedback.
- **Special Access (Usage Stats / Notification Listener)**: Optional special access granted via Android Settings only when the user explicitly requests device diagnostic tools.

## 5. Open Source Transparency
Termdroid is licensed under the Apache 2.0 License. The full source code is publicly auditable at [https://github.com/guidonaselli/termdroid](https://github.com/guidonaselli/termdroid).

## 6. Contact
For any questions regarding this Privacy Policy, please open an issue at:
[https://github.com/guidonaselli/termdroid/issues](https://github.com/guidonaselli/termdroid/issues)
