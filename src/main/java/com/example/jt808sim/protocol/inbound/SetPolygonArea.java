package com.example.jt808sim.protocol.inbound;

import com.example.jt808sim.fleet.geofence.PolygonArea;

/**
 * 0x8604 Setting polygon area (Table 63, JT808-2013).
 *
 * One polygon per message. settingAttribute: 0=upgrade 1=append 2=modify.
 */
public record SetPolygonArea(int settingAttribute, PolygonArea area) {
}
