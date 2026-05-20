package com.example.jt808sim.protocol.inbound;

/**
 * 0x8304 Information service (Table 49, JT808-2013).
 * Pushed by the platform to the terminal (news, weather, etc.).
 */
public record InfoService(int infoType, String content) {
}
