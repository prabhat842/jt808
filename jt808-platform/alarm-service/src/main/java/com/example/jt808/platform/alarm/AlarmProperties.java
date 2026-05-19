package com.example.jt808.platform.alarm;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jt808.alarm")
public class AlarmProperties {
    private Kafka kafka = new Kafka();
    private ClickHouse clickHouse = new ClickHouse();

    public Kafka getKafka() {
        return kafka;
    }

    public void setKafka(Kafka kafka) {
        this.kafka = kafka;
    }

    public ClickHouse getClickHouse() {
        return clickHouse;
    }

    public void setClickHouse(ClickHouse clickHouse) {
        this.clickHouse = clickHouse;
    }

    public static class Kafka {
        private boolean enabled;
        private String bootstrapServers = "127.0.0.1:9092";
        private String groupId = "alarm-service";

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

    public static class ClickHouse {
        private String url = "http://127.0.0.1:8123/";
        private String database = "jt808";
        private String alarmTable = "vehicle_alarm";
        private String attachmentTable = "vehicle_alarm_file";
        private String username = "default";
        private String password = "";
        private int batchSize = 500;
        private int flushIntervalMs = 1000;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getDatabase() {
            return database;
        }

        public void setDatabase(String database) {
            this.database = database;
        }

        public String getAlarmTable() {
            return alarmTable;
        }

        public void setAlarmTable(String alarmTable) {
            this.alarmTable = alarmTable;
        }

        public String getAttachmentTable() {
            return attachmentTable;
        }

        public void setAttachmentTable(String attachmentTable) {
            this.attachmentTable = attachmentTable;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public int getFlushIntervalMs() {
            return flushIntervalMs;
        }

        public void setFlushIntervalMs(int flushIntervalMs) {
            this.flushIntervalMs = flushIntervalMs;
        }
    }
}
