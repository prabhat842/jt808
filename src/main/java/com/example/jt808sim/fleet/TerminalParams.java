package com.example.jt808sim.fleet;

import com.example.jt808sim.config.FleetConfig;
import com.example.jt808sim.protocol.ParameterSetting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.function.LongConsumer;

/**
 * Live runtime parameters for a single terminal session (JT808-2013 Table 12).
 *
 * Initialised from {@link FleetConfig} at startup; updated in-place when the
 * platform sends a 0x8103 terminal parameter setting message.
 * All fields are volatile for safe cross-thread access between the Netty I/O
 * thread that writes them and the scheduler thread that reads them.
 *
 * Call {@link #apply} to update from a received 0x8103 setting message.
 * Call {@link #allParameters} / {@link #parametersFor} to build the 0x0104 response.
 * Call {@link #reset} to restore factory defaults.
 */
public class TerminalParams {
    private static final Logger log = LoggerFactory.getLogger(TerminalParams.class);

    // ── Parameter IDs (Table 12) ──────────────────────────────────────────────
    public static final int PARAM_HEARTBEAT_INTERVAL    = 0x0001;
    public static final int PARAM_TCP_ACK_TIMEOUT       = 0x0002;
    public static final int PARAM_TCP_RESEND_COUNT      = 0x0003;
    public static final int PARAM_UDP_ACK_TIMEOUT       = 0x0004;
    public static final int PARAM_UDP_RESEND_COUNT      = 0x0005;
    public static final int PARAM_SERVER_HOST           = 0x0013;
    public static final int PARAM_SERVER_TCP_PORT       = 0x0018;
    public static final int PARAM_SERVER_UDP_PORT       = 0x0019;
    public static final int PARAM_LOCATION_STRATEGY     = 0x0020;
    public static final int PARAM_LOCATION_SCHEME       = 0x0021;
    public static final int PARAM_INTERVAL_NO_DRIVER    = 0x0022;
    public static final int PARAM_INTERVAL_DORMANCY     = 0x0027;
    public static final int PARAM_INTERVAL_EMERGENCY    = 0x0028;
    public static final int PARAM_INTERVAL_DEFAULT      = 0x0029;
    public static final int PARAM_DISTANCE_DEFAULT      = 0x002C;
    public static final int PARAM_DISTANCE_NO_DRIVER    = 0x002D;
    public static final int PARAM_DISTANCE_DORMANCY     = 0x002E;
    public static final int PARAM_DISTANCE_EMERGENCY    = 0x002F;
    public static final int PARAM_INFLECTION_ANGLE      = 0x0030;
    public static final int PARAM_MAX_SPEED             = 0x0055;
    public static final int PARAM_OVERSPEED_DURATION    = 0x0056;
    public static final int PARAM_CONT_DRIVE_LIMIT      = 0x0057;
    public static final int PARAM_ACCUM_DRIVE_LIMIT     = 0x0058;
    public static final int PARAM_MIN_REST_TIME         = 0x0059;
    public static final int PARAM_MAX_PARK_TIME         = 0x005A;
    public static final int PARAM_IMAGE_QUALITY         = 0x0070;
    public static final int PARAM_BRIGHTNESS            = 0x0071;
    public static final int PARAM_CONTRAST              = 0x0072;
    public static final int PARAM_SATURATION            = 0x0073;
    public static final int PARAM_CHROMA                = 0x0074;

    // ── Runtime fields ────────────────────────────────────────────────────────
    private volatile String  serverHost;
    private volatile int     serverPort;
    private volatile int     serverUdpPort;

    private volatile long heartbeatIntervalSeconds;
    private volatile long tcpAckTimeoutSeconds;
    private volatile long tcpResendCount;
    private volatile long udpAckTimeoutSeconds;
    private volatile long udpResendCount;
    private volatile long ackTimeoutSeconds;        // alias: effective TCP ack timeout

    private volatile long locationStrategy;         // 0=timing 1=distance 2=both
    private volatile long locationScheme;           // 0=ACC status
    private volatile long locationIntervalSeconds;  // default report interval (0x0029)
    private volatile long intervalNoDriverSeconds;
    private volatile long intervalDormancySeconds;
    private volatile long intervalEmergencySeconds;

