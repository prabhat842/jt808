package com.example.jt808sim.jt1078;

import com.example.jt808sim.config.FleetConfig;

public record Jt1078MediaConfig(
        String host,
        int port,
        String streamMode,
        java.util.List<String> mediaFiles,
        int payloadBytesPerPacket,
        int packetsPerSecond,
        CaptureConfig capture,
        TalkConfig talk
) {
    public static Jt1078MediaConfig from(FleetConfig.Jt1078Settings settings) {
        return new Jt1078MediaConfig(
                settings.getHost(),
                settings.getPort(),
                settings.getStreamMode(),
                java.util.List.copyOf(settings.getMediaFiles()),
                settings.getVideoPayloadBytesPerPacket(),
                settings.getVideoPacketsPerSecond(),
                new CaptureConfig(
                        settings.getCapture().isVideoEnabled(),
                        settings.getCapture().isAudioEnabled(),
                        settings.getCapture().getVideoDevice(),
                        settings.getCapture().getAudioDevice(),
                        settings.getCapture().getVideoWidth(),
                        settings.getCapture().getVideoHeight(),
                        settings.getCapture().getVideoFps(),
                        settings.getCapture().getVideoBitrateKbps(),
                        settings.getCapture().getAudioSampleRate(),
                        settings.getCapture().getAudioChannels(),
                        settings.getCapture().getAudioBitrateKbps(),
                        settings.getCapture().getFfmpegPath()),
                new TalkConfig(
                        settings.getTalk().isEnabled(),
                        settings.getTalk().isPlayReceivedAudio(),
                        settings.getTalk().isRecordReceivedAudio(),
                        settings.getTalk().getRecordOutputDirectory(),
                        settings.getTalk().getReceiveBufferMillis()));
    }

    public Jt1078MediaConfig withEndpoint(String host, int port) {
        if (host == null || host.isBlank() || port <= 0) {
            return this;
        }
        return new Jt1078MediaConfig(host, port, streamMode, mediaFiles, payloadBytesPerPacket, packetsPerSecond, capture, talk);
    }

    public record CaptureConfig(
            boolean videoEnabled,
            boolean audioEnabled,
            String videoDevice,
            String audioDevice,
            int videoWidth,
            int videoHeight,
            int videoFps,
            int videoBitrateKbps,
            int audioSampleRate,
            int audioChannels,
            int audioBitrateKbps,
            String ffmpegPath) {
    }

    public record TalkConfig(
            boolean enabled,
            boolean playReceivedAudio,
            boolean recordReceivedAudio,
            String recordOutputDirectory,
            int receiveBufferMillis) {
    }
}
