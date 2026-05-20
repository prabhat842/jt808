package com.example.jt808.platform.gateway;

import com.example.jt808.platform.protocol.TerminalRegistration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

class RedisGatewaySessionStore implements GatewaySessionStore {
    private final ReactiveStringRedisTemplate redis;
    private final Duration sessionTtl;

    RedisGatewaySessionStore(ReactiveStringRedisTemplate redis, GatewayProperties properties) {
        this.redis = redis;
        this.sessionTtl = Duration.ofSeconds(properties.getRedis().getSessionTtlSeconds());
    }

    @Override
    public Mono<Void> register(String terminalId, String remoteAddress) {
        String key = key(terminalId);
        return redis.opsForHash().putAll(key, Map.of(
                        "terminalId", terminalId,
                        "remoteAddress", remoteAddress,
                        "authenticated", "false",
                        "registeredAt", Instant.now().toString(),
                        "updatedAt", Instant.now().toString()))
                .then(redis.expire(key, sessionTtl))
                .then();
    }

    @Override
    public Mono<Void> storeIdentity(String terminalId, TerminalRegistration registration) {
        return redis.opsForHash().putAll(key(terminalId), Map.of(
                        "plateNumber", registration.plateNumber(),
                        "plateColor", String.valueOf(registration.plateColor()),
                        "provinceId", String.valueOf(registration.provinceId()),
                        "cityId", String.valueOf(registration.cityId()),
                        "manufacturerId", registration.manufacturerId(),
                        "terminalModel", registration.terminalModel()))
                .then();
    }

    @Override
    public Mono<Void> authenticate(String terminalId) {
        return redis.opsForHash().putAll(key(terminalId), Map.of(
                        "authenticated", "true",
                        "updatedAt", Instant.now().toString()))
                .then();
    }

    @Override
    public Mono<Void> heartbeat(String terminalId) {
        String key = key(terminalId);
        return redis.opsForHash().put(key, "lastHeartbeatAt", Instant.now().toString())
                .then(redis.expire(key, sessionTtl))
                .then();
    }

    @Override
    public Mono<Void> remove(String terminalId) {
        return redis.delete(key(terminalId)).then();
    }

    static String key(String terminalId) {
        return "jt808:session:" + terminalId;
    }
}
