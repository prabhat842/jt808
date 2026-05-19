package com.example.jt808.platform.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

class LoggingEventPublisher implements EventPublisher {
    private static final Logger log = LoggerFactory.getLogger(LoggingEventPublisher.class);

    private final ObjectMapper objectMapper;

    LoggingEventPublisher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> publish(String topic, String key, Object event) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(event))
                .subscribeOn(Schedulers.parallel())
                .doOnNext(payload -> log.debug("kafka disabled; would publish topic={} key={} payload={}", topic, key, payload))
                .then();
    }
}
