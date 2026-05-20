package com.example.jt808sim.protocol.inbound;

import java.util.List;

/**
 * 0x8303 Information on-demand menu setting (Tables 46-47, JT808-2013).
 *
 * settingType: 0=delete all items, 1=upgrade, 2=append, 3=modify
 */
public record InfoOnDemandMenuSetting(int settingType, List<InfoMenuItem> items) {
    public record InfoMenuItem(int infoType, String name) {}
}
