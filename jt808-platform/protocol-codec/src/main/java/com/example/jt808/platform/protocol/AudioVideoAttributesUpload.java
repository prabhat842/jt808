package com.example.jt808.platform.protocol;

/**
 * Decoded body of 0x1003 terminal upload audio/video attributes (Table 11, JT/T 1078-2016).
 * Sent by the terminal in response to 0x9003 query.
 *
 * audioEncoding / videoEncoding are Table 12 codec codes:
 *   7=G.711U  8=G.726  19=AAC  98=H.264  99=H.265
 * audioSampleRate: 0=8kHz  1=22.05kHz  2=44.1kHz  3=48kHz
 * audioSampleBits: 0=8bit  1=16bit  2=32bit
 */
public record AudioVideoAttributesUpload(
        int     audioEncoding,
        int     audioChannels,
        int     audioSampleRate,
        int     audioSampleBits,
        int     audioFrameLength,
        boolean audioOutputSupported,
        int     videoEncoding,
        int     maxAudioChannels,
        int     maxVideoChannels
) {
}
