<p align="center">
  <h1 align="center">Neara</h1>
  <p align="center">
    <strong>Offline-First Peer-to-Peer Mesh Communication Platform</strong><br>
    Pure local Wi-Fi, Hotspot & LAN • End-to-End Encrypted • No Internet Required
  </p>
  <p align="center">
    <img src="https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin">
    <img src="https://img.shields.io/badge/Platform-Android%20%7C%20Desktop-059669" alt="Platform">
    <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white" alt="Compose">
    <img src="https://img.shields.io/badge/Encryption-Ed25519%20%2B%20ChaCha20-0284C7" alt="Encryption">
    <img src="https://img.shields.io/badge/License-Non--Commercial-orange" alt="License">
  </p>
</p>

---

## 📌 Overview

**Neara** is an internet-independent, peer-to-peer communication application engineered in Kotlin. It enables nearby devices to discover one another automatically, exchange end-to-end encrypted messages, make voice & video calls, record voice messages, and transfer files over local Wi-Fi, Hotspots, or Wi-Fi Direct—completely independent of cellular data, cloud servers, or external infrastructure.

---

## ✨ Key Features

- 💬 **Direct & Group Messaging** — Chat 1-on-1 or create public & private channels with instant delivery receipts (`Sent`, `Delivered`, `Read`), message reactions, and swipeable conversation controls.
- 📞 **Voice & Video Calling** — Crystal-clear peer-to-peer audio and video calls over local Wi-Fi/LAN with interactive call history cards and full-screen call management.
- 🎙️ **Voice Notes** — Push-to-record voice audio clips featuring symmetrical waveform playback visualization.
- 📷 **High-Speed Media Sharing** — Share photos and files directly device-to-device at full Wi-Fi speeds using chunked transfer.
- 📡 **Instant Nearby Radar Discovery** — Zero-configuration UDP multicast discovery on local subnets without cumbersome pairing.
- 🛡️ **Military-Grade Cryptography** — Every packet is authenticated and encrypted:
  - **Ed25519** digital signatures for identity verification
  - **X25519** ephemeral ECDH key agreement
  - **ChaCha20-Poly1305** AEAD symmetric encryption
  - **HKDF** session key derivation
- 🔄 **Mesh Routing Engine** — Multi-hop packet forwarding with duplicate suppression, TTL hop limits, and loop protection to bridge devices out of direct range.
- 📬 **Offline Queue** — Automatic local SQLite queueing when a recipient is away, auto-drained when they reconnect.

---

## 🏗 Architecture & Modules

Neara is structured into clean, decoupled multiplatform modules:

| Module | Description |
| :--- | :--- |
| **`neara-android`** | Jetpack Compose Android client with modern Messenger-style UI, calls, and animated splash screen. |
| **`neara-desktop`** | Compose Multiplatform desktop application for Windows, macOS, and Linux. |
| **`neara-core`** | Domain models (`Peer`, `ChatMessage`, `Network`), interfaces, and business entities. |
| **`neara-crypto`** | Industrial cryptography engine with zero hardcoded keys. |
| **`neara-protocol`** | Binary wire framing protocol with packet versioning and stream codecs. |
| **`neara-discovery`** | UDP multicast discovery, peer presence detection, and heartbeat sweeps. |
| **`neara-transport`** | Low-latency TCP framed socket server and client connection pooling. |
| **`neara-storage`** | Local-first message persistence and offline delivery queue. |
| **`neara-service`** | Application orchestrator: `ChatService`, `NetworkManager`, `VoiceService`, and `MeshRouter`. |

---

## 🚀 Building & Running

### Prerequisites
- JDK 21 or higher
- Android SDK (for Android build)

### Build Android APK
```bash
./gradlew :neara-android:assembleDebug
```
*The generated APK will be available at:* `neara-android/build/outputs/apk/debug/neara-android-debug.apk`

### Run Desktop Application
```bash
./gradlew :neara-desktop:run
```

### Run Tests
```bash
./gradlew test
```

---

## 👨‍💻 Author

- **Developer**: Tuhinx
- **GitHub**: [@tuhinxbd](https://github.com/tuhinxbd)
- **Repository**: [https://github.com/tuhinxbd/Neara](https://github.com/tuhinxbd/Neara)

---

## 📄 License & Terms

Neara is distributed under the **Neara Source-Available Non-Commercial & Attribution License**.

- ❌ **No Selling / No Commercial Use**: Strictly prohibited from selling, renting, licensing, or commercializing this software or its binaries.
- ❌ **No Rebranding**: You may not remove or alter original author credits or claim this project as your own creation.
- ⚖️ **Mandatory Attribution**: All forks or derived works must visibly credit original authorship and link back to this repository.
- 🔄 **ShareAlike**: Modified versions must carry the identical license terms.

See the complete terms in the [LICENSE](LICENSE) file.
