package com.example.jt808sim.protocol.inbound;

import java.util.List;

/**
 * 0x8301 Event setting (Tables 39-40, JT808-2013).
 *
 * settingType: 0=delete all, 1=upgrade (replace), 2=append, 3=modify, 4=delete specific
 * When settingType==4 the items list contains only event IDs to delete (content empty).
 */
public record EventSetting(int settingType, List<EventItem> items) {
    public record EventItem(int eventId, String content) {}
}
