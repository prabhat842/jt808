package com.example.jt808.platform.alarm;

import com.example.jt808.platform.contracts.AlarmEvent;
import com.example.jt808.platform.contracts.AttachmentEvent;
import com.example.jt808.platform.shared.JsonSupport;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

@Component
class AlarmClickHouseWriter {
    private final AlarmProperties.ClickHouse properties;
    private final WebClient webClient;
    private final DateTimeFormatter time = DateTimeFormatter.ISO_INSTANT;

    AlarmClickHouseWriter(AlarmProperties properties, WebClient.Builder builder) {
        this.properties = properties.getClickHouse();
        WebClient.Builder configured = builder.baseUrl(this.properties.getUrl());
        if (this.properties.getUsername() != null && !this.properties.getUsername().isBlank()) {
            String token = this.properties.getUsername() + ":" + (this.properties.getPassword() == null ? "" : this.properties.getPassword());
            configured.defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8)));
        }
        this.webClient = configured.build();
    }

    Mono<Void> writeAlarms(List<AlarmEvent> alarms) {
        if (alarms.isEmpty()) {
            return Mono.empty();
        }
        return insert(properties.getAlarmTable(), alarms.stream().map(this::alarmRow).toList());
    }

    Mono<Void> writeAttachments(List<AttachmentEvent> attachments) {
        if (attachments.isEmpty()) {
            return Mono.empty();
        }
        return insert(properties.getAttachmentTable(), attachments.stream().map(this::attachmentRow).toList());
    }

    private Mono<Void> insert(String table, List<String> rows) {
        String body = String.join("\n", rows) + "\n";
        String query = "INSERT INTO `" + properties.getDatabase() + "`.`" + table + "` FORMAT JSONEachRow";
        return webClient.post()
                .uri(uriBuilder -> uriBuilder.path("/").queryParam("query", query).build())
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .then();
    }

    private String alarmRow(AlarmEvent event) {
        return "{"
                + JsonSupport.field("vehicle_id", event.vehicleId()) + ","
                + JsonSupport.field("device_id", event.terminalId()) + ","
                + JsonSupport.field("sim", event.terminalId()) + ","
                + JsonSupport.field("alarm_id", event.alarmId()) + ","
                + JsonSupport.field("jt_alarm_id", event.alarmId()) + ","
                + JsonSupport.field("gps_time", time.format(event.alarmTime())) + ","
                + JsonSupport.field("alarm_start_time", time.format(event.alarmTime())) + ","
                + JsonSupport.numberField("alarm_type", event.alarmType()) + ","
                + JsonSupport.field("alarm_name", event.alarmName()) + ","
                + JsonSupport.numberField("alarm_level", event.alarmLevel()) + ","
                + JsonSupport.numberField("warn_bit", event.warnBit()) + ","
                + JsonSupport.numberField("speed", event.speedKph()) + ","
                + "\"longitude\":\"" + decimal(event.longitude()) + "\","
                + "\"latitude\":\"" + decimal(event.latitude()) + "\","
                + JsonSupport.numberField("cleared", event.cleared() ? 1 : 0) + ","
                + JsonSupport.field("receive_time", time.format(event.receivedAt()))
                + "}";
    }

    private String attachmentRow(AttachmentEvent event) {
        return "{"
                + JsonSupport.field("vehicle_id", event.vehicleId()) + ","
                + JsonSupport.field("device_id", event.terminalId()) + ","
                + JsonSupport.field("sim", event.terminalId()) + ","
                + JsonSupport.field("alarm_id", event.alarmId()) + ","
                + JsonSupport.field("jt_alarm_id", event.alarmId()) + ","
                + JsonSupport.numberField("alarm_type", event.alarmType()) + ","
                + JsonSupport.field("alarm_start_time", time.format(event.uploadTime())) + ","
                + JsonSupport.numberField("channel", event.channel()) + ","
                + JsonSupport.numberField("format", event.format()) + ","
                + JsonSupport.numberField("index_num", event.indexNum()) + ","
                + JsonSupport.field("file_name", event.fileName()) + ","
                + JsonSupport.numberField("size", event.size()) + ","
                + JsonSupport.field("url", event.url()) + ","
                + JsonSupport.field("upload_time", time.format(event.uploadTime())) + ","
                + JsonSupport.field("receive_time", time.format(event.receivedAt()))
                + "}";
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.8f", value);
    }
}
