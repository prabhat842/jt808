package com.example.jt808sim.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class FleetConfig {
    private ServerConfig server = new ServerConfig();
    private FleetSettings fleet = new FleetSettings();
    private Jt1078Settings jt1078 = new Jt1078Settings();
    private DmsConfig dms = new DmsConfig();
    private List<VehicleIdentity> vehicles = new ArrayList<>();

    public static FleetConfig load(Path path) throws IOException {
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        FleetConfig config = mapper.readValue(path.toFile(), FleetConfig.class);
        config.validate();
        return config;
    }

    public void validate() {
        if (server.host == null || server.host.isBlank()) {
            throw new IllegalArgumentException("server.host is required");
        }
        if (server.port <= 0 || server.port > 65535) {
            throw new IllegalArgumentException("server.port must be in 1..65535");
        }
        if (fleet.connectionCount <= 0) {
            throw new IllegalArgumentException("fleet.connectionCount must be positive");
        }
        if (vehicles.isEmpty()) {
            throw new IllegalArgumentException("vehicles must contain at least one identity");
        }
        validateJt1078();
        vehicles.forEach(VehicleIdentity::validate);
    }

    private void validateJt1078() {
        if (jt1078.host == null || jt1078.host.isBlank()) {
            throw new IllegalArgumentException("jt1078.host is required");
        }
        if (jt1078.port <= 0 || jt1078.port > 65535) {
            throw new IllegalArgumentException("jt1078.port must be in 1..65535");
        }
        if ("camera".equalsIgnoreCase(jt1078.streamMode)) {
            if (!jt1078.capture.videoEnabled && !jt1078.capture.audioEnabled) {
                throw new IllegalArgumentException("jt1078.capture must enable video and/or audio in camera mode");
            }
            if (jt1078.capture.ffmpegPath == null || jt1078.capture.ffmpegPath.isBlank()) {
                throw new IllegalArgumentException("jt1078.capture.ffmpegPath is required in camera mode");
            }
            if (jt1078.capture.videoEnabled && (jt1078.capture.videoDevice == null || jt1078.capture.videoDevice.isBlank())) {
                throw new IllegalArgumentException("jt1078.capture.videoDevice is required when camera video is enabled");
            }
            if (jt1078.capture.audioEnabled && (jt1078.capture.audioDevice == null || jt1078.capture.audioDevice.isBlank())) {
                throw new IllegalArgumentException("jt1078.capture.audioDevice is required when camera audio is enabled");
            }
        }
    }

    public ServerConfig getServer() {
        return server;
    }

    public void setServer(ServerConfig server) {
        this.server = server;
    }

    public FleetSettings getFleet() {
        return fleet;
    }

    public void setFleet(FleetSettings fleet) {
        this.fleet = fleet;
    }

    public Jt1078Settings getJt1078() {
        return jt1078;
    }

    public void setJt1078(Jt1078Settings jt1078) {
        this.jt1078 = jt1078;
    }

    public DmsConfig getDms() {
        return dms;
    }

    public void setDms(DmsConfig dms) {
        this.dms = dms;
    }

    public List<VehicleIdentity> getVehicles() {
        return vehicles;
    }

    public void setVehicles(List<VehicleIdentity> vehicles) {
        this.vehicles = vehicles;
    }

    public static class ServerConfig {
        private String host = "127.0.0.1";
        private int port = 7611;

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }
    }

    public static class FleetSettings {
        private int connectionCount = 1;
        private long connectStaggerMs = 10;
        private long locationIntervalSeconds = 5;
        private long heartbeatIntervalSeconds = 30;
        private long ackTimeoutSeconds = 15;
        private RouteMode routeMode = RouteMode.REVERSE;

        public int getConnectionCount() {
            return connectionCount;
        }

        public void setConnectionCount(int connectionCount) {
            this.connectionCount = connectionCount;
        }

        public long getConnectStaggerMs() {
            return connectStaggerMs;
        }

        public void setConnectStaggerMs(long connectStaggerMs) {
            this.connectStaggerMs = connectStaggerMs;
        }

        public long getLocationIntervalSeconds() {
            return locationIntervalSeconds;
        }

        public void setLocationIntervalSeconds(long locationIntervalSeconds) {
            this.locationIntervalSeconds = locationIntervalSeconds;
        }

        public long getHeartbeatIntervalSeconds() {
            return heartbeatIntervalSeconds;
        }

        public void setHeartbeatIntervalSeconds(long heartbeatIntervalSeconds) {
            this.heartbeatIntervalSeconds = heartbeatIntervalSeconds;
        }

        public long getAckTimeoutSeconds() {
            return ackTimeoutSeconds;
        }

        public void setAckTimeoutSeconds(long ackTimeoutSeconds) {
            this.ackTimeoutSeconds = ackTimeoutSeconds;
        }

        public RouteMode getRouteMode() {
            return routeMode;
        }

        public void setRouteMode(RouteMode routeMode) {
            this.routeMode = routeMode;
        }
    }

    public static class Jt1078Settings {
        private int mediaCapableTerminalCount = 0;
        private String host = "127.0.0.1";
        private int port = 1078;
        private String streamMode = "synthetic";
        private List<String> mediaFiles = new ArrayList<>();
        private int videoPayloadBytesPerPacket = 950;
        private int videoPacketsPerSecond = 25;
        private CaptureSettings capture = new CaptureSettings();
        private TalkSettings talk = new TalkSettings();

        public int getMediaCapableTerminalCount() {
            return mediaCapableTerminalCount;
        }

        public void setMediaCapableTerminalCount(int mediaCapableTerminalCount) {
            this.mediaCapableTerminalCount = mediaCapableTerminalCount;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getStreamMode() {
            return streamMode;
        }

        public void setStreamMode(String streamMode) {
            this.streamMode = streamMode;
        }

        public List<String> getMediaFiles() {
            return mediaFiles;
        }

        public void setMediaFiles(List<String> mediaFiles) {
            this.mediaFiles = mediaFiles == null ? new ArrayList<>() : mediaFiles;
        }

        public int getVideoPayloadBytesPerPacket() {
            return videoPayloadBytesPerPacket;
        }

        public void setVideoPayloadBytesPerPacket(int videoPayloadBytesPerPacket) {
            this.videoPayloadBytesPerPacket = videoPayloadBytesPerPacket;
        }

        public int getVideoPacketsPerSecond() {
            return videoPacketsPerSecond;
        }

        public void setVideoPacketsPerSecond(int videoPacketsPerSecond) {
            this.videoPacketsPerSecond = videoPacketsPerSecond;
        }

        public CaptureSettings getCapture() {
            return capture;
        }

        public void setCapture(CaptureSettings capture) {
            this.capture = capture == null ? new CaptureSettings() : capture;
        }

        public TalkSettings getTalk() {
            return talk;
        }

        public void setTalk(TalkSettings talk) {
            this.talk = talk == null ? new TalkSettings() : talk;
        }
    }

    public static class CaptureSettings {
        private boolean videoEnabled = true;
        private boolean audioEnabled;
        private String videoDevice = "/dev/video0";
        private String audioDevice = "default";
        private int videoWidth = 1280;
        private int videoHeight = 720;
        private int videoFps = 25;
        private int videoBitrateKbps = 1200;
        private int audioSampleRate = 8000;
        private int audioChannels = 1;
        private int audioBitrateKbps = 32;
        private String ffmpegPath = "ffmpeg";

        public boolean isVideoEnabled() {
            return videoEnabled;
        }

        public void setVideoEnabled(boolean videoEnabled) {
            this.videoEnabled = videoEnabled;
        }

        public boolean isAudioEnabled() {
            return audioEnabled;
        }

        public void setAudioEnabled(boolean audioEnabled) {
            this.audioEnabled = audioEnabled;
        }

        public String getVideoDevice() {
            return videoDevice;
        }

        public void setVideoDevice(String videoDevice) {
            this.videoDevice = videoDevice;
        }

        public String getAudioDevice() {
            return audioDevice;
        }

        public void setAudioDevice(String audioDevice) {
            this.audioDevice = audioDevice;
        }

        public int getVideoWidth() {
            return videoWidth;
        }

        public void setVideoWidth(int videoWidth) {
            this.videoWidth = videoWidth;
        }

        public int getVideoHeight() {
            return videoHeight;
        }

        public void setVideoHeight(int videoHeight) {
            this.videoHeight = videoHeight;
        }

        public int getVideoFps() {
            return videoFps;
        }

        public void setVideoFps(int videoFps) {
            this.videoFps = videoFps;
        }

        public int getVideoBitrateKbps() {
            return videoBitrateKbps;
        }

        public void setVideoBitrateKbps(int videoBitrateKbps) {
            this.videoBitrateKbps = videoBitrateKbps;
        }

        public int getAudioSampleRate() {
            return audioSampleRate;
        }

        public void setAudioSampleRate(int audioSampleRate) {
            this.audioSampleRate = audioSampleRate;
        }

        public int getAudioChannels() {
            return audioChannels;
        }

        public void setAudioChannels(int audioChannels) {
            this.audioChannels = audioChannels;
        }

        public int getAudioBitrateKbps() {
            return audioBitrateKbps;
        }

        public void setAudioBitrateKbps(int audioBitrateKbps) {
            this.audioBitrateKbps = audioBitrateKbps;
        }

        public String getFfmpegPath() {
            return ffmpegPath;
        }

        public void setFfmpegPath(String ffmpegPath) {
            this.ffmpegPath = ffmpegPath;
        }
    }

    public static class TalkSettings {
        private boolean enabled;
        private boolean playReceivedAudio;
        private boolean recordReceivedAudio;
        private String recordOutputDirectory = "tmp/jt1078-downlink";
        private int receiveBufferMillis = 200;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isPlayReceivedAudio() {
            return playReceivedAudio;
        }

        public void setPlayReceivedAudio(boolean playReceivedAudio) {
            this.playReceivedAudio = playReceivedAudio;
        }

        public boolean isRecordReceivedAudio() {
            return recordReceivedAudio;
        }

        public void setRecordReceivedAudio(boolean recordReceivedAudio) {
            this.recordReceivedAudio = recordReceivedAudio;
        }

        public String getRecordOutputDirectory() {
            return recordOutputDirectory;
        }

        public void setRecordOutputDirectory(String recordOutputDirectory) {
            this.recordOutputDirectory = recordOutputDirectory;
        }

        public int getReceiveBufferMillis() {
            return receiveBufferMillis;
        }

        public void setReceiveBufferMillis(int receiveBufferMillis) {
            this.receiveBufferMillis = receiveBufferMillis;
        }
    }

    public enum RouteMode {
        STOP,
        LOOP,
        REVERSE
    }

    public static class DmsConfig {
        private boolean enabled        = false;
        private String  sidecarUrl     = "http://127.0.0.1:7500";
        private long    pollIntervalMs = 500;
        private String  sidecarScript  = null; // auto-launch path, e.g. "dms-sidecar/dms_server.py"

        public boolean isEnabled()          { return enabled; }
        public void    setEnabled(boolean enabled)         { this.enabled = enabled; }
        public String  getSidecarUrl()      { return sidecarUrl; }
        public void    setSidecarUrl(String sidecarUrl)    { this.sidecarUrl = sidecarUrl; }
        public long    getPollIntervalMs()  { return pollIntervalMs; }
        public void    setPollIntervalMs(long pollIntervalMs) { this.pollIntervalMs = pollIntervalMs; }
        public String  getSidecarScript()   { return sidecarScript; }
        public void    setSidecarScript(String sidecarScript) { this.sidecarScript = sidecarScript; }
    }
}
