package com.example.jt808sim.jt1078;

import java.time.Instant;

public record Jt1078SessionRequest(
        String host,
        int port,
        int channel,
        boolean uplinkVideo,
        boolean uplinkAudio,
        boolean downlinkAudio,
        boolean playbackMode,
        Jt1078FrameType preferredFrameType,
        int playbackControlType,
        int playbackSpeed,
        long playbackStartTicks,
        long playbackEndTicks,
        Instant playbackStartTime,
        Instant playbackEndTime) {

    public static Jt1078SessionRequest defaultLive(String host, int port, int channel) {
        return new Jt1078SessionRequest(
                host, port, channel, true, false, false, false, Jt1078FrameType.VIDEO_P, 0, 1, 0, Long.MAX_VALUE, null, null);
    }

    public static Jt1078SessionRequest fromRealTimeRequest(Jt1078Command.RealTimeRequest request) {
        return switch (request.mode()) {
            case LIVE_AUDIO_VIDEO -> new Jt1078SessionRequest(
                    request.host(), request.preferredPort(), request.channel(), true, true, false, false, Jt1078FrameType.VIDEO_P, 0, 1, 0, Long.MAX_VALUE, null, null);
            case LIVE_VIDEO -> new Jt1078SessionRequest(
                    request.host(), request.preferredPort(), request.channel(), true, false, false, false, Jt1078FrameType.VIDEO_P, 0, 1, 0, Long.MAX_VALUE, null, null);
            case TALK -> new Jt1078SessionRequest(
                    request.host(), request.preferredPort(), request.channel(), false, true, true, false, Jt1078FrameType.AUDIO, 0, 1, 0, Long.MAX_VALUE, null, null);
            case LISTEN, BROADCAST -> new Jt1078SessionRequest(
                    request.host(), request.preferredPort(), request.channel(), false, false, true, false, Jt1078FrameType.AUDIO, 0, 1, 0, Long.MAX_VALUE, null, null);
            case PASSTHROUGH -> new Jt1078SessionRequest(
                    request.host(), request.preferredPort(), request.channel(), true, false, false, false, Jt1078FrameType.PASSTHROUGH, 0, 1, 0, Long.MAX_VALUE, null, null);
            case UNKNOWN -> new Jt1078SessionRequest(
                    request.host(), request.preferredPort(), request.channel(), true, false, false, false, Jt1078FrameType.VIDEO_P, 0, 1, 0, Long.MAX_VALUE, null, null);
        };
    }

    public static Jt1078SessionRequest fromPlaybackRequest(Jt1078Command.PlaybackRequest request) {
        boolean audioOnly = request.audioVideoType() == 0;
        return new Jt1078SessionRequest(
                request.host(),
                request.preferredPort(),
                request.channel(),
                !audioOnly,
                audioOnly,
                false,
                true,
                audioOnly ? Jt1078FrameType.AUDIO : Jt1078FrameType.VIDEO_P,
                request.playbackMode(),
                request.playbackSpeed(),
                0,
                Long.MAX_VALUE,
                null,
                null);
    }

    public boolean hasUplinkMedia() {
        return uplinkVideo || uplinkAudio;
    }

    public Jt1078SessionRequest withPlaybackWindow(long startTicks, long endTicks, Instant startTime, Instant endTime) {
        return new Jt1078SessionRequest(
                host,
                port,
                channel,
                uplinkVideo,
                uplinkAudio,
                downlinkAudio,
                playbackMode,
                preferredFrameType,
                playbackControlType,
                playbackSpeed,
                startTicks,
                endTicks,
                startTime,
                endTime);
    }
}
