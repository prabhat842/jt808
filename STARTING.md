# Starting the JT808 Stack

Three processes, three terminals. Start them in order.

```
simulator  ──── JT808 signaling ──►  jt808-server  :7611 / :7612
           ──── JT1078 media    ──►  jt808-rtvs    :1078
```

---

## 1. Build

```bash
cd ~/jt808-server  && mvn clean package -q
cd ~/jt808-rtvs    && mvn clean package -q
cd ~/jt808         && mvn clean package -q
```

---

## 2. Start jt808-server (JT808 signaling + RTVS command API)

```bash
java -jar ~/jt808-server/target/jt808-server-0.1.0-SNAPSHOT.jar \
     --config ~/jt808-server/config/server.json
```

Ports bound:
- `7611` — JT808 alarm/signaling (terminals connect here)
- `7612` — JT808 file upload
- `8888` — RTVS gateway API + web UI (`http://localhost:8888`)

---

## 3. Start jt808-rtvs (JT1078 media ingest + browser studio)

```bash
java -jar ~/jt808-rtvs/target/jt808-rtvs-0.1.0-SNAPSHOT.jar \
     --config ~/jt808-rtvs/config/rtvs.json
```

Ports bound:
- `1078` — JT1078 media ingest (simulator streams video here)
- `8089` — RTVS browser studio (`http://localhost:8089`)

---

## 4. Start the simulator

```bash
cd ~/jt808
java -jar target/jt808-fleet-simulator-0.1.0-SNAPSHOT.jar --config config/camera-host.json
```

The simulator connects to `127.0.0.1:7611` (server) and streams media to `127.0.0.1:1078` (rtvs).
If the server isn't up yet it will retry automatically.

With `camera-host.json` the simulator **auto-launches the DMS sidecar** (`dms-sidecar/dms_server.py`)
on startup — no separate step needed. The camera turns on immediately as a vehicle-side component,
independent of whether jt808-server or rtvs is running.

Other config options:

| Config | What it does |
|--------|-------------|
| `config/fleet.json` | 10 terminals, file-based media (default) |
| `config/server.json` | 1 terminal, no media |
| `config/camera-host.json` | 1 terminal, live webcam + DMS auto-launch |
| `config/camera-smoke.json` | 1 terminal, synthetic camera |

---

## 5. Verify

```bash
# Connected terminals
curl http://localhost:8888/api/terminals

# Active media sessions
curl http://localhost:8888/api/media/sessions

# RTVS studio health
curl http://localhost:8089/api/health

# Browser UIs
# http://localhost:8888   — RTVS gateway UI (send commands to terminals)
# http://localhost:8089   — RTVS studio (media session viewer)
```

---

## Port reference

| Service | Port | Protocol |
|---------|------|----------|
| JT808 signaling | 7611 | TCP |
| JT808 file upload | 7612 | TCP |
| JT1078 media ingest | 1078 | TCP |
| RTVS gateway API + UI | 8888 | HTTP |
| RTVS browser studio | 8089 | HTTP |

---

## DMS sidecar (auto-managed)

With `camera-host.json`, the simulator starts `dms-sidecar/dms_server.py` automatically.
Install its dependencies once if not already done:

```bash
pip install -r ~/jt808/dms-sidecar/requirements.txt
```
