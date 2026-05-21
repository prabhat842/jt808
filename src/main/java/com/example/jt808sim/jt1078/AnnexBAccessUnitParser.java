package com.example.jt808sim.jt1078;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Groups H.264 Annex-B NAL units into complete access units.
 *
 * A H.264 access unit = all NAL units belonging to one video frame.
 * For libx264 baseline / single-slice output this is:
 *   I-frame: [SPS] [PPS] [IDR slice]      — all flushed together
 *   P-frame: [P-slice]                     — flushed alone
 *
 * Boundary rule: flush the current accumulator when a coded-slice NAL
 * (type 1 or 5) has been accumulated AND the NEXT NAL unit arrives
 * (i.e., the previous NAL ended a picture).
 *
 * This ensures exactly one access unit is emitted per video frame at
 * the camera's fps, preventing queue overflow and H.264 stream corruption
 * from dropped NAL units.
 */
final class AnnexBAccessUnitParser {

    private final ByteArrayOutputStream current = new ByteArrayOutputStream(16_384);
    private boolean lastWasCodedSlice = false;

    /** Feed a chunk of raw bytes from the FFmpeg stdout pipe. */
    public synchronized List<byte[]> append(byte[] chunk, int length) {
        List<byte[]> result = new ArrayList<>();
        if (chunk == null || length <= 0) return result;

        // Find all start-code positions in the incoming chunk, combined with any
        // leftover bytes already in `current`.
        byte[] data = mergeWithCurrent(chunk, length);

        List<int[]> nals = splitNals(data); // each int[] = {offset, length}

        for (int[] nal : nals) {
            int nalOffset = nal[0];
            int nalLen    = nal[1];
            int nalType   = nalType(data, nalOffset, nalLen);

            boolean isCodedSlice = (nalType == 1 || nalType == 2 || nalType == 5);

            if (lastWasCodedSlice && current.size() > 0) {
                // Previous NAL was the last in its picture — flush that picture now
                result.add(current.toByteArray());
                current.reset();
            }

            current.write(data, nalOffset, nalLen);
            lastWasCodedSlice = isCodedSlice;
        }
        return result;
    }

    /** Flush any remaining bytes (called when FFmpeg stdout closes). */
    public synchronized List<byte[]> finish() {
        List<byte[]> result = new ArrayList<>();
        if (current.size() > 0) {
            result.add(current.toByteArray());
            current.reset();
        }
        lastWasCodedSlice = false;
        return result;
    }

    // ── internals ─────────────────────────────────────────────────────────

    /** Prepend any bytes already buffered in `current` so split NALs reunite. */
    private byte[] mergeWithCurrent(byte[] chunk, int length) {
        if (current.size() == 0) {
            // Fast path: no leftover, check if chunk starts with start code
            byte[] copy = new byte[length];
            System.arraycopy(chunk, 0, copy, 0, length);
            return copy;
        }
        // Merge leftover + new chunk, then reset current (we'll re-fill it below)
        byte[] merged = new byte[current.size() + length];
        current.toByteArray(); // side-effect-free; we do it properly:
        byte[] old = current.toByteArray();
        System.arraycopy(old, 0, merged, 0, old.length);
        System.arraycopy(chunk, 0, merged, old.length, length);
        current.reset();
        lastWasCodedSlice = false; // state will be reconstructed from the merged data
        return merged;
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

    /** Extract the 5-bit NAL unit type from the first NAL byte after the start code. */
    private static int nalType(byte[] data, int offset, int len) {
        // skip the start code (3 or 4 bytes)
        int sc = 3;
        if (offset + 3 < data.length && data[offset + 2] == 0) sc = 4; // 00 00 00 01
        int nalHeaderOffset = offset + sc;
        if (nalHeaderOffset >= data.length || nalHeaderOffset >= offset + len) return -1;
        return data[nalHeaderOffset] & 0x1F;
    }
}
