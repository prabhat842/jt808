# Testing Team Guide

This guide covers how to build, run, and test the JT808/JT1078 Fleet Simulator.

For exact protocol coverage and known gaps, see [protocol-scope.md](protocol-scope.md).

## 1. Environment

Use Ubuntu 22.04+ or a comparable Linux host.

Required tools:

- JDK 21
- Maven 3.9+
- Bash
- `ss`, `lsof`, or similar socket inspection tools

Recommended Linux limits for large tests:

```bash
ulimit -n 200000
```

Verify tools:

```bash
java -version
mvn -version
ulimit -n
```

## 2. Build

From the repository root:

```bash
mvn clean package
```

Expected result:

- Build exits with status `0`.
- Runnable jar exists under `target/`.
- Example jar name:

```text
target/jt808-fleet-simulator-0.1.0-SNAPSHOT.jar
```

## 2.1 Protocol Unit Tests

Run the protocol-focused unit tests:

```bash
mvn test
```

If the default Maven cache is not writable in the test environment, use a local cache:

```bash
mvn -Dmaven.repo.local=/tmp/m2-jt808 test
```

Covered protocol checks:

- 20-digit terminal ID BCD encoding/decoding
- XOR checksum calculation
- outbound JT/T 808-2019 header serialization
- outbound delimiter wrapping and escape handling
- inbound `0x8001` server acknowledgment decoding
- inbound `0x8100` registration response decoding
- terminal registration body field sizing
- JT/T 1078 `0x9101` real-time request decoding
- JT/T 1078 `0x9201` playback request decoding
- JT/T 1078 Table 19 stream packet header layout

Current JT/T 1078 media checks validate framing and synthetic stream transport. The payload is generated test data, not real H.264/AAC media.

## 3. Configuration

Default config:

```bash
config/fleet.json
```

Important fields to vary during testing:

- `server.host`
- `server.port`
- `fleet.connectionCount`
- `fleet.connectStaggerMs`
- `fleet.locationIntervalSeconds`
- `fleet.heartbeatIntervalSeconds`
- `fleet.ackTimeoutSeconds`
- `jt1078.mediaCapableTerminalCount`
- `jt1078.host`
- `jt1078.port`
- `jt1078.streamMode`: `synthetic` or `file`
- `jt1078.mediaFiles`: MP4 fixture paths used when `streamMode` is `file`
- `jt1078.videoPayloadBytesPerPacket`
- `jt1078.videoPacketsPerSecond`

For a quick local smoke run, start with:

```json
"connectionCount": 10,
"mediaCapableTerminalCount": 2
```

For scale testing, increase gradually:

```text
100 -> 1,000 -> 5,000 -> 10,000
```

## 4. Run

```bash
java \
  -Xms512m \
  -Xmx1g \
  -XX:+UseG1GC \
  -Dio.netty.allocator.type=pooled \
  -jar target/jt808-fleet-simulator-0.1.0-SNAPSHOT.jar \
  --config config/fleet.json
```

The simulator prints a refreshing console dashboard with:

- configured terminal count
- connected terminal count
- authenticated sessions
- outbound/inbound message rates
- location report rate
- heartbeat rate
- active JT1078 media sessions
- media packet and byte rate
- ack latency
- reconnect attempts
- checksum failures
- heap usage

If a local sandbox or VM restricts Netty native epoll socket creation, force NIO for the test run:

```bash
java -Djt808.transport=nio -jar target/jt808-fleet-simulator-0.1.0-SNAPSHOT.jar --config config/fleet.json
```

## 4.1 Run Server And Simulator Separately

Start the server in one terminal or on a separate machine:

```bash
java \
  -Djt808.transport=nio \
  -cp target/jt808-fleet-simulator-0.1.0-SNAPSHOT.jar \
  com.example.jt808sim.server.ServerMain \
  --config config/server.json
```

Then point `config/fleet.json` at the server host and run the simulator normally. See [server-side-guide.md](server-side-guide.md).

## 5. Required Server Side

The simulator is a client fleet. For full lifecycle testing, the test environment needs a JT808/JT1078-compatible server that can:

- accept JT808 TCP connections
- decode `0x0100` terminal registration
- send `0x8100` registration response
- decode `0x0102` authentication
- send `0x8001` general server ack
- ack `0x0200` location reports and `0x0002` heartbeats
- accept JT1078 media transport sessions for media-capable terminals

Without a compatible server, connection counters may rise briefly but authentication and streaming will not complete.

## 6. Smoke Test Script

