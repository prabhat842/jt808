package com.example.jt808.platform.gateway;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
class RtvsGatewayController {
    private final CommandDispatchService dispatchService;
    private final ActiveConnectionRegistry connections;
    private final ObjectProvider<ReactiveStringRedisTemplate> redisProvider;

    RtvsGatewayController(CommandDispatchService dispatchService,
                           ActiveConnectionRegistry connections,
                           ObjectProvider<ReactiveStringRedisTemplate> redisProvider) {
        this.dispatchService = dispatchService;
        this.connections = connections;
        this.redisProvider = redisProvider;
    }

    @GetMapping(value = "/VideoControl", produces = MediaType.TEXT_PLAIN_VALUE)
    Mono<String> videoControl(@RequestParam("Content") String content) {
        return dispatchService.dispatchRtvsHex(content).map(CommandDispatchResult::rtvsReturnValue);
    }

    @GetMapping(value = "/WCF0x9105", produces = MediaType.TEXT_PLAIN_VALUE)
    Mono<String> wcf9105() {
        return Mono.just("1");
    }

    /**
     * Plate-based lookup: RTVS calls with PlateCode+PlateColor to find the terminal SIM.
     * Direct SIM lookup: returns the SIM if it is currently online.
     */
    @GetMapping(value = "/GetVehicleSim", produces = MediaType.TEXT_PLAIN_VALUE)
    Mono<String> getVehicleSim(
            @RequestParam(value = "Sim", required = false) String sim,
            @RequestParam(value = "PlateCode", required = false) String plateCode,
            @RequestParam(value = "PlateColor", required = false) String plateColor) {
        if (sim != null && !sim.isBlank()) {
            return Mono.just(connections.isOnline(sim) ? sim : "");
        }
        if (plateCode != null && !plateCode.isBlank()) {
            ReactiveStringRedisTemplate redis = redisProvider.getIfAvailable();
            if (redis != null) {
                String plateKey = "jt808:plate:" + plateCode + ":" + (plateColor == null ? "0" : plateColor);
                return redis.opsForValue().get(plateKey).defaultIfEmpty("");
            }
        }
        return Mono.just("");
    }
}
