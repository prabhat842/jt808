package com.example.jt808.platform.protocol;

public record TerminalRegistration(
        int provinceId,
        int cityId,
        String manufacturerId,
        String terminalModel,
        String terminalIdentifier,
        int plateColor,
        String plateNumber
) {
}
