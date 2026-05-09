package com.example.jt808sim.protocol;

public record RegistrationResponse(int responseSequence, int result, String authCode) {
    public boolean success() {
        return result == 0;
    }
}
