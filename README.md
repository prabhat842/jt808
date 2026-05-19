# JT808/JT1078 Fleet Simulator

Linux-first Java 21 + Netty simulator for fleet-scale JT/T 808 terminal sessions with integrated JT/T 1078 synthetic media behavior.

The simulator is designed to run thousands of independent terminal TCP clients through a shared Netty event loop. Each terminal performs the JT/T 808 registration/authentication lifecycle, emits heartbeats and location reports, and can be marked as media-capable for JT/T 1078 synthetic stream sessions.

## Requirements

- Ubuntu 22.04 or newer
- JDK 21
- Maven 3.9+
- High file descriptor limit for large runs

```bash
ulimit -n 200000
```

## Build

```bash
mvn clean package
```

This creates a runnable shaded jar under `target/`.

## Run

```bash
java \
  -Xms512m \
  -Xmx1g \
  -XX:+UseG1GC \
  -Dio.netty.allocator.type=pooled \
  -jar target/jt808-fleet-simulator-0.1.0-SNAPSHOT.jar \
  --config config/fleet.json
```

The console dashboard refreshes once per second with connection, authentication, telemetry, acknowledgment, checksum, and media counters.

For constrained local environments where Netty native epoll cannot create sockets, force Java NIO:

```bash
java -Djt808.transport=nio -jar target/jt808-fleet-simulator-0.1.0-SNAPSHOT.jar --config config/fleet.json
```

## Server Application

The server side is a separate application entrypoint:

```bash
java \
  -Djt808.transport=nio \
  -cp target/jt808-fleet-simulator-0.1.0-SNAPSHOT.jar \
  com.example.jt808sim.server.ServerMain \
  --config config/server.json
```

Server-side operating notes are in [docs/server-side-guide.md](docs/server-side-guide.md).

## Configuration

Edit [config/fleet.json](config/fleet.json).

Important fields:

- `server.host` / `server.port`: JT/T 808 platform endpoint.
- `fleet.connectionCount`: total simulated terminals.
- `fleet.connectStaggerMs`: delay between connection attempts.
- `fleet.locationIntervalSeconds`: location report cadence.
- `fleet.heartbeatIntervalSeconds`: heartbeat cadence.
- `fleet.ackTimeoutSeconds`: timeout for `0x8001` response correlation.
- `jt1078.mediaCapableTerminalCount`: number of generated terminals marked media-capable.
- `jt1078.host` / `jt1078.port`: JT/T 1078 media endpoint.
- `vehicles[].terminalId`: 20-digit JT/T 808-2019 terminal identifier.
- `vehicles[].mediaCapable` and `vehicles[].mediaChannels`: per-template media capability.

If `fleet.connectionCount` is larger than `vehicles.length`, identities are generated from the configured templates.

## Protocol Scope

See [docs/protocol-scope.md](docs/protocol-scope.md) for the detailed JT/T 808 and JT/T 1078 protocol map derived from the local protocol PDFs.

Implemented JT/T 808 behavior:

- `0x7E` frame delimiters
- JT/T 808 escaping and unescaping
- XOR checksum validation/generation
- JT/T 808-2019 versioned header with 20-digit terminal identifiers encoded as BCD
- `0x0100` registration
- `0x0102` authentication
- `0x0200` location report
- `0x0002` heartbeat
- inbound `0x8100` registration response
- inbound `0x8001` server acknowledgment with sequence correlation

Implemented JT/T 1078 behavior:

- media-capable terminal modeling coordinated through the JT/T 808 lifecycle
- synthetic media transport sessions
- deterministic packet rate and payload size from config
- media session, packet, byte, and failure metrics
- JT/T 1078 Appendix A platform command decoding for `0x9003`, `0x9101`, `0x9102`, `0x9205`, `0x9201`, `0x9202`, `0x9206`, `0x9207`, and `0x9301` through `0x9306`
- JT/T 808 terminal general response `0x0001` for handled JT/T 1078 platform commands
- JT/T 1078 terminal uploads for audio/video attributes, stream status, empty resource lists, and file upload completion
- Table 19-compatible synthetic media packet header

JT/T 1078 signaling is integrated with JT/T 808. The current media stream generator is useful for load and framing tests; payload bytes are synthetic and are not intended for media decoder certification.

The code uses SmallChi/JT808 as a protocol fidelity reference, without coupling to its architecture.

## Testing

Testing instructions for QA are in [docs/testing-team-guide.md](docs/testing-team-guide.md).

Server-side RTVS integration notes are in [docs/server-side-rtvs-integration.md](docs/server-side-rtvs-integration.md).

System architecture for the simulator, JT808 server, RTVS/CVNet media platform, and ClickHouse storage is in [docs/system-architecture.md](docs/system-architecture.md).

The next-generation Spring WebFlux/Kafka/Redis/ClickHouse target architecture is in [docs/next-generation-server-architecture.md](docs/next-generation-server-architecture.md).

The first implementation of that architecture is under [jt808-platform](jt808-platform/README.md).

Planned real-camera and two-way-talk JT1078 work is described in [docs/jt1078-camera-talk-implementation.md](docs/jt1078-camera-talk-implementation.md).
