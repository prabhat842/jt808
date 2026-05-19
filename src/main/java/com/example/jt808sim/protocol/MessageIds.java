package com.example.jt808sim.protocol;

public final class MessageIds {
    public static final int TERMINAL_GENERAL_RESPONSE = 0x0001;
    public static final int HEARTBEAT = 0x0002;
    public static final int TERMINAL_REGISTER = 0x0100;
    public static final int TERMINAL_AUTH = 0x0102;
    public static final int LOCATION_REPORT = 0x0200;
    public static final int SERVER_ACK = 0x8001;
    public static final int REGISTER_RESPONSE = 0x8100;
    public static final int TERMINAL_PARAM_SETTING = 0x8103;

    public static final int JT1078_QUERY_AV_ATTRIBUTES = 0x9003;
    public static final int JT1078_UPLOAD_AV_ATTRIBUTES = 0x1003;
    public static final int JT1078_REALTIME_REQUEST = 0x9101;
    public static final int JT1078_PASSENGER_TRAFFIC = 0x1005;
    public static final int JT1078_REALTIME_CONTROL = 0x9102;
    public static final int JT1078_REALTIME_STATUS = 0x9105;
    public static final int JT1078_QUERY_RESOURCE_LIST = 0x9205;
    public static final int JT1078_UPLOAD_RESOURCE_LIST = 0x1205;
    public static final int JT1078_PLAYBACK_REQUEST = 0x9201;
    public static final int JT1078_PLAYBACK_CONTROL = 0x9202;
    public static final int JT1078_FILE_UPLOAD_COMMAND = 0x9206;
    public static final int JT1078_FILE_UPLOAD_COMPLETION = 0x1206;
    public static final int JT1078_FILE_UPLOAD_CONTROL = 0x9207;
    public static final int JT1078_PTZ = 0x9301;
    public static final int JT1078_FOCUS = 0x9302;
    public static final int JT1078_APERTURE = 0x9303;
    public static final int JT1078_WIPER = 0x9304;
    public static final int JT1078_INFRARED = 0x9305;
    public static final int JT1078_ZOOM = 0x9306;

    private MessageIds() {
    }
}
