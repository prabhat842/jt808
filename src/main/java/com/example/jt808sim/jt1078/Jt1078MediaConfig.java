package com.example.jt808sim.jt1078;

import com.example.jt808sim.config.FleetConfig;

public record Jt1078MediaConfig(
        String host,
        int port,
        String streamMode,
        java.util.List<String> mediaFiles,
        int payloadBytesPerPacket,
        int packetsPerSecond
) {
    public static Jt1078MediaConfig from(FleetConfig.Jt1078Settings settings) {
        return new Jt1078MediaConfig(
                settings.getHost(),
                settings.getPort(),
                settings.getStreamMode(),
                java.util.List.copyOf(settings.getMediaFiles()),
                settings.getVideoPayloadBytesPerPacket(),
                settings.getVideoPacketsPerSecond());
    }

    public Jt1078MediaConfig withEndpoint(String host, int port) {
        if (host == null || host.isBlank() || port <= 0) {
            return this;
        }
        return new Jt1078MediaConfig(host, port, streamMode, mediaFiles, payloadBytesPerPacket, packetsPerSecond);
    }
}
