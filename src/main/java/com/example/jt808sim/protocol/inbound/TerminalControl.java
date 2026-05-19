package com.example.jt808sim.protocol.inbound;

/**
 * 0x8105 Terminal control (Tables 17-19, JT808-2013).
 *
 * command values (Table 18):
 *   1 = wireless upgrade (params: URL;dialName;user;pass;host;tcpPort;udpPort;mfgId;hwVer;fwVer;timeLimit)
 *   2 = connect to specified server (params: connControl;monitorAuth;dialName;user;pass;host;tcpPort;udpPort;timeLimit)
 *   3 = terminal power off
 *   4 = terminal reset
 *   5 = factory reset
 *   6 = turn off data communication
 *   7 = close all wireless communication
 */
public record TerminalControl(int command, String commandParams) {
}
