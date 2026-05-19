package com.example.jt808sim.protocol.inbound;

import com.example.jt808sim.fleet.geofence.RouteArea;

/**
 * 0x8606 Setting route (Table 66, JT808-2013).
 *
 * One route per message. Routes are always added or replaced by ID (no setting-attribute field).
 */
public record SetRoute(RouteArea route) {
}
