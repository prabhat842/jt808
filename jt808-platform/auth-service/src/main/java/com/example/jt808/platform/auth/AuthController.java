package com.example.jt808.platform.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
class AuthController {
    private final AuthProperties properties;

    AuthController(AuthProperties properties) {
        this.properties = properties;
    }

    @PostMapping("/auth/api/validate")
    Mono<AuthResult> validateApi(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        String expected = "Bearer " + properties.getApiToken();
        return Mono.just(new AuthResult(expected.equals(authorization), expected.equals(authorization) ? "ok" : "invalid token"));
    }

    @PostMapping("/auth/terminal/validate")
    Mono<AuthResult> validateTerminal(@RequestBody TerminalAuthRequest request) {
        boolean valid = properties.getTerminalToken().equals(request.token());
        return Mono.just(new AuthResult(valid, valid ? "ok" : "invalid terminal token"));
    }

    record TerminalAuthRequest(String terminalId, String token) {
    }

    record AuthResult(boolean valid, String message) {
    }
}
