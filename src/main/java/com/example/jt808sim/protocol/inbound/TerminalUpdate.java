package com.example.jt808sim.protocol.inbound;

/**
 * 0x8108 Send down terminal update packet (Table 21, JT808-2013).
 *
 * upgradeType: 0=terminal, 12=road-transport IC card reader, 52=Beidou module.
 * The terminal ACKs immediately with 0x0001 then reports result via 0x0108.
 */
public record TerminalUpdate(int upgradeType, byte[] manufacturerId, String version, byte[] data) {
}
