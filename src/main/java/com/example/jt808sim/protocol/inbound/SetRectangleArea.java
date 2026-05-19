package com.example.jt808sim.protocol.inbound;

import com.example.jt808sim.fleet.geofence.RectangleArea;

import java.util.List;

/**
 * 0x8602 Setting rectangle area (Table 60, JT808-2013).
 *
 * settingAttribute: 0=upgrade 1=append 2=modify
 */
public record SetRectangleArea(int settingAttribute, List<RectangleArea> areas) {
}
