package com.example.jt808.platform.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.ZoneId;

@ConfigurationProperties(prefix = "jt808.gateway")
public class GatewayProperties {
    private String host = "0.0.0.0";
    private int signalingPort = 7611;
    private int filePort = 7612;
    private String authCode = "server-token";
    private ZoneId protocolZone = ZoneId.of("Asia/Shanghai");
    private Kafka kafka = new Kafka();
    private Redis redis = new Redis();

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getSignalingPort() {
        return signalingPort;
    }

    public void setSignalingPort(int signalingPort) {
        this.signalingPort = signalingPort;
    }

    public int getFilePort() {
        return filePort;
    }

    public void setFilePort(int filePort) {
        this.filePort = filePort;
    }

    public String getAuthCode() {
        return authCode;
    }

    public void setAuthCode(String authCode) {
        this.authCode = authCode;
    }

    public ZoneId getProtocolZone() {
        return protocolZone;
    }

    public void setProtocolZone(ZoneId protocolZone) {
        this.protocolZone = protocolZone;
    }

    public Kafka getKafka() {
        return kafka;
    }

    public void setKafka(Kafka kafka) {
        this.kafka = kafka;
    }

    public Redis getRedis() {
        return redis;
    }

    public void setRedis(Redis redis) {
        this.redis = redis;
    }

    public static class Kafka {
        private boolean enabled;
        private String bootstrapServers = "127.0.0.1:9092";
        private String clientId = "jt808-gateway";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBootstrapServers() {
            return bootstrapServers;
        }

        public void setBootstrapServers(String bootstrapServers) {
            this.bootstrapServers = bootstrapServers;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }
    }

    public static class Redis {
        private boolean enabled;
        private long sessionTtlSeconds = 90;
        private long commandTtlSeconds = 120;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getSessionTtlSeconds() {
            return sessionTtlSeconds;
        }

        public void setSessionTtlSeconds(long sessionTtlSeconds) {
            this.sessionTtlSeconds = sessionTtlSeconds;
        }

        public long getCommandTtlSeconds() {
            return commandTtlSeconds;
        }

        public void setCommandTtlSeconds(long commandTtlSeconds) {
            this.commandTtlSeconds = commandTtlSeconds;
        }
    }
}
