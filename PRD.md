
1️⃣ Master Project Prompt
Build a production-quality cross-platform offline communication application.

## Product Concept

Create an Internet-independent nearby communication platform where users can discover nearby users/devices and communicate without requiring Internet access.

The application must support:

- Nearby device discovery
- 1-to-1 messaging
- Group messaging
- Voice messages
- Push-to-talk voice communication
- File/photo sharing
- Public local networks
- Private encrypted networks
- QR-based network invitations
- User presence/status
- Optional location sharing
- Offline message queue
- Local network discovery
- Mesh networking as a later phase
- Optional Internet/cloud mode in the future

The primary goal is LOCAL-FIRST communication.

Internet must NOT be required for core communication.

## Important Design Principle

Do not build this as an AnyDesk/remote-desktop application.

This is a local communication/social networking platform.

Users should be able to communicate directly with nearby users/devices.

## Modes

Implement these conceptual modes:

1. Nearby Mode
2. Public Network
3. Private Network
4. Direct Device-to-Device
5. Mesh Mode
6. Optional Online Mode

## Privacy

Privacy must be a first-class feature.

Users should be able to choose:

- Visible
- Invisible
- Public discovery
- Private-only discovery
- Location sharing OFF
- Approximate location
- Exact location
- Block user
- Leave network

Never expose a user's exact location without explicit permission.

## Security

Use strong modern encryption for private communication.

Never transmit passwords, private keys, or sensitive data in plaintext.

Use authenticated device identities and secure key exchange.

Do not rely on a hardcoded encryption key.

## Architecture Requirements

Design the application with clear separation between:

- UI
- Device discovery
- Network transport
- Peer management
- Authentication
- Encryption
- Messaging
- File transfer
- Voice communication
- Network management
- Persistence
- Mesh routing

Use modular architecture so transport technologies can be replaced later.

## Networking

The architecture must support multiple transports where available:

- Wi-Fi LAN
- Wi-Fi Direct / peer-to-peer mechanisms
- Bluetooth/BLE for discovery where appropriate
- Local hotspot networks
- Future mesh transport

Use the best available transport dynamically.

Do not assume every device supports every transport.

## Discovery

Nearby devices should be discoverable without a central Internet server.

Discovery information should be minimal:

- Temporary device ID
- Display name
- Avatar identifier
- Capabilities
- Public-key identity
- Network/group information
- Approximate proximity when technically available

Avoid exposing sensitive information during discovery.

## Public Network

Users can create a public local network.

Example:

Network Name: University Event

Users nearby can discover it and request/join.

## Private Network

Private networks should support:

- Network name
- Password/PIN
- QR invitation
- Member approval
- Admin
- Remove member
- Block member
- Leave network
- Encryption

## Messaging

Implement:

- Text
- Emoji
- Reply
- Reactions
- Message timestamps
- Sending
- Sent
- Delivered
- Read
- Retry
- Offline queue

Messages should be stored locally.

## File Sharing

Support:

- Images
- Videos
- Documents
- ZIP files
- Audio

Use chunked transfer for large files.

Show:

- Transfer progress
- Speed
- Remaining size
- Pause
- Resume
- Cancel
- Retry

## Voice

Implement:

- Voice messages
- Push-to-talk
- 1-to-1 voice communication
- Group voice communication architecture

Optimize for low latency on LAN.

## Mesh

Design the networking layer so mesh can be added later.

Example:

Device A → Device B → Device C → Device D

A message should be able to travel through trusted intermediate peers when direct communication is unavailable.

Implement loop prevention, TTL/hop limits, duplicate detection, routing identifiers, and peer expiration.

Do NOT blindly forward private data through unknown devices.

## UI

Create a modern, lightweight interface inspired by modern messaging applications.

Main navigation:

- Nearby
- Chats
- Networks
- Files
- Profile

Nearby screen should show:

- Nearby users
- Nearby networks
- Connection status
- Approximate proximity where supported
- Join/Create Network

Chat screen should be familiar and responsive.

Network screen should show:

- Network members
- Admin
- Network type
- Security state
- Invite QR
- Leave network

## Offline-first

The application must continue functioning when Internet is unavailable.

Do not show "No Internet" as a fatal error.

Instead show:

"Local mode active"

## Reliability

Handle:

- Device leaving range
- Connection interruption
- Device reconnecting
- Duplicate messages
- Message ordering
- Network switching
- Wi-Fi changes
- Bluetooth changes
- App backgrounding
- Application restart
- Partial file transfer
- Peer disappearance

## Development Rules

Before writing implementation code:

1. Analyze the requirements.
2. Propose the architecture.
3. Identify platform limitations.
4. Identify APIs available on the target platform.
5. Identify security risks.
6. Create the project structure.
7. Implement one module at a time.
8. Test every module before continuing.
9. Do not invent APIs.
10. Do not use deprecated APIs unless there is no viable alternative.
11. Explain platform-specific limitations instead of pretending a feature is universally supported.

