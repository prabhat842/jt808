package com.example.jt808sim.jt1078;

public enum Jt1078FrameType {
    VIDEO_I(0x0, 98),
    VIDEO_P(0x1, 98),
    VIDEO_B(0x2, 98),
    AUDIO(0x3, 19),
    PASSTHROUGH(0x4, 0);

    private final int dataTypeNibble;
    private final int payloadType;

    Jt1078FrameType(int dataTypeNibble, int payloadType) {
        this.dataTypeNibble = dataTypeNibble;
        this.payloadType = payloadType;
    }

    public int dataTypeNibble() {
        return dataTypeNibble;
    }

    public int payloadType() {
        return payloadType;
    }

    public boolean isVideo() {
        return this == VIDEO_I || this == VIDEO_P || this == VIDEO_B;
    }
}
