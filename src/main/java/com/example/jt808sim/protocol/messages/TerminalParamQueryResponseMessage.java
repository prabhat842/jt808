package com.example.jt808sim.protocol.messages;

import com.example.jt808sim.protocol.MessageIds;
import io.netty.buffer.ByteBuf;

import java.util.Map;

/**
 * 0x0104 Check terminal parameter response (Table 16, JT808-2013).
 *
 * Body: response serial WORD + parameter count BYTE + TLV list (same format as Table 10/11).
 * Each item: param ID DWORD + length BYTE + value BYTE[n].
 */
public class TerminalParamQueryResponseMessage extends AbstractJt808Message {
    private final int responseSerial;
    private final Map<Integer, byte[]> parameters;

    public TerminalParamQueryResponseMessage(int sequence, String terminalId,
                                             int responseSerial, Map<Integer, byte[]> parameters) {
        super(sequence, terminalId, true);
        this.responseSerial = responseSerial;
        this.parameters = parameters;
    }

    @Override
    public int messageId() {
        return MessageIds.TERMINAL_PARAM_QUERY_RESP;
    }

    @Override
    public void encodeBody(ByteBuf out) {
        out.writeShort(responseSerial);
        out.writeByte(parameters.size());
        for (Map.Entry<Integer, byte[]> entry : parameters.entrySet()) {
            out.writeInt(entry.getKey());
            out.writeByte(entry.getValue().length);
            out.writeBytes(entry.getValue());
        }
    }
}
