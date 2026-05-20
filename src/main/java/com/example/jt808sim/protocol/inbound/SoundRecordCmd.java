package com.example.jt808sim.protocol.inbound;

/**
 * 0x8804 Sound record start command (Table 89, JT808-2013).
 * command: 0=stop, 1=start recording
 * recordSeconds: 0=record indefinitely
 * storeSign: 0=real-time upload, 1=store
 * samplingRate: 0=8kHz, 1=11kHz, 2=22kHz, 3=32kHz
 */
public record SoundRecordCmd(int command, int recordSeconds, int storeSign, int samplingRate) {
}
