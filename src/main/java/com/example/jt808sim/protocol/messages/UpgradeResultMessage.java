package com.example.jt808sim.protocol.messages;

import com.example.jt808sim.protocol.MessageIds;
import io.netty.buffer.ByteBuf;

/**
 * 0x0108 Notification of terminal upgrade result (Table 22, JT808-2013).
 *
 * Sent after the terminal completes (or fails) a firmware upgrade triggered
 * by a 0x8108 send-down-terminal-update-packet command.
 *
 * Body: upgrade type BYTE + result BYTE (0=success 1=failure 2=cancel).
 */
public class UpgradeResultMessage extends AbstractJt808Message {
    private final int upgradeType;
    private final int result;

    public UpgradeResultMessage(int sequence, String terminalId, int upgradeType, int result) {
        super(sequence, terminalId, true);
        this.upgradeType = upgradeType;
        this.result = result;
    }

    @Override
    public int messageId() {
        return MessageIds.TERMINAL_UPDATE_RESULT;
    }

    @Override
    public void encodeBody(ByteBuf out) {
        out.writeByte(upgradeType);
        out.writeByte(result);
    }
}
