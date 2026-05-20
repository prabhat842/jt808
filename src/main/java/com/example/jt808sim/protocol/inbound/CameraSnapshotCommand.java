package com.example.jt808sim.protocol.inbound;

/**
 * 0x8801 Camera immediately taken command (Table 83, JT808-2013).
 *
 * takenCommand: 0=stop, 0xFFFF=start recording, n>0=take n photos
 * intervalSeconds: 0=minimum interval
 * savingSign: 1=store locally, 0=real-time upload
 * resolution: 0x01=320×240, 0x02=640×480, 0x03=800×600, 0x04=1024×768
 */
public record CameraSnapshotCommand(
        int channelId,
        int takenCommand,
        int intervalSeconds,
        int savingSign,
        int resolution,
        int quality,
        int brightness,
        int contrast,
        int saturation,
        int chroma
) {
    /** True when the command requests real-time upload (savingSign == 0). */
    public boolean realtimeUpload() { return savingSign == 0; }
    /** Number of photos requested (0 or 0xFFFF have special meanings). */
    public int photoCount() { return takenCommand > 0 && takenCommand < 0xFFFF ? takenCommand : 1; }
}
