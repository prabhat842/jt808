# JT1078 Camera And Two-Way Talk Implementation Plan

This document defines the next implementation phase for turning the simulator from a framing/load-test JT/T 1078 client into a protocol-aware live media terminal that can:

- capture laptop camera video inside the simulator host
- capture laptop microphone audio inside the simulator host
- stream encoded JT/T 1078 media after JT/T 808 control commands
- receive downlink talk/intercom audio from the platform
- optionally play received talk audio on the simulator host

The control plane remains JT/T 808. The media plane remains JT/T 1078.

## Goal

The target behavior is:

1. platform sends JT/T 1078 control messages over JT/T 808 such as `0x9101` and `0x9102`
2. simulator terminal acknowledges the control command over JT/T 808
3. simulator terminal opens or updates a JT/T 1078 media socket
4. simulator terminal pushes real encoded video and audio frames over the JT/T 1078 media socket
5. for intercom/listen/broadcast modes, simulator terminal can also receive and optionally play downlink audio

This keeps the architecture aligned with the standards and with the existing RTVS integration notes.

## Current State

Current implementation status:

- JT/T 808 registration, authentication, heartbeat, location, and general response flow is implemented
- JT/T 1078 control messages are decoded from JT/T 808 payloads
- media sockets are opened from the terminal side after control commands
- current media payload generation is `synthetic` or `file`
- current media packets always resemble a simple video packet stream
- reverse audio and true capture/encode are not implemented

Current limitations:

- no webcam capture
- no microphone capture
- no audio frame packetization
- no distinction between I/P/B video frames in packet headers
- no fragmented large-frame handling
- no downlink intercom audio receive path
- no real codec pipeline

## Protocol Position

The important protocol boundary is:

- JT/T 808 carries signaling and control
- JT/T 1078 carries the actual media stream

The simulator must not treat video as if it is transported inside normal JT/T 808 packets. The JT/T 808 session only triggers and controls media behavior.

Relevant control messages already modeled:

- `0x9003` query audio/video attributes
- `0x9101` real-time audio/video transmission request
- `0x9102` real-time audio/video transmission control
- `0x9201` playback request
- `0x9202` playback control
- `0x9205` resource list query
- `0x9206` file upload command
- `0x9207` file upload control
- `0x9301` through `0x9306` device controls

Relevant media directions to add:

- uplink live video
- uplink live audio
- downlink talk/intercom audio

## Scope

Phase 1 scope:

- add real laptop camera capture for one or a few media-capable terminals
- add real laptop microphone capture
- encode video to H.264
- encode audio to AAC by default
- packetize encoded access units into JT/T 1078 media packets
- support live video and intercom/listen/broadcast request types through `0x9101`
- support receive path for downlink talk audio
- keep synthetic and file modes for scale/load tests

Out of scope for this phase:

- pure Java software encoding pipeline
- decoder certification or pixel-perfect renderer validation
- multi-camera device emulation per terminal
- large-fleet real webcam capture
- full DVR/history playback implementation

## Configuration Additions

Extend `FleetConfig.Jt1078Settings` with capture and talk settings.

Proposed additions:

```json
"jt1078": {
  "mediaCapableTerminalCount": 1,
  "host": "127.0.0.1",
  "port": 1078,
  "streamMode": "camera",
  "mediaFiles": [],
  "videoPayloadBytesPerPacket": 950,
  "videoPacketsPerSecond": 25,
  "capture": {
    "videoEnabled": true,
    "audioEnabled": true,
    "videoDevice": "/dev/video0",
    "audioDevice": "default",
    "videoWidth": 1280,
    "videoHeight": 720,
    "videoFps": 25,
    "videoBitrateKbps": 1200,
    "audioSampleRate": 8000,
    "audioChannels": 1,
    "audioBitrateKbps": 32,
    "ffmpegPath": "ffmpeg"
  },
  "talk": {
    "enabled": true,
    "playReceivedAudio": false,
    "receiveBufferMillis": 200
  }
}
```

Proposed `streamMode` values:

- `synthetic`
- `file`
- `camera`

Validation rules:

- `camera` requires `ffmpeg` availability
- `camera` should only be used for a small number of media-capable terminals
- audio and video can be enabled independently
- if both are disabled under `camera`, config is invalid

## Class-Level Design

### Replace byte filler with frame-oriented media

The current `MediaPayloadSource.writePayload(...)` contract is too weak for real JT/T 1078 media because it only fills raw bytes. Replace it with frame-oriented output.

