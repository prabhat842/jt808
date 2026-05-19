package com.example.jt808sim.jt1078;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class FileDownlinkAudioSink implements DownlinkAudioSink {
    private final String terminalId;
    private final int channelId;
    private final String outputDirectory;
    private OutputStream output;

    public FileDownlinkAudioSink(String terminalId, int channelId, String outputDirectory) {
        this.terminalId = terminalId;
        this.channelId = channelId;
        this.outputDirectory = outputDirectory;
    }

    @Override
    public void write(byte[] payload) throws IOException {
        output().write(payload);
        output().flush();
    }

    @Override
    public void close() throws IOException {
        if (output != null) {
            output.close();
            output = null;
        }
    }

    private OutputStream output() throws IOException {
        if (output == null) {
            Path dir = Path.of(outputDirectory == null || outputDirectory.isBlank() ? "tmp/jt1078-downlink" : outputDirectory);
            Files.createDirectories(dir);
            Path file = dir.resolve(terminalId + "-ch" + channelId + ".aac");
            output = Files.newOutputStream(file, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        return output;
    }
}