    private volatile long distanceDefaultMeters;
    private volatile long distanceNoDriverMeters;
    private volatile long distanceDormancyMeters;
    private volatile long distanceEmergencyMeters;
    private volatile long inflectionAngleDegrees;

    private volatile long maxSpeedKph;
    private volatile long overspeedDurationSeconds;
    private volatile long contDriveLimitSeconds;
    private volatile long accumDriveLimitSeconds;
    private volatile long minRestSeconds;
    private volatile long maxParkSeconds;

    private volatile long imageQuality;
    private volatile long brightness;
    private volatile long contrast;
    private volatile long saturation;
    private volatile long chroma;

    private TerminalParams() {}

    public static TerminalParams from(FleetConfig config) {
        TerminalParams p = new TerminalParams();
        p.serverHost               = config.getServer().getHost();
        p.serverPort               = config.getServer().getPort();
        p.serverUdpPort            = config.getServer().getPort();
        p.heartbeatIntervalSeconds = config.getFleet().getHeartbeatIntervalSeconds();
        p.locationIntervalSeconds  = config.getFleet().getLocationIntervalSeconds();
        p.ackTimeoutSeconds        = config.getFleet().getAckTimeoutSeconds();
        p.setDefaults();
        return p;
    }

    private void setDefaults() {
        tcpAckTimeoutSeconds       = ackTimeoutSeconds > 0 ? ackTimeoutSeconds : 30;
        tcpResendCount             = 3;
        udpAckTimeoutSeconds       = 5;
        udpResendCount             = 3;
        locationStrategy           = 0;
        locationScheme             = 0;
        intervalNoDriverSeconds    = 60;
        intervalDormancySeconds    = 300;
        intervalEmergencySeconds   = 10;
        distanceDefaultMeters      = 200;
        distanceNoDriverMeters     = 500;
        distanceDormancyMeters     = 1000;
        distanceEmergencyMeters    = 50;
        inflectionAngleDegrees     = 30;
        maxSpeedKph                = 120;
        overspeedDurationSeconds   = 30;
        contDriveLimitSeconds      = 4 * 3600L;
        accumDriveLimitSeconds     = 8 * 3600L;
        minRestSeconds             = 20 * 60L;
        maxParkSeconds             = 3 * 3600L;
        imageQuality               = 8;
        brightness                 = 127;
        contrast                   = 64;
        saturation                 = 64;
        chroma                     = 128;
    }

