package com.example.jt808sim.jt1078;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

final class AnnexBAccessUnitParser {
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    public synchronized List<byte[]> append(byte[] chunk, int length) {
        if (chunk != null && length > 0) {
            buffer.write(chunk, 0, length);
        }
        return drain(false);
    }

    public synchronized List<byte[]> finish() {
        return drain(true);
    }

    private List<byte[]> drain(boolean flushTail) {
        byte[] data = buffer.toByteArray();
        List<Integer> starts = findStartCodes(data);
        List<byte[]> accessUnits = new ArrayList<>();
        if (starts.isEmpty()) {
            if (flushTail && data.length > 0) {
                accessUnits.add(data);
                buffer.reset();
            }
            return accessUnits;
        }

        int completeCount = flushTail ? starts.size() : Math.max(0, starts.size() - 1);
        int consumed = 0;
        for (int i = 0; i < completeCount; i++) {
            int start = starts.get(i);
            int end = (i + 1 < starts.size()) ? starts.get(i + 1) : data.length;
            if (end > start) {
                accessUnits.add(copyRange(data, start, end));
                consumed = end;
            }
        }

        if (flushTail && completeCount == 0 && data.length > 0) {
            accessUnits.add(data);
            consumed = data.length;
        }

        if (consumed > 0) {
            buffer.reset();
            if (consumed < data.length) {
                buffer.write(data, consumed, data.length - consumed);
            }
        }
        return accessUnits;
    }

    private static List<Integer> findStartCodes(byte[] data) {
        List<Integer> starts = new ArrayList<>();
        for (int i = 0; i <= data.length - 4; i++) {
            if (isStartCode(data, i)) {
                starts.add(i);
                i += 2;
            }
        }
        return starts;
    }

    private static boolean isStartCode(byte[] data, int index) {
        return data[index] == 0 && data[index + 1] == 0
                && ((data[index + 2] == 1) || (data[index + 2] == 0 && data[index + 3] == 1));
    }

    private static byte[] copyRange(byte[] data, int start, int end) {
        byte[] out = new byte[end - start];
        System.arraycopy(data, start, out, 0, out.length);
        return out;
    }
}
