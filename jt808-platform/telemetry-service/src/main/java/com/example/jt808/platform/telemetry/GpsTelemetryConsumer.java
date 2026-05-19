package com.example.jt808.platform.telemetry;

import com.example.jt808.platform.contracts.GpsTelemetryEvent;
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
class GpsTelemetryConsumer implements SmartLifecycle {
    private static final Logger log = LoggerFactory.getLogger(GpsTelemetryConsumer.class);

    private final TelemetryProperties properties;
    private final ObjectMapper objectMapper;
    private final ClickHouseGpsWriter writer;
    private reactor.core.Disposable subscription;
    private volatile boolean running;

    GpsTelemetryConsumer(TelemetryProperties properties, ObjectMapper objectMapper, ClickHouseGpsWriter writer) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.writer = writer;
    }

    @Override
    public void start() {
        if (!properties.getKafka().isEnabled()) {
            log.info("telemetry Kafka consumer disabled");
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
                .subscription(List.of(KafkaTopics.TELEMETRY_GPS));

        subscription = KafkaReceiver.create(options)
                        .receive()
                        .<ReceivedGps>handle((record, sink) -> {
                            try {
                                GpsTelemetryEvent event = objectMapper.readValue(record.value(), GpsTelemetryEvent.class);
                                sink.next(new ReceivedGps(event, record.receiverOffset()));
                            } catch (Exception e) {
                                log.warn("dropping invalid telemetry.gps payload at offset {}", record.offset(), e);
                                record.receiverOffset().acknowledge();
                            }
                        })
                .bufferTimeout(properties.getClickHouse().getBatchSize(), Duration.ofMillis(properties.getClickHouse().getFlushIntervalMs()))
                .filter(batch -> !batch.isEmpty())
                .concatMap(batch -> writer.writeBatch(batch.stream().map(ReceivedGps::event).toList())
                        .doOnSuccess(ignored -> batch.forEach(received -> received.offset().acknowledge())))
                .doOnError(error -> log.warn("telemetry consumer failed", error))
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

    private record ReceivedGps(GpsTelemetryEvent event, ReceiverOffset offset) {
    }
}
