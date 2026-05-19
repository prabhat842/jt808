package com.example.jt808.platform.telemetry;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
class TelemetryConfiguration {
    @Bean
    ClickHouseGpsWriter clickHouseGpsWriter(TelemetryProperties properties, WebClient.Builder builder) {
        return new ClickHouseGpsWriter(properties.getClickHouse(), builder);
    }
}
