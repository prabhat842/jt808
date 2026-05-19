package com.example.jt808sim.protocol.inbound;

import com.example.jt808sim.fleet.geofence.CircleArea;

import java.util.List;

/**
 * 0x8600 Setting circle area (Table 56, JT808-2013).
 *
 * settingAttribute: 0=upgrade (replace all) 1=append 2=modify
 */
public record SetCircleArea(int settingAttribute, List<CircleArea> areas) {
}
