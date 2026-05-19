# Server-Side RTVS Integration

This document defines how the planned JT808/JT1078 server should integrate with `vanjoge/RTVS`.

Reference repository:

- GitHub: https://github.com/vanjoge/RTVS
- Gateway demo: https://github.com/vanjoge/JT808GW

## Role Boundary

RTVS should be treated as the video platform component, not as the JT808 terminal server.

The server we build should own:

- JT808 TCP terminal access
- terminal registration/authentication/session state
- platform command serialization and delivery over JT808
- terminal general response correlation
- location, heartbeat, alarm, and JT1078 signaling persistence
- gateway HTTP endpoints required by RTVS
- Redis writes/reads expected by RTVS

RTVS should own:

- JT1078 media ingest
- stream distribution to Web/H5/mobile clients
- real-time and historical playback serving
- media cache/transcoding behavior
- WebRTC/FMP4/RTMP/HLS playback surfaces
- optional GB28181 video platform behavior

This keeps two-way communication correct: commands and control flow through the JT808 server, while media flows to RTVS.

## RTVS Gateway Contract

RTVS expects an 808 gateway HTTP API. The important endpoint is:

```text
[GatewayBaseAPI]/VideoControl?Content=<jt808-header-and-body-hex>&IsSuperiorPlatformSend=<bool>
```

`Content` is a JT808 packet fragment containing header and body:

- no `0x7E` delimiters
- not escaped
- sequence number must be replaced by the JT808 server before sending to the terminal

RTVS uses this path to ask the gateway to send JT1078-over-JT808 commands such as:

- `0x9101` real-time audio/video request
- `0x9201` remote playback request
- `0x9202` playback control
- `0x9205` query resource list
- `0x9206` file upload command
- PTZ/control commands where applicable

Return values expected by RTVS:

- `"0"`: vehicle offline
- `"-1"`: command failed
- `"1"`: command accepted/sent for most commands
- for `0x9201` and `0x9205`: return an internal command ID used later for resource/playback response correlation
- for `0x9206`: return the terminal acknowledgment sequence when applicable

## Status Notifications

RTVS can post real-time transmission status notifications to:

```text
[GatewayBaseAPI]/WCF0x9105?Content=<json>
```

The JSON is an array grouped by SIM/terminal ID:

```json
[
  {
    "Sim": "013777883221",
    "NotifyList": [
      { "Channel": 1, "PacketLossRate": 0 }
    ]
  }
]
```

The server should translate this into JT1078 `0x9105` handling/storage as needed and return:

- `"1"` for accepted
- `"-1"` for failure

## Vehicle Lookup

RTVS may call:

```text
[GatewayBaseAPI]/GetVehicleSim?PlateCode=<plate>&PlateColor=<color>
```

The server should return the terminal SIM/phone number string or an empty result if unknown.

## Redis Exchange

RTVS also expects Redis data for gateway/video coordination. The server should support these keys at minimum:

| Key | Type | Writer | Purpose |
| --- | --- | --- | --- |
| `AVParameters:<sim>` | Hash/String JSON | JT808 server | terminal `0x1003` audio/video attributes |
| `OCX_ORDERINFO_<commandId>` | String JSON | JT808 server | `0x1205` resource list response |
| `SIM_CONFIG_FOR_RTVS_<sim>` | String JSON | JT808 server/admin | per-terminal media capability/config |
| `storage_settings` | Hash | admin/server | RTVS storage behavior |
| `not_enough_storage_space_channel` | Pub/Sub | RTVS | storage alarm notification |

For government-platform media requests, RTVS expects request metadata in Redis using a key shaped like:

```text
<plate>.<plateColor>.<channel>.<avType>
```

Values are prefixed with:

- `real@<json>` for real-time requests
- `back@<json>` for historical playback requests

## Server-Side Flow

Real-time video:

1. Web/client or upstream platform requests live video through RTVS.
2. RTVS calls our `VideoControl` endpoint with a JT1078 `0x9101` command encoded as JT808 header+body hex.
3. Our server replaces the sequence number, sends the command over the existing JT808 terminal channel, and returns accepted/offline/failure.
4. Terminal replies with JT808 terminal general response.
5. Terminal opens JT1078 media connection to the host/port supplied in `0x9101`, which should be an RTVS media endpoint.
6. RTVS ingests and distributes the stream.

