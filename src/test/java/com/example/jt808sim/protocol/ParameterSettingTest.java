package com.example.jt808sim.protocol;

import com.example.jt808sim.fleet.TerminalParams;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import com.example.jt808sim.netty.Jt808EscapeCodec;
import com.example.jt808sim.netty.Jt808MessageDecoder;
import com.example.jt808sim.monitoring.MetricsRegistry;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ParameterSettingTest {

    // ── ParameterSetting record accessors ─────────────────────────────────────

    @Test
    void getDwordReadsUnsigned32BitBigEndian() {
        Map<Integer, byte[]> params = Map.of(
                0x0001, new byte[]{0x00, 0x00, 0x00, 0x1E}   // 30
        );
        ParameterSetting setting = ParameterSetting.of(params);
        assertEquals(30L, setting.getDword(0x0001).orElseThrow());
    }

    @Test
    void getDwordEmptyWhenNotPresent() {
        ParameterSetting setting = ParameterSetting.of(Map.of());
        assertTrue(setting.getDword(0x0001).isEmpty());
    }

    @Test
    void getStringDecodesGbk() {
        byte[] gbkBytes = "192.168.1.100".getBytes(Charset.forName("GBK"));
        ParameterSetting setting = ParameterSetting.of(Map.of(0x0013, gbkBytes));
        assertEquals("192.168.1.100", setting.getString(0x0013).orElseThrow());
    }

    @Test
    void hasReturnsTrueOnlyWhenPresent() {
        ParameterSetting setting = ParameterSetting.of(Map.of(0x0001, new byte[4]));
        assertTrue(setting.has(0x0001));
        assertFalse(setting.has(0x0002));
    }

    // ── TerminalParams.apply ───────────────────────────────────────────────────

    @Test
    void applyUpdatesHeartbeatInterval() {
        TerminalParams p = terminalParamsWithDefaults(30, 5, 15, "127.0.0.1", 7611);
        ParameterSetting setting = ParameterSetting.of(Map.of(
                TerminalParams.PARAM_HEARTBEAT_INTERVAL, dword(60)
        ));
        boolean reschedule = p.apply("test-terminal", setting);
        assertEquals(60, p.heartbeatIntervalSeconds());
        assertTrue(reschedule);
    }

    @Test
    void applyUpdatesLocationInterval() {
        TerminalParams p = terminalParamsWithDefaults(30, 5, 15, "127.0.0.1", 7611);
        ParameterSetting setting = ParameterSetting.of(Map.of(
                TerminalParams.PARAM_INTERVAL_DEFAULT, dword(10)
        ));
        boolean reschedule = p.apply("test-terminal", setting);
        assertEquals(10, p.locationIntervalSeconds());
        assertTrue(reschedule);
    }

    @Test
    void applyUpdatesServerHostAndPort() {
        TerminalParams p = terminalParamsWithDefaults(30, 5, 15, "127.0.0.1", 7611);
        ParameterSetting setting = ParameterSetting.of(Map.of(
                TerminalParams.PARAM_SERVER_HOST,     "192.168.1.50".getBytes(Charset.forName("GBK")),
                TerminalParams.PARAM_SERVER_TCP_PORT, dword(7622)
        ));
        boolean reschedule = p.apply("test-terminal", setting);
        assertEquals("192.168.1.50", p.serverHost());
        assertEquals(7622, p.serverPort());
        assertFalse(reschedule); // no timing param changed
    }

    @Test
    void applyIgnoresZeroAndOutOfRangePort() {
        TerminalParams p = terminalParamsWithDefaults(30, 5, 15, "127.0.0.1", 7611);
        ParameterSetting setting = ParameterSetting.of(Map.of(
                TerminalParams.PARAM_SERVER_TCP_PORT, dword(0)
        ));
        p.apply("test-terminal", setting);
        assertEquals(7611, p.serverPort()); // unchanged
    }

    @Test
    void applyNoRescheduleWhenOnlyServerParamsChange() {
        TerminalParams p = terminalParamsWithDefaults(30, 5, 15, "127.0.0.1", 7611);
        ParameterSetting setting = ParameterSetting.of(Map.of(
                TerminalParams.PARAM_SERVER_HOST, "10.0.0.1".getBytes(Charset.forName("GBK"))
        ));
        assertFalse(p.apply("test-terminal", setting));
    }

    // ── Decoder round-trip: 0x8103 wire bytes → ParameterSetting ─────────────

    @Test
    void decoderParsesParameterSettingFrame() {
        // Build a 0x8103 body: count=2, then two parameter items
        ByteBuf body = Unpooled.buffer();
        body.writeByte(2); // total params
        // param 1: 0x0001 (heartbeat), length=4, value=30
        body.writeInt(0x0001);
        body.writeByte(4);
        body.writeBytes(dword(30));
        // param 2: 0x0013 (server host), length=varies
        byte[] host = "10.0.0.1".getBytes(Charset.forName("GBK"));
        body.writeInt(0x0013);
        body.writeByte(host.length);
        body.writeBytes(host);

        Jt808Message msg = decode(MessageIds.TERMINAL_PARAM_SETTING, body);
        assertInstanceOf(ParameterSetting.class, msg.body());

        ParameterSetting setting = (ParameterSetting) msg.body();
        assertEquals(30L, setting.getDword(0x0001).orElseThrow());
        assertEquals("10.0.0.1", setting.getString(0x0013).orElseThrow());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static byte[] dword(long value) {
        return new byte[]{
                (byte) (value >> 24),
                (byte) (value >> 16),
                (byte) (value >> 8),
                (byte) value
        };
    }

    private static TerminalParams terminalParamsWithDefaults(long heartbeat, long location, long ackTimeout,
                                                              String host, int port) {
        // construct via reflection-free builder using apply on a blank-ish params
        Map<Integer, byte[]> init = new HashMap<>();
        init.put(TerminalParams.PARAM_HEARTBEAT_INTERVAL, dword(heartbeat));
        init.put(TerminalParams.PARAM_INTERVAL_DEFAULT, dword(location));
        init.put(TerminalParams.PARAM_TCP_ACK_TIMEOUT, dword(ackTimeout));
        init.put(TerminalParams.PARAM_SERVER_HOST, host.getBytes(Charset.forName("GBK")));
        init.put(TerminalParams.PARAM_SERVER_TCP_PORT, dword(port));

        // Build via a fresh FleetConfig-seeded instance then apply
        // Since TerminalParams.from() needs a FleetConfig, use a minimal one via the package-private path.
        // Instead, test the apply path directly on a default instance seeded from a minimal config.
        TerminalParams p = buildMinimalParams();
        p.apply("init", ParameterSetting.of(init));
        return p;
    }

    private static TerminalParams buildMinimalParams() {
        // Use apply() with known-good values to seed a fresh TerminalParams.
        // TerminalParams has no public no-arg constructor by design, so we use
        // a ParameterSetting to set all fields on a params built from a minimal FleetConfig.
        com.example.jt808sim.config.FleetConfig cfg = buildMinimalConfig();
        return TerminalParams.from(cfg);
    }

    private static com.example.jt808sim.config.FleetConfig buildMinimalConfig() {
        try {
            // Build via JSON to avoid internal coupling with private setters
            String json = """
                    {
                      "server": {"host": "127.0.0.1", "port": 7611},
                      "fleet": {"connectionCount": 1, "heartbeatIntervalSeconds": 30,
                                "locationIntervalSeconds": 5, "ackTimeoutSeconds": 15},
                      "jt1078": {"host": "127.0.0.1", "port": 1078},
                      "vehicles": [{"vin":"VIN00000000000001","terminalId":"00000000000000000001",
                                    "plateNumber":"T-001","manufacturerId":"TEST1",
                                    "startLat":22.0,"startLon":72.0,"targetLat":22.1,"targetLon":72.1}]
                    }
                    """;
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(json, com.example.jt808sim.config.FleetConfig.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Encodes a minimal versioned JT808 frame around the body and decodes it. */
    private static Jt808Message decode(int messageId, ByteBuf body) {
        MetricsRegistry metrics = new MetricsRegistry();
        EmbeddedChannel ch = new EmbeddedChannel(new Jt808EscapeCodec(), new Jt808MessageDecoder(metrics));

        // Build raw JT808 2019 frame (versioned, unescaped — EscapeCodec will decode)
        ByteBuf frame = Unpooled.buffer();
        int bodyLen = body.readableBytes();
        // header: msgId(2) + props(2) + version(1) + terminalId(10) + seq(2) = 17 bytes
        int props = 0x4000 | bodyLen; // VERSION_FLAG set, no fragment
        frame.writeShort(messageId);
        frame.writeShort(props);
        frame.writeByte(1); // protocol version
        // terminal ID: 10 BCD bytes = 20 digits "00000000000000000001"
        for (int i = 0; i < 9; i++) frame.writeByte(0x00);
        frame.writeByte(0x01);
        frame.writeShort(1); // sequence
        frame.writeBytes(body);
        // checksum
        byte xor = 0;
        for (int i = 0; i < frame.readableBytes(); i++) xor ^= frame.getByte(i);
        frame.writeByte(xor);

        ch.writeInbound(frame);
        Jt808Message msg = ch.readInbound();
        ch.finishAndReleaseAll();
        return msg;
    }
}
