package com.example.jt808.platform.gateway;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

class RedisGatewaySessionStore implements GatewaySessionStore {
    private final ReactiveStringRedisTemplate redis;
    private final Duration sessionTtl;

    RedisGatewaySessionStore(ReactiveStringRedisTemplate redis, GatewayProperties properties) {
        this.redis = redis;
        this.sessionTtl = Duration.ofSeconds(properties.getRedis().getSessionTtlSeconds());
    }

    @Override
    public Mono<Void> register(String terminalId, String remoteAddress) {
        String value = "{\"terminalId\":\"" + terminalId + "\",\"remoteAddress\":\"" + remoteAddress + "\",\"authenticated\":false,\"updatedAt\":\"" + Instant.now() + "\"}";
        return redis.opsForValue().set(key(terminalId), value, sessionTtl).then();
    }

    @Override
    public Mono<Void> authenticate(String terminalId) {
        String value = "{\"terminalId\":\"" + terminalId + "\",\"authenticated\":true,\"updatedAt\":\"" + Instant.now() + "\"}";
        return redis.opsForValue().set(key(terminalId), value, sessionTtl).then();
    }

    @Override
    public Mono<Void> heartbeat(String terminalId) {
        return redis.expire(key(terminalId), sessionTtl).then();
    }

    @Override
    public Mono<Void> remove(String terminalId) {
        return redis.delete(key(terminalId)).then();
    }

    private static String key(String terminalId) {
        return "jt808:session:" + terminalId;
    }
}
