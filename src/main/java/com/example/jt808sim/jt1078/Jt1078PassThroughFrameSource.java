package com.example.jt808sim.jt1078;

public class Jt1078PassThroughFrameSource implements Jt1078FrameSource {
    private final int payloadBytes;

    public Jt1078PassThroughFrameSource(int payloadBytes) {
        this.payloadBytes = payloadBytes;
    }

    @Override
    public Jt1078Frame nextFrame(long frameIndex) {
        byte[] payload = new byte[payloadBytes];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) ((frameIndex * 7 + i) & 0xFF);
        }
        return new Jt1078Frame(Jt1078FrameType.PASSTHROUGH, frameIndex * 20, false, 0, 0, payload);
    }
}
