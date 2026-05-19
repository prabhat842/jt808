package com.example.jt808.platform.telemetry;

import com.example.jt808.platform.contracts.GpsTelemetryEvent;
import com.example.jt808.platform.shared.JsonSupport;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

class ClickHouseGpsWriter {
    private final TelemetryProperties.ClickHouse properties;
    private final WebClient webClient;
    private final DateTimeFormatter timestampFormatter = DateTimeFormatter.ISO_INSTANT;

    ClickHouseGpsWriter(TelemetryProperties.ClickHouse properties, WebClient.Builder builder) {
        this.properties = properties;
        WebClient.Builder configured = builder.baseUrl(properties.getUrl());
        if (properties.getUsername() != null && !properties.getUsername().isBlank()) {
            String token = properties.getUsername() + ":" + (properties.getPassword() == null ? "" : properties.getPassword());
            configured.defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8)));
        }
        this.webClient = configured.build();
    }

    Mono<Void> writeBatch(List<GpsTelemetryEvent> batch) {
        if (batch.isEmpty()) {
            return Mono.empty();
        }
        String body = batch.stream().map(this::jsonRow).reduce((left, right) -> left + "\n" + right).orElse("") + "\n";
        String query = "INSERT INTO `" + properties.getDatabase() + "`.`" + properties.getTable() + "` FORMAT JSONEachRow";
        return webClient.post()
                .uri(uriBuilder -> uriBuilder.path("/").queryParam("query", query).build())
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .then();
    }

    Flux<List<GpsTelemetryEvent>> batches(Flux<GpsTelemetryEvent> events) {
        return events.bufferTimeout(properties.getBatchSize(), java.time.Duration.ofMillis(properties.getFlushIntervalMs()))
                .filter(batch -> !batch.isEmpty());
    }

    private String jsonRow(GpsTelemetryEvent event) {
        return "{"
                + JsonSupport.field("vehicle_id", event.vehicleId()) + ","
                + JsonSupport.field("device_id", event.deviceId()) + ","
                + JsonSupport.field("sim", event.terminalId()) + ","
                + JsonSupport.field("protocol_id", "JT808") + ","
                + JsonSupport.numberField("protocol_type", 2) + ","
                + JsonSupport.field("gps_time", timestampFormatter.format(event.gpsTime())) + ","
                + "\"longitude\":\"" + decimal(event.longitude()) + "\","
                + "\"latitude\":\"" + decimal(event.latitude()) + "\","
                + JsonSupport.numberField("altitude", event.altitudeMeters()) + ","
                + JsonSupport.numberField("gps_speed", event.speedKph()) + ","
                + JsonSupport.numberField("direction", event.direction()) + ","
                + JsonSupport.numberField("state_bit", event.stateBit()) + ","
                + JsonSupport.numberField("warn_bit", event.warnBit()) + ","
                + JsonSupport.numberField("location_state", event.positioned() ? 1 : 0) + ","
                + JsonSupport.field("ip_address", event.remoteAddress()) + ","
                + JsonSupport.field("receive_time", timestampFormatter.format(event.receivedAt()))
                + "}";
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.8f", value);
    }
}
