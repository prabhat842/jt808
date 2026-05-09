package com.example.jt808sim.config;

import java.util.ArrayList;
import java.util.List;

public class VehicleIdentity {
    private String vin;
    private String terminalId;
    private String plateNumber;
    private String manufacturerId;
    private double startLat;
    private double startLon;
    private double targetLat;
    private double targetLon;
    private double speedKph = 40.0;
    private boolean mediaCapable;
    private List<Integer> mediaChannels = new ArrayList<>();

    public VehicleIdentity copyForIndex(int index) {
        VehicleIdentity copy = new VehicleIdentity();
        copy.vin = suffix(vin, index, 17);
        copy.terminalId = terminalIdForIndex(index);
        copy.plateNumber = suffix(plateNumber, index, 12);
        copy.manufacturerId = manufacturerId;
        copy.startLat = startLat;
        copy.startLon = startLon;
        copy.targetLat = targetLat;
        copy.targetLon = targetLon;
        copy.speedKph = speedKph;
        copy.mediaCapable = mediaCapable;
        copy.mediaChannels = new ArrayList<>(mediaChannels);
        return copy;
    }

    public void validate() {
        if (terminalId == null || !terminalId.matches("\\d{20}")) {
            throw new IllegalArgumentException("terminalId must be a 20-digit string");
        }
        if (manufacturerId == null || manufacturerId.isBlank()) {
            throw new IllegalArgumentException("manufacturerId is required");
        }
        if (plateNumber == null || plateNumber.isBlank()) {
            throw new IllegalArgumentException("plateNumber is required");
        }
    }

    private String terminalIdForIndex(int index) {
        long base = Long.parseUnsignedLong(terminalId.substring(4));
        return String.format("%04d%016d", Integer.parseInt(terminalId.substring(0, 4)), base + index);
    }

    private static String suffix(String value, int index, int maxLength) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String next = value + "-" + (index + 1);
        return next.length() <= maxLength ? next : next.substring(next.length() - maxLength);
    }

    public String getVin() {
        return vin;
    }

    public void setVin(String vin) {
        this.vin = vin;
    }

    public String getTerminalId() {
        return terminalId;
    }

    public void setTerminalId(String terminalId) {
        this.terminalId = terminalId;
    }

    public String getPlateNumber() {
        return plateNumber;
    }

    public void setPlateNumber(String plateNumber) {
        this.plateNumber = plateNumber;
    }

    public String getManufacturerId() {
        return manufacturerId;
    }

    public void setManufacturerId(String manufacturerId) {
        this.manufacturerId = manufacturerId;
    }

    public double getStartLat() {
        return startLat;
    }

    public void setStartLat(double startLat) {
        this.startLat = startLat;
    }

    public double getStartLon() {
        return startLon;
    }

    public void setStartLon(double startLon) {
        this.startLon = startLon;
    }

    public double getTargetLat() {
        return targetLat;
    }

    public void setTargetLat(double targetLat) {
        this.targetLat = targetLat;
    }

    public double getTargetLon() {
        return targetLon;
    }

    public void setTargetLon(double targetLon) {
        this.targetLon = targetLon;
    }

    public double getSpeedKph() {
        return speedKph;
    }

    public void setSpeedKph(double speedKph) {
        this.speedKph = speedKph;
    }

    public boolean isMediaCapable() {
        return mediaCapable;
    }

    public void setMediaCapable(boolean mediaCapable) {
        this.mediaCapable = mediaCapable;
    }

    public List<Integer> getMediaChannels() {
        return mediaChannels;
    }

    public void setMediaChannels(List<Integer> mediaChannels) {
        this.mediaChannels = mediaChannels;
    }
}
