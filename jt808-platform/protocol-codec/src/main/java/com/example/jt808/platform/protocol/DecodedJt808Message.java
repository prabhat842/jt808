package com.example.jt808.platform.protocol;

public record DecodedJt808Message(Jt808Header header, Object body) {
}
