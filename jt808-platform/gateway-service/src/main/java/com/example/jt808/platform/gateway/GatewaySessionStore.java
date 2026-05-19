package com.example.jt808.platform.gateway;

import reactor.core.publisher.Mono;

interface GatewaySessionStore {
    Mono<Void> register(String terminalId, String remoteAddress);

    Mono<Void> authenticate(String terminalId);

    Mono<Void> heartbeat(String terminalId);

    Mono<Void> remove(String terminalId);
}
