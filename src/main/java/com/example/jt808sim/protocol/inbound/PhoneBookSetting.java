package com.example.jt808sim.protocol.inbound;

import java.util.List;

/**
 * 0x8401 Phone book setting (Tables 51-52, JT808-2013).
 * settingType: 0=delete all, 1=upgrade (replace), 2=append, 3=modify by contact.
 */
public record PhoneBookSetting(int settingType, List<ContactItem> contacts) {
    public record ContactItem(int sign, String phoneNumber, String contactName) {
        // sign: 1=incoming, 2=outgoing, 3=both
    }
}
