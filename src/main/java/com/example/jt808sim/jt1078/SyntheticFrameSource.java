package com.example.jt808sim.jt1078;

public class SyntheticFrameSource implements Jt1078FrameSource {
    private final int payloadBytes;
    private final Jt1078FrameType frameType;

    public SyntheticFrameSource(int payloadBytes, Jt1078FrameType frameType) {
        this.payloadBytes = payloadBytes;
        this.frameType = frameType;
    }

    @Override
    public Jt1078Frame nextFrame(long frameIndex) {
        byte[] payload = new byte[payloadBytes];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) ((frameIndex + i) & 0xFF);
        }
        boolean video = frameType.isVideo();
        long timestampMillis = video ? frameIndex * 40 : frameIndex * 20;
        return new Jt1078Frame(frameType, timestampMillis, frameType == Jt1078FrameType.VIDEO_I, 0, video ? 40 : 0, payload);
    }
}