    /**
     * Applies a 0x8103 parameter setting message.
     *
     * @return true when the heartbeat or location interval changed,
     *         meaning the caller must reschedule the periodic tasks.
     */
    public boolean apply(String terminalId, ParameterSetting setting) {
        boolean reschedule = false;

        if (setting.has(PARAM_HEARTBEAT_INTERVAL)) {
            applyPositive(setting, PARAM_HEARTBEAT_INTERVAL, v -> {
                long old = heartbeatIntervalSeconds;
                heartbeatIntervalSeconds = v;
                if (old != v) log.info("terminal {} heartbeat {}→{}s", terminalId, old, v);
            });
            reschedule = true;
        }
        if (setting.has(PARAM_INTERVAL_DEFAULT)) {
            applyPositive(setting, PARAM_INTERVAL_DEFAULT, v -> {
                long old = locationIntervalSeconds;
                locationIntervalSeconds = v;
                if (old != v) log.info("terminal {} location interval {}→{}s", terminalId, old, v);
            });
            reschedule = true;
        }
        applyPositive(setting, PARAM_TCP_ACK_TIMEOUT,  v -> { ackTimeoutSeconds = v; tcpAckTimeoutSeconds = v; });
        applyPositive(setting, PARAM_TCP_RESEND_COUNT, v -> tcpResendCount = v);
        applyPositive(setting, PARAM_UDP_ACK_TIMEOUT,  v -> udpAckTimeoutSeconds = v);
        applyPositive(setting, PARAM_UDP_RESEND_COUNT, v -> udpResendCount = v);

        setting.getString(PARAM_SERVER_HOST).filter(s -> !s.isEmpty()).ifPresent(h -> {
            log.info("terminal {} server host →{} (next reconnect)", terminalId, h);
            serverHost = h;
        });
        applyInRange(setting, PARAM_SERVER_TCP_PORT, 1, 65535, v -> {
            log.info("terminal {} server TCP port →{} (next reconnect)", terminalId, v);
            serverPort = (int) v;
        });
        applyInRange(setting, PARAM_SERVER_UDP_PORT, 1, 65535, v -> serverUdpPort = (int) v);

        apply(setting, PARAM_LOCATION_STRATEGY,  v -> locationStrategy = v);
        apply(setting, PARAM_LOCATION_SCHEME,    v -> locationScheme = v);
        applyPositive(setting, PARAM_INTERVAL_NO_DRIVER,  v -> intervalNoDriverSeconds = v);
        applyPositive(setting, PARAM_INTERVAL_DORMANCY,   v -> intervalDormancySeconds = v);
        applyPositive(setting, PARAM_INTERVAL_EMERGENCY,  v -> intervalEmergencySeconds = v);
        applyPositive(setting, PARAM_DISTANCE_DEFAULT,    v -> distanceDefaultMeters = v);
        applyPositive(setting, PARAM_DISTANCE_NO_DRIVER,  v -> distanceNoDriverMeters = v);
        applyPositive(setting, PARAM_DISTANCE_DORMANCY,   v -> distanceDormancyMeters = v);
        applyPositive(setting, PARAM_DISTANCE_EMERGENCY,  v -> distanceEmergencyMeters = v);
        apply(setting, PARAM_INFLECTION_ANGLE,   v -> inflectionAngleDegrees = v);
        apply(setting, PARAM_MAX_SPEED,          v -> maxSpeedKph = v);
        apply(setting, PARAM_OVERSPEED_DURATION, v -> overspeedDurationSeconds = v);
        apply(setting, PARAM_CONT_DRIVE_LIMIT,   v -> contDriveLimitSeconds = v);
        apply(setting, PARAM_ACCUM_DRIVE_LIMIT,  v -> accumDriveLimitSeconds = v);
        apply(setting, PARAM_MIN_REST_TIME,      v -> minRestSeconds = v);
        apply(setting, PARAM_MAX_PARK_TIME,      v -> maxParkSeconds = v);
        apply(setting, PARAM_IMAGE_QUALITY,      v -> imageQuality = v);
        apply(setting, PARAM_BRIGHTNESS,         v -> brightness = v);
        apply(setting, PARAM_CONTRAST,           v -> contrast = v);
        apply(setting, PARAM_SATURATION,         v -> saturation = v);
        apply(setting, PARAM_CHROMA,             v -> chroma = v);

        return reschedule;
    }

    private static void apply(ParameterSetting setting, int id, LongConsumer consumer) {
        OptionalLong opt = setting.getDword(id);
        if (opt.isPresent()) consumer.accept(opt.getAsLong());
    }

    private static void applyPositive(ParameterSetting setting, int id, LongConsumer consumer) {
        OptionalLong opt = setting.getDword(id);
        if (opt.isPresent() && opt.getAsLong() > 0) consumer.accept(opt.getAsLong());
    }

    private static void applyInRange(ParameterSetting setting, int id, long min, long max, LongConsumer consumer) {
        OptionalLong opt = setting.getDword(id);
        if (opt.isPresent() && opt.getAsLong() >= min && opt.getAsLong() <= max) consumer.accept(opt.getAsLong());
    }

