package com.example.jt808.platform.media;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jt808.media")
public class MediaProperties {
    private Kafka kafka = new Kafka();
    private Redis redis = new Redis();

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
        private String groupId = "media-service";

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

        public String getGroupId() {
            return groupId;
        }

        public void setGroupId(String groupId) {
            this.groupId = groupId;
        }
    }

    public static class Redis {
        private boolean enabled;
        private long correlationTtlSeconds = 300;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getCorrelationTtlSeconds() {
            return correlationTtlSeconds;
        }

        public void setCorrelationTtlSeconds(long correlationTtlSeconds) {
            this.correlationTtlSeconds = correlationTtlSeconds;
        }
    }
}
