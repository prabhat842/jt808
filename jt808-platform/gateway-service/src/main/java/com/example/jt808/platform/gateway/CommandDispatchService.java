package com.example.jt808.platform.gateway;

import com.example.jt808.platform.contracts.CommandDispatchEvent;
import com.example.jt808.platform.contracts.KafkaTopics;
import com.example.jt808.platform.protocol.Jt808FrameCodec;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
class CommandDispatchService {
    private final Jt808FrameCodec codec;
    private final ActiveConnectionRegistry connections;
    private final CommandCorrelationStore correlations;
    private final EventPublisher events;
    private final SequenceGenerator sequenceGenerator = new SequenceGenerator();

    CommandDispatchService(Jt808FrameCodec codec, ActiveConnectionRegistry connections, CommandCorrelationStore correlations, EventPublisher events) {
        this.codec = codec;
        this.connections = connections;
        this.correlations = correlations;
        this.events = events;
    }

    Mono<CommandDispatchResult> dispatchRtvsHex(String contentHex) {
        byte[] content;
        try {
            content = HexFormat.of().parseHex(contentHex.replaceAll("\\s+", ""));
        } catch (IllegalArgumentException e) {
            return Mono.just(CommandDispatchResult.failure("-1", "invalid hex content"));
        }
        Jt808FrameCodec.ParsedCommand command;
        try {
            command = codec.parseHeaderBody(content);
        } catch (RuntimeException e) {
            return Mono.just(CommandDispatchResult.failure("-1", e.getMessage()));
        }
        if (!connections.isOnline(command.terminalId())) {
            return Mono.just(CommandDispatchResult.failure("0", "vehicle offline"));
        }
        int sequence = sequenceGenerator.next();
        String commandId = UUID.randomUUID().toString();
        byte[] frame = codec.platformCommand(command, sequence);
        CommandCorrelation correlation = new CommandCorrelation(commandId, command.terminalId(), command.messageId(), sequence, "rtvs", Instant.now());
        return correlations.put(correlation)
                .then(connections.send(command.terminalId(), frame))
                .flatMap(sent -> {
                    if (!sent) {
                        return Mono.just(CommandDispatchResult.failure("-1", "send failed"));
                    }
                    CommandDispatchEvent event = new CommandDispatchEvent(commandId, command.terminalId(), command.terminalId(), command.messageId(), sequence, "rtvs", Instant.now());
                    return events.publish(KafkaTopics.JT808_COMMAND, command.terminalId(), event)
                            .thenReturn(CommandDispatchResult.accepted(commandId, command.messageId(), sequence));
                });
    }
}