Never implement fake networking.

If a feature cannot work without Internet on a particular platform, clearly identify the limitation and provide the technically correct alternative.

Start by producing:

1. Architecture
2. Technology stack
3. Project folder structure
4. Networking architecture
5. Security architecture
6. Database/local-storage design
7. Protocol design
8. UI screen map
9. Development phases

Do not start writing the complete application yet.

2️⃣ Architecture Prompt
Based on the previous project requirements, design the complete networking architecture.

The application must support Internet-free communication between nearby devices.

Compare and select appropriate technologies for:

- Device discovery
- Local network discovery
- Wi-Fi Direct/P2P
- Bluetooth/BLE
- Local hotspot
- TCP
- UDP
- QUIC where appropriate
- WebSocket where appropriate
- Voice transport
- File transfer
- Mesh routing

For every technology explain:

- What it is used for
- Advantages
- Limitations
- Android support
- Windows support
- Background limitations
- Battery impact
- Range
- Bandwidth
- Security implications

Then design a unified abstraction:

DiscoveryProvider
TransportProvider
PeerManager
ConnectionManager
MessageTransport
FileTransport
VoiceTransport
MeshRouter
EncryptionManager

Create interfaces so different transports can be swapped without rewriting the application.

Do not assume that Wi-Fi Direct, Bluetooth, Wi-Fi LAN, or mesh work identically across Android and Windows.

Clearly identify what is realistically possible on each platform.

Do not write implementation code yet.

3️⃣ Nearby Discovery Prompt
Implement the Nearby Discovery subsystem.

Requirements:

- Discover nearby application users/devices.
- No Internet dependency.
- Use appropriate local discovery mechanisms.
- Use temporary device identifiers.
- Exchange only minimal discovery metadata.
- Support visible/invisible mode.
- Prevent unnecessary information leakage.
- Detect device appearance/disappearance.
- Handle duplicate discovery events.
- Maintain peer timeout.
- Update connection state in real time.

Each discovered peer should contain:

peerId
displayName
avatar
publicKey
capabilities
transportTypes
lastSeen
connectionState
networkIds
proximityEstimate

Create:

DiscoveryService
PeerRepository
PeerStateManager
DiscoveryEvent

Events:

PeerFound
PeerUpdated
PeerLost
PeerConnected
PeerDisconnected

Add unit tests.

Do not implement fake discovery.

4️⃣ Chat Prompt
Implement the local-first messaging system.

Requirements:

- 1-to-1 chat
- Group chat
- Offline message queue
- Local message persistence
- Delivery state
- Retry
- Duplicate prevention
- Message ordering
- Reconnection handling

Message model:

messageId
conversationId
senderId
recipientId
timestamp
sequenceNumber
type
payload
signature
status

Statuses:

Pending
Sending
Sent
Delivered
Read
Failed

Messages must be encrypted before transmission.

Implement:

ChatService
MessageRepository
MessageQueue
DeliveryManager
ConversationManager

The system must continue working when Internet is unavailable.

If a peer disconnects, queue messages locally and retry when the peer reconnects.

Do not lose messages because a device temporarily leaves network range.

5️⃣ Private/Public Network Prompt
Implement the local Network Management system.

Support:

PUBLIC NETWORK
PRIVATE NETWORK

Public network:

- Discoverable nearby
- Users can request to join
- Network owner/admin
- Member list
- Leave network

Private network:

- Hidden or restricted discovery
- Secure invitation
- QR invitation
- Password/PIN
- Member approval
- Admin controls
- Remove member
- Block member
- Leave network

Network model:

networkId
name
type
ownerId
createdAt
publicKey
securityPolicy
members

Create secure join protocol.

Never transmit a plaintext password.

QR codes must contain only the minimum information required to initiate secure joining.

Implement network creation, joining, approval, removal, and leaving.

Add security tests.
6️⃣ Mesh Prompt
Design and implement a secure peer-to-peer mesh routing layer.

Example:

A → B → C → D

A and D may not have direct connectivity.

Requirements:

- Multi-hop messaging
- Peer discovery
- Route discovery
- Route expiration
- TTL/hop limit
- Duplicate packet detection
- Message IDs
- Loop prevention
- Route failure recovery
- Trusted relay policy
- Encryption end-to-end

Intermediate peers must NOT be able to read private messages.

Never forward arbitrary traffic without authorization.

Design the routing protocol first and document it before implementation.

Test:

1-hop
2-hop
3-hop
Disconnected peer
Duplicate packet
Loop
Route failure
Reconnection
Multiple simultaneous routes

Do not implement routing that can create uncontrolled broadcast storms.