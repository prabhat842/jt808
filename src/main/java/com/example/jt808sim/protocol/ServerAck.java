package com.example.jt808sim.protocol;

public record ServerAck(int responseSequence, int responseMessageId, int result) {
    public boolean success() {
        return result == 0;
    }
}
