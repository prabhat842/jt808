package com.example.jt808sim.fleet;

import com.example.jt808sim.config.FleetConfig;
import com.example.jt808sim.protocol.ParameterSetting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Live runtime parameters for a single terminal session.
 *
 * Initialized from {@link FleetConfig} at startup and updated in-place when the
 * server sends a 0x8103 terminal parameter setting message. All fields are
 * volatile so the Netty I/O thread that writes them and the scheduler thread
 * that reads them see consistent values without locking.
 *
 * Server address/port changes are stored here and applied on the next reconnect.
 * Heartbeat and location intervals are applied immediately by rescheduling the
 * periodic tasks (the caller is responsible for doing this after apply() returns true).
 */
public class TerminalParams {
    private static final Logger log = LoggerFactory.getLogger(TerminalParams.class);

    // Parameter IDs from JT808 spec Table 12
    public static final int PARAM_HEARTBEAT_INTERVAL  = 0x0001;
    public static final int PARAM_ACK_TIMEOUT         = 0x0002;
    public static final int PARAM_SERVER_HOST         = 0x0013;
    public static final int PARAM_SERVER_TCP_PORT     = 0x0018;
    public static final int PARAM_LOCATION_INTERVAL   = 0x0029;

    private volatile String serverHost;
    private volatile int    serverPort;
    private volatile long   heartbeatIntervalSeconds;
    private volatile long   locationIntervalSeconds;
    private volatile long   ackTimeoutSeconds;

    private TerminalParams() {}

    public static TerminalParams from(FleetConfig config) {
        TerminalParams p = new TerminalParams();
        p.serverHost               = config.getServer().getHost();
        p.serverPort               = config.getServer().getPort();
        p.heartbeatIntervalSeconds = config.getFleet().getHeartbeatIntervalSeconds();
        p.locationIntervalSeconds  = config.getFleet().getLocationIntervalSeconds();
        p.ackTimeoutSeconds        = config.getFleet().getAckTimeoutSeconds();
        return p;
    }

    /**
     * Applies the parameters received in a 0x8103 message.
     *
     * @return true if the heartbeat or location interval changed, meaning the
     *         caller should reschedule the periodic streaming tasks.
     */
    public boolean apply(String terminalId, ParameterSetting setting) {
        boolean reschedule = false;

        setting.getDword(PARAM_HEARTBEAT_INTERVAL).ifPresent(v -> {
            if (v > 0) {
                long old = heartbeatIntervalSeconds;
                heartbeatIntervalSeconds = v;
                if (old != v) log.info("terminal {} heartbeat interval {} -> {}s", terminalId, old, v);
            }
        });

        setting.getDword(PARAM_LOCATION_INTERVAL).ifPresent(v -> {
            if (v > 0) {
                long old = locationIntervalSeconds;
                locationIntervalSeconds = v;
                if (old != v) log.info("terminal {} location interval {} -> {}s", terminalId, old, v);
            }
        });

        setting.getDword(PARAM_ACK_TIMEOUT).ifPresent(v -> {
            if (v > 0) {
                long old = ackTimeoutSeconds;
                ackTimeoutSeconds = v;
                if (old != v) log.info("terminal {} ack timeout {} -> {}s", terminalId, old, v);
            }
        });

        setting.getString(PARAM_SERVER_HOST).filter(s -> !s.isEmpty()).ifPresent(host -> {
            String old = serverHost;
            serverHost = host;
            if (!old.equals(host)) log.info("terminal {} server host {} -> {} (applied on next reconnect)", terminalId, old, host);
        });

        setting.getDword(PARAM_SERVER_TCP_PORT).ifPresent(v -> {
            if (v > 0 && v <= 65535) {
                int old = serverPort;
                serverPort = (int) v;
                if (old != v) log.info("terminal {} server port {} -> {} (applied on next reconnect)", terminalId, old, v);
            }
        });

        // reschedule if either timed task interval changed
        if (setting.has(PARAM_HEARTBEAT_INTERVAL) || setting.has(PARAM_LOCATION_INTERVAL)) {
            reschedule = true;
        }

        return reschedule;
    }

    public String serverHost()               { return serverHost; }
    public int    serverPort()               { return serverPort; }
    public long   heartbeatIntervalSeconds() { return heartbeatIntervalSeconds; }
    public long   locationIntervalSeconds()  { return locationIntervalSeconds; }
    public long   ackTimeoutSeconds()        { return ackTimeoutSeconds; }
}
