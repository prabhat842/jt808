package com.example.jt808.platform.gateway;

import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

class ActiveConnectionRegistry {
    private final ConcurrentMap<String, Sinks.Many<byte[]>> connections = new ConcurrentHashMap<>();
    // Reverse map: sink identity → terminalId, used to resolve disconnect events
    private final ConcurrentMap<Sinks.Many<byte[]>, String> sinkToTerminal = new ConcurrentHashMap<>();

    void register(String terminalId, Sinks.Many<byte[]> outbound) {
        connections.put(terminalId, outbound);
        sinkToTerminal.put(outbound, terminalId);
    }

    void remove(String terminalId, Sinks.Many<byte[]> outbound) {
        connections.remove(terminalId, outbound);
        sinkToTerminal.remove(outbound);
    }

    /** Resolves the terminalId for a disconnecting sink, then removes it. Returns null if unknown. */
    String resolveAndRemove(Sinks.Many<byte[]> outbound) {
        String terminalId = sinkToTerminal.remove(outbound);
        if (terminalId != null) {
            connections.remove(terminalId, outbound);
        }
        return terminalId;
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
