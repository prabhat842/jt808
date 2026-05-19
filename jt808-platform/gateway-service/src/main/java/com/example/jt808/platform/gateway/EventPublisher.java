package com.example.jt808.platform.gateway;

import reactor.core.publisher.Mono;

interface EventPublisher {
    Mono<Void> publish(String topic, String key, Object event);
}
