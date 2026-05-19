package com.example.jt808.platform.gateway;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
class RtvsGatewayController {
    private final CommandDispatchService dispatchService;
    private final ActiveConnectionRegistry connections;

    RtvsGatewayController(CommandDispatchService dispatchService, ActiveConnectionRegistry connections) {
        this.dispatchService = dispatchService;
        this.connections = connections;
    }

    @GetMapping(value = "/VideoControl", produces = MediaType.TEXT_PLAIN_VALUE)
    Mono<String> videoControl(@RequestParam("Content") String content) {
        return dispatchService.dispatchRtvsHex(content).map(CommandDispatchResult::rtvsReturnValue);
    }

    @GetMapping(value = "/WCF0x9105", produces = MediaType.TEXT_PLAIN_VALUE)
    Mono<String> wcf9105() {
        return Mono.just("1");
    }

    @GetMapping(value = "/GetVehicleSim", produces = MediaType.TEXT_PLAIN_VALUE)
    Mono<String> getVehicleSim(@RequestParam(value = "Sim", required = false) String sim) {
        if (sim == null || sim.isBlank()) {
            return Mono.just("");
        }
        return Mono.just(connections.isOnline(sim) ? sim : "");
    }
}
