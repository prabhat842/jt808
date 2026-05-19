# Testing Team Guide

Two-machine setup: the fleet simulator runs on a dedicated **simulator machine**; the JT808 server and RTVS server run on a separate **server machine**. This matches production topology where terminals are remote devices connecting over a network.

---

## 1. System Overview

```
SIMULATOR MACHINE                          SERVER MACHINE
┌─────────────────────────────┐           ┌──────────────────────────────────────┐
│  jt808 fleet simulator      │           │  jt808-server  (JT808 control plane) │
│                             │──7611────▶│  port 7611  JT808 signaling          │
│  Simulates N vehicle        │           │  port 7612  file/attachment           │
│  terminals, each with:      │           │  port 8888  HTTP admin UI + RTVS API │
│  • GPS trajectory           │           └──────────────────────────────────────┘
│  • JT808 registration,      │
│    auth, heartbeat,         │           ┌──────────────────────────────────────┐
│    location reports         │           │  jt808-rtvs    (JT1078 media server) │
│  • JT1078 media streams     │──1078────▶│  port 1078  JT1078 media ingest      │
│    (video + audio)          │           │  port 8089  HTTP studio UI           │
└─────────────────────────────┘           └──────────────────────────────────────┘
```

| Component | Repo | Runs on |
|---|---|---|
| Fleet simulator | `jt808/` | Simulator machine |
| JT808 server | `jt808-server/` | Server machine |
| RTVS / JT1078 server | `jt808-rtvs/` | Server machine |

---

## 2. Prerequisites

Install on **both machines**:

| Tool | Minimum version | Check |
|---|---|---|
| Ubuntu / Debian Linux | 22.04 LTS | `lsb_release -a` |
| JDK | 21 | `java -version` |
| Maven | 3.9 | `mvn -version` |

Install on **simulator machine** only (for camera-mode testing):

```bash
sudo apt-get install ffmpeg
ffmpeg -version
```

Raise the file-descriptor limit on the **simulator machine** before large runs:

```bash
ulimit -n 200000
```

---

## 3. Network Requirements

Decide the server machine's IP address — referred to as `SERVER_IP` throughout this guide. Every place you see `SERVER_IP`, substitute the real IP (e.g. `192.168.1.50`).

Open these ports on the **server machine** firewall:

| Port | Protocol | Direction | Purpose |
|---|---|---|---|
| 7611 | TCP | inbound from simulator | JT808 signaling |
| 7612 | TCP | inbound from simulator | JT808 file/attachment |
| 1078 | TCP | inbound from simulator | JT1078 media ingest |
| 8888 | TCP | inbound from your browser | JT808 admin UI + RTVS API |
| 8089 | TCP | inbound from your browser | RTVS studio UI |

Verify connectivity from the simulator machine before running any tests:

```bash
nc -zv SERVER_IP 7611   # JT808 signaling
nc -zv SERVER_IP 1078   # JT1078 media
nc -zv SERVER_IP 8888   # JT808 admin UI
nc -zv SERVER_IP 8089   # RTVS studio
```

All four should print `Connection to SERVER_IP <port> port [tcp/*] succeeded!`

---

## 4. Build

Run on **each machine** from its respective repository root.

### 4a. Server machine — build jt808-server

```bash
cd /path/to/jt808-server
mvn clean package -q
ls target/jt808-server-*.jar
```

### 4b. Server machine — build jt808-rtvs

```bash
cd /path/to/jt808-rtvs
mvn clean package -q
ls target/jt808-rtvs-*.jar
```

### 4c. Simulator machine — build jt808

```bash
cd /path/to/jt808
mvn clean package -q
ls target/jt808-fleet-simulator-*.jar
```

**All three builds must exit with `BUILD SUCCESS` before proceeding.**

Run unit tests on each machine to confirm the build is healthy:

```bash
# server machine
mvn test -f /path/to/jt808-server/pom.xml
mvn test -f /path/to/jt808-rtvs/pom.xml

# simulator machine
mvn test -f /path/to/jt808/pom.xml
```

