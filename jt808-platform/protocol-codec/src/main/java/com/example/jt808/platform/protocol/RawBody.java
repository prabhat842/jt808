package com.example.jt808.platform.protocol;

public record RawBody(byte[] bytes) {
    public RawBody {
        bytes = bytes == null ? new byte[0] : bytes.clone();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }
}
