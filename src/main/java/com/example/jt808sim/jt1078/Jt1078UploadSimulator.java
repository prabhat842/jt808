package com.example.jt808sim.jt1078;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;
import java.util.function.LongUnaryOperator;

/**
 * Simulates FTP file upload lifecycle for 0x9206/0x9207/0x1206 (§5.6.5–5.6.7, JT/T 1078-2016).
 *
 * On startUpload(): schedules delayed 0x1206 completion notification proportional to file size.
 * On control(): responds to 0x9207 pause (0), resume (1), or cancel (2).
 */
public class Jt1078UploadSimulator {
    private static final Logger log = LoggerFactory.getLogger(Jt1078UploadSimulator.class);
    private static final long BYTES_PER_SECOND = 500_000L;
    private static final long MAX_DELAY_SECONDS = 30L;

    private final LongUnaryOperator delayFn;

    private record UploadEntry(int requestSeq, AtomicBoolean cancelled) {}
    private final ConcurrentHashMap<Integer, UploadEntry> active = new ConcurrentHashMap<>();

    public Jt1078UploadSimulator() {
        this(bytes -> Math.max(1L, Math.min(MAX_DELAY_SECONDS, bytes / BYTES_PER_SECOND)));
    }

    Jt1078UploadSimulator(LongUnaryOperator delayFn) {
        this.delayFn = delayFn;
    }

    /**
     * Starts a simulated upload for the given 0x9206 request sequence.
     * Calls onComplete with result 0 (success) or 1 (cancelled/failure) after simulated delay.
     */
    public void startUpload(int requestSeq, long totalBytes,
                            ScheduledExecutorService executor, IntConsumer onComplete) {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        active.put(requestSeq, new UploadEntry(requestSeq, cancelled));
        long delaySeconds = delayFn.applyAsLong(totalBytes);
        executor.schedule(() -> {
            active.remove(requestSeq);
            int result = cancelled.get() ? 1 : 0;
            log.debug("upload simulation done: seq={} result={}", requestSeq, result);
            onComplete.accept(result);
        }, delaySeconds, TimeUnit.SECONDS);
        log.debug("upload simulation started: seq={} bytes={} delay={}s", requestSeq, totalBytes, delaySeconds);
    }

    /**
     * Handles 0x9207 file upload control. command: 0=suspend, 1=continue, 2=cancel.
     * Cancel marks the upload as failed; the next completion callback fires with result=1.
     */
    public void control(int requestSeq, int command) {
        UploadEntry entry = active.get(requestSeq);
        if (entry == null) {
            return;
        }
        if (command == 2) {
            entry.cancelled().set(true);
            log.debug("upload cancelled: seq={}", requestSeq);
        }
        // Suspend (0) and resume (1) are acknowledged but don't change timing in this simulator.
    }
}
