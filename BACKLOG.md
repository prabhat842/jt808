# Development Backlog

Remaining work after Phase 8. Items are grouped by area and ordered by production priority.

---

## Security

| Item | Detail |
|---|---|
| TLS on JT808 TCP listeners | Server-side TLS cert on ports 7611/7612; optional client cert for mutual auth. Add `spring.ssl.bundle` config to gateway-service. |
| Admin API IP whitelist | Restrict `/VideoControl`, `/WCF0x9105`, `/GetVehicleSim` (port 8888) and admin-api (port 8090) to private network via Spring Security `remoteAddress` matcher. |
| Encrypted secrets | Move ClickHouse and Redis passwords out of `application.yml` into Jasypt-encrypted properties or a Spring Cloud Config vault backend. |
| Rate limiting | Add a token-bucket filter on the JT808 TCP listener to guard against mass-registration floods from rogue devices. |

---

## Observability

| Item | Detail |
|---|---|
| Kafka/ClickHouse/Redis liveness probes | Custom `HealthIndicator` beans in gateway-service, telemetry-service, and alarm-service. Expose via `/actuator/health`. |
| ClickHouse writer counters | Micrometer counters in `ClickHouseGpsWriter` and `AlarmClickHouseWriter`: `rows_written_total`, `batch_errors_total`, `queue_depth`. |
| Kafka consumer lag metric | Expose consumer-group lag for all service consumer groups via `/actuator/metrics`. |
| Prometheus scrape endpoint | Already exposed at `/actuator/prometheus` by Spring Boot; document the Prometheus scrape config and add a sample Grafana dashboard JSON. |
| Gateway connection counter | `active_terminals` and `active_media_sessions` gauges in `ActiveConnectionRegistry` and the JT1078 media session pool. |
| Simulator dashboard export | Add `--metrics-port` flag to the simulator so Prometheus can scrape its counters (connections, heartbeats, location reports, alarm triggers, media bytes). |

---

## Media Relay (optional)

| Item | Detail |
|---|---|
| JT1078 relay mode | When `jt1078.relay: true`, gateway-service opens a second TCP connection to RTVS on behalf of the terminal and proxies JT1078 stream packets. Needed when terminals cannot reach RTVS directly. |
| Relay metrics | Bytes relayed, active relay sessions, relay latency histogram. |
| Relay config | `jt808.gateway.relay.rtvsHost`, `rtvsPort`, `maxConcurrentSessions`. |

---

## Remaining Protocol Gaps

| Item | Detail |
|---|---|
| Geofence query API | `GET /admin/geofences/{terminalId}` — expose circle/rectangle/polygon areas stored per terminal in the embedded server. |
| `0x8003` sub-packet resend | The gateway does not re-request missing sub-packets for large fragmented messages. Needed for 0x0704 bulk location upload reliability. |
| `0x0303` info-on-demand request | Terminal-initiated info demand is decoded but not emitted as a Kafka event. Add a `MediaSignalEvent` for it. |
| Platform-side `0x9105` handler | `WCF0x9105` only returns `"1"`. Wire it to update a per-channel packet-loss metric in Redis for RTVS coordination. |

---

## Session Durability

| Item | Detail |
|---|---|
| `GatewaySessionStore.findAll()` | Add a scan/range method so `GET /admin/sessions` can list all active sessions, not just look up by terminalId. |
| Cross-restart session recovery | On gateway startup, scan `jt808:session:*` Redis keys and restore the in-memory `ActiveConnectionRegistry` for terminals that are still TCP-connected. |

---

## Infrastructure

| Item | Detail |
|---|---|
| Docker Compose dev stack | Single `docker-compose.yml` that starts Kafka, ClickHouse, and Redis; maps correct ports; includes health checks. |
| ClickHouse DDL migrations | Versioned SQL migration files (Flyway or Liquibase) for `vehicle_gps`, `vehicle_alarm`, `vehicle_alarm_file` with correct partition keys, TTLs, and the new video-alarm columns added in Phase 7. |
| Kubernetes manifests | `Deployment`, `Service`, `ConfigMap`, and `HorizontalPodAutoscaler` for each Spring Boot service. |
| CI pipeline | GitHub Actions workflow: compile, test, Docker build, push to registry. |
