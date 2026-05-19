package com.example.jt808.platform.protocol;

import io.netty.buffer.ByteBuf;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

public final class Jt808CodecSupport {
    public static final Charset GBK = Charset.forName("GBK");

    private Jt808CodecSupport() {
    }

    public static String readBcdDigits(ByteBuf in, int byteLength) {
        StringBuilder value = new StringBuilder(byteLength * 2);
        for (int i = 0; i < byteLength; i++) {
            int b = in.readUnsignedByte();
            value.append((b >>> 4) & 0x0F);
            value.append(b & 0x0F);
        }
        return value.toString();
    }

    public static void writeBcdDigits(ByteBuf out, String digits, int byteLength) {
        String normalized = digits == null ? "" : digits.replaceAll("\\D", "");
        if (normalized.length() > byteLength * 2) {
            normalized = normalized.substring(normalized.length() - byteLength * 2);
        }
        normalized = "0".repeat(byteLength * 2 - normalized.length()) + normalized;
        for (int i = 0; i < normalized.length(); i += 2) {
            int high = Character.digit(normalized.charAt(i), 10);
            int low = Character.digit(normalized.charAt(i + 1), 10);
            out.writeByte((high << 4) | low);
        }
    }

    public static void writeBcdTimestamp(ByteBuf out, Instant instant, ZoneId zoneId) {
        java.time.ZonedDateTime ts = instant.atZone(zoneId);
        writeBcdDigits(out, String.format("%02d%02d%02d%02d%02d%02d",
                ts.getYear() % 100,
                ts.getMonthValue(),
                ts.getDayOfMonth(),
                ts.getHour(),
                ts.getMinute(),
                ts.getSecond()), 6);
    }

    public static Instant readBcdTimestamp(ByteBuf body, ZoneId zoneId) {
        int year = 2000 + readBcdByte(body);
        int month = readBcdByte(body);
        int day = readBcdByte(body);
        int hour = readBcdByte(body);
        int minute = readBcdByte(body);
        int second = readBcdByte(body);
        try {
            return LocalDateTime.of(year, month, day, hour, minute, second).atZone(zoneId).toInstant();
        } catch (DateTimeException e) {
            return Instant.EPOCH;
        }
    }

    public static int xor(ByteBuf buf, int startInclusive, int endExclusive) {
        int checksum = 0;
        for (int i = startInclusive; i < endExclusive; i++) {
            checksum ^= buf.getUnsignedByte(i);
        }
        return checksum & 0xFF;
    }

    static String readTrimmedAscii(ByteBuf body, int length) {
        int readable = Math.min(length, body.readableBytes());
        String value = body.toString(body.readerIndex(), readable, StandardCharsets.US_ASCII).trim();
        body.skipBytes(readable);
        return value;
    }

    private static int readBcdByte(ByteBuf body) {
        int value = body.readUnsignedByte();
        return ((value >>> 4) * 10) + (value & 0x0F);
    }
}
