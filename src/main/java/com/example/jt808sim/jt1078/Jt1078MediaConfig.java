package com.example.jt808sim.jt1078;

import com.example.jt808sim.config.FleetConfig;

public record Jt1078MediaConfig(
        String host,
        int port,
        int payloadBytesPerPacket,
        int packetsPerSecond
) {
    public static Jt1078MediaConfig from(FleetConfig.Jt1078Settings settings) {
        return new Jt1078MediaConfig(
                settings.getHost(),
                settings.getPort(),
                settings.getVideoPayloadBytesPerPacket(),
                settings.getVideoPacketsPerSecond());
    }
}
