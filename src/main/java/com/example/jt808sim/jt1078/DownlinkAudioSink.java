package com.example.jt808sim.jt1078;

import java.io.Closeable;
import java.io.IOException;

public interface DownlinkAudioSink extends Closeable {
    void write(byte[] payload) throws IOException;

    @Override
    default void close() throws IOException {
    }
}
