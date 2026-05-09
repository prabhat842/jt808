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
        vehicles.forEach(VehicleIdentity::validate);
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
        private int videoPayloadBytesPerPacket = 950;
        private int videoPacketsPerSecond = 25;

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
    }

    public enum RouteMode {
        STOP,
        LOOP,
        REVERSE
    }
}