All tests must show `Tests run: N, Failures: 0, Errors: 0`.

---

## 5. Configuration

### 5a. jt808-server — `config/server.json`

Set `ingestEnabled` to `false` so jt808-rtvs owns port 1078:

```json
{
  "jt808": {
    "host": "0.0.0.0",
    "alarmPort": 7611,
    "filePort": 7612,
    "authCode": "server-token"
  },
  "rtvs": {
    "gatewayHost": "0.0.0.0",
    "gatewayPort": 8888,
    "mediaHost": "0.0.0.0",
    "mediaPort": 1078,
    "ingestEnabled": false
  }
}
```

### 5b. jt808-rtvs — `config/rtvs.json`

Bind to all interfaces so the simulator machine can reach port 1078:

```json
{
  "ingest": {
    "host": "0.0.0.0",
    "port": 1078
  },
  "studio": {
    "host": "0.0.0.0",
    "port": 8089
  }
}
```

### 5c. jt808 simulator — `config/fleet.json`

Replace both `SERVER_IP` references with the server machine's actual IP address:

```json
{
  "server": {
    "host": "SERVER_IP",
    "port": 7611
  },
  "fleet": {
    "connectionCount": 10,
    "connectStaggerMs": 20,
    "locationIntervalSeconds": 5,
    "heartbeatIntervalSeconds": 30,
    "ackTimeoutSeconds": 15,
    "routeMode": "REVERSE"
  },
  "jt1078": {
    "mediaCapableTerminalCount": 2,
    "host": "SERVER_IP",
    "port": 1078,
    "streamMode": "file",
    "mediaFiles": [
      "sample-media/city-loop.mp4",
      "sample-media/road-loop.mp4"
    ],
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

---

## 6. Startup Sequence

Always start servers before the simulator. Follow the order below exactly.

### Step 1 — Start jt808-server (server machine, terminal 1)

```bash
cd /path/to/jt808-server
java \
  -Xms256m -Xmx512m \
  -Djt808.transport=nio \
  -jar target/jt808-server-0.1.0-SNAPSHOT.jar \
  --config config/server.json
```

**Expected startup log lines (within 5 seconds):**

```
INFO  JT808 alarm server listening on 0.0.0.0:7611
INFO  JT808 file server listening on 0.0.0.0:7612
INFO  JT1078 media ingest disabled — expecting external jt808-rtvs on port 1078
INFO  RTVS gateway API listening on 0.0.0.0:8888
```

If `ingestEnabled` is still `true`, the line will read `JT1078 media ingest listening on ...` — this means jt808-rtvs cannot bind port 1078. Fix the config and restart.

### Step 2 — Start jt808-rtvs (server machine, terminal 2)

```bash
cd /path/to/jt808-rtvs
java \
  -Xms128m -Xmx256m \
  -jar target/jt808-rtvs-0.1.0-SNAPSHOT.jar \
  --config config/rtvs.json
```

**Expected startup log lines:**

```
INFO  JT1078 ingest listening on 0.0.0.0:1078
INFO  RTVS studio listening on 0.0.0.0:8089
```

Open the studio in a browser: `http://SERVER_IP:8089/`

The page should load and show an empty session list (no streams yet).

### Step 3 — Start the simulator (simulator machine, terminal 1)

```bash
cd /path/to/jt808
java \
  -Xms512m -Xmx1g \
  -XX:+UseG1GC \
  -Dio.netty.allocator.type=pooled \
  -Djt808.transport=nio \
  -jar target/jt808-fleet-simulator-0.1.0-SNAPSHOT.jar \
  --config config/fleet.json
```

The simulator prints a live dashboard that refreshes every second.

---

## 7. Test Scenarios

### T-01 — Connection and Authentication

**What it tests:** Basic JT808 lifecycle — register, authenticate, go online.

**Wait:** 30 seconds after simulator start.

**Check simulator dashboard:**