    /** Returns all known parameters encoded as DWORD or STRING byte arrays. */
    public Map<Integer, byte[]> allParameters() {
        Map<Integer, byte[]> map = new LinkedHashMap<>();
        putDword(map, PARAM_HEARTBEAT_INTERVAL,  heartbeatIntervalSeconds);
        putDword(map, PARAM_TCP_ACK_TIMEOUT,     tcpAckTimeoutSeconds);
        putDword(map, PARAM_TCP_RESEND_COUNT,    tcpResendCount);
        putDword(map, PARAM_UDP_ACK_TIMEOUT,     udpAckTimeoutSeconds);
        putDword(map, PARAM_UDP_RESEND_COUNT,    udpResendCount);
        putString(map, PARAM_SERVER_HOST,        serverHost);
        putDword(map, PARAM_SERVER_TCP_PORT,     serverPort);
        putDword(map, PARAM_SERVER_UDP_PORT,     serverUdpPort);
        putDword(map, PARAM_LOCATION_STRATEGY,   locationStrategy);
        putDword(map, PARAM_LOCATION_SCHEME,     locationScheme);
        putDword(map, PARAM_INTERVAL_NO_DRIVER,  intervalNoDriverSeconds);
        putDword(map, PARAM_INTERVAL_DORMANCY,   intervalDormancySeconds);
        putDword(map, PARAM_INTERVAL_EMERGENCY,  intervalEmergencySeconds);
        putDword(map, PARAM_INTERVAL_DEFAULT,    locationIntervalSeconds);
        putDword(map, PARAM_DISTANCE_DEFAULT,    distanceDefaultMeters);
        putDword(map, PARAM_DISTANCE_NO_DRIVER,  distanceNoDriverMeters);
        putDword(map, PARAM_DISTANCE_DORMANCY,   distanceDormancyMeters);
        putDword(map, PARAM_DISTANCE_EMERGENCY,  distanceEmergencyMeters);
        putDword(map, PARAM_INFLECTION_ANGLE,    inflectionAngleDegrees);
        putDword(map, PARAM_MAX_SPEED,           maxSpeedKph);
        putDword(map, PARAM_OVERSPEED_DURATION,  overspeedDurationSeconds);
        putDword(map, PARAM_CONT_DRIVE_LIMIT,    contDriveLimitSeconds);
        putDword(map, PARAM_ACCUM_DRIVE_LIMIT,   accumDriveLimitSeconds);
        putDword(map, PARAM_MIN_REST_TIME,       minRestSeconds);
        putDword(map, PARAM_MAX_PARK_TIME,       maxParkSeconds);
        putDword(map, PARAM_IMAGE_QUALITY,       imageQuality);
        putDword(map, PARAM_BRIGHTNESS,          brightness);
        putDword(map, PARAM_CONTRAST,            contrast);
        putDword(map, PARAM_SATURATION,          saturation);
        putDword(map, PARAM_CHROMA,              chroma);
        return map;
    }

    /** Returns only the requested parameter IDs, silently ignoring unknown ones. */
    public Map<Integer, byte[]> parametersFor(List<Integer> ids) {
        Map<Integer, byte[]> all = allParameters();
        Map<Integer, byte[]> result = new LinkedHashMap<>();
        for (int id : ids) {
            byte[] v = all.get(id);
            if (v != null) result.put(id, v);
        }
        return result;
    }

    /** Restores defaults from the fleet config (factory reset). */
    public void reset(FleetConfig config) {
        serverHost               = config.getServer().getHost();
        serverPort               = config.getServer().getPort();
        heartbeatIntervalSeconds = config.getFleet().getHeartbeatIntervalSeconds();
        locationIntervalSeconds  = config.getFleet().getLocationIntervalSeconds();
        ackTimeoutSeconds        = config.getFleet().getAckTimeoutSeconds();
        setDefaults();
    }

    // ── Accessors ─────────────────────────────────────────────────────────────
    public String serverHost()               { return serverHost; }
    public int    serverPort()               { return serverPort; }
    public long   heartbeatIntervalSeconds() { return heartbeatIntervalSeconds; }
    public long   locationIntervalSeconds()  { return locationIntervalSeconds; }
    public long   ackTimeoutSeconds()        { return ackTimeoutSeconds; }
    public long   maxSpeedKph()              { return maxSpeedKph; }
    public long   overspeedDurationSeconds() { return overspeedDurationSeconds; }
    public long   contDriveLimitSeconds()    { return contDriveLimitSeconds; }
    public long   accumDriveLimitSeconds()   { return accumDriveLimitSeconds; }
    public long   minRestSeconds()           { return minRestSeconds; }
    public long   maxParkSeconds()           { return maxParkSeconds; }

    // ── Encoding helpers ──────────────────────────────────────────────────────
    private static void putDword(Map<Integer, byte[]> map, int id, long value) {
        map.put(id, ByteBuffer.allocate(4).putInt((int) value).array());
    }

    private static void putString(Map<Integer, byte[]> map, int id, String value) {
        if (value != null && !value.isEmpty()) {
            map.put(id, value.getBytes(StandardCharsets.US_ASCII));
        }
    }
}
