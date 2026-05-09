package com.example.jt808sim.protocol;

import io.netty.buffer.ByteBuf;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public final class Jt808CodecSupport {
    public static final Charset GBK = Charset.forName("GBK");

    private Jt808CodecSupport() {
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

    public static String readBcdDigits(ByteBuf in, int byteLength) {
        StringBuilder value = new StringBuilder(byteLength * 2);
        for (int i = 0; i < byteLength; i++) {
            int b = in.readUnsignedByte();
            value.append((b >>> 4) & 0x0F);
            value.append(b & 0x0F);
        }
        return value.toString();
    }

    public static void writeFixedAscii(ByteBuf out, String value, int length) {
        writeFixedBytes(out, value, length, StandardCharsets.US_ASCII);
    }

    public static void writeFixedGbk(ByteBuf out, String value, int length) {
        writeFixedBytes(out, value, length, GBK);
    }

    public static void writeFixedBytes(ByteBuf out, String value, int length, Charset charset) {
        byte[] raw = value == null ? new byte[0] : value.getBytes(charset);
        int copyLength = Math.min(raw.length, length);
        out.writeBytes(raw, 0, copyLength);
        out.writeZero(length - copyLength);
    }

    public static void writeBcdTimestamp(ByteBuf out, java.time.Instant instant) {
        java.time.ZonedDateTime ts = instant.atZone(java.time.ZoneId.systemDefault());
        String value = String.format("%02d%02d%02d%02d%02d%02d",
                ts.getYear() % 100,
                ts.getMonthValue(),
                ts.getDayOfMonth(),
                ts.getHour(),
                ts.getMinute(),
                ts.getSecond());
        writeBcdDigits(out, value, 6);
    }

    public static int xor(ByteBuf buf, int startInclusive, int endExclusive) {
        int checksum = 0;
        for (int i = startInclusive; i < endExclusive; i++) {
            checksum ^= buf.getUnsignedByte(i);
        }
        return checksum & 0xFF;
    }
}
