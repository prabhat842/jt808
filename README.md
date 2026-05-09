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

The code uses SmallChi/JT808 as a protocol fidelity reference, without coupling to its architecture.

## Current Notes

This repository contains the simulator implementation and documentation. The current execution environment used to create it did not have `java` or `mvn` installed, so build verification should be run on a JDK 21/Maven host.

## Testing

Testing instructions for QA are in [docs/testing-team-guide.md](docs/testing-team-guide.md).
