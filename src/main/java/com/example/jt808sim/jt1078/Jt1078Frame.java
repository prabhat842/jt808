package com.example.jt808sim.jt1078;

public record Jt1078Frame(
        Jt1078FrameType type,
        long timestampMillis,
        boolean keyFrame,
        int previousIFrameIntervalMillis,
        int previousFrameIntervalMillis,
        byte[] payload) {

    public Jt1078Frame {
        payload = payload == null ? new byte[0] : payload.clone();
    }
}
