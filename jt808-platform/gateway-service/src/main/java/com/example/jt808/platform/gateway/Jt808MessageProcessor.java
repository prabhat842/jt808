package com.example.jt808.platform.gateway;

import com.example.jt808.platform.contracts.CommandResponseEvent;
import com.example.jt808.platform.contracts.GpsTelemetryEvent;
import com.example.jt808.platform.contracts.HeartbeatEvent;
import com.example.jt808.platform.contracts.KafkaTopics;
import com.example.jt808.platform.contracts.MediaSignalEvent;
import com.example.jt808.platform.protocol.AuthenticationBody;
import com.example.jt808.platform.protocol.DecodedJt808Message;
import com.example.jt808.platform.protocol.Jt808FrameCodec;
import com.example.jt808.platform.protocol.MessageIds;
import com.example.jt808.platform.protocol.TerminalGeneralResponse;
import com.example.jt808.platform.protocol.TerminalLocationReport;
import com.example.jt808.platform.protocol.TerminalRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Component
class Jt808MessageProcessor {
    private static final Logger log = LoggerFactory.getLogger(Jt808MessageProcessor.class);

    private final Jt808FrameCodec codec;
    private final GatewayProperties properties;
    private final GatewaySessionStore sessions;
    private final ActiveConnectionRegistry connections;
    private final EventPublisher events;
    private final SequenceGenerator sequenceGenerator = new SequenceGenerator();

    Jt808MessageProcessor(Jt808FrameCodec codec, GatewayProperties properties, GatewaySessionStore sessions, ActiveConnectionRegistry connections, EventPublisher events) {
        this.codec = codec;
        this.properties = properties;
        this.sessions = sessions;
        this.connections = connections;
        this.events = events;
    }

    Mono<byte[]> process(byte[] frame, String remoteAddress, reactor.core.publisher.Sinks.Many<byte[]> outbound) {
        DecodedJt808Message message;
        try {
            message = codec.decode(frame);
        } catch (RuntimeException e) {
            log.debug("dropping invalid JT808 frame from {}", remoteAddress, e);
            return Mono.empty();
        }

        String terminalId = message.header().terminalId();
        int messageId = message.header().messageId();

        if (messageId == MessageIds.TERMINAL_REGISTER) {
            return handleRegistration(message, remoteAddress, terminalId, outbound);
        }
        if (messageId == MessageIds.TERMINAL_AUTH) {
            return handleAuthentication(message, terminalId, outbound);
        }
        if (messageId == MessageIds.HEARTBEAT) {
            return handleHeartbeat(message, remoteAddress, terminalId);
        }
        if (messageId == MessageIds.LOCATION_REPORT && message.body() instanceof TerminalLocationReport location) {
            return handleLocation(message, remoteAddress, terminalId, location);
        }
        if (messageId == MessageIds.TERMINAL_GENERAL_RESPONSE && message.body() instanceof TerminalGeneralResponse response) {
            return handleTerminalResponse(message, terminalId, response);
        }
        if (isMediaSignal(messageId)) {
            return publishMediaSignal(message, terminalId).thenReturn(ack(message, 0));
        }
        return Mono.just(ack(message, 0));
    }

    private Mono<byte[]> handleRegistration(DecodedJt808Message message, String remoteAddress, String terminalId, reactor.core.publisher.Sinks.Many<byte[]> outbound) {
        Mono<Void> publish = Mono.empty();
        if (message.body() instanceof TerminalRegistration registration) {
            publish = events.publish(KafkaTopics.TELEMETRY_HEARTBEAT, terminalId, new HeartbeatEvent(terminalId, terminalId, message.header().sequence(), remoteAddress, Instant.now()))
                    .doOnSuccess(ignored -> log.info("registered terminal {} plate={} color={}", terminalId, registration.plateNumber(), registration.plateColor()));
        }
        byte[] response = codec.registrationResponse(terminalId, sequenceGenerator.next(), message.header().sequence(), 0, properties.getAuthCode());
        connections.register(terminalId, outbound);
        return sessions.register(terminalId, remoteAddress).then(publish).thenReturn(response);
    }

    private Mono<byte[]> handleAuthentication(DecodedJt808Message message, String terminalId, reactor.core.publisher.Sinks.Many<byte[]> outbound) {
        if (message.body() instanceof AuthenticationBody auth) {
            log.debug("terminal {} authenticated with token length {}", terminalId, auth.token().length());
        }
        connections.register(terminalId, outbound);
        return sessions.authenticate(terminalId).thenReturn(ack(message, 0));
    }

    private Mono<byte[]> handleHeartbeat(DecodedJt808Message message, String remoteAddress, String terminalId) {
        HeartbeatEvent event = new HeartbeatEvent(terminalId, terminalId, message.header().sequence(), remoteAddress, Instant.now());
        return sessions.heartbeat(terminalId)
                .then(events.publish(KafkaTopics.TELEMETRY_HEARTBEAT, terminalId, event))
                .thenReturn(ack(message, 0));
    }

    private Mono<byte[]> handleLocation(DecodedJt808Message message, String remoteAddress, String terminalId, TerminalLocationReport location) {
        GpsTelemetryEvent event = new GpsTelemetryEvent(
                terminalId,
                terminalId,
                terminalId,
                location.gpsTime(),
                location.latitude(),
                location.longitude(),
                location.altitudeMeters(),
                location.speedKph(),
                location.direction(),
                location.stateBit(),
                location.warnBit(),
                location.positioned(),
                remoteAddress,
                Instant.now());
        return events.publish(KafkaTopics.TELEMETRY_GPS, terminalId, event).thenReturn(ack(message, 0));
    }

    private Mono<byte[]> handleTerminalResponse(DecodedJt808Message message, String terminalId, TerminalGeneralResponse response) {
        CommandResponseEvent event = new CommandResponseEvent(
                terminalId,
                terminalId,
                message.header().sequence(),
                response.responseSequence(),
                response.responseMessageId(),
                response.result(),
                Instant.now());
        return events.publish(KafkaTopics.MEDIA_RESPONSE, terminalId, event).then(Mono.empty());
    }

    private Mono<Void> publishMediaSignal(DecodedJt808Message message, String terminalId) {
        MediaSignalEvent event = new MediaSignalEvent(terminalId, terminalId, message.header().messageId(), message.header().sequence(), "terminal_to_platform", Instant.now());
        return events.publish(KafkaTopics.MEDIA_SIGNAL, terminalId, event);
    }

    private byte[] ack(DecodedJt808Message message, int result) {
        return codec.platformGeneralAck(
                message.header().terminalId(),
                sequenceGenerator.next(),
                message.header().sequence(),
                message.header().messageId(),
                result);
    }

    private static boolean isMediaSignal(int messageId) {
        return messageId == MessageIds.JT1078_REALTIME_REQUEST
                || messageId == MessageIds.JT1078_PLAYBACK_REQUEST
                || messageId == MessageIds.JT1078_QUERY_RESOURCE_LIST;
    }
}
