package com.example.jt808sim.protocol;

public final class MessageIds {
    // Terminal → Platform (uplink)
    public static final int TERMINAL_GENERAL_RESPONSE  = 0x0001;
    public static final int HEARTBEAT                  = 0x0002;
    public static final int TERMINAL_LOGOUT            = 0x0003;
    public static final int TERMINAL_PARAM_QUERY_RESP  = 0x0104;
    public static final int TERMINAL_ATTR_RESP         = 0x0107;
    public static final int TERMINAL_UPDATE_RESULT     = 0x0108;
    public static final int TERMINAL_REGISTER          = 0x0100;
    public static final int TERMINAL_AUTH              = 0x0102;
    public static final int LOCATION_REPORT            = 0x0200;
    public static final int LOCATION_QUERY_RESP        = 0x0201;

    // Platform → Terminal (downlink)
    public static final int SERVER_ACK                 = 0x8001;
    public static final int RESEND_SUBPACKAGE          = 0x8003;
    public static final int REGISTER_RESPONSE          = 0x8100;
    public static final int TERMINAL_PARAM_SETTING     = 0x8103;
    public static final int TERMINAL_PARAM_QUERY_ALL   = 0x8104;
    public static final int TERMINAL_CONTROL           = 0x8105;
    public static final int TERMINAL_PARAM_QUERY_SPEC  = 0x8106;
    public static final int TERMINAL_ATTR_QUERY        = 0x8107;
    public static final int TERMINAL_UPDATE            = 0x8108;
    public static final int LOCATION_QUERY             = 0x8201;
    public static final int TEMP_LOCATION_TRACKING     = 0x8202;
    public static final int MANUAL_ALARM_CONFIRM       = 0x8203;
    // Information protocol — terminal → platform (§8.25, 8.27, 8.29)
    public static final int EVENT_REPORT            = 0x0301;
    public static final int QUESTION_RESPONSE       = 0x0302;
    public static final int INFO_ON_DEMAND          = 0x0303;
    // Multimedia — terminal → platform (§8.51-8.57)
    public static final int MULTIMEDIA_EVENT        = 0x0800;
    public static final int MULTIMEDIA_DATA_UPLOAD  = 0x0801;
    public static final int STORE_MEDIA_RETRIEVE_RESP = 0x0802;
    public static final int CAMERA_SNAPSHOT_RESP    = 0x0805;

    // Information protocol — platform → terminal (§8.23-8.30)
    public static final int TEXT_INFO               = 0x8300;
    public static final int EVENT_SETTING           = 0x8301;
    public static final int QUESTION_SEND           = 0x8302;
    public static final int INFO_ON_DEMAND_MENU     = 0x8303;
    public static final int INFO_SERVICE            = 0x8304;
    // Telephone protocol (§8.31-8.32)
    public static final int CALLBACK                = 0x8400;
    public static final int PHONE_BOOK_SETTING      = 0x8401;
    // Multimedia — platform → terminal (§8.53-8.60)
    public static final int MULTIMEDIA_UPLOAD_ACK   = 0x8800;
    public static final int CAMERA_SNAPSHOT_CMD     = 0x8801;
    public static final int STORE_MEDIA_QUERY       = 0x8802;
    public static final int STORE_MEDIA_UPLOAD_CMD  = 0x8803;
    public static final int SOUND_RECORD_CMD        = 0x8804;
    public static final int SINGLE_MEDIA_UPLOAD_CMD = 0x8805;

    // Tachograph & driver identity (§8.43-8.50)
    public static final int TACHOGRAPH_DATA_UPLOAD  = 0x0700;
    public static final int DRIVER_IC_CARD_DATA     = 0x0701;
    public static final int DRIVER_IDENTITY_REPORT  = 0x0702;
    public static final int BULK_LOCATION_UPLOAD    = 0x0704;
    public static final int ACCIDENT_SUSPECT_REPORT = 0x0705;
    public static final int TACHOGRAPH_CMD          = 0x8700; // platform → terminal
    public static final int DRIVER_IDENTITY_ACK     = 0x8702; // platform → terminal

    // Vehicle control (§8.33-8.34)
    public static final int VEHICLE_CONTROL            = 0x8500;
    public static final int VEHICLE_CONTROL_RESP       = 0x0500;
    // Vehicle management / geofencing
    public static final int SET_CIRCLE_AREA            = 0x8600;
    public static final int DELETE_CIRCLE_AREA         = 0x8601;
    public static final int SET_RECTANGLE_AREA         = 0x8602;
    public static final int DELETE_RECTANGLE_AREA      = 0x8603;
    public static final int SET_POLYGON_AREA           = 0x8604;
    public static final int DELETE_POLYGON_AREA        = 0x8605;
    public static final int SET_ROUTE                  = 0x8606;
    public static final int DELETE_ROUTE               = 0x8607;

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
