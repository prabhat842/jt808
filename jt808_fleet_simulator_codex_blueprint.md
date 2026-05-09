# Blueprint: Industrial Java/Netty Terminal Simulator (Fleet-Scale)

**Target Environment:** Ubuntu 22.04+ Linux-first deployment  
**Core Tech:** Java 21 LTS, Netty 4.1.x, Maven  
**Protocol Reference:** SmallChi/JT808 and JT1078 patterns for protocol fidelity  
**Primary Goal:** Simulate 10,000+ JT/T 808 terminal devices as independent TCP clients using a shared Netty NIO event loop, including integrated JT/T 1078 media-session behavior.

---

## 1. Strategic Context

This simulator provides high-fidelity synthetic vehicle telemetry and media traffic. It will be used to stress-test a Java + Netty JT/T 808/JT/T 1078 server implementation by generating thousands of realistic terminal sessions.

The simulator must be:

- Headless and background-service friendly.
- Linux optimized.
- Capable of sustaining 10,000+ concurrent TCP sessions.
- Protocol-faithful to JT/T 808-2019.
- Protocol-faithful to JT/T 1078 audio/video signaling and synthetic media streaming.
- Deterministic enough for repeatable load tests.
- Observable through a lightweight console dashboard.

---

## 2. Architectural Strategy

### 2.1 Service Model

The simulator is a headless Java service that multiplexes thousands of independent TCP connections through a shared Netty `EventLoopGroup`.

Each simulated terminal is represented by one Netty `Channel` plus a lightweight per-channel state machine.

### 2.2 Concurrency Model

Use a single shared Netty event loop infrastructure to manage thousands of channels:

- Prefer `EpollEventLoopGroup` on Linux.
- Fall back to `NioEventLoopGroup` only if epoll is unavailable.
- Reuse a shared `Bootstrap` instance where possible.
- Avoid one-thread-per-terminal designs.

Target scale:

- 10,000+ active TCP channels.
- Minimal thread switching.
- Non-blocking message processing.
- JVM heap target below 1 GB for large fleet runs.

### 2.3 Protocol Standard

Implement native JT/T 808-2019 support, including:

- 2019-version terminal identifiers, represented in config as 20-digit strings and encoded according to JT/T 808-2019 header rules.
- Expanded message body attributes.
- Correct message escaping and unescaping.
- Message sequence tracking.
- Server acknowledgment handling through `0x8001`.

JT/T 1078 support is part of the simulator scope and should be modeled as media behavior coordinated through the JT/T 808 terminal session:

- Reuse JT/T 808 registration, authentication, heartbeat, and command acknowledgment behavior.
- Handle platform media commands through the JT/T 808 control channel.
- Support synthetic audio/video stream sessions without requiring real camera input.
- Keep media transport in a dedicated internal module so media pacing, packetization, and backpressure can be tuned without weakening the 10,000+ terminal session target.

### 2.4 Reference Repository

Protocol behavior and message semantics should follow the implementation patterns from:

