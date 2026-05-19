package com.example.jt808.platform.media;

import com.example.jt808.platform.contracts.CommandDispatchEvent;
import com.example.jt808.platform.contracts.CommandResponseEvent;
import com.example.jt808.platform.contracts.KafkaTopics;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOptions;

import java.util.List;
import java.util.Map;

@Component
class MediaEventConsumer implements SmartLifecycle {
    private static final Logger log = LoggerFactory.getLogger(MediaEventConsumer.class);

    private final MediaProperties properties;
    private final ObjectMapper objectMapper;
    private final RtvsCompatibilityStore rtvsStore;
    private reactor.core.Disposable subscription;
    private volatile boolean running;

    MediaEventConsumer(MediaProperties properties, ObjectMapper objectMapper, RtvsCompatibilityStore rtvsStore) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.rtvsStore = rtvsStore;
    }

    @Override
    public void start() {
        if (!properties.getKafka().isEnabled()) {
            log.info("media Kafka consumer disabled");
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
                .subscription(List.of(KafkaTopics.JT808_COMMAND, KafkaTopics.MEDIA_RESPONSE));

        subscription = KafkaReceiver.create(options).receive()
                .concatMap(record -> {
                    Mono<Void> work;
                    try {
                        if (KafkaTopics.JT808_COMMAND.equals(record.topic())) {
                            work = rtvsStore.recordDispatch(objectMapper.readValue(record.value(), CommandDispatchEvent.class));
                        } else {
                            work = rtvsStore.recordResponse(objectMapper.readValue(record.value(), CommandResponseEvent.class));
                        }
                    } catch (Exception e) {
                        log.warn("dropping invalid media payload topic={} offset={}", record.topic(), record.offset(), e);
                        work = Mono.empty();
                    }
                    return work.doFinally(signal -> record.receiverOffset().acknowledge());
                })
                .doOnError(error -> log.warn("media consumer failed", error))
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
}
