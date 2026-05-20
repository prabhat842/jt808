package com.example.jt808sim.protocol.inbound;

/**
 * 0x8700 Platform tachograph command (Tables 69-70, JT808-2013).
 *
 * commandType values (Table 70):
 *   0x0001 = set tachograph time
 *   0x0002 = request 1-day speed records
 *   0x0003 = request incident speed records
 *   0x0004 = request driver cumulative drive time
 *   0x0005 = request last tachograph position
 *   0x0006 = request accident records
 *   0x0007 = request overspeed records
 *   0x0008 = request fatigue drive records
 *   0xFF00 = request all records
 *
 * The terminal responds with 0x0700 carrying the same commandType.
 */
public record TachographDataCmd(int commandType, byte[] commandData) {
}