Historical playback:

1. RTVS calls `VideoControl` with `0x9205` or `0x9201`.
2. Our server sends the command to the terminal and returns a command ID for correlation.
3. Terminal uploads `0x1205` resource list or starts playback stream.
4. Our server writes `OCX_ORDERINFO_<commandId>` for resource-list responses.
5. Playback media goes to RTVS.

Two-way audio/intercom:

1. RTVS initiates a `0x9101` request with data type for intercom/listen/broadcast.
2. Our server delivers the JT808 command.
3. Terminal opens media stream to RTVS.
4. RTVS handles browser/client audio path and media exchange.

## Implementation Impact

The server should include an `rtvs-adapter` module with:

- HTTP controller for `VideoControl`
- HTTP controller for `WCF0x9105`
- HTTP controller for `GetVehicleSim`
- Redis writer for `AVParameters:<sim>`
- Redis writer for `OCX_ORDERINFO_<commandId>`
- command correlation store mapping RTVS command IDs to JT808 terminal sequence numbers
- RTVS cluster client for `api/GetBest?Type=1005&Tag=<sim>` when server-to-government video server selection is needed

The JT808 codec and JT1078 command serializers should remain internal server code. RTVS should not be placed inside the terminal session pipeline.

## Port-Minimized Deployment

The deployment should expose the smallest possible public surface.

Our JT808 server needs two public terminal-facing ports:

| Port role | Protocol | Public | Purpose |
| --- | --- | --- | --- |
| JT808 alarm/signaling port | TCP | yes | registration, auth, heartbeat, location, alarms, JT1078 signaling commands |
| JT808 file/attachment port | TCP | yes | terminal file/attachment uploads where separated from alarm/signaling traffic |

RTVS default documentation lists these external mappings when using the bundled scripts:

| Default mapping | Protocol | Required for minimal deployment | Notes |
| --- | --- | --- | --- |
| `17000` | TCP | no, unless exposing RTVS cluster management/WebSocket directly | keep private behind VPN/admin network where possible |
| `6001-6029` | TCP | partly | RTVS media/service ports; reduce to the actual ports used by the selected RTVS instance/profile |
| `14001-14034` | TCP+UDP | no if WebRTC is disabled | WebRTC port range, documented as CPU-core-count plus 2; omit when using non-WebRTC playback |
| `6030` | TCP+UDP | no | optional active-safety attachment service |
| `9300` | TCP | no | optional RTVS demo/test JT808 gateway; our server replaces it |
| `5060` | TCP+UDP | no | optional GB28181 SIP |

Minimal external profile:

| Component | Expose externally | Keep private |
| --- | --- | --- |
| Our JT808 server | two TCP terminal ports | admin/API/metrics |
| RTVS | one selected JT1078 media ingest TCP port if devices reach RTVS directly; one HTTPS playback endpoint if users access RTVS directly | Redis, GatewayBaseAPI, cluster manager, internal RTVS service ports |
| Redis | no | private network only |
| `VideoControl`, `WCF0x9105`, `GetVehicleSim` | no | private RTVS-to-gateway network only |

If devices can only connect to our server, add a media relay/proxy in our server and keep RTVS completely private. If devices can connect to RTVS directly, the `0x9101` and `0x9201` commands should contain only the single approved RTVS media endpoint.

Avoid exposing the RTVS demo gateway port `9300`; it is only for their sample gateway and is redundant once our Netty JT808 server is implemented.

## Local Integration Test Shape

Use three processes:

1. JT808/JT1078 simulator from this repository.
2. New server-side JT808 gateway.
3. RTVS Docker deployment or a mocked RTVS gateway client.

Minimum pass criteria:

- terminal registers/authenticates with our server
- RTVS `VideoControl` request reaches server
- server sends `0x9101` to terminal
- terminal opens JT1078 media connection to RTVS endpoint
- server handles terminal general response
- server writes `AVParameters:<sim>` after `0x1003`
- server writes `OCX_ORDERINFO_<commandId>` after `0x1205`
