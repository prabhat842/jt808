package com.example.jt808.platform.gateway;

import com.example.jt808.platform.protocol.TerminalRegistration;
import reactor.core.publisher.Mono;

interface GatewaySessionStore {
    Mono<Void> register(String terminalId, String remoteAddress);

    /** Stores terminal plate/color/province/city from the 0x0100 registration body. */
    default Mono<Void> storeIdentity(String terminalId, TerminalRegistration registration) {
        return Mono.empty();
    }

    Mono<Void> authenticate(String terminalId);

    Mono<Void> heartbeat(String terminalId);

    Mono<Void> remove(String terminalId);
}
