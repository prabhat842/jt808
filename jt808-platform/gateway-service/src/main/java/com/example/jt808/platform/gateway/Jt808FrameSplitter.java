package com.example.jt808.platform.gateway;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

class Jt808FrameSplitter {
    private final ByteArrayOutputStream current = new ByteArrayOutputStream(256);
    private boolean inFrame;

    List<byte[]> push(byte[] chunk) {
        List<byte[]> frames = new ArrayList<>();
        for (byte value : chunk) {
            int b = value & 0xFF;
            if (b == 0x7E) {
                if (inFrame && current.size() > 0) {
                    frames.add(current.toByteArray());
                    current.reset();
                }
                inFrame = true;
            } else if (inFrame) {
                current.write(b);
            }
        }
        return frames;
    }
}
