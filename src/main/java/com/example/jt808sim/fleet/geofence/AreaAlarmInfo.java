package com.example.jt808sim.fleet.geofence;

/**
 * Carries the area/route alarm context for encoding in 0x0200 additional info items.
 *
 * 0x12 item (Table 29): locationType + areaId + direction
 *   locationType: 1=circle 2=rectangle 3=polygon 4=route
 *   direction:    0=entered  1=exited
 */
public record AreaAlarmInfo(int locationType, long areaId, int direction) {
}
