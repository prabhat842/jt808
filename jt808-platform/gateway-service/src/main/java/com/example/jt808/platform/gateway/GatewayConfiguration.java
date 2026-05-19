package com.example.jt808.platform.gateway;

import com.example.jt808.platform.protocol.Jt808FrameCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;

import java.util.HashMap;
import java.util.Map;

@Configuration
class GatewayConfiguration {
    @Bean
    Jt808FrameCodec jt808FrameCodec(GatewayProperties properties) {
        return new Jt808FrameCodec(properties.getProtocolZone());
    }

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Bean
    EventPublisher eventPublisher(GatewayProperties properties, ObjectMapper objectMapper) {
        if (!properties.getKafka().isEnabled()) {
            return new LoggingEventPublisher(objectMapper);
        }
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getKafka().getBootstrapServers());
        config.put(ProducerConfig.CLIENT_ID_CONFIG, properties.getKafka().getClientId());
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return new ReactorKafkaEventPublisher(KafkaSender.create(SenderOptions.create(config)), objectMapper);
    }

    @Bean
    GatewaySessionStore gatewaySessionStore(GatewayProperties properties, ObjectProviderAdapter redisTemplate) {
        if (properties.getRedis().isEnabled() && redisTemplate.template() != null) {
            return new RedisGatewaySessionStore(redisTemplate.template(), properties);
        }
        return new InMemoryGatewaySessionStore();
    }

    @Bean
    CommandCorrelationStore commandCorrelationStore(GatewayProperties properties, ObjectProviderAdapter redisTemplate) {
        if (properties.getRedis().isEnabled() && redisTemplate.template() != null) {
            return new RedisCommandCorrelationStore(redisTemplate.template(), properties);
        }
        return new InMemoryCommandCorrelationStore();
    }

    @Bean
    ActiveConnectionRegistry activeConnectionRegistry() {
        return new ActiveConnectionRegistry();
    }

    @Bean
    ObjectProviderAdapter redisTemplateProvider(org.springframework.beans.factory.ObjectProvider<ReactiveStringRedisTemplate> provider) {
        return new ObjectProviderAdapter(provider.getIfAvailable());
    }

    record ObjectProviderAdapter(ReactiveStringRedisTemplate template) {
    }
}
