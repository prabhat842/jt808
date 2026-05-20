package com.example.jt808.platform.gateway;

import java.time.Instant;

record TerminalSession(
        String  terminalId,
        String  remoteAddress,
        boolean authenticated,
        Instant registeredAt,
        Instant lastHeartbeatAt,
        Instant updatedAt,
        // Identity from 0x0100 registration (null/0 until storeIdentity is called)
        String  plateNumber,
        int     plateColor,
        int     provinceId,
        int     cityId,
        String  manufacturerId,
        String  terminalModel
) {
    TerminalSession authenticated(Instant now) {
        return new TerminalSession(terminalId, remoteAddress, true, registeredAt,
                lastHeartbeatAt, now, plateNumber, plateColor, provinceId, cityId,
                manufacturerId, terminalModel);
    }

    TerminalSession heartbeat(Instant now) {
        return new TerminalSession(terminalId, remoteAddress, authenticated, registeredAt,
                now, now, plateNumber, plateColor, provinceId, cityId,
                manufacturerId, terminalModel);
    }

    TerminalSession withIdentity(String plate, int color, int province, int city,
                                  String manufacturer, String model) {
        return new TerminalSession(terminalId, remoteAddress, authenticated, registeredAt,
                lastHeartbeatAt, Instant.now(), plate, color, province, city,
                manufacturer, model);
    }
}
