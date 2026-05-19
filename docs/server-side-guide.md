# Server-Side Guide

This repository contains two separate runnable applications:

- device-side fleet simulator: `com.example.jt808sim.Main`
- server-side JT808/JT1078 gateway: `com.example.jt808sim.server.ServerMain`

They can run on the same host for local testing or on separate machines for integration testing.

## Build

```bash
mvn clean package
```

## Run Server

```bash
java \
  -Djt808.transport=nio \
  -cp target/jt808-fleet-simulator-0.1.0-SNAPSHOT.jar \
  com.example.jt808sim.server.ServerMain \
  --config config/server.json
```

Default server ports from [config/server.json](../config/server.json):

| Port | Protocol | Purpose |
| --- | --- | --- |
| `7611` | TCP | JT808 alarm/signaling |
| `7612` | TCP | JT808 file/attachment |
| `8888` | HTTP | private RTVS gateway API |

In production, expose only the two JT808 terminal-facing ports publicly. Keep the RTVS gateway HTTP API private between RTVS and this server.

## Run Simulator Against Server

On the simulator machine, edit [config/fleet.json](../config/fleet.json):

```json
"server": {
  "host": "<server-ip-or-dns>",
  "port": 7611
}
```

Then run:

```bash
java \
  -Djt808.transport=nio \
  -jar target/jt808-fleet-simulator-0.1.0-SNAPSHOT.jar \
  --config config/fleet.json
```

The simulator and server do not share memory or runtime state. The only coupling is the JT808/JT1078 network protocol.

## RTVS Gateway API

RTVS should call:

```text
http://<server-private-ip>:8888/VideoControl?Content=<jt808-header-body-hex>
```

The server:

- parses the JT808 header/body hex from RTVS
- finds the terminal by terminal ID
- replaces the sequence number internally by re-encoding the command
- sends the command over the live JT808 terminal channel
- returns the status value expected by RTVS

Return values:

| Return | Meaning |
| --- | --- |
| `0` | vehicle offline |
| `-1` | failure |
| `1` | accepted/sent |
| command ID | returned for `0x9201` and `0x9205` |
| sequence | returned for `0x9206` |

Implemented auxiliary endpoints:

```text
/WCF0x9105?Content=<json>
/GetVehicleSim?Sim=<terminalId>
```

`WCF0x9105` currently accepts and returns `1`. `GetVehicleSim` returns the SIM only when that exact SIM is online.

## Current Server Scope

Implemented:

- JT808 TCP acceptor for alarm/signaling and file/attachment ports
- JT808 frame, escape, checksum, and message decoding through the shared protocol pipeline
- terminal session registry
- `0x0100` registration response with `0x8100`
- `0x0102` authentication acknowledgment with `0x8001`
- platform general acknowledgment for heartbeat, location, and JT1078 terminal uploads
- terminal general response correlation for platform commands
- RTVS `VideoControl` pass-through for JT1078-over-JT808 commands
- optional ClickHouse schema bootstrap for `vehicle_gps`, `vehicle_alarm`, and `vehicle_alarm_file`
- optional ClickHouse persistence for decoded JT808 `0x0200` GPS/location reports

## ClickHouse Storage

The server can create the database tables from `JT808 database structure.pdf` and persist incoming location reports to `vehicle_gps`.

Enable it in [config/server.json](../config/server.json):

```json
"clickHouse": {
  "enabled": true,
  "url": "http://127.0.0.1:8123/",
  "username": "default",
  "password": "",
  "database": "jt808",
  "cluster": "",
  "engine": "ReplacingMergeTree",
  "timezone": "Asia/Shanghai",
  "createSchema": true,
  "ttlDays": 365,
  "batchSize": 500,
  "flushIntervalMs": 1000
}
```

For a single ClickHouse node, leave `cluster` empty. For a distributed cluster, set `cluster` to the ClickHouse cluster name; the generated DDL will add `ON CLUSTER '<name>'`.

Runtime ingestion currently covers GPS rows from JT808 `0x0200`. The alarm and alarm-file tables are created so the server-side storage layout is ready, but alarm/file ingestion still depends on implementing the terminal file upload and alarm attachment workflows.

Still to add before production:

- durable terminal/session storage
- Redis integration for RTVS keys such as `AVParameters:<sim>` and `OCX_ORDERINFO_<commandId>`
- authentication policy beyond static auth token
- file/attachment upload protocol implementation on the file port
- ClickHouse persistence for alarm lifecycle and uploaded alarm attachments
- server metrics dashboard and health endpoints
- TLS or network policy around private HTTP APIs
