package com.example.jt808.platform.gateway;

import reactor.core.publisher.Mono;

interface CommandCorrelationStore {
    Mono<Void> put(CommandCorrelation correlation);

    Mono<CommandCorrelation> get(String commandId);
}
