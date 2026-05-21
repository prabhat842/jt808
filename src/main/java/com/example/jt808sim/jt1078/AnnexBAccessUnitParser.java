package com.example.jt808sim.jt1078;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Groups H.264 Annex-B NAL units into complete access units.
 *
 * A H.264 access unit = all NAL units belonging to one video frame.
 * For libx264 output this is usually:
 *   I-frame: [SPS] [PPS] [SEI] [IDR slice...]
 *   P-frame: [P-slice...]
 *
 * Boundary rule: flush the current access unit when a new picture starts.
 * For coded slices, the H.264 slice header's first_mb_in_slice value is zero
 * on the first slice of a picture and non-zero on later slices of that same
 * picture. This matters because x264 can emit multiple slice NALs per frame.
 *
 * This ensures exactly one access unit is emitted per video frame at
 * the camera's fps, preventing queue overflow and H.264 stream corruption
 * from dropped NAL units.
 */
final class AnnexBAccessUnitParser {

    private final ByteArrayOutputStream currentAccessUnit = new ByteArrayOutputStream(16_384);
    private final ByteArrayOutputStream pendingBytes = new ByteArrayOutputStream(8_192);
    private boolean currentHasCodedSlice;

    /** Feed a chunk of raw bytes from the FFmpeg stdout pipe. */
    public synchronized List<byte[]> append(byte[] chunk, int length) {
        List<byte[]> result = new ArrayList<>();
        if (chunk == null || length <= 0) return result;

        pendingBytes.write(chunk, 0, length);
        byte[] data = pendingBytes.toByteArray();
        List<int[]> nals = splitNals(data);
        if (nals.isEmpty()) {
            return result;
        }

        int completeNalCount = Math.max(0, nals.size() - 1);
        for (int i = 0; i < completeNalCount; i++) {
            processNal(result, data, nals.get(i)[0], nals.get(i)[1], true);
        }

        int[] last = nals.get(nals.size() - 1);
        if (last[0] > 0) {
            pendingBytes.reset();
            pendingBytes.write(data, last[0], data.length - last[0]);
        }

        if (startsNextAccessUnit(data, last[0], last[1])) {
            flushCurrent(result);
        }
        return result;
    }

    /** Flush any remaining bytes (called when FFmpeg stdout closes). */
    public synchronized List<byte[]> finish() {
        List<byte[]> result = new ArrayList<>();
        byte[] pending = pendingBytes.toByteArray();
        for (int[] nal : splitNals(pending)) {
            processNal(result, pending, nal[0], nal[1], false);
        }
        pendingBytes.reset();
        flushCurrent(result);
        currentHasCodedSlice = false;
        return result;
    }

    // ── internals ─────────────────────────────────────────────────────────

    private void processNal(List<byte[]> result, byte[] data, int offset, int length, boolean complete) {
        if (length <= 0) {
            return;
        }
        int nalType = nalType(data, offset, length);
        if (currentHasCodedSlice && startsNextAccessUnit(nalType, data, offset, length)) {
            flushCurrent(result);
        }
        currentAccessUnit.write(data, offset, length);
        if (complete && isCodedSlice(nalType)) {
            currentHasCodedSlice = true;
        }
    }

    private void flushCurrent(List<byte[]> result) {
        if (currentAccessUnit.size() == 0) {
            return;
        }
        result.add(currentAccessUnit.toByteArray());
        currentAccessUnit.reset();
        currentHasCodedSlice = false;
    }

    private static boolean startsNextAccessUnit(byte[] data, int offset, int length) {
        return startsNextAccessUnit(nalType(data, offset, length), data, offset, length);
    }

    private static boolean startsNextAccessUnit(int nalType, byte[] data, int offset, int length) {
        if (isCodedSlice(nalType)) {
            return firstMbInSlice(data, offset, length) == 0;
        }
        return nalType == 6 || nalType == 7 || nalType == 8 || nalType == 9;
    }

    /**
     * Split a byte array into NAL unit segments (each starting with 00 00 01 or
     * 00 00 00 01). Returns [{offset, length}] for each NAL found.
     * The last NAL extends to the end of data and is always included (it may be
     * incomplete, which is handled by the caller accumulating it in `current`).
     */
    private static List<int[]> splitNals(byte[] data) {
        List<Integer> starts = new ArrayList<>();
        for (int i = 0; i <= data.length - 3; i++) {
            if (data[i] == 0 && data[i + 1] == 0) {
                if (i + 2 < data.length && data[i + 2] == 1) {
                    starts.add(i);
                    i += 2;
                } else if (i + 3 < data.length && data[i + 2] == 0 && data[i + 3] == 1) {
                    starts.add(i);
                    i += 3;
                }
            }
        }
        List<int[]> nals = new ArrayList<>(starts.size());
        for (int k = 0; k < starts.size(); k++) {
            int off = starts.get(k);
            int end = (k + 1 < starts.size()) ? starts.get(k + 1) : data.length;
            nals.add(new int[]{off, end - off});
        }
        return nals;
    }

    private static boolean isCodedSlice(int nalType) {
        return nalType >= 1 && nalType <= 5;
    }

    /** Extract the 5-bit NAL unit type from the first NAL byte after the start code. */
    private static int nalType(byte[] data, int offset, int len) {
        int sc = startCodeLength(data, offset);
        int nalHeaderOffset = offset + sc;
        if (nalHeaderOffset >= data.length || nalHeaderOffset >= offset + len) return -1;
        return data[nalHeaderOffset] & 0x1F;
    }

    private static int firstMbInSlice(byte[] data, int offset, int len) {
        int sc = startCodeLength(data, offset);
        int rbspOffset = offset + sc + 1;
        int end = offset + len;
        if (rbspOffset >= end) {
            return -1;
        }
        byte[] rbsp = removeEmulationPrevention(data, rbspOffset, end);
        return readUnsignedExpGolomb(rbsp);
    }

    private static int startCodeLength(byte[] data, int offset) {
        if (offset + 3 < data.length
                && data[offset] == 0
                && data[offset + 1] == 0
                && data[offset + 2] == 1) {
            return 3;
        }
        if (offset + 4 < data.length
                && data[offset] == 0
                && data[offset + 1] == 0
                && data[offset + 2] == 0
                && data[offset + 3] == 1) {
            return 4;
        }
        return 0;
    }

    private static byte[] removeEmulationPrevention(byte[] data, int offset, int end) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(end - offset);
        int zeroCount = 0;
        for (int i = offset; i < end; i++) {
            byte value = data[i];
            if (zeroCount >= 2 && value == 0x03) {
                zeroCount = 0;
                continue;
            }
            out.write(value);
            zeroCount = value == 0 ? zeroCount + 1 : 0;
        }
        return out.toByteArray();
    }

    private static int readUnsignedExpGolomb(byte[] data) {
        int bitCount = data.length * 8;
        int bitIndex = 0;
        int leadingZeroBits = 0;
        while (bitIndex < bitCount && readBit(data, bitIndex) == 0) {
            leadingZeroBits++;
            bitIndex++;
        }
        if (bitIndex >= bitCount) {
            return -1;
        }
        bitIndex++;
        int value = 1;
        for (int i = 0; i < leadingZeroBits; i++) {
            value = (value << 1) | readBit(data, bitIndex++);
        }
        return value - 1;
    }

    private static int readBit(byte[] data, int bitIndex) {
        int byteIndex = bitIndex >>> 3;
        int bitInByte = 7 - (bitIndex & 7);
        return (data[byteIndex] >>> bitInByte) & 1;
    }
}