| Counter | Expected |
|---|---|
| Configured terminals | Equals `connectionCount` in config |
| Active connections | Equal to or approaching configured count |
| Authenticated sessions | Equal to active connections (within 10 seconds) |
| Connection failures | 0 or low and not climbing |
| Reconnect attempts | 0 (no disconnects expected) |
| Invalid checksums | 0 (always) |

**Check jt808-server terminal:**
```
RTVS gateway API listening on 0.0.0.0:8888
```
No error lines after terminals connect.

**Check jt808-server admin UI:**
Open `http://SERVER_IP:8888/rtvs` in a browser.

The terminal list should show all connected terminals with their IDs and timestamps.

**Pass:** Authenticated sessions == configured terminal count. Zero checksum errors.

---

### T-02 — Location Reporting (GPS Telemetry)

**What it tests:** Terminals send periodic `0x0200` location reports; server acknowledges.

**Wait:** 2 minutes after T-01 passes.

**Check simulator dashboard:**

| Counter | Expected |
|---|---|
| Location reports sent | Non-zero and increasing |
| Ack latency (avg) | Under 500 ms |
| Ack latency (P95) | Under 2000 ms |
| Outbound messages | Increasing steadily |

**Check jt808-server terminal:** No decode errors. Ack messages logged for `0x0200`.

**Pass:** Location reports steadily increment. Ack latency stays within bounds.

---

### T-03 — Heartbeat

**What it tests:** Terminals maintain connection with `0x0002` heartbeats every `heartbeatIntervalSeconds`.

**Wait:** Until the first heartbeat interval elapses (default: 30 seconds after authentication).

**Check simulator dashboard:**

| Counter | Expected |
|---|---|
| Heartbeats sent | Non-zero, incrementing once per interval per terminal |
| Authenticated sessions | Unchanged (no drops) |

**Pass:** Heartbeat count increases at the expected rate. No session drops.

---

### T-04 — JT1078 Media Streaming

**What it tests:** Media-capable terminals open a separate TCP connection to port 1078 and stream JT1078 packets.

**Precondition:** `jt1078.mediaCapableTerminalCount` ≥ 1 in fleet config.

**Wait:** 60 seconds after T-01 passes.

**Check simulator dashboard:**

| Counter | Expected |
|---|---|
| Active media sessions | Equal to `mediaCapableTerminalCount` |
| Media packets sent | Non-zero and increasing |
| Media bytes sent | Non-zero and increasing |
| Media connection failures | 0 |

**Check jt808-rtvs terminal:**

```
INFO  JT1078 <terminalId> ch=1 frames=1 ...
INFO  JT1078 <terminalId> ch=1 frames=250 ...
```
New log lines appear every 250 frames (~10 seconds at 25 fps).

**Check jt808-rtvs studio:**
Open `http://SERVER_IP:8089/api/sessions`

Response should be a JSON array with one entry per active stream:
```json
[
  {
    "terminalId": "00000000000000000001",
    "channelId": 1,
    "active": true,
    "frames": 850,
    "bytes": 807500,
    "lastFrameLength": 950,
    ...
  }
]
```

**Check TCP connections from simulator machine:**
```bash
ss -tan | grep SERVER_IP:1078 | wc -l
```
Should equal `mediaCapableTerminalCount`.

**Pass:** Active media sessions match config. Frame count grows. RTVS `/api/sessions` returns live data.

---

### T-05 — RTVS Gateway — Live Video Command

**What it tests:** The RTVS HTTP gateway forwards a `0x9101` (start live video) command through the JT808 server to a terminal; the terminal acknowledges.

**Precondition:** At least one terminal is authenticated (T-01 passed).

**Steps:**

1. Find a connected terminal ID from the admin UI: `http://SERVER_IP:8888/rtvs`
2. Send a live-start command using the UI's **Start Live** button, or directly via HTTP:

```bash
curl -s "http://SERVER_IP:8888/api/live/start?terminalId=00000000000000000001&channel=1"
```

**Expected response:**
```json
{"accepted":true,"offline":false,"commandId":1234,"sequence":3}
```

3. The simulator dashboard **Media sessions** count should increment within a few seconds.
4. The terminal log in jt808-server shows `0x0001` terminal general response received.
5. Check command history in the admin UI — the command should appear as acknowledged.

