# Sample Media Fixtures

These small `.mp4` files are deterministic byte fixtures for JT1078 media transport tests.

They are intended to exercise file-backed payload streaming, packet pacing, connection handling, and server-side ingest counters. They are not intended for video decoder certification.

Use them through `config/fleet.json`:

```json
"jt1078": {
  "streamMode": "file",
  "mediaFiles": [
    "sample-media/city-loop.mp4",
    "sample-media/road-loop.mp4"
  ]
}
```
