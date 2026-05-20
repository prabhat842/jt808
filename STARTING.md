# Starting the JT808 Stack

Two separate apps: the **platform** (server) and the **simulator** (client).
Start the platform first, then the simulator. The simulator will wait and retry if the platform isn't up yet.

---

## 1. Build everything

```bash
# Build the simulator
mvn clean package -q

# Build the platform
cd jt808-platform
mvn clean package -q
cd ..
```

---

## 2. Start the platform (5 services)

Open **five terminals** and run one service per terminal, in this order:

**Terminal 1 — Gateway** (JT808 TCP listener on port 7611)
```bash
java -jar jt808-platform/gateway-service/target/gateway-service-0.1.0-SNAPSHOT.jar
```

**Terminal 2 — Auth service**
```bash
java -jar jt808-platform/auth-service/target/auth-service-0.1.0-SNAPSHOT.jar
```

**Terminal 3 — Telemetry service**
```bash
java -jar jt808-platform/telemetry-service/target/telemetry-service-0.1.0-SNAPSHOT.jar
```

**Terminal 4 — Alarm service**
```bash
java -jar jt808-platform/alarm-service/target/alarm-service-0.1.0-SNAPSHOT.jar
```

**Terminal 5 — Admin API** (REST on port 8090)
```bash
java -jar jt808-platform/admin-api/target/admin-api-0.1.0-SNAPSHOT.jar
```

**Optional — Media service** (only needed for JT1078 video)
```bash
java -jar jt808-platform/media-service/target/media-service-0.1.0-SNAPSHOT.jar
```

Wait until you see `Started ...Application` in each terminal before moving on.

---

## 3. Start the simulator

Pick the config that matches what you want to test:

| Config | What it does |
|--------|-------------|
| `config/fleet.json` | 10 terminals, file-based media (default) |
| `config/server.json` | 1 terminal, no media |
| `config/camera-host.json` | 1 terminal, live webcam via ffmpeg |
| `config/camera-smoke.json` | 1 terminal, synthetic camera (no real webcam needed) |

```bash
java -jar target/jt808-fleet-simulator-0.1.0-SNAPSHOT.jar --config config/fleet.json
```

The simulator connects to `127.0.0.1:7611` (gateway). If the gateway isn't up yet it will retry automatically:
```
terminal 000...001 connecting to 127.0.0.1:7611
terminal 000...001 connection to 127.0.0.1:7611 failed — Connection refused
terminal 000...001 will retry in 1 s (attempt 1)
```

---

## 4. (Optional) Start the DMS sidecar

Only needed when using `camera-host.json` with `dms.enabled: true`.
Requires a webcam and the Python dependencies installed.

```bash
# Install dependencies (once)
pip install -r dms-sidecar/requirements.txt

# Start the sidecar (runs on port 7500)
python3 dms-sidecar/dms_server.py
```

Start this **before** the simulator so the sidecar is ready when the simulator polls it.

---

## Verify everything is connected

```bash
# Gateway health
curl http://localhost:8090/actuator/health

# Check terminals registered (replace with your admin token)
curl -H "X-Api-Token: admin-token" http://localhost:8090/api/terminals
```

---

## Quick reference: ports

| Service | Port | Protocol |
|---------|------|----------|
| Gateway (JT808 signaling) | 7611 | TCP |
| Gateway (file upload) | 7612 | TCP |
| JT1078 media | 1078 | TCP |
| Gateway (actuator) | 8080 | HTTP |
| Telemetry service | 8082 | HTTP |
| Alarm service | 8083 | HTTP |
| Media service | 8084 | HTTP |
| Admin API | 8090 | HTTP |
| Auth service | 8091 | HTTP |
| DMS sidecar | 7500 | HTTP |
