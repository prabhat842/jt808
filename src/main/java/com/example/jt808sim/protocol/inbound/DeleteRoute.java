package com.example.jt808sim.protocol.inbound;

import java.util.List;

/**
 * 0x8607 Delete route (Table 70, JT808-2013).
 *
 * An empty routeIds list means "delete all routes" (count==0 in wire format).
 */
public record DeleteRoute(List<Long> routeIds) {
}