**Send stop command:**
```bash
curl -s "http://SERVER_IP:8888/api/live/stop?terminalId=00000000000000000001&channel=1"
```

Media session count should drop back.

**Pass:** `accepted:true`, command appears in history, media session increments and decrements on start/stop.

---

### T-06 — Reconnection After Network Interruption

**What it tests:** Terminals detect a dropped connection and reconnect automatically with exponential backoff.

**Steps:**

1. While the simulator is running (T-01 passed), temporarily block port 7611 on the server machine:

```bash
# on server machine — block port 7611 for 60 seconds
sudo iptables -A INPUT -p tcp --dport 7611 -j DROP
sleep 60
sudo iptables -D INPUT -p tcp --dport 7611 -j DROP
```

2. Watch the simulator dashboard during the block period.

**Expected during block:**

| Counter | Expected |
|---|---|
| Active connections | Drops toward 0 |
| Authenticated sessions | Drops toward 0 |
| Reconnect attempts | Climbs (backoff: 1s, 2s, 4s, 8s, ...) |
| Connection failures | Increases |

**Expected after block is removed (within 2 minutes):**

| Counter | Expected |
|---|---|
| Active connections | Recovers to configured count |
| Authenticated sessions | Recovers to configured count |
| Reconnect attempts | Stops climbing |
| Invalid checksums | Still 0 |

**Pass:** Full recovery within 2 minutes of network restoration. No checksum errors.

---

### T-07 — Scale Test (100 Terminals, 10 Media Sessions)

**What it tests:** Higher terminal count. Confirms server and simulator handle concurrent load.

**Edit simulator config:**
```json
"connectionCount": 100,
"connectStaggerMs": 10,
"mediaCapableTerminalCount": 10
```

**Raise file descriptor limit on simulator machine:**
```bash
ulimit -n 200000
```

**Run simulator:**
```bash
java \
  -Xms512m -Xmx1g \
  -XX:+UseG1GC \
  -Dio.netty.allocator.type=pooled \
  -Djt808.transport=nio \
  -jar target/jt808-fleet-simulator-0.1.0-SNAPSHOT.jar \
  --config config/fleet.json \
  2>&1 | tee logs/scale-100.log
```

**Wait:** 5 minutes after all connections stabilise.

**Pass criteria:**

| Metric | Pass threshold |
|---|---|
| Authenticated sessions | ≥ 95 of 100 |
| Active media sessions | ≥ 9 of 10 |
| Invalid checksums | 0 |
| Reconnect attempts | Not climbing |
| Ack latency avg | < 500 ms |
| Ack latency P95 | < 2000 ms |
| jt808-server heap | Stable (not growing) |
| jt808-rtvs heap | Stable |

**Check session count on RTVS:**
```bash
curl -s http://SERVER_IP:8089/api/sessions | python3 -m json.tool | grep '"active": true' | wc -l
```
Expected: 10

---

### T-08 — Negative: Simulator Started Before Servers

**What it tests:** Simulator handles unavailable server gracefully without crashing.

**Steps:**

1. Stop both jt808-server and jt808-rtvs.
2. Start the simulator with `connectionCount: 5`.
3. Observe for 2 minutes.

**Expected:**

| Counter | Expected |
|---|---|
| Active connections | 0 |
| Connection failures | Climbing |
| Reconnect attempts | Climbing (with backoff delays) |
| Invalid checksums | 0 |
| Process | Still running — no crash or exception |

4. Start jt808-rtvs then jt808-server while simulator is still running.

**Expected within 2 minutes:**

| Counter | Expected |
|---|---|
| Active connections | Climbs to 5 |
| Authenticated sessions | Climbs to 5 |

**Pass:** Simulator survives absence of server. Fully recovers when servers come up.

---

### T-09 — Negative: Invalid Terminal ID in Config

**What it tests:** Config validation rejects malformed terminal IDs before any network activity.

**Edit fleet.json:**
```json
"terminalId": "BAD-ID"
```

