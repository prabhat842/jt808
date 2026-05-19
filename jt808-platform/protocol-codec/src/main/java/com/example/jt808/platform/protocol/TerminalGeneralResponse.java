package com.example.jt808.platform.protocol;

public record TerminalGeneralResponse(int responseSequence, int responseMessageId, int result) {
    public boolean success() {
        return result == 0;
    }
}
