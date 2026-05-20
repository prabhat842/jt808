package com.example.jt808sim.jt1078;

import java.util.Arrays;

/**
 * Audio frame source emitting G.711 mu-law (PCMU) silence frames.
 * 8 kHz, 8-bit, 160 samples per 20 ms frame. 0xFF = silence in mu-law encoding.
 * Audio codec code 7 (G.711U) per Table 12 of JT/T 1078-2016.
 */
public class G711AudioFrameSource implements Jt1078FrameSource {
    private static final int FRAME_BYTES = 160;     // 8kHz × 20ms
    private static final int PERIOD_MILLIS = 20;
    private static final byte MULAW_SILENCE = (byte) 0xFF;
    private static final byte[] SILENCE_FRAME = new byte[FRAME_BYTES];

    static {
        Arrays.fill(SILENCE_FRAME, MULAW_SILENCE);
    }

    @Override
    public Jt1078Frame nextFrame(long frameIndex) {
        return new Jt1078Frame(
                Jt1078FrameType.AUDIO,
                frameIndex * PERIOD_MILLIS,
                false, 0, 0,
                SILENCE_FRAME.clone());
    }
}
