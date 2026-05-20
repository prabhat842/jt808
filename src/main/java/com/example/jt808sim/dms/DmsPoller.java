package com.example.jt808sim.dms;

import com.example.jt808sim.fleet.VehicleState;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Polls the Python DMS sidecar every {@code pollIntervalMs} milliseconds
 * and updates {@link VehicleState#dmsState()} from the JSON response.
 *
 * Lifecycle: {@link #start} / {@link #stop} can be called repeatedly as the
 * terminal connects and disconnects. {@link #close} shuts down the executor
 * permanently when the terminal session is destroyed.
 */
public class DmsPoller {
    private static final Logger log = LoggerFactory.getLogger(DmsPoller.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String stateUrl;
    private final VehicleState vehicleState;
    private final HttpClient httpClient;
    private final ScheduledExecutorService executor;
    private final AtomicReference<ScheduledFuture<?>> task = new AtomicReference<>();

    public DmsPoller(String sidecarBaseUrl, VehicleState vehicleState) {
        String base       = sidecarBaseUrl.endsWith("/")
                ? sidecarBaseUrl.substring(0, sidecarBaseUrl.length() - 1) : sidecarBaseUrl;
        this.stateUrl     = base + "/dms/state";
        this.vehicleState = vehicleState;
        this.httpClient   = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(500))
                .build();
        this.executor     = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "dms-poller");
            t.setDaemon(true);
            return t;
        });
    }

    public void start(long pollIntervalMs) {
        stop();
        ScheduledFuture<?> t = executor.scheduleAtFixedRate(
                this::poll, 0, pollIntervalMs, TimeUnit.MILLISECONDS);
        task.set(t);
    }

    public void stop() {
        ScheduledFuture<?> t = task.getAndSet(null);
        if (t != null) {
            t.cancel(false);
        }
    }

    public void close() {
        stop();
        executor.shutdown();
    }

    void poll() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(stateUrl))
                    .GET()
                    .timeout(Duration.ofMillis(300))
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                applyResponse(resp.body());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            vehicleState.dmsState().clear();
            log.debug("DMS sidecar unavailable ({}): {}", stateUrl, e.getMessage());
        }
    }

    void applyResponse(String json) throws Exception {
        JsonNode node           = MAPPER.readTree(json);
        int primaryAlarm        = node.path("primaryAlarm").asInt(0);
        int fatigueDegree       = node.path("fatigueDegree").asInt(0);
        int alarmFlags          = node.path("alarmFlags").asInt(0);
        vehicleState.dmsState().update(primaryAlarm, fatigueDegree, alarmFlags);
    }
}
