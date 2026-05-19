package com.example.jt808.platform.protocol;

public final class MessageIds {
    public static final int TERMINAL_GENERAL_RESPONSE = 0x0001;
    public static final int HEARTBEAT = 0x0002;
    public static final int TERMINAL_REGISTER = 0x0100;
    public static final int TERMINAL_AUTH = 0x0102;
    public static final int LOCATION_REPORT = 0x0200;
    public static final int BATCH_LOCATION_UPLOAD = 0x0704;
    public static final int PLATFORM_GENERAL_ACK = 0x8001;
    public static final int REGISTER_RESPONSE = 0x8100;

    public static final int VEHICLE_CONTROL      = 0x8500;
    public static final int VEHICLE_CONTROL_RESP = 0x0500;

    public static final int JT1078_REALTIME_REQUEST = 0x9101;
    public static final int JT1078_PLAYBACK_REQUEST = 0x9201;
    public static final int JT1078_QUERY_RESOURCE_LIST = 0x9205;

    private MessageIds() {
    }
}
