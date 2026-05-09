package com.example.jt808sim.protocol;

import java.util.concurrent.atomic.AtomicInteger;

public class SequenceGenerator {
    private final AtomicInteger next = new AtomicInteger(1);

    public int next() {
        return next.getAndUpdate(value -> value >= 0xFFFF ? 1 : value + 1);
    }
}
