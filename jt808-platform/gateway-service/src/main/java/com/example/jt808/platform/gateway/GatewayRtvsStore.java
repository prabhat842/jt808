package com.example.jt808.platform.gateway;

import com.example.jt808.platform.protocol.AudioVideoAttributesUpload;
import com.example.jt808.platform.protocol.TerminalRegistration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Writes RTVS-compatibility Redis keys required for JT1078 media coordination:
 *
 *   AVParameters:<sim>          — terminal AV capabilities (written on 0x1003)
 *   SIM_CONFIG_FOR_RTVS_<sim>  — terminal plate/config (written on 0x0100 registration)
 *   jt808:plate:<plate>:<color> — reverse plate→SIM index for GetVehicleSim
 */
@Component
class GatewayRtvsStore {
    private static final Duration AV_TTL  = Duration.ofHours(24);
    private static final Duration SIM_TTL = Duration.ofDays(30);

    private final GatewayProperties properties;
    private final ObjectProvider<ReactiveStringRedisTemplate> redisProvider;

    GatewayRtvsStore(GatewayProperties properties,
                     ObjectProvider<ReactiveStringRedisTemplate> redisProvider) {
        this.properties = properties;
        this.redisProvider = redisProvider;
    }

    Mono<Void> writeAvParameters(String terminalId, AudioVideoAttributesUpload attrs) {
        ReactiveStringRedisTemplate redis = redisProvider.getIfAvailable();
        if (!properties.getRedis().isEnabled() || redis == null) return Mono.empty();
        String value = buildAvParameters(attrs);
        return redis.opsForValue().set("AVParameters:" + terminalId, value, AV_TTL).then();
    }

    Mono<Void> writeSimConfig(String terminalId, TerminalRegistration registration) {
        ReactiveStringRedisTemplate redis = redisProvider.getIfAvailable();
        if (!properties.getRedis().isEnabled() || redis == null) return Mono.empty();
        String simConfig = buildSimConfig(terminalId, registration);
        Mono<Void> configWrite = redis.opsForValue()
                .set("SIM_CONFIG_FOR_RTVS_" + terminalId, simConfig, SIM_TTL).then();
        String plateKey = "jt808:plate:" + registration.plateNumber() + ":" + registration.plateColor();
        Mono<Void> plateWrite = redis.opsForValue()
                .set(plateKey, terminalId, SIM_TTL).then();
        return configWrite.then(plateWrite);
    }

    // ── JSON builders ────────────────────────────────────────────────────────

    static String buildAvParameters(AudioVideoAttributesUpload attrs) {
        return "{\"AudioEncoding\":" + attrs.audioEncoding()
                + ",\"AudioChannels\":" + attrs.audioChannels()
                + ",\"AudioSampleRate\":" + attrs.audioSampleRate()
                + ",\"AudioSampleBits\":" + attrs.audioSampleBits()
                + ",\"AudioFrameLength\":" + attrs.audioFrameLength()
                + ",\"AudioOutputSupported\":" + attrs.audioOutputSupported()
                + ",\"VideoEncoding\":" + attrs.videoEncoding()
                + ",\"MaxAudioChannels\":" + attrs.maxAudioChannels()
                + ",\"MaxVideoChannels\":" + attrs.maxVideoChannels()
                + "}";
    }

    static String buildSimConfig(String terminalId, TerminalRegistration registration) {
        return "{\"Sim\":\"" + terminalId + "\""
                + ",\"PlateNumber\":\"" + registration.plateNumber() + "\""
                + ",\"PlateColor\":" + registration.plateColor()
                + ",\"ProvinceId\":" + registration.provinceId()
                + ",\"CityId\":" + registration.cityId()
                + ",\"ManufacturerId\":\"" + registration.manufacturerId() + "\""
                + ",\"TerminalModel\":\"" + registration.terminalModel() + "\""
                + "}";
    }
}
