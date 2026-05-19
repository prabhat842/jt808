package com.example.jt808.platform.protocol;

public record Jt808Header(
        int messageId,
        int bodyProperties,
        int protocolVersion,
        String terminalId,
        int sequence,
        boolean versioned,
        boolean fragmented,
        int bodyLength
) {
    public static final int VERSION_FLAG = 0x4000;
    public static final int FRAGMENT_FLAG = 0x2000;
    public static final int BODY_LENGTH_MASK = 0x03FF;

    public static int bodyProperties(int bodyLength, boolean versioned) {
        int props = bodyLength & BODY_LENGTH_MASK;
        if (versioned) {
            props |= VERSION_FLAG;
        }
        return props;
    }
}