**Run simulator.**

**Expected:** Process exits immediately with:
```
terminalId must be a 20-digit string
```

No network connections are attempted.

**Pass:** Clean error message, immediate exit, no partial connections.

---

## 8. What to Verify at Each Stage

| Stage | Where to look | Tool |
|---|---|---|
| JT808 connections | Simulator dashboard: Active connections | Console |
| JT808 auth | Simulator dashboard: Authenticated sessions | Console |
| JT808 admin list | `http://SERVER_IP:8888/rtvs` | Browser |
| JT808 command history | `http://SERVER_IP:8888/rtvs` | Browser |
| JT1078 sessions (RTVS) | `http://SERVER_IP:8089/api/sessions` | Browser / curl |
| JT1078 session count | Simulator dashboard: Active media sessions | Console |
| TCP connections to JT808 server | `ss -tan \| grep SERVER_IP:7611 \| wc -l` | Server machine shell |
| TCP connections to RTVS | `ss -tan \| grep :1078 \| wc -l` | Server machine shell |
| Server health | `http://SERVER_IP:8089/api/health` | curl |
| Checksum errors | Simulator dashboard: Invalid checksums | Console — must remain 0 |

---

## 9. Evidence to Capture per Test Run

For each test run, collect and file the following:

```bash
# on both machines
java -version 2>&1
mvn -version
uname -r
lscpu | grep -E "Model name|CPU\(s\)"
free -h

# on simulator machine
ulimit -n

# save all logs
cp logs/simulator.log evidence/T-XX-simulator.log
```

Save a screenshot or text copy of:
- Simulator dashboard at stable state
- jt808-server admin UI terminal list (`http://SERVER_IP:8888/rtvs`)
- RTVS sessions response (`http://SERVER_IP:8089/api/sessions`)

Required metrics to record per test:

| Metric | Value |
|---|---|
| Test ID | e.g. T-04 |
| Date / time | |
| Simulator machine OS + JDK | |
| Server machine OS + JDK | |
| connectionCount | |
| mediaCapableTerminalCount | |
| Authenticated sessions at stable state | |
| Active media sessions at stable state | |
| Ack latency avg (ms) | |
| Ack latency P95 (ms) | |
| Invalid checksums | Must be 0 |
| Connection failures | |
| Reconnect attempts | |
| Pass / Fail | |
| Notes | |

---

## 10. Shutdown

Always shut down in reverse startup order:

1. Stop the simulator (Ctrl+C on simulator machine)
2. Stop jt808-rtvs (Ctrl+C on server machine terminal 2)
3. Stop jt808-server (Ctrl+C on server machine terminal 1)

Both servers handle `SIGINT` cleanly (shutdown hook closes all Netty channels).

---

## 11. Troubleshooting

### Simulator connects but never authenticates

- Confirm jt808-server is running and `authCode` in `server.json` matches what the server sends in the `0x8100` response
- Check jt808-server logs for decode errors on incoming `0x0100` / `0x0102` messages

### Media sessions never appear in RTVS

- Confirm `jt1078.host` and `jt1078.port` in `fleet.json` point to the **server machine** (not `127.0.0.1`)
- Confirm jt808-rtvs is running: `curl http://SERVER_IP:8089/api/health`
- Confirm jt808-server config has `"ingestEnabled": false`
- Check jt808-rtvs logs for `JT1078 connection from ...` lines

### Port already in use

```bash
ss -tlnp | grep -E '7611|7612|1078|8888|8089'
```
Kill the process holding the port or restart the server.

### Invalid checksum count > 0

This indicates a codec or escape-processing bug. Stop the test, collect logs from both machines, and file a bug report with the raw packet capture if possible:

```bash
sudo tcpdump -i any -w /tmp/jt808-capture.pcap port 7611
```

### Connection failures climbing continuously (no recovery)

- Confirm the server machine IP is reachable: `ping SERVER_IP`
- Confirm firewall rules are open (see Section 3)
- Check server machine has not run out of file descriptors: `ulimit -n` on server machine
