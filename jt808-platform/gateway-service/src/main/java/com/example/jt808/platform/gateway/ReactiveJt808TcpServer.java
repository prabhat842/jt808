package com.example.jt808.platform.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.netty.DisposableServer;
import reactor.netty.tcp.TcpServer;

import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicReference;

@Component
class ReactiveJt808TcpServer implements SmartLifecycle {
    private static final Logger log = LoggerFactory.getLogger(ReactiveJt808TcpServer.class);

    private final GatewayProperties properties;
    private final Jt808MessageProcessor processor;
    private DisposableServer signalingServer;
    private DisposableServer fileServer;
    private volatile boolean running;

    ReactiveJt808TcpServer(GatewayProperties properties, Jt808MessageProcessor processor) {
        this.properties = properties;
        this.processor = processor;
    }

    @Override
    public void start() {
        signalingServer = bind(properties.getSignalingPort(), "signaling");
        fileServer = bind(properties.getFilePort(), "file");
        running = true;
    }

    @Override
    public void stop() {
        if (signalingServer != null) {
            signalingServer.disposeNow();
        }
        if (fileServer != null) {
            fileServer.disposeNow();
        }
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private DisposableServer bind(int port, String role) {
        DisposableServer server = TcpServer.create()
                .host(properties.getHost())
                .port(port)
                .handle((in, out) -> {
                    Jt808FrameSplitter splitter = new Jt808FrameSplitter();
                    Sinks.Many<byte[]> outboundSink = Sinks.many().unicast().onBackpressureBuffer();
                    AtomicReference<String> remoteAddress = new AtomicReference<>("");
                    in.withConnection(connection -> remoteAddress.set(remoteAddress(connection.channel().remoteAddress())));
                    Flux<byte[]> responses = in.receive()
                            .asByteArray()
                            .concatMapIterable(splitter::push)
                            .concatMap(frame -> processor.process(frame, remoteAddress.get(), outboundSink));
                    return out.sendByteArray(Flux.merge(responses, outboundSink.asFlux()))
                            .then()
                            .doFinally(signal -> processor.onDisconnect(outboundSink));
                })
                .bindNow();
        log.info("reactive JT808 {} gateway listening on {}:{}", role, properties.getHost(), port);
        return server;
    }

    private static String remoteAddress(SocketAddress address) {
        return address == null ? "" : address.toString();
    }
}
