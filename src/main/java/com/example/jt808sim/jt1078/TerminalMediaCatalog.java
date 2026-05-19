package com.example.jt808sim.jt1078;

import com.example.jt808sim.config.VehicleIdentity;
import com.example.jt808sim.jt1078.messages.ResourceListEntry;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class TerminalMediaCatalog {
    private static final AtomicLong IDS = new AtomicLong();
    private final List<RecordedMediaResource> resources;

    public TerminalMediaCatalog(List<RecordedMediaResource> resources) {
        this.resources = List.copyOf(resources);
    }

    public static TerminalMediaCatalog seed(VehicleIdentity identity) {
        if (!identity.isMediaCapable() || identity.getMediaChannels().isEmpty()) {
            return new TerminalMediaCatalog(List.of());
        }
        Instant now = Instant.now();
        List<RecordedMediaResource> resources = new ArrayList<>();
        for (int channel : identity.getMediaChannels()) {
            resources.add(new RecordedMediaResource(
                    IDS.incrementAndGet(),
                    channel,
                    now.minus(Duration.ofMinutes(15)),
                    now.minus(Duration.ofMinutes(10)),
                    0,
                    0,
                    2,
                    1,
                    1,
                    512_000L));
            resources.add(new RecordedMediaResource(
                    IDS.incrementAndGet(),
                    channel,
                    now.minus(Duration.ofMinutes(8)),
                    now.minus(Duration.ofMinutes(3)),
                    0,
                    0,
                    2,
                    1,
                    1,
                    768_000L));
        }
        return new TerminalMediaCatalog(resources);
    }

    public List<ResourceListEntry> query(Jt1078Command.QueryResourceList query) {
        return filtered(
                query.channel(),
                decodeTimestamp(query.startTime()),
                decodeTimestamp(query.endTime()),
                query.alarmHigh(),
                query.alarmLow(),
                query.resourceType(),
                query.streamType(),
                query.storageType()).stream()
                .map(resource -> new ResourceListEntry(
                        resource.channel(),
                        resource.startTime(),
                        resource.endTime(),
                        resource.alarmFlagsHigh(),
                        resource.alarmFlagsLow(),
                        resource.audioVideoType(),
                        resource.streamType(),
                        resource.memoryType(),
                        resource.fileSizeBytes()))
                .toList();
    }

    public PlaybackSelection playbackTarget(Jt1078Command.PlaybackRequest request) {
        Instant requestStart = decodeTimestamp(request.startTime());
        Instant requestEnd = decodeTimestamp(request.endTime());
        RecordedMediaResource resource = filtered(
                request.channel(),
                requestStart,
                requestEnd,
                0,
                0,
                request.audioVideoType(),
                request.streamType(),
                request.storageType()).stream()
                .min(Comparator.comparing(RecordedMediaResource::startTime))
                .orElse(null);
        if (resource == null) {
            return null;
        }
        Instant effectiveStart = resource.clippedStart(requestStart);
        Instant effectiveEnd = resource.clippedEnd(requestEnd);
        if (effectiveEnd.isBefore(effectiveStart)) {
            return null;
        }
        return new PlaybackSelection(resource, effectiveStart, effectiveEnd);
    }

    public List<RecordedMediaResource> uploadTargets(Jt1078Command.FileUploadCommand request) {
        return filtered(
                request.channel(),
                decodeTimestamp(request.startTime()),
                decodeTimestamp(request.endTime()),
                request.alarmHigh(),
                request.alarmLow(),
                request.resourceType(),
                request.streamType(),
                request.storageType());
    }

    private List<RecordedMediaResource> filtered(
            int channel,
            Instant startTime,
            Instant endTime,
            long alarmHigh,
            long alarmLow,
            int resourceType,
            int streamType,
            int storageType) {
        return resources.stream()
                .filter(resource -> resource.matches(channel, resourceType, streamType, storageType))
                .filter(resource -> resource.overlaps(startTime, endTime))
                .filter(resource -> resource.matchesAlarmFlags(alarmHigh, alarmLow))
                .toList();
    }

    private static Instant decodeTimestamp(byte[] bcdTimestamp) {
        if (bcdTimestamp == null || bcdTimestamp.length < 6) {
            return null;
        }
        boolean allZero = true;
        for (int i = 0; i < 6; i++) {
            if ((bcdTimestamp[i] & 0xFF) != 0) {
                allZero = false;
                break;
            }
        }
        if (allZero) {
            return null;
        }
        try {
            int year = 2000 + bcd(bcdTimestamp[0]);
            int month = bcd(bcdTimestamp[1]);
            int day = bcd(bcdTimestamp[2]);
            int hour = bcd(bcdTimestamp[3]);
            int minute = bcd(bcdTimestamp[4]);
            int second = bcd(bcdTimestamp[5]);
            return LocalDateTime.of(year, month, day, hour, minute, second)
                    .atZone(ZoneId.systemDefault())
                    .toInstant();
        } catch (DateTimeException ignored) {
            return null;
        }
    }

    private static int bcd(byte value) {
        return ((value >> 4) & 0x0F) * 10 + (value & 0x0F);
    }
}
