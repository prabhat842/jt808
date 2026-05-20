package com.example.jt808sim.protocol.inbound;

/**
 * 0x8300 Send down text information (Table 37, JT808-2013).
 *
 * sign bits (Table 38):
 *   bit 0: emergency, bit 2: display on terminal, bit 3: TTS reading,
 *   bit 4: display on advertising screen, bit 5: CAN fault code
 */
public record TextInfo(int sign, String text) {
}
