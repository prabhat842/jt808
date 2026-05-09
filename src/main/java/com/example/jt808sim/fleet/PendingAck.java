package com.example.jt808sim.fleet;

import com.example.jt808sim.protocol.ServerAck;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

record PendingAck(int messageId, Instant sentAt, CompletableFuture<ServerAck> future) {
}
