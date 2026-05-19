package com.example.jt808.platform.gateway;

import java.util.concurrent.atomic.AtomicInteger;

class SequenceGenerator {
    private final AtomicInteger value = new AtomicInteger();

    int next() {
        return value.updateAndGet(current -> current >= 0xFFFF ? 1 : current + 1);
    }
}
