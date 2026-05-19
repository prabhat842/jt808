package com.example.jt808.platform.gateway;

import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

class ActiveConnectionRegistry {
    private final ConcurrentMap<String, Sinks.Many<byte[]>> connections = new ConcurrentHashMap<>();

    void register(String terminalId, Sinks.Many<byte[]> outbound) {
        connections.put(terminalId, outbound);
    }

    void remove(String terminalId, Sinks.Many<byte[]> outbound) {
        connections.remove(terminalId, outbound);
    }

    boolean isOnline(String terminalId) {
        return connections.containsKey(terminalId);
    }

    Mono<Boolean> send(String terminalId, byte[] frame) {
        Sinks.Many<byte[]> sink = connections.get(terminalId);
        if (sink == null) {
            return Mono.just(false);
        }
        Sinks.EmitResult result = sink.tryEmitNext(frame);
        return Mono.just(result.isSuccess());
    }
}
