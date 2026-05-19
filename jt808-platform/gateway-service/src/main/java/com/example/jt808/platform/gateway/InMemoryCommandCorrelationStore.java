package com.example.jt808.platform.gateway;

import reactor.core.publisher.Mono;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

class InMemoryCommandCorrelationStore implements CommandCorrelationStore {
    private final ConcurrentMap<String, CommandCorrelation> correlations = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> put(CommandCorrelation correlation) {
        return Mono.fromRunnable(() -> correlations.put(correlation.commandId(), correlation));
    }

    @Override
    public Mono<CommandCorrelation> get(String commandId) {
        return Mono.justOrEmpty(correlations.get(commandId));
    }
}
