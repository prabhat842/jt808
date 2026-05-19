package com.example.jt808sim.protocol.inbound;

import java.util.List;

/**
 * 0x8106 Check specified terminal parameters (Table 15, JT808-2013).
 *
 * The terminal responds with 0x0104 containing only the requested parameter IDs.
 */
public record TerminalParamQuerySpec(List<Integer> paramIds) {
}