Introduce:

- `Jt1078Frame`
- `Jt1078FrameSource`
- `Jt1078FrameType`
- `Jt1078Packetizer`

Proposed model:

```java
public record Jt1078Frame(
        Jt1078FrameType type,
        long timestampMillis,
        boolean keyFrame,
        byte[] payload) {
}
```

`Jt1078FrameType` should include:

- `VIDEO_I`
- `VIDEO_P`
- `VIDEO_B`
- `AUDIO`
- `PASSTHROUGH`

`Jt1078FrameSource` should expose a pull or callback API that yields complete encoded frames, not arbitrary bytes.

### Media session split

Split current `Jt1078MediaSession` responsibilities into:

- session lifecycle
- uplink packet send path
- optional downlink receive path
- packetization
- capture backend integration

Recommended classes:

- `Jt1078MediaSession`
- `Jt1078UplinkSender`
- `Jt1078DownlinkReceiver`
- `Jt1078Packetizer`
- `Jt1078FrameAssembler` for inbound audio if playback is enabled

### Capture backends

Introduce capture backends behind the frame source interface:

- `SyntheticFrameSource`
- `FileFrameSource`
- `FfmpegCaptureFrameSource`

The existing `SyntheticMediaSource` and `FileMediaSource` can remain temporarily, but the end state should move them behind the same frame-oriented interface.

## FFmpeg Integration

Use FFmpeg as the practical capture and encode backend.

Reason:

- webcam and microphone capture are OS/device specific
- H.264 and AAC encoding is already solved well by FFmpeg
- process-based integration is much faster to deliver than building native capture/encode in Java

### FFmpeg process model

Start one FFmpeg process per real-capture terminal session, not per packet.

Preferred mode:

- capture camera and microphone
- encode H.264 video and AAC audio
- emit a transport stream or elementary stream format that the simulator can parse into access units

Recommended initial output approach:

- FFmpeg writes MPEG-TS to stdout
- Java reads stdout
- a small demux/parser extracts H.264 access units and AAC frames
- extracted frames become `Jt1078Frame` objects

Alternative initial approach:

- FFmpeg writes Annex B H.264 for video-only mode
- audio capture is added in a second step

### Example Linux capture commands

Video-only prototype:

```bash
ffmpeg \
  -f v4l2 -framerate 25 -video_size 1280x720 -i /dev/video0 \
  -an \
  -c:v libx264 -preset veryfast -tune zerolatency \
  -g 50 -keyint_min 50 -pix_fmt yuv420p \
  -f h264 -
```

Video + audio prototype:

```bash
ffmpeg \
  -f v4l2 -framerate 25 -video_size 1280x720 -i /dev/video0 \
  -f alsa -i default \
  -c:v libx264 -preset veryfast -tune zerolatency \
  -g 50 -keyint_min 50 -pix_fmt yuv420p \
  -c:a aac -ar 8000 -ac 1 -b:a 32k \
  -f mpegts -
```

### Operational constraints

- FFmpeg startup failure must not crash the whole simulator
- if capture backend fails, mark only the affected terminal media session failed
- process stdout must be consumed continuously
- stderr should be logged at debug or info level
- process cleanup on session stop is mandatory

## JT1078 Packet Mapping

### Uplink video

Map encoded H.264 access units to JT/T 1078 frame types:

- IDR or keyframe NAL units -> `VIDEO_I`
- non-IDR predictive frames -> `VIDEO_P`
- B-frames only if encoder is configured to emit them

For Phase 1:

- configure FFmpeg for low latency and no B-frames if possible
- treat stream as I/P only

### Uplink audio

AAC frames should map to:

- `AUDIO`

### Fragmentation

Large encoded frames may exceed one JT/T 1078 packet payload. Add fragmentation support:

- first packet marks subpackage start
- middle packets mark continuation
- final packet marks end

The current code only emits atomic packets. That is insufficient for real encoded frames.

### Timestamps

Use frame timestamps derived from encoder output timing or a monotonic clock aligned to capture order.

Rules:

- video timestamp should advance with frame cadence
- audio timestamp should reflect audio frame cadence
- timestamps must be monotonic per media session

### Header fields

Update `Jt1078MediaPacket` so packet headers are driven by frame metadata rather than hard-coded video P-frame defaults.

Specifically make dynamic:

- payload type
- data type nibble
- subpackage marker
- timestamp
- body length
- previous frame interval values where applicable

