package com.example.jt808sim.jt1078;

import com.example.jt808sim.protocol.Jt808Message;

public class Jt1078CommandHandler {
    public boolean isMediaCommand(Jt808Message message) {
        int id = message.header().messageId();
        return id == 0x9101 || id == 0x9102 || id == 0x9201 || id == 0x9202;
    }
}
