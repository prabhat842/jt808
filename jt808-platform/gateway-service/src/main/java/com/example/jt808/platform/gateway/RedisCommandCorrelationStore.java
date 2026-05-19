package com.example.jt808.platform.gateway;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

import java.time.Duration;

class RedisCommandCorrelationStore implements CommandCorrelationStore {
    private final ReactiveStringRedisTemplate redis;
    private final Duration ttl;

    RedisCommandCorrelationStore(ReactiveStringRedisTemplate redis, GatewayProperties properties) {
        this.redis = redis;
        this.ttl = Duration.ofSeconds(properties.getRedis().getCommandTtlSeconds());
    }

    @Override
    public Mono<Void> put(CommandCorrelation correlation) {
        String value = correlation.terminalId() + "," + correlation.messageId() + "," + correlation.sequence() + "," + correlation.source() + "," + correlation.createdAt();
        return redis.opsForValue().set(key(correlation.commandId()), value, ttl).then();
    }

    @Override
    public Mono<CommandCorrelation> get(String commandId) {
        return redis.opsForValue().get(key(commandId)).map(value -> {
            String[] parts = value.split(",", 5);
            return new CommandCorrelation(commandId, parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), parts[3], java.time.Instant.parse(parts[4]));
        });
    }

    private static String key(String commandId) {
        return "jt808:command:" + commandId;
    }
}
