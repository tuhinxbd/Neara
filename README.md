# Neara (Offline Nearby Communication App)

Neara is a production-quality, Internet-independent local communication application built in Kotlin. It allows users to discover nearby devices, chat 1-on-1 and in groups, create public and private networks with QR code invitations, transfer files in chunks, stream push-to-talk audio, and route packets over peer-to-peer mesh—all without requiring any Internet connection.

---

## 🏛 Architecture & Project Structure

The project is structured into 8 modular Kotlin components following Clean Architecture:

| Module | Purpose |
| :--- | :--- |
| [neara-core](file:///i:/Kotlin/Neara/neara-core) | Domain models (`Peer`, `ChatMessage`, `Network`, `FileMetadata`), enums, and core abstraction interfaces (`DiscoveryProvider`, `TransportProvider`, `PeerManager`, `EncryptionManager`, `MeshRouter`). |
| [neara-crypto](file:///i:/Kotlin/Neara/neara-crypto) | Industrial cryptography engine with **Ed25519** digital signatures & identity, **X25519** Diffie-Hellman key agreement, **HKDF** key derivation, and **ChaCha20-Poly1305** AEAD encryption. Zero hardcoded keys. |
| [neara-protocol](file:///i:/Kotlin/Neara/neara-protocol) | Binary wire framing protocol with magic bytes `[0x4E, 0x45, 0x41, 0x52]`, packet versioning, payload lengths, and frame codec for TCP/UDP streams. |
| [neara-discovery](file:///i:/Kotlin/Neara/neara-discovery) | Local UDP multicast discovery engine on `239.255.60.60:45780`, heartbeat sweep, peer timeouts, presence states (`PeerFound`, `PeerUpdated`, `PeerLost`), and visible/invisible toggle. |
| [neara-transport](file:///i:/Kotlin/Neara/neara-transport) | Low-latency TCP framed socket server and client (`TcpTransportProvider`), peer connection pooling, and frame streams. |
| [neara-storage](file:///i:/Kotlin/Neara/neara-storage) | SQLite local-first message and peer persistence with `OfflineQueueManager` (automatic queueing when peer is offline and draining upon reconnection). |
| [neara-service](file:///i:/Kotlin/Neara/neara-service) | High-level application services: `ChatService`, `NetworkManager` (public/private networks & QR codes), `MeshRouterImpl` (multi-hop routing, loop prevention, TTL, duplicate suppression), `FileTransferService`, and `VoiceService` (PTT audio). |
| [neara-desktop](file:///i:/Kotlin/Neara/neara-desktop) | Compose Multiplatform desktop UI featuring 5 tabs (`Nearby`, `Chats`, `Networks`, `Files`, `Profile`), live "Local mode active" banner, message bubble ticks, PTT controls, and QR code rendering. |

---

## 🚀 Running the Application

### 1. Launch the Desktop App
```powershell
.\gradlew.bat :neara-desktop:run
```

### 2. Run All Unit & Integration Tests
```powershell
.\gradlew.bat test
```

### 3. Build & Assemble All Modules
```powershell
.\gradlew.bat assemble
```

---

## 🔒 Security & Privacy Highlights

- **Zero Plaintext Transmission**: All messages are signed with Ed25519 and encrypted using ephemeral X25519 ECDH + ChaCha20-Poly1305 AEAD.
- **Offline First**: If a destination peer goes offline, messages are stored in SQLite and automatically delivered with retry logic when the peer reappears on the local network.
- **Privacy Controls**: Users can toggle between `Visible` and `Invisible (Silent)` discovery modes anytime.
- **Secure QR Codes**: QR codes contain only minimal public key fingerprints and rendezvous metadata (`neara://join?netId=...`), never transmitting raw passwords or master keys over plaintext.

---

## 👨‍💻 Author & Maintainer

- **Developer**: Tuhin
- **GitHub**: [@tuhinxbd](https://github.com/tuhinxbd)
- **Project Repository**: [https://github.com/tuhinxbd/Neara](https://github.com/tuhinxbd/Neara)

---

## 📄 License & Restrictions

This software is licensed under the **Neara Source-Available Non-Commercial & Attribution License**.

- ❌ **No Commercial Use / No Selling**: You may **NOT** sell, rent, monetize, or charge any fee for this software, compiled APKs/binaries, or any derivative works.
- ❌ **No Rebranding as Original**: You may **NOT** remove or alter the author's name (**Tuhin / tuhinxbd**) or claim this project as your own creation.
- ⚖️ **Mandatory Attribution**: All forks, copies, and modifications must clearly state original authorship and link back to [https://github.com/tuhinxbd/Neara](https://github.com/tuhinxbd/Neara).
- 🔄 **ShareAlike**: Modified versions must carry the exact same license terms.

See the full [LICENSE](LICENSE) file for complete legal terms.

