# JT808 Next Generation Platform

This Maven reactor is the production-oriented implementation path for the Spring WebFlux/Reactor Netty/Kafka/Redis/ClickHouse architecture.

It is intentionally separate from the existing simulator/server so the current simulator can continue to act as a load-test client while this platform matures.

## Modules

| Module | Status | Purpose |
| --- | --- | --- |
| `shared` | implemented foundation | common utilities |
| `protocol-codec` | implemented foundation | JT808 frame decode/encode, message IDs, registration/auth/heartbeat/location models |
| `kafka-contracts` | implemented foundation | Kafka topic names and event contracts |
| `gateway-service` | implemented Phase 1 skeleton | Spring Boot WebFlux + Reactor Netty TCP gateway, session store, ACKs, Kafka publishing |
| `telemetry-service` | implemented Phase 1 skeleton | Kafka `telemetry.gps` consumer and ClickHouse batch writer |
| `alarm-service` | implemented skeleton | Kafka `telemetry.alarm` / `telemetry.attachment` consumers and ClickHouse writers |
| `media-service` | implemented skeleton | media command/response consumer and RTVS Redis compatibility keys |
| `auth-service` | implemented skeleton | API and terminal token validation endpoints |
| `admin-api` | implemented skeleton | topology and Redis-backed session lookup endpoints |

## Build

```bash
mvn -f jt808-platform/pom.xml test
```

## Gateway Service

Default config is in [gateway-service/src/main/resources/application.yml](gateway-service/src/main/resources/application.yml).

By default Kafka and Redis are disabled so the gateway can start locally without infrastructure:

```yaml
jt808:
  gateway:
    signaling-port: 7611
    file-port: 7612
    kafka:
      enabled: false
    redis:
      enabled: false
```

When Kafka is disabled, decoded events are logged instead of published.

Run:

```bash
mvn -f jt808-platform/pom.xml -pl gateway-service spring-boot:run
```

## Telemetry Service

Default config is in [telemetry-service/src/main/resources/application.yml](telemetry-service/src/main/resources/application.yml).

The service consumes `telemetry.gps`, batches records, writes to ClickHouse, and acknowledges Kafka offsets only after the ClickHouse batch succeeds.

Run:

```bash
mvn -f jt808-platform/pom.xml -pl telemetry-service spring-boot:run
```

## Current Scope

Implemented now:

- multi-module Java 21 Maven platform
- Spring Boot 3.x service modules
- Reactor Netty TCP gateway listeners
- JT808 frame splitting, unescaping, checksum validation, and decoding
- `0x0100`, `0x0102`, `0x0002`, `0x0200`, and `0x0001` handling
- `0x8100` registration response and `0x8001` platform ACK encoding
- Kafka topic and event contracts
- Kafka publisher abstraction with no-infrastructure logging fallback
- Redis-ready session store with in-memory fallback
- RTVS/CVNet `/VideoControl` command ingress
- live terminal command delivery through gateway connection registry
- Redis-ready command correlation with in-memory fallback
- reactive telemetry consumer and ClickHouse batch writer
- alarm and attachment Kafka consumers
- ClickHouse writers for `vehicle_alarm` and `vehicle_alarm_file`
- media-service Redis compatibility writer for command/response correlation
- auth-service token validation endpoints
- admin-api topology and session lookup endpoints

Still planned:

- geospatial and AI services
- Flink stream-processing layer
- production auth integration between gateway/admin/media services
- integration tests with Kafka, Redis, ClickHouse, and simulator traffic
- Kubernetes manifests and dashboards

## Service Entrypoints

```bash
mvn -f jt808-platform/pom.xml -pl gateway-service spring-boot:run
mvn -f jt808-platform/pom.xml -pl telemetry-service spring-boot:run
mvn -f jt808-platform/pom.xml -pl alarm-service spring-boot:run
mvn -f jt808-platform/pom.xml -pl media-service spring-boot:run
mvn -f jt808-platform/pom.xml -pl auth-service spring-boot:run
mvn -f jt808-platform/pom.xml -pl admin-api spring-boot:run
```

Each service defaults to infrastructure-disabled mode where practical. Enable Kafka, Redis, and ClickHouse in each service `application.yml` when running integration tests.