## Two-Way Talk / Downlink Audio

Two-way communication needs a receive path, not just uplink media.

Minimum Phase 1 behavior:

1. platform sends `0x9101` with an audio-related mode
2. terminal starts or joins a media session
3. terminal sends microphone audio uplink if requested
4. terminal accepts downlink audio packets on the same or paired media connection
5. simulator either discards, records, or locally plays the received audio

Proposed modes to support first:

- talk/intercom
- listen
- broadcast

Implementation options for received audio:

- `discard`: useful for protocol testing
- `record`: save raw AAC or decoded PCM to file
- `play`: decode and play to local speaker

Recommended sequence:

1. implement `discard`
2. add `record`
3. add `play`

This keeps protocol progress separate from local audio-device complexity.

## TerminalSession Behavior Changes

`TerminalSession.handleJt1078Command(...)` should become more mode-aware.

Required changes:

- inspect `RealTimeRequest.dataType()` to distinguish live video, audio-only, talk, listen, and broadcast
- create media sessions with capabilities rather than always starting the same uplink sender
- allow playback requests to use file or fixture sources rather than the live camera source
- stop or pause the correct media directions on `0x9102` and `0x9202`

Add a session description object, for example:

```java
public record Jt1078SessionRequest(
        String host,
        int port,
        int channel,
        boolean uplinkVideo,
        boolean uplinkAudio,
        boolean downlinkAudio,
        boolean playbackMode) {
}
```

## Testing Strategy

### Unit tests

Add tests for:

- frame type to packet header mapping
- frame fragmentation into multiple JT/T 1078 packets
- `0x9101` mode decoding to session capabilities
- packet receive parsing for downlink audio

### Integration tests

Add local integration tests with:

- mock JT808 platform
- mock JT1078 media sink
- optional FFmpeg-backed camera source

Pass criteria:

- terminal registers and authenticates
- platform sends `0x9101`
- simulator opens media connection
- simulator sends decodable JT/T 1078 headers with correct frame types
- simulator stops on `0x9102`
- talk mode can receive downlink audio packets without crashing

### Manual test path

For a laptop smoke test:

1. configure one media-capable terminal
2. set `streamMode` to `camera`
3. point JT1078 media host/port at a controlled sink
4. trigger `0x9101` from the platform
5. verify visible camera motion in the received stream
6. verify microphone packets are emitted in talk mode
7. verify simulator can consume downlink audio packets

## Performance Guidance

Real capture mode is not a fleet-scale mode.

Recommended operational rule:

- `camera` mode only for `1` to `4` terminals on a developer machine
- use `file` or `synthetic` modes for larger fleets

Reason:

- webcam and microphone are single-host resources
- H.264 and AAC encoding are CPU-intensive
- audio playback/capture introduces device contention

## Rollout Plan

Implement in this order:

1. add config schema for `camera` and `talk`
2. introduce frame-oriented media abstractions
3. update `Jt1078MediaPacket` to support dynamic frame types and fragmentation
4. implement FFmpeg-backed video-only capture
5. wire `0x9101` live video requests to camera mode
6. add audio capture uplink
7. add downlink audio receive path with discard mode
8. add optional record/play behavior for downlink audio
9. update docs and testing guide

## Acceptance Criteria

The feature is complete when all of the following are true:

- a platform `0x9101` command can trigger real webcam video uplink
- the simulator can send JT/T 1078 packets reflecting real encoded frame boundaries
- the simulator can send microphone audio when the request mode requires it
- the simulator can receive downlink talk audio without protocol failure
- `synthetic` and `file` modes continue to work
- tests cover packet mapping, fragmentation, and control-to-session behavior

## Immediate Code Targets

The first code changes should focus on these files:

- `src/main/java/com/example/jt808sim/config/FleetConfig.java`
- `src/main/java/com/example/jt808sim/fleet/TerminalSession.java`
- `src/main/java/com/example/jt808sim/jt1078/Jt1078MediaSession.java`
- `src/main/java/com/example/jt808sim/jt1078/Jt1078MediaPacket.java`
- `src/main/java/com/example/jt808sim/jt1078/Jt1078MediaConfig.java`

New classes will likely be needed under:

- `src/main/java/com/example/jt808sim/jt1078/capture/`
- `src/main/java/com/example/jt808sim/jt1078/packet/`
- `src/main/java/com/example/jt808sim/jt1078/audio/`
