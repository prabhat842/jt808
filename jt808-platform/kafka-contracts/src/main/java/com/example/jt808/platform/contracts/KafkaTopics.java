package com.example.jt808.platform.contracts;

public final class KafkaTopics {
    public static final String TELEMETRY_GPS = "telemetry.gps";
    public static final String TELEMETRY_ALARM = "telemetry.alarm";
    public static final String TELEMETRY_ATTACHMENT = "telemetry.attachment";
    public static final String TELEMETRY_HEARTBEAT = "telemetry.heartbeat";
    public static final String JT808_COMMAND = "jt808.command";
    public static final String MEDIA_SIGNAL = "media.signal";
    public static final String MEDIA_RESPONSE = "media.response";
    public static final String AI_ALERT = "ai.alert";

    private KafkaTopics() {
    }
}
