package com.example.jt808sim.protocol;

public final class MessageIds {
    public static final int HEARTBEAT = 0x0002;
    public static final int TERMINAL_REGISTER = 0x0100;
    public static final int TERMINAL_AUTH = 0x0102;
    public static final int LOCATION_REPORT = 0x0200;
    public static final int SERVER_ACK = 0x8001;
    public static final int REGISTER_RESPONSE = 0x8100;

    private MessageIds() {
    }
}
