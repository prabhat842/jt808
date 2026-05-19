package com.example.jt808.platform.media;

import com.example.jt808.platform.contracts.CommandDispatchEvent;
import com.example.jt808.platform.contracts.CommandResponseEvent;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
class RtvsCompatibilityStore {
    private final MediaProperties properties;
    private final org.springframework.beans.factory.ObjectProvider<ReactiveStringRedisTemplate> redisProvider;

    RtvsCompatibilityStore(MediaProperties properties, org.springframework.beans.factory.ObjectProvider<ReactiveStringRedisTemplate> redisProvider) {
        this.properties = properties;
        this.redisProvider = redisProvider;
    }

    Mono<Void> recordDispatch(CommandDispatchEvent event) {
        ReactiveStringRedisTemplate redis = redisProvider.getIfAvailable();
        if (!properties.getRedis().isEnabled() || redis == null) {
            return Mono.empty();
        }
        String value = "{\"commandId\":\"" + event.commandId() + "\",\"terminalId\":\"" + event.terminalId() + "\",\"messageId\":" + event.messageId() + ",\"sequence\":" + event.sequence() + "}";
        return redis.opsForValue()
                .set("OCX_ORDERINFO_" + event.commandId(), value, Duration.ofSeconds(properties.getRedis().getCorrelationTtlSeconds()))
                .then();
    }

    Mono<Void> recordResponse(CommandResponseEvent event) {
        ReactiveStringRedisTemplate redis = redisProvider.getIfAvailable();
        if (!properties.getRedis().isEnabled() || redis == null) {
            return Mono.empty();
        }
        String value = "{\"terminalId\":\"" + event.terminalId() + "\",\"responseSequence\":" + event.responseSequence() + ",\"responseMessageId\":" + event.responseMessageId() + ",\"result\":" + event.result() + "}";
        return redis.opsForValue()
                .set("JT808_RESPONSE_" + event.terminalId() + "_" + event.responseSequence(), value, Duration.ofSeconds(properties.getRedis().getCorrelationTtlSeconds()))
                .then();
    }
}
