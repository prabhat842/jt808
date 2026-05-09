package com.example.jt808sim.protocol;

public class Jt808Message {
    private final Jt808Header header;
    private final Object body;

    public Jt808Message(Jt808Header header, Object body) {
        this.header = header;
        this.body = body;
    }

    public Jt808Header header() {
        return header;
    }

    public Object body() {
        return body;
    }
}
