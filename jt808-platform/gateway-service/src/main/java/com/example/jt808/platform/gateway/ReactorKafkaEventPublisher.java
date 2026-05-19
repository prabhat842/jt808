package com.example.jt808.platform.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerRecord;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

class ReactorKafkaEventPublisher implements EventPublisher {
    private final KafkaSender<String, String> sender;
    private final ObjectMapper objectMapper;

    ReactorKafkaEventPublisher(KafkaSender<String, String> sender, ObjectMapper objectMapper) {
        this.sender = sender;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> publish(String topic, String key, Object event) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(event))
                .subscribeOn(Schedulers.parallel())
                .flatMap(payload -> sender.send(Mono.just(SenderRecord.create(new ProducerRecord<>(topic, key, payload), null))).then());
    }
}