- [SmallChi/JT808 GitHub Repository](https://github.com/SmallChi/JT808)

Use this repository strictly as a protocol fidelity reference for:

- JT/T 808 framing.
- Escape/unescape rules.
- Message serialization.
- Registration/authentication flow.
- 2019 protocol compatibility.
- Message body attribute handling.
- JT/T 1078 message semantics and media-session control patterns.

Avoid tight architectural coupling to the original implementation. This simulator must remain Linux-first, Netty-native, and optimized for 10,000+ concurrent TCP sessions.

### 2.5 State Integrity

Do not rely on fragile “last-sent message” tracking.

Use a per-channel response correlation structure:

```java
Map<Integer, CompletableFuture<ServerAck>> responseMap = new ConcurrentHashMap<>();
```

Each outbound message sequence ID should be registered before sending. When the server returns `0x8001`, the handler must resolve the matching future.

---

## 3. Core Modules

## A. Protocol Pipeline

### A.1 Framing

Use Netty `DelimiterBasedFrameDecoder` with `0x7E` as the JT/T 808 frame delimiter.

Expected behavior:

- Split incoming TCP streams into full JT/T 808 frames.
- Preserve frame boundaries.
- Protect against oversized frames using a configured max-frame length.

Recommended max-frame length:

```text
4096 to 8192 bytes for normal terminal simulation
```

### A.2 Escaping / Unescaping

Implement a custom `ByteToMessageCodec<ByteBuf>` or equivalent decoder/encoder pair for JT/T 808 escape rules.

Required conversions:

```text
0x7D 0x01 -> 0x7D
0x7D 0x02 -> 0x7E
```

Outbound escaping:

```text
0x7D -> 0x7D 0x01
0x7E -> 0x7D 0x02
```

### A.3 Checksum

Implement JT/T 808 XOR checksum verification.

For inbound messages:

- Validate checksum before dispatch.
- Drop invalid frames.
- Increment a metric for invalid checksums.

For outbound messages:

- Compute checksum after header and body serialization.
- Apply escaping after checksum generation.

### A.4 Serialization

Create high-performance serializers for these message types:

| Message ID | Name | Direction |
|---|---|---|
| `0x0100` | Terminal Registration | Client -> Server |
| `0x0102` | Terminal Authentication | Client -> Server |
| `0x0200` | Location Report | Client -> Server |
| `0x0002` | Heartbeat | Client -> Server |
| `0x8001` | General Server Acknowledgment | Server -> Client |
| `0x8100` | Registration Response | Server -> Client |

---

## B. Fleet Manager

### B.1 Identity Loader

Read a local JSON config containing terminal identities.

Example:

```json
{
  "server": {
    "host": "127.0.0.1",
    "port": 7611
  },
  "fleet": {
    "connectionCount": 10000,
    "connectStaggerMs": 2,
    "locationIntervalSeconds": 5,
    "heartbeatIntervalSeconds": 30,
    "ackTimeoutSeconds": 15,
    "routeMode": "REVERSE"
  },
  "jt1078": {
    "mediaCapableTerminalCount": 1000,
    "host": "127.0.0.1",
    "port": 1078,
    "streamMode": "synthetic",
    "videoPayloadBytesPerPacket": 950,
    "videoPacketsPerSecond": 25
  },
  "vehicles": [
    {
      "vin": "VIN00000000000001",
      "terminalId": "00000000000000000001",
      "plateNumber": "TEST-0001",
      "manufacturerId": "TEST1",
      "startLat": 22.250000,
      "startLon": 72.200000,
      "targetLat": 22.350000,
      "targetLon": 72.350000,
      "speedKph": 45.0,
      "mediaCapable": true,
      "mediaChannels": [1]
    }
  ]
}
```

CSV may be supported later, but JSON should be the first-class config format.

### B.2 Orchestrator

Create a `FleetManager` that:

- Loads all terminal records.
- Creates one `TerminalSession` per record.
- Uses a shared `Bootstrap` and shared `EventLoopGroup`.
- Staggers `Bootstrap.connect()` calls to avoid connection storms.
- Tracks connected, authenticated, failed, and reconnecting terminals.

### B.3 Reconnect Policy

Implement bounded reconnect behavior:

- Initial reconnect delay: 1 second.
- Max reconnect delay: 60 seconds.
- Add jitter to avoid synchronized reconnect storms.
- Preserve terminal identity across reconnects.

---

## C. Kinematics Engine

### C.1 Deterministic Movement

Create a physics-aware coordinate generator that updates each vehicle’s latitude and longitude based on:

- Current coordinate.
- Target coordinate.
- Speed in km/h.
- Time delta between updates.
- Haversine distance calculation.

### C.2 No-Drift Requirement

The coordinate generator must avoid cumulative drift by computing movement based on elapsed time and geodesic distance rather than blindly adding fixed lat/lon increments.

### C.3 Behavior

Each terminal should:

- Start at `startLat/startLon`.
- Move toward `targetLat/targetLon`.
- Emit location reports every configured interval.
- Stop, loop, or reverse once the target is reached, depending on config.

Default behavior:

```text
reverse route when target is reached
```

---

## 4. Engineering Requirements

| Module | Technical Requirement | Performance Goal |
|---|---|---|
| I/O Stack | Netty NIO with Linux `EpollEventLoopGroup` | Minimize CPU cycles per connection |
| Memory | `PooledByteBufAllocator` with direct buffers | Sustain 10,000+ sessions under 1 GB JVM heap |
| Protocol | JT/T 808-2019 header, body attributes, escaping, checksum | Protocol-faithful interoperability |
| Media | JT/T 1078 control handling and synthetic media stream generation | Validate media command handling without real camera input |
| Logic | Asynchronous state machine per channel | Non-blocking registration -> auth -> stream lifecycle |
| Correlation | `Map<Integer, CompletableFuture<ServerAck>>` per channel | Reliable ack matching for `0x8001` |
| Physics | Haversine-based deterministic trajectory calculation | Real-time coordinate updates without drift |
| Monitoring | Console dashboard | Show live fleet health and throughput |

---

## 5. Netty Pipeline Design

Recommended pipeline order:

```java
pipeline.addLast("frameDecoder", new DelimiterBasedFrameDecoder(maxFrameLength, delimiter));
pipeline.addLast("escapeCodec", new Jt808EscapeCodec());
pipeline.addLast("messageDecoder", new Jt808MessageDecoder());
pipeline.addLast("messageEncoder", new Jt808MessageEncoder());
pipeline.addLast("clientHandler", new JT808ClientHandler(sessionContext));
```

Notes:

- Decoding must unescape before checksum validation.
- Encoding must serialize, checksum, escape, and wrap with `0x7E` delimiters.
- Avoid blocking calls inside Netty handlers.
- Use scheduled tasks on the channel’s event loop for heartbeat and location reporting.

---

## 6. Terminal Lifecycle

Each terminal follows this lifecycle:

```text
DISCONNECTED
  -> CONNECTING
  -> CONNECTED
  -> REGISTERING
  -> AUTHENTICATING
  -> STREAMING
  -> RECONNECTING / CLOSED
```

### 6.1 Registration Flow

1. TCP connection opens.
2. Client sends `0x0100` registration.
3. Server replies with `0x8100` registration response.
4. Client extracts authentication token if provided.
5. Client sends `0x0102` authentication.
6. Server replies with `0x8001`.
7. Client enters `STREAMING` state.

### 6.2 Streaming Flow

Once authenticated:

- Send `0x0200` location reports every configured interval.
- Send `0x0002` heartbeat messages every configured interval.
- Track every location sequence ID in `responseMap`.
- Resolve the matching future when `0x8001` arrives.
- Record acknowledgment latency.

### 6.3 JT/T 1078 Media Flow

For media-capable terminals:

- Treat JT/T 1078 as integrated terminal behavior coordinated through the JT/T 808 control channel.
- Receive and decode media-related platform commands on the JT/T 808 control channel.
- Acknowledge supported commands using the correct JT/T 808 response semantics.
- Open media transport sessions for terminals configured with media capability.
- Generate deterministic synthetic audio/video payloads at configured packet sizes and rates.
- Track media sessions, media bytes/sec, media packets/sec, and media connection failures separately from telemetry metrics.
- Keep media scheduling and writes non-blocking and Netty-native.

---

## 7. Headless Monitoring Dashboard

Implement a console dashboard that refreshes periodically.

Required metrics:

```text
Total configured terminals
Active TCP connections
Authenticated sessions
Registration failures
Reconnect attempts
Messages sent per second
Messages received per second
Location reports per second
Heartbeat messages per second
Active JT1078 media sessions
JT1078 media packets per second
JT1078 media bytes per second
Average ack latency
P95 ack latency
Invalid checksum count
Connection failure count
Heap usage
Direct memory estimate if available
```

Example display:

```text
JT808 Fleet Simulator
--------------------------------------------------
Configured terminals : 10000
Connected terminals  : 9987
Authenticated        : 9979
Msg/sec outbound     : 2145
Msg/sec inbound      : 2098
Media sessions       : 0
Media outbound       : 0 pkt/s, 0 MB/s
Avg ack latency      : 18 ms
P95 ack latency      : 44 ms
Reconnect attempts   : 12
Invalid checksums    : 0
Heap used            : 512 MB
Uptime               : 00:18:42
--------------------------------------------------
```

---

## 8. Maven Project Structure

```text
jt808-fleet-simulator/
├── pom.xml
├── config/
│   └── fleet.json
├── src/
│   └── main/
│       ├── java/
│       │   └── com/example/jt808sim/
│       │       ├── Main.java
│       │       ├── config/
│       │       │   ├── FleetConfig.java
│       │       │   └── VehicleIdentity.java
│       │       ├── fleet/
│       │       │   ├── FleetManager.java
│       │       │   ├── PendingAck.java
│       │       │   ├── TerminalSession.java
│       │       │   └── TerminalState.java
│       │       ├── netty/
│       │       │   ├── JT808ClientHandler.java
│       │       │   ├── Jt808EscapeCodec.java
│       │       │   ├── Jt808MessageDecoder.java
│       │       │   └── Jt808MessageEncoder.java
│       │       ├── protocol/
│       │       │   ├── Jt808Header.java
│       │       │   ├── Jt808Message.java
│       │       │   ├── MessageIds.java
│       │       │   ├── OutboundJt808Message.java
│       │       │   ├── RegistrationResponse.java
│       │       │   ├── ServerAck.java
│       │       │   └── SequenceGenerator.java
│       │       ├── protocol/messages/
│       │       │   ├── RegistrationMessage.java
│       │       │   ├── AuthenticationMessage.java
│       │       │   ├── LocationReportMessage.java
│       │       │   └── HeartbeatMessage.java
│       │       ├── jt1078/
│       │       │   ├── Jt1078MediaConfig.java
│       │       │   ├── Jt1078MediaSession.java
│       │       │   ├── Jt1078MediaPacket.java
│       │       │   ├── Jt1078CommandHandler.java
│       │       │   └── SyntheticMediaSource.java
│       │       ├── physics/
│       │       │   ├── Coordinate.java
│       │       │   ├── Haversine.java
│       │       │   └── TrajectoryEngine.java
│       │       └── monitoring/
│       │           ├── MetricsRegistry.java
│       │           └── ConsoleDashboard.java
│       └── resources/
│           └── logback.xml
└── README.md
```

---

## 9. Local Setup on Ubuntu

```bash
# Verify Java 21
java -version

# Build from the repository root
mvn clean package
```

Recommended JVM flags for large-scale local testing:

```bash
java \
  -Xms512m \
  -Xmx1g \
  -XX:+UseG1GC \
  -Dio.netty.allocator.type=pooled \
  -jar target/jt808-fleet-simulator.jar \
  --config config/fleet.json
```

Linux file descriptor requirement:

```bash
ulimit -n 200000
```

---

## 10. Codex Implementation Prompt

Use the following prompt directly in Codex:

```text
Develop a Java 21 terminal simulator for Ubuntu using Netty 4.1.x and Maven.

The simulator must implement a fleet-scale JT/T 808-2019 TCP client simulator capable of running 10,000+ concurrent simulated terminals through a shared Netty EventLoopGroup.

Core requirements:

1. Project Setup
- Create a Maven Java 21 project.
- Add Netty 4.1.x, Jackson Databind, SLF4J, and Logback dependencies.
- Use Linux-optimized EpollEventLoopGroup when available, with fallback to NioEventLoopGroup.
- Use PooledByteBufAllocator with direct buffers.

2. Protocol Pipeline
- Build a Netty pipeline for JT/T 808 frames.
- Use DelimiterBasedFrameDecoder with 0x7E frame delimiters.
- Implement a custom escape codec:
  - 0x7D 0x01 -> 0x7D
  - 0x7D 0x02 -> 0x7E
  - outbound 0x7D -> 0x7D 0x01
  - outbound 0x7E -> 0x7D 0x02
- Implement XOR checksum validation and generation.
- Implement encoders and decoders for the required JT/T 808 messages.

3. JT/T 808-2019 Support
- Implement the 2019 message header format.
- Support 2019-version terminal identifiers, represented in config as 20-digit strings and encoded according to JT/T 808-2019 header rules.
- Support expanded message body attributes.
- Implement message sequence generation per terminal.
- Use SmallChi/JT808 as a protocol fidelity reference for framing, escaping, serialization, registration/authentication flow, 2019 compatibility, message body attribute handling, and JT1078 semantics.
- Avoid tight architectural coupling to SmallChi/JT808; keep this simulator Java 21, Linux-first, Netty-native, and optimized for 10,000+ concurrent sessions.

4. JT/T 1078 Support
- Add integrated JT1078 media-session simulation coordinated through the JT/T 808 terminal lifecycle.
- Decode and handle JT1078-related platform commands received through the JT/T 808 control channel.
- Acknowledge supported media commands with correct JT/T 808 response semantics.
- Open media transport sessions for terminals configured as media-capable.
- Generate deterministic synthetic audio/video packets at configured packet sizes and rates.
- Track active media sessions, media packets/sec, media bytes/sec, and media connection failures.
- Keep JT1078 media implementation modular internally so media packetization, pacing, and backpressure can be tuned independently while both protocols remain first-class simulator behavior.

5. Client Handler
- Build JT808ClientHandler to manage the handshake lifecycle:
  - send 0x0100 terminal registration after channel activation
  - handle 0x8100 registration response
  - send 0x0102 terminal authentication
  - handle 0x8001 server acknowledgments
  - transition to STREAMING state after successful auth
- Use an asynchronous per-channel state machine.
- Do not block inside Netty event loop threads.

6. Message Correlation
- Implement per-channel sequence tracking using:
  Map<Integer, CompletableFuture<ServerAck>> responseMap
- Register a CompletableFuture before sending any message that expects 0x8001.
- Resolve the correct future when 0x8001 arrives.
- Track acknowledgment latency for each resolved future.
- Remove timed-out futures safely.

7. Fleet Orchestration
- Create FleetManager that reads a JSON config file containing VINs, terminal IDs, plate numbers, route coordinates, and speed.
- Create one TerminalSession per vehicle identity.
- Initiate up to 10,000 concurrent TCP connections using a shared Bootstrap.
- Stagger connection attempts to avoid connection storms.
- Implement reconnect with exponential backoff and jitter.

8. Kinematics Engine
- Implement a deterministic trajectory engine using the Haversine formula.
- Update vehicle coordinates every configured interval.
- Generate realistic latitude, longitude, speed, heading, timestamp, and status fields for 0x0200 location reports.
- Avoid cumulative coordinate drift.
- Reverse the route when the target coordinate is reached.

9. Streaming
- After authentication, send 0x0200 location reports every configured t seconds.
- Send 0x0002 heartbeat messages every configured interval.
- Schedule both tasks on the channel's EventLoop.
- Track outbound and inbound message rates.

10. Headless Monitoring
- Provide a console dashboard that reports:
  - total configured terminals
  - active TCP connections
  - authenticated sessions
  - messages per second
  - location reports per second
  - heartbeat messages per second
  - active JT1078 media sessions
  - JT1078 media packets per second
  - JT1078 media bytes per second
  - average ack latency
  - p95 ack latency
  - reconnect attempts
  - checksum failures
  - connection failures
  - heap usage
- Refresh the dashboard periodically without blocking Netty event loops.

11. Deliverables
- Complete Maven source tree.
- Example config/fleet.json.
- README.md with Ubuntu setup instructions.
- Clear package structure.
- Buildable project using mvn clean package.
```

---

## 11. Acceptance Criteria

The implementation is acceptable when:

- `mvn clean package` completes successfully.
- The simulator starts from CLI with a JSON config path.
- The simulator can create at least 1,000 local TCP client sessions in a smoke test.
- Architecture is designed to scale to 10,000+ sessions on a tuned Linux host.
- Every terminal performs registration and authentication lifecycle.
- Location reports and heartbeats are scheduled asynchronously.
- `0x8001` responses are correlated by message sequence ID.
- Console dashboard reports live metrics.
- No blocking operations run inside Netty event loop handlers.
- The code uses direct pooled ByteBuf allocation.
- The design remains Linux-first with epoll support.
- JT/T 1078 media behavior is supported for media-capable terminals through the JT/T 808 control lifecycle.
- Media metrics are reported separately from JT/T 808 telemetry metrics.

---

## 12. Notes for Later Server Stress-Test Phase

When the Java + Netty server is built, this simulator should be used to validate:

- Maximum accepted concurrent terminal sessions.
- Registration/authentication throughput.
- Location ingest throughput.
- Ack latency under pressure.
- Reconnect storm behavior.
- Server memory stability.
- Backpressure handling.
- Protocol correctness under malformed frame tests.
- JT/T 1078 media command handling and synthetic media transport behavior.
- Server behavior under mixed low-media and high-media terminal populations.
