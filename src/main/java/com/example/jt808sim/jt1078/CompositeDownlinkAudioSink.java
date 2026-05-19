package com.example.jt808sim.jt1078;

import java.io.IOException;
import java.util.List;

public class CompositeDownlinkAudioSink implements DownlinkAudioSink {
    private final List<DownlinkAudioSink> sinks;

    public CompositeDownlinkAudioSink(List<DownlinkAudioSink> sinks) {
        this.sinks = List.copyOf(sinks);
    }

    @Override
    public void write(byte[] payload) throws IOException {
        IOException failure = null;
        for (DownlinkAudioSink sink : sinks) {
            try {
                sink.write(payload);
            } catch (IOException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    @Override
    public void close() throws IOException {
        IOException failure = null;
        for (DownlinkAudioSink sink : sinks) {
            try {
                sink.close();
            } catch (IOException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
