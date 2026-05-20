package com.example.jt808.platform.protocol;

/**
 * Decoded body for 0x0800 multimedia event message and 0x0801 multimedia data upload (JT808-2013).
 *
 * 0x0800 (multimedia event) carries only the header fields; location and payloadBytes are absent.
 * 0x0801 (multimedia data upload) carries the full header + embedded location + payload size.
 *
 * mediaType:  0=image  1=audio  2=video
 * formatCode: 0=JPEG   1=TIF    2=MP3   3=WAV  4=WMV
 * eventCode:  0=platform-cmd  1=timing  2=distance  3=alarm  4=manual
 */
public record MultimediaUploadBody(
        long multimediaId,
        int  mediaType,
        int  formatCode,
        int  eventCode,
        int  channelId,
        TerminalLocationReport location,  // null for 0x0800
        int  payloadBytes                 // 0 for 0x0800
) {
    public boolean isDataUpload() {
        return location != null;
    }

    public String mediaTypeLabel() {
        return switch (mediaType) {
            case 0 -> "image";
            case 1 -> "audio";
            case 2 -> "video";
            default -> "unknown";
        };
    }

    public String formatExtension() {
        return switch (formatCode) {
            case 0 -> "jpg";
            case 1 -> "tif";
            case 2 -> "mp3";
            case 3 -> "wav";
            case 4 -> "wmv";
            default -> "bin";
        };
    }
}
