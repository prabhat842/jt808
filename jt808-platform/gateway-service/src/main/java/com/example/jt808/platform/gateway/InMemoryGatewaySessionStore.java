package com.example.jt808.platform.gateway;

import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

class InMemoryGatewaySessionStore implements GatewaySessionStore {
    private final ConcurrentMap<String, TerminalSession> sessions = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> register(String terminalId, String remoteAddress) {
        return Mono.fromRunnable(() -> {
            Instant now = Instant.now();
            sessions.put(terminalId, new TerminalSession(terminalId, remoteAddress, false, now, null, now));
        });
    }

    @Override
    public Mono<Void> authenticate(String terminalId) {
        return Mono.fromRunnable(() -> sessions.computeIfPresent(terminalId, (key, value) -> value.authenticated(Instant.now())));
    }

    @Override
    public Mono<Void> heartbeat(String terminalId) {
        return Mono.fromRunnable(() -> sessions.computeIfPresent(terminalId, (key, value) -> value.heartbeat(Instant.now())));
    }

    @Override
    public Mono<Void> remove(String terminalId) {
        return Mono.fromRunnable(() -> sessions.remove(terminalId));
    }
}
