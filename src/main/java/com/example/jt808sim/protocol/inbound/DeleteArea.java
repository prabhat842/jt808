package com.example.jt808sim.protocol.inbound;

import java.util.List;

/**
 * Shared model for 0x8601 Delete circle area, 0x8603 Delete rectangle area,
 * and 0x8605 Delete polygon area (Tables 59, 62, 65 — JT808-2013).
 *
 * An empty areaIds list means "delete all areas of this type" (count==0 in wire format).
 */
public record DeleteArea(List<Long> areaIds) {
}
