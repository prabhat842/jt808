package com.example.jt808sim.jt1078;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

final class AdtsFrameParser {
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    public synchronized List<byte[]> append(byte[] chunk, int length) {
        if (chunk != null && length > 0) {
            buffer.write(chunk, 0, length);
        }
        return drain(false);
    }

    public synchronized List<byte[]> finish() {
        return drain(true);
    }

    private List<byte[]> drain(boolean flushTail) {
        byte[] data = buffer.toByteArray();
        List<byte[]> frames = new ArrayList<>();
        int offset = 0;
        while (offset + 7 <= data.length) {
            if (!isSyncWord(data, offset)) {
                offset++;
                continue;
            }
            int frameLength = ((data[offset + 3] & 0x03) << 11)
                    | ((data[offset + 4] & 0xFF) << 3)
                    | ((data[offset + 5] & 0xE0) >> 5);
            if (frameLength <= 0) {
                offset++;
                continue;
            }
            if (offset + frameLength > data.length) {
                break;
            }
            frames.add(copyRange(data, offset, offset + frameLength));
            offset += frameLength;
        }

        if (offset > 0) {
            buffer.reset();
            if (offset < data.length) {
                buffer.write(data, offset, data.length - offset);
            }
        } else if (flushTail) {
            buffer.reset();
        }
        return frames;
    }

    private static boolean isSyncWord(byte[] data, int offset) {
        return (data[offset] & 0xFF) == 0xFF && (data[offset + 1] & 0xF0) == 0xF0;
    }

    private static byte[] copyRange(byte[] data, int start, int end) {
        byte[] out = new byte[end - start];
        System.arraycopy(data, start, out, 0, out.length);
        return out;
    }
}
