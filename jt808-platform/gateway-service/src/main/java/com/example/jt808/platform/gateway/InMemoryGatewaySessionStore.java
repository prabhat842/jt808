package com.example.jt808.platform.gateway;

import com.example.jt808.platform.protocol.TerminalRegistration;
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
            sessions.put(terminalId, new TerminalSession(
                    terminalId, remoteAddress, false, now, null, now,
                    null, 0, 0, 0, null, null));
        });
    }

    @Override
    public Mono<Void> storeIdentity(String terminalId, TerminalRegistration registration) {
        return Mono.fromRunnable(() -> sessions.computeIfPresent(terminalId,
                (k, v) -> v.withIdentity(registration.plateNumber(), registration.plateColor(),
                        registration.provinceId(), registration.cityId(),
                        registration.manufacturerId(), registration.terminalModel())));
    }

    @Override
    public Mono<Void> authenticate(String terminalId) {
        return Mono.fromRunnable(() ->
                sessions.computeIfPresent(terminalId, (k, v) -> v.authenticated(Instant.now())));
    }

    @Override
    public Mono<Void> heartbeat(String terminalId) {
        return Mono.fromRunnable(() ->
                sessions.computeIfPresent(terminalId, (k, v) -> v.heartbeat(Instant.now())));
    }

    @Override
    public Mono<Void> remove(String terminalId) {
        return Mono.fromRunnable(() -> sessions.remove(terminalId));
    }
}
