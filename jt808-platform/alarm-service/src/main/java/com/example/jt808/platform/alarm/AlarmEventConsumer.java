package com.example.jt808.platform.alarm;

import com.example.jt808.platform.contracts.AlarmEvent;
import com.example.jt808.platform.contracts.AttachmentEvent;
import com.example.jt808.platform.contracts.KafkaTopics;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOffset;
import reactor.kafka.receiver.ReceiverOptions;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
class AlarmEventConsumer implements SmartLifecycle {
    private static final Logger log = LoggerFactory.getLogger(AlarmEventConsumer.class);

    private final AlarmProperties properties;
    private final ObjectMapper objectMapper;
    private final AlarmClickHouseWriter writer;
    private reactor.core.Disposable subscription;
    private volatile boolean running;

    AlarmEventConsumer(AlarmProperties properties, ObjectMapper objectMapper, AlarmClickHouseWriter writer) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.writer = writer;
    }

    @Override
    public void start() {
        if (!properties.getKafka().isEnabled()) {
            log.info("alarm Kafka consumer disabled");
            running = true;
            return;
        }
        ReceiverOptions<String, String> options = ReceiverOptions.<String, String>create(Map.of(
                        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getKafka().getBootstrapServers(),
                        ConsumerConfig.GROUP_ID_CONFIG, properties.getKafka().getGroupId(),
                        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                        ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false))
                .subscription(List.of(KafkaTopics.TELEMETRY_ALARM, KafkaTopics.TELEMETRY_ATTACHMENT));

        subscription = KafkaReceiver.create(options).receive()
                .<ReceivedEvent>handle((record, sink) -> {
                    try {
                        Object event = KafkaTopics.TELEMETRY_ALARM.equals(record.topic())
                                ? objectMapper.readValue(record.value(), AlarmEvent.class)
                                : objectMapper.readValue(record.value(), AttachmentEvent.class);
                        sink.next(new ReceivedEvent(event, record.receiverOffset()));
                    } catch (Exception e) {
                        log.warn("dropping invalid alarm payload topic={} offset={}", record.topic(), record.offset(), e);
                        record.receiverOffset().acknowledge();
                    }
                })
                .bufferTimeout(properties.getClickHouse().getBatchSize(), Duration.ofMillis(properties.getClickHouse().getFlushIntervalMs()))
                .filter(batch -> !batch.isEmpty())
                .concatMap(batch -> {
                    List<AlarmEvent> alarms = batch.stream().map(ReceivedEvent::event).filter(AlarmEvent.class::isInstance).map(AlarmEvent.class::cast).toList();
                    List<AttachmentEvent> attachments = batch.stream().map(ReceivedEvent::event).filter(AttachmentEvent.class::isInstance).map(AttachmentEvent.class::cast).toList();
                    return writer.writeAlarms(alarms)
                            .then(writer.writeAttachments(attachments))
                            .doOnSuccess(ignored -> batch.forEach(event -> event.offset().acknowledge()));
                })
                .doOnError(error -> log.warn("alarm consumer failed", error))
                .retry()
                .subscribe();
        running = true;
    }

    @Override
    public void stop() {
        if (subscription != null) {
            subscription.dispose();
        }
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private record ReceivedEvent(Object event, ReceiverOffset offset) {
    }
}
