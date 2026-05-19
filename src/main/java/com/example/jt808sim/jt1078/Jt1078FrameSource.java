package com.example.jt808sim.jt1078;

import java.util.List;

public interface Jt1078FrameSource {
    Jt1078Frame nextFrame(long frameIndex);

    default List<Jt1078Frame> nextFrames(long tickIndex) {
        Jt1078Frame frame = nextFrame(tickIndex);
        return frame == null ? List.of() : List.of(frame);
    }

    default void close() {
    }
}
