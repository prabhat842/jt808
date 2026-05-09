package com.example.jt808sim.jt1078;

import io.netty.buffer.ByteBuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class FileMediaSource implements MediaPayloadSource {
    private static final Logger log = LoggerFactory.getLogger(FileMediaSource.class);

    private final byte[] data;
    private int offset;

    public FileMediaSource(List<String> mediaFiles) {
        this.data = load(mediaFiles);
    }

    @Override
    public synchronized void writePayload(ByteBuf out, int bytes, long sequence) {
        if (data.length == 0) {
            new SyntheticMediaSource().writePayload(out, bytes, sequence);
            return;
        }
        for (int i = 0; i < bytes; i++) {
            out.writeByte(data[offset] & 0xFF);
            offset = (offset + 1) % data.length;
        }
    }

    private static byte[] load(List<String> mediaFiles) {
        if (mediaFiles == null || mediaFiles.isEmpty()) {
            log.warn("jt1078.streamMode=file selected but no mediaFiles configured; using synthetic payloads");
            return new byte[0];
        }
        String file = mediaFiles.get(ThreadLocalRandom.current().nextInt(mediaFiles.size()));
        try {
            byte[] bytes = Files.readAllBytes(Path.of(file));
            if (bytes.length == 0) {
                log.warn("media file {} is empty; using synthetic payloads", file);
            } else {
                log.info("streaming JT1078 payloads from {}", file);
            }
            return bytes;
        } catch (IOException e) {
            log.warn("failed to read media file {}; using synthetic payloads", file, e);
            return new byte[0];
        }
    }
}
