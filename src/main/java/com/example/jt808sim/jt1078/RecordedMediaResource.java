package com.example.jt808sim.jt1078;

import java.time.Instant;

public record RecordedMediaResource(
        long resourceId,
        int channel,
        Instant startTime,
        Instant endTime,
        long alarmFlagsHigh,
        long alarmFlagsLow,
        int audioVideoType,
        int streamType,
        int memoryType,
        long fileSizeBytes) {

    public boolean matches(
            int channelFilter,
            int resourceTypeFilter,
            int streamTypeFilter,
            int storageTypeFilter) {
        return matchesChannel(channelFilter)
                && matchesResourceType(resourceTypeFilter)
                && matchesStreamType(streamTypeFilter)
                && matchesStorageType(storageTypeFilter);
    }

    public boolean overlaps(Instant rangeStart, Instant rangeEnd) {
        if (rangeStart == null && rangeEnd == null) {
            return true;
        }
        if (rangeStart != null && endTime.isBefore(rangeStart)) {
            return false;
        }
        if (rangeEnd != null && startTime.isAfter(rangeEnd)) {
            return false;
        }
        return true;
    }

    public boolean matchesAlarmFlags(long alarmHighFilter, long alarmLowFilter) {
        boolean highMatches = alarmHighFilter == 0 || (alarmFlagsHigh & alarmHighFilter) == alarmHighFilter;
        boolean lowMatches = alarmLowFilter == 0 || (alarmFlagsLow & alarmLowFilter) == alarmLowFilter;
        return highMatches && lowMatches;
    }

    public Instant clippedStart(Instant rangeStart) {
        return rangeStart == null || startTime.isAfter(rangeStart) ? startTime : rangeStart;
    }

    public Instant clippedEnd(Instant rangeEnd) {
        return rangeEnd == null || endTime.isBefore(rangeEnd) ? endTime : rangeEnd;
    }

    private boolean matchesChannel(int channelFilter) {
        return channelFilter == 0 || channelFilter == channel;
    }

    private boolean matchesResourceType(int resourceTypeFilter) {
        if (resourceTypeFilter == 0) {
            return true;
        }
        if (resourceTypeFilter == 3) {
            return audioVideoType == 0 || audioVideoType == 2;
        }
        return audioVideoType == resourceTypeFilter;
    }

    private boolean matchesStreamType(int streamTypeFilter) {
        return streamTypeFilter == 0 || streamType == streamTypeFilter;
    }

    private boolean matchesStorageType(int storageTypeFilter) {
        return storageTypeFilter == 0 || memoryType == storageTypeFilter;
    }
}
