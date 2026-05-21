# Starting the JT808 Stack

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│  Vehicle Terminal (independent — starts any time)           │
│                                                             │
│  Simulator ──── DMS/ADAS/BSD sensors (camera always on)     │
│           ──── JT808 signaling ──►  jt808-server  :7611     │
│           ──── JT1078 media    ──►  jt808-rtvs    :1078     │
│                                                             │
│  The terminal reconnects to the server automatically.       │
│  Camera and DMS run regardless of server availability.      │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│  Infrastructure (independent — can restart without vehicle) │
│                                                             │
│  jt808-server  :7611 :7612 :8888                            │
│  jt808-rtvs    :1078 :8089                                  │
└─────────────────────────────────────────────────────────────┘
```

---

## Recommended: Control Panel

The control panel manages all services with a single web UI.

### 1. Build (once)

```bash
cd ~/jt808-server  && mvn clean package -q
cd ~/jt808-rtvs    && mvn clean package -q
cd ~/jt808         && mvn clean package -q
cd ~/jt808-control && mvn clean package -q
```

Install DMS sidecar dependencies once:

```bash
pip install -r ~/jt808/dms-sidecar/requirements.txt
```

### 2. Start the control panel

```bash
java -jar ~/jt808-control/target/jt808-control-1.0.0.jar
```

Open **http://localhost:8080**

### 3. Use the control panel

| Button | Action |
|--------|--------|
| ▶ Start Vehicle | Starts simulator (camera on, DMS active immediately) |
| ▶ Start Infrastructure | Starts jt808-server + jt808-rtvs |
| ▶ Start All | Starts all three concurrently |
| ■ Stop All | Stops vehicle first (clean logout), then infrastructure |

The simulator can be started **before or after** infrastructure.
When the server comes up, the terminal auto-connects and begins
JT808 registration → authentication → location reporting.

### 4. Verify via control panel

- **Live Logs** tab — wire-level frames for every service (`RX 7E … 7E`, `TX 7E … 7E`)
- **Status cards** — PID, uptime, running/stopped state per service

---

## Manual startup (alternative)

### Start simulator (vehicle — start any time)

```bash
cd ~/jt808
java -jar target/jt808-fleet-simulator-0.1.0-SNAPSHOT.jar \
     --config config/camera-host.json
```

- Auto-launches DMS sidecar (`dms-sidecar/dms_server.py`) — camera on immediately
- Retries connecting to `127.0.0.1:7611` until the server is available
- **Only one instance** — running two simulators with the same terminal ID
  causes interleaved JT1078 streams and a blank video player

### Start infrastructure (can be started before or after simulator)

```bash
# JT808 signaling + RTVS command API
java -jar ~/jt808-server/target/jt808-server-0.1.0-SNAPSHOT.jar \
     --config ~/jt808-server/config/server.json

# JT1078 media ingest + browser studio
java -jar ~/jt808-rtvs/target/jt808-rtvs-0.1.0-SNAPSHOT.jar \
     --config ~/jt808-rtvs/config/rtvs.json
```

### Verify

```bash
curl http://localhost:8888/api/terminals       # connected terminals
curl http://localhost:8888/api/media/sessions  # active media sessions
curl http://localhost:8089/api/health          # RTVS studio health
curl http://localhost:8089/api/sessions        # active JT1078 streams
```

---

## Live video (RTVS Studio)

Open **http://localhost:8089** in Chrome or Edge (WebCodecs required).

The studio auto-connects to the first active session. To start a stream
manually if the player shows "buffering":

```bash
curl "http://localhost:8888/api/live/start?terminal=00000000000000000001&channel=1"
```

The player badge shows:
- `connecting…` — WebSocket opened, waiting for server
- `buffering…` — subscribed, waiting for first I-frame
- `live` — decoding and rendering H.264 frames
- `error: …` — WebCodecs unavailable or decode failure (check browser console)

H.264 debug captures are written to `/tmp/rtvs-capture/` automatically.
Replay with: `ffplay /tmp/rtvs-capture/000000000001-ch1.h264`

---

## Simulator configs

| Config | What it does |
|--------|-------------|
| `config/camera-host.json` | 1 terminal, live webcam + DMS auto-launch |
| `config/fleet.json` | 10 terminals, file-based media |
| `config/camera-smoke.json` | 1 terminal, synthetic camera |
| `config/server.json` | 1 terminal, no media |

---

## Port reference

| Service | Port | Protocol |
|---------|------|----------|
| Control panel UI | 8080 | HTTP |
| JT808 signaling | 7611 | TCP |
| JT808 file upload | 7612 | TCP |
| RTVS gateway API + UI | 8888 | HTTP |
| JT1078 media ingest | 1078 | TCP |
| RTVS browser studio | 8089 | HTTP + WebSocket (/ws) |
| DMS sidecar | 7500 | HTTP |
