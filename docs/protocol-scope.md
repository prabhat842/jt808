# Protocol Scope

This document records the simulator's JT/T 808 and JT/T 1078 protocol scope.

The implementation uses `JT808-protocol.pdf`, `jt1078.pdf`, and SmallChi/JT808 as protocol fidelity references. The code must stay Netty-native and simulator-oriented; do not copy or couple to another repository's architecture.

Server-side integration with RTVS is tracked separately in [server-side-rtvs-integration.md](server-side-rtvs-integration.md).

## JT/T 1078 Integration Model

JT/T 1078 is not a standalone terminal protocol in this simulator. The standard extends a JT/T 808 terminal/platform session:

- terminal registration, authentication, heartbeats, location, general responses, and command acknowledgments remain JT/T 808 signaling
- JT/T 1078 audio/video control messages are carried as JT/T 808 message bodies
- audio/video stream data uses a separate RTP-like JT/T 1078 transport packet over TCP or UDP
- a media-capable terminal therefore needs both JT/T 808 session state and JT/T 1078 media behavior

Treating JT/T 1078 in isolation is only useful for low-level media packet tests. It is not correct for end-to-end terminal simulation.

## Implemented JT/T 808 Behavior

- `0x7E` frame delimiters
- escape/unescape handling
- XOR checksum validation and generation
- versioned JT/T 808 header handling for 20-digit terminal identifiers
- `0x0100` terminal registration
- `0x0102` terminal authentication
- `0x0200` location report
- `0x0002` terminal heartbeat
- inbound `0x8100` registration response
- inbound `0x8001` platform general acknowledgment

## Implemented JT/T 1078 Behavior

- media-capable terminal modeling tied to the JT/T 808 lifecycle
- synthetic media socket sessions
- configurable packet rate and payload size
- media session, packet, byte, and failure metrics
- decoding of Appendix A platform commands `0x9003`, `0x9101`, `0x9102`, `0x9205`, `0x9201`, `0x9202`, `0x9206`, `0x9207`, and `0x9301` through `0x9306`
- terminal general response `0x0001` for handled JT/T 1078 platform commands
- terminal upload messages `0x1003`, `0x9105`, `0x1205`, and `0x1206`
- Table 19-compatible synthetic stream packet header

The current media packet writer uses a Table 19-compatible header with synthetic H.264-like payload bytes. It is suitable for protocol framing and load tests, not media decoder certification.

## JT/T 1078 Signaling Messages From The PDF

The terminal/video platform message cross-reference in Appendix A includes:

| Message | Direction | Purpose |
| --- | --- | --- |
| `0x9003` | platform to terminal | query terminal audio/video attributes |
| `0x1003` | terminal to platform | upload terminal audio/video attributes |
| `0x9101` | platform to terminal | real-time audio/video transmission request |
| `0x1005` | terminal to platform | upload passenger traffic |
| `0x9102` | platform to terminal | real-time audio/video transmission control |
| stream packet | terminal to media server | real-time audio/video stream and passthrough data |
| `0x9105` | terminal to platform | real-time audio/video transmission status notification |
| `0x9205` | platform to terminal | query audio/video resource list |
| `0x1205` | terminal to platform | upload audio/video resource list |
| `0x9201` | platform to terminal | remote video playback request |
| `0x9202` | platform to terminal | remote playback control |
| `0x9206` | platform to terminal | file upload command |
| `0x1206` | terminal to platform | file upload completion notification |
| `0x9207` | platform to terminal | file upload control |
| `0x9301` | platform to terminal | PTZ/head rotation |
| `0x9302` | platform to terminal | focus control |
| `0x9303` | platform to terminal | aperture control |
| `0x9304` | platform to terminal | wiper control |
| `0x9305` | platform to terminal | infrared fill light control |
| `0x9306` | platform to terminal | zoom control |
| `WAKEUPXX` | platform to terminal | SMS wake-up request |

## JT/T 1078 Stream Packet

The stream packet is based on RTP and adds terminal/media fields:

- fixed header identifier: `0x30316364`
- RTP flags: version `2`, padding `0`, extension `0`, CSRC count `1`
- marker bit and payload type
- packet sequence number
- terminal SIM number as `BCD[6]`
- logical channel number
- data type and subpackage marker
- timestamp for non-passthrough data
- previous I-frame interval and previous frame interval for video
- body length
- media or passthrough body, up to 950 bytes per packet

Data type values include video I-frame, video P-frame, video B-frame, audio frame, and passthrough data.

## Implementation Backlog

Protocol work still required for complete JT/T 1078 support:

- richer resource-list fixtures for `0x9205`/`0x1205`
- playback fixture state and completion behavior for `0x9201`/`0x9202`
- FTP upload simulation beyond immediate `0x1206` completion notification
- persistent PTZ/focus/aperture/wiper/infrared/zoom state modeling
- add video alarm additional info fields `0x14` through `0x18` to location reports when configured
- add `0x1005` passenger-traffic scheduling/configuration when passenger counting simulation is needed
- add stream packet variants for audio frame, I-frame, B-frame, passthrough data, and fragmented packets
