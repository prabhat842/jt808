package com.example.jt808sim.protocol;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Decoded body of a 0x8103 terminal parameter setting message.
 *
 * Each parameter is stored as raw bytes keyed by its 4-byte parameter ID.
 * Use {@link #getDword} for DWORD (4-byte unsigned int) values and
 * {@link #getString} for STRING (GBK-encoded) values.
 *
 * Commonly used parameter IDs (spec Table 12):
 *   0x0001  heartbeat interval, seconds (DWORD)
 *   0x0002  TCP response timeout, seconds (DWORD)
 *   0x0013  main server address, IP or domain (STRING)
 *   0x0018  server TCP port (DWORD)
 *   0x0029  location report interval (default), seconds (DWORD)
 */
public record ParameterSetting(Map<Integer, byte[]> params) {
    private static final Charset GBK = Charset.forName("GBK");

    public static ParameterSetting of(Map<Integer, byte[]> params) {
        return new ParameterSetting(Collections.unmodifiableMap(params));
    }

    /** Returns parameter value as an unsigned 32-bit integer if present and long enough. */
    public OptionalLong getDword(int id) {
        byte[] raw = params.get(id);
        if (raw == null || raw.length < 4) return OptionalLong.empty();
        long v = ((raw[0] & 0xFFL) << 24)
                | ((raw[1] & 0xFFL) << 16)
                | ((raw[2] & 0xFFL) << 8)
                | (raw[3] & 0xFFL);
        return OptionalLong.of(v);
    }

    /** Returns parameter value as a GBK string if present. */
    public Optional<String> getString(int id) {
        byte[] raw = params.get(id);
        if (raw == null) return Optional.empty();
        return Optional.of(new String(raw, GBK).trim());
    }

    public boolean has(int id) {
        return params.containsKey(id);
    }
}
