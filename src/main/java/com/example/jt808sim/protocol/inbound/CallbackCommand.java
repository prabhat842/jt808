package com.example.jt808sim.protocol.inbound;

/**
 * 0x8400 Call back (Table 50, JT808-2013).
 * sign: 0=ordinary call, 1=monitoring (terminal does not open speaker).
 */
public record CallbackCommand(int sign, String phoneNumber) {
}
