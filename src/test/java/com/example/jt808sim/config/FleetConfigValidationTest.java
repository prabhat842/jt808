package com.example.jt808sim.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FleetConfigValidationTest {
    @Test
    void rejectsCameraModeWhenBothCaptureStreamsDisabled() {
        FleetConfig config = minimalConfig();
        config.getJt1078().setStreamMode("camera");
        config.getJt1078().getCapture().setVideoEnabled(false);
        config.getJt1078().getCapture().setAudioEnabled(false);

        assertThrows(IllegalArgumentException.class, config::validate);
    }

    @Test
    void acceptsCameraModeWhenAudioOnlyIsEnabled() {
        FleetConfig config = minimalConfig();
        config.getJt1078().setStreamMode("camera");
        config.getJt1078().getCapture().setVideoEnabled(false);
        config.getJt1078().getCapture().setAudioEnabled(true);
        config.getJt1078().getCapture().setAudioDevice("lavfi:anullsrc=r=8000:cl=mono");

        assertDoesNotThrow(config::validate);
    }

    private static FleetConfig minimalConfig() {
        FleetConfig config = new FleetConfig();
        VehicleIdentity vehicle = new VehicleIdentity();
        vehicle.setVin("VIN00000000000001");
        vehicle.setTerminalId("00000000000000000001");
        vehicle.setPlateNumber("TEST-0001");
        vehicle.setManufacturerId("TEST1");
        vehicle.setStartLat(22.25);
        vehicle.setStartLon(72.2);
        vehicle.setTargetLat(22.35);
        vehicle.setTargetLon(72.35);
        vehicle.setSpeedKph(45.0);
        vehicle.setMediaCapable(true);
        vehicle.setMediaChannels(List.of(1));
        config.setVehicles(List.of(vehicle));
        return config;
    }
}