For a local positive-path smoke test without a real platform server, start the bundled mock platform in one terminal:

```bash
python3 scripts/mock_platform.py
```

Then run the simulator from another terminal:

```bash
java -Djt808.transport=nio -jar target/jt808-fleet-simulator-0.1.0-SNAPSHOT.jar --config config/fleet.json
```

The mock platform responds to registration/authentication and accepts synthetic JT1078 media sockets. It is only for simulator smoke testing, not protocol certification.

Create `tmp/smoke-config.json` from the default config with a small fleet:

```bash
mkdir -p tmp logs
cp config/fleet.json tmp/smoke-config.json
```

Edit:

```json
"connectionCount": 10,
"connectStaggerMs": 20,
"mediaCapableTerminalCount": 2
```

Run:

```bash
java \
  -Xms256m \
  -Xmx512m \
  -Dio.netty.allocator.type=pooled \
  -jar target/jt808-fleet-simulator-0.1.0-SNAPSHOT.jar \
  --config tmp/smoke-config.json \
  2>&1 | tee logs/smoke.log
```

Pass criteria:

- process starts without exception
- dashboard appears
- connected terminals increase
- authenticated sessions increase when server is available
- outbound message rate is non-zero after authentication
- checksum failures remain `0`

## 7. Scale Test Script

Use a tuned Linux host and a real server target.

Create `tmp/scale-config.json`:

```bash
mkdir -p tmp logs
cp config/fleet.json tmp/scale-config.json
```

Recommended staged values:

```json
"connectionCount": 1000,
"connectStaggerMs": 2,
"locationIntervalSeconds": 5,
"heartbeatIntervalSeconds": 30,
"mediaCapableTerminalCount": 100
```

Run:

```bash
ulimit -n 200000

java \
  -Xms512m \
  -Xmx1g \
  -XX:+UseG1GC \
  -Dio.netty.allocator.type=pooled \
  -jar target/jt808-fleet-simulator-0.1.0-SNAPSHOT.jar \
  --config tmp/scale-config.json \
  2>&1 | tee logs/scale-1000.log
```

Then repeat with:

```text
connectionCount=5000
connectionCount=10000
```

Pass criteria:

- process remains alive for the full test duration
- connected terminal count approaches configured count
- authenticated count remains stable
- reconnect attempts do not climb continuously
- heap remains within configured JVM budget
- invalid checksum count remains `0`
- ack latency remains within the server test target

## 8. Socket Inspection

Check active client connections:

```bash
ss -tan | grep ':7611' | wc -l
```

Check media connections:

```bash
ss -tan | grep ':1078' | wc -l
```

## File-Backed Media Fixtures

The simulator includes small `.mp4` byte fixtures under `sample-media/` and the default config uses:

```json
"streamMode": "file",
"mediaFiles": [
  "sample-media/city-loop.mp4",
  "sample-media/road-loop.mp4"
]
```

This mode streams bytes from the files into JT1078 packets. It is useful for repeatable load and transport tests. It is not a video decoder certification test.

## Server Test Console

When the standalone server is running, open:

```text
http://127.0.0.1:8888/ui
```

Use the console to:

- verify online terminal list
- send `0x9101` start-live commands
- send `0x9102` stop-live commands
- confirm terminal general responses in the recent command table

Check Java process:

```bash
jps -l
```

## 9. Negative Tests

Run with no server listening:

```bash
java -jar target/jt808-fleet-simulator-0.1.0-SNAPSHOT.jar --config config/fleet.json
```

Expected:

- connection failures increase
- reconnect attempts increase with backoff
- process does not crash

Run with an invalid config:

```json
"terminalId": "bad-id"
```

Expected:

- process exits during config validation
- error mentions `terminalId must be a 20-digit string`

## 10. Evidence to Capture

For each test run, save:

- config file used
- simulator log
- server log
- dashboard screenshot or copied dashboard output
- `java -version`
- `mvn -version`
- `ulimit -n`
- host CPU and memory summary
- final connected/authenticated/media counts
- average and P95 ack latency
- invalid checksum count
- connection failure count

Useful commands:

```bash
java -version 2>&1 | tee logs/java-version.txt
mvn -version | tee logs/maven-version.txt
ulimit -n | tee logs/ulimit.txt
lscpu | tee logs/lscpu.txt
free -h | tee logs/memory.txt
```

## 11. Known Verification Gap

The development environment used to create the current project did not have `java` or `mvn` installed, so the first QA action should be:

```bash
mvn clean package
```

Record and report any compilation failure with the full Maven output.
