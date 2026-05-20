package com.example.jt808.platform.admin;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
class AdminController {
    private final AdminProperties properties;
    private final ObjectProvider<ReactiveStringRedisTemplate> redisProvider;

    AdminController(AdminProperties properties,
                    ObjectProvider<ReactiveStringRedisTemplate> redisProvider) {
        this.properties = properties;
        this.redisProvider = redisProvider;
    }

    @GetMapping("/admin/health/topology")
    Mono<Map<String, Object>> topology() {
        return Mono.just(Map.of(
                "gateway", "external service",
                "kafka", "event bus",
                "redisEnabled", properties.isRedisEnabled(),
                "clickHouse", "writer services"));
    }

    @GetMapping("/admin/sessions/{terminalId}")
    Mono<Map<String, Object>> session(@PathVariable String terminalId) {
        ReactiveStringRedisTemplate redis = redisProvider.getIfAvailable();
        if (!properties.isRedisEnabled() || redis == null) {
            return Mono.just(Map.of("terminalId", terminalId, "available", false, "reason", "redis disabled"));
        }
        // Session is stored as a Redis Hash (HSET fields) — collect all field→value pairs
        return redis.<String, String>opsForHash()
                .entries("jt808:session:" + terminalId)
                .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                .map(fields -> fields.isEmpty()
                        ? Map.<String, Object>of("terminalId", terminalId, "available", false)
                        : Map.<String, Object>of("terminalId", terminalId, "available", true, "session", fields));
    }
}
