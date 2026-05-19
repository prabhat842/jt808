package com.example.jt808sim.protocol.inbound;

/**
 * 0x8500 Vehicle control (Tables 53-54, JT808-2013).
 *
 * controlSign bit definitions (Table 54):
 *   bit 0: 0=car doors unlocked  1=car doors locked
 *   bits 1-7: reserved
 *
 * The terminal responds immediately with 0x0001 terminal general response,
 * then sends 0x0500 vehicle control response after applying the control.
 */
public record VehicleControlCommand(int controlSign) {
    public boolean lockDoors()   { return (controlSign & 0x01) != 0; }
    public boolean unlockDoors() { return (controlSign & 0x01) == 0; }
}
