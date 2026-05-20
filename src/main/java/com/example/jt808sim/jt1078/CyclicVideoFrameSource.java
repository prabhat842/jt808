package com.example.jt808sim.jt1078;

/**
 * Video frame source that cycles through a realistic I/P/B GOP pattern.
 * GOP size is 30 frames: frame 0 is I, then P and B alternate, repeating every 30 frames.
 */
public class CyclicVideoFrameSource implements Jt1078FrameSource {
    private static final int GOP_SIZE = 30;
    private static final int FRAME_PERIOD_MILLIS = 40; // 25 fps

    private final int payloadBytes;

    public CyclicVideoFrameSource(int payloadBytes) {
        this.payloadBytes = payloadBytes;
    }

    @Override
    public Jt1078Frame nextFrame(long frameIndex) {
        int posInGop = (int) (frameIndex % GOP_SIZE);
        boolean isIFrame = (posInGop == 0);
        Jt1078FrameType type = isIFrame ? Jt1078FrameType.VIDEO_I
                : (posInGop % 3 == 2) ? Jt1078FrameType.VIDEO_B
                : Jt1078FrameType.VIDEO_P;

        byte[] payload = new byte[payloadBytes];
        payload[0] = (byte) (frameIndex >> 8);
        payload[1] = (byte) frameIndex;
        payload[2] = (byte) type.dataTypeNibble();

        long tsMillis = frameIndex * FRAME_PERIOD_MILLIS;
        int prevIFrameInterval = isIFrame ? 0 : posInGop * FRAME_PERIOD_MILLIS;
        return new Jt1078Frame(type, tsMillis, isIFrame, prevIFrameInterval, FRAME_PERIOD_MILLIS, payload);
    }
}
