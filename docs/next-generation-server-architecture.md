# JT808 / JT1078 Next-Generation Server Architecture

This document defines the target production architecture for evolving the current simulator/server into a scalable, reactive JT808/JT1078 backend platform.

The current repository already contains:

- device-side JT808/JT1078 simulator
- Netty-based JT808 server
- RTVS/CVNet gateway integration notes
- ClickHouse schema and GPS persistence support

The next-generation architecture moves the production backend toward Spring Boot WebFlux, Reactor Netty, Kafka, Redis, ClickHouse, and future Flink-based stream processing.

Initial implementation now lives under [../jt808-platform](../jt808-platform). It contains the production-oriented Maven reactor for the protocol codec, Kafka contracts, Reactor Netty gateway service, telemetry-to-ClickHouse service, alarm/attachment service, media coordination service, auth service, and admin API.

## Objective

Build a scalable, production-grade, reactive JT808/JT1078 backend platform using:

- Java 21
- Spring Boot 3.x
- Spring WebFlux
- Reactor Netty
- Apache Kafka
- ClickHouse
- Redis
- Apache Flink in a later phase

The system must support:

- high concurrency
- persistent TCP sessions
- JT808 binary protocol
- JT1078 signaling and media coordination
- asynchronous telemetry ingestion
- real-time command delivery
- future AI and geospatial intelligence integration

## High-Level Architecture

```text
Devices / Simulators
        |
        v
JT808 Reactive Gateway
Spring Boot + Reactor Netty
        |
        v
Kafka Event Bus
        |
        v
Processing Services
        |-- ClickHouse Writer
        |-- Alarm Processor
        |-- Media Coordination
        |-- Geospatial Intelligence
        |-- AI Inference
        `-- Analytics APIs
```

## Technology Stack

| Layer | Technology |
| --- | --- |
| Runtime | Java 21 |
| Framework | Spring Boot 3.x |
| Reactive layer | Spring WebFlux |
| Network engine | Reactor Netty |
| Event streaming | Kafka |
| Storage | ClickHouse |
| Cache / coordination | Redis |
| Geospatial | PostGIS + H3 |
| Stream processing | Flink, future phase |
| Containerization | Docker |
| Orchestration | Kubernetes |
| Metrics | Prometheus |
| Dashboards | Grafana |
| Logging | Loki / ELK |

## Core Principles

Mandatory rules:

- Use non-blocking architecture for all gateway paths.
- Do not block Netty event-loop threads.
- Keep JT808 TCP parsing, database writes, AI inference, media processing, and REST APIs separated.
- Publish telemetry, alarms, media events, and command responses to Kafka.
- Persist data asynchronously from downstream writer services.
- Keep gateway services horizontally scalable.

Forbidden on event-loop threads:

- JDBC
- filesystem I/O
- blocking REST clients
- `Thread.sleep`
- synchronous HTTP clients
- large JSON serialization
- AI inference
- direct ClickHouse writes

## Target Module Structure

```text
jt808-platform/
|
|-- gateway-service/
|-- protocol-codec/
|-- kafka-contracts/
|-- telemetry-service/
|-- alarm-service/
|-- media-service/
|-- geo-service/
|-- ai-service/
|-- auth-service/
|-- admin-api/
|-- observability/
`-- shared/
```

## Service Responsibilities

### gateway-service

Technology:

- Spring Boot WebFlux
- Reactor Netty TCP server

Responsibilities:

- JT808 TCP session management
- online/offline state
- registration and authentication state
- heartbeat timestamps
- command correlation
- JT808 frame decoding
- escaping and unescaping
- checksum validation
- packet fragmentation
- retransmission support
- decoded event publishing to Kafka
- Redis session and command coordination
- RTVS/CVNet compatibility endpoints

Mandatory JT808 messages:

| Message | Purpose |
| --- | --- |
| `0x0100` | registration |
| `0x0102` | authentication |
| `0x0002` | heartbeat |
| `0x0200` | location report |
| `0x0704` | batch location upload |
| `0x0001` | terminal general response |

Mandatory media signaling:

| Message | Purpose |
| --- | --- |
| `0x9101` | live video request |
| `0x9201` | playback request |
| `0x9205` | resource query |

### protocol-codec

Responsibilities:

- JT808 frame model
- JT808 header/body encoders and decoders
- checksum utilities
- escape/unescape utilities
- BCD/time helpers
- JT1078 signaling command models
- protocol fuzz-test fixtures

This module must have no database, Kafka, Redis, Spring WebFlux controller, or media-processing dependency.

### kafka-contracts

Responsibilities:

- event schemas
- topic names
- partitioning rules
- serialization contracts
- versioned message definitions

Initial topics:

| Topic | Purpose |
| --- | --- |
| `telemetry.gps` | decoded GPS/location telemetry |
| `telemetry.alarm` | alarm lifecycle events |
| `telemetry.attachment` | attachment metadata |
| `telemetry.heartbeat` | heartbeat/online state |
| `jt808.command` | outbound command requests |
| `media.signal` | JT1078 signaling events |
| `media.response` | media command responses |
| `ai.alert` | AI-generated alerts |

Partition key:

```text
vehicle_id
```

Reason:

- preserve per-vehicle ordering
- simplify session and command consistency
- support stream processing by vehicle

### telemetry-service

Responsibilities:

- consume `telemetry.gps`
- batch inserts into ClickHouse
- retry failed batches
- apply backpressure
- enrich telemetry with optional H3 index in later phases

The gateway must not write telemetry directly to ClickHouse in the target architecture.

### alarm-service

Responsibilities:

- consume `telemetry.alarm`
- model alarm lifecycle
- deduplicate alarm events
- write `vehicle_alarm`
- publish alerts when needed

### media-service

Responsibilities:

- coordinate JT1078 signaling with RTVS/CVNet
- store command correlation in Redis
- produce and consume `media.signal` / `media.response`
- maintain compatibility keys expected by RTVS

RTVS/CVNet owns media ingest, transcoding, playback, and distribution. The gateway owns signaling and command delivery.

### geo-service

Phase 2 service.

Responsibilities:

- H3 indexing
- geofencing
- route intelligence
- flood overlays
- terrain risk
- infrastructure intelligence
- stream joins with geospatial datasets

### ai-service

Phase 3 service.

Responsibilities:

- driver behavior scoring
- fatigue detection
- collision prediction
- anomaly detection
- predictive maintenance

AI inference must consume from Kafka or downstream stores. It must never run synchronously inside gateway event loops.

## Kafka Design

Required initial topics:

```text
telemetry.gps
telemetry.alarm
telemetry.attachment
telemetry.heartbeat
jt808.command
media.signal
media.response
ai.alert
```

Partition all per-terminal/per-vehicle topics by:

```text
vehicle_id
```

Recommended production Kafka baseline:

- 3 brokers minimum
- replication enabled
- topic-level retention configured by data class
- dead-letter topics for writer failures and schema errors

## ClickHouse Design

Initial tables:

| Table | Purpose |
| --- | --- |
| `vehicle_gps` | high-frequency telemetry |
| `vehicle_alarm` | alarm lifecycle |
| `vehicle_alarm_file` | alarm media/file metadata |

Target write path:

```text
Gateway
    |
    v
Kafka
    |
    v
Telemetry Writer Service
    |
    v
Batch Insert to ClickHouse
```

Rules:

- no synchronous inserts from gateway event loops
- use async batching
- retry failed batches
- expose insert latency and failure metrics
- preserve backpressure behavior

## Redis Usage

Redis is not telemetry storage.

Use Redis only for:

- active session cache
- online status
- command correlation
- media correlation
- RTVS compatibility keys
- command expiry tracking

All Redis keys that represent runtime/session state must have TTLs.

Important RTVS/CVNet compatibility keys:

| Key | Purpose |
| --- | --- |
| `AVParameters:<sim>` | terminal audio/video attributes |
| `OCX_ORDERINFO_<commandId>` | resource list response correlation |
| `SIM_CONFIG_FOR_RTVS_<sim>` | per-terminal media capability/config |

## Media Architecture

JT808 gateway owns:

- signaling
- command dispatch
- terminal responses
- session state

RTVS/CVNet owns:

- JT1078 media ingest
- transcoding
- live playback
- historical playback
- distribution to Web/H5/mobile/RTMP/HLS/WebRTC clients

Media path must remain independent from telemetry ingestion.

## Security Requirements

Mandatory:

- keep ClickHouse private
- keep Redis private
- keep admin APIs private
- require TLS for admin APIs
- require TLS or private mTLS for service-to-service communication
- validate terminal authentication tokens
- add ACLs for command endpoints
- rate-limit APIs and command paths

## Observability

Metrics to expose:

- active sessions
- packets per second
- inbound/outbound messages by message ID
- Kafka publish latency
- Kafka lag
- ClickHouse insert latency
- command latency
- alarm throughput
- media command success/failure

Logging:

- structured JSON logs only in production
- include `vehicle_id`, `device_id`, `message_id`, and `trace_id`

Tracing:

- OpenTelemetry
- distributed tracing across gateway, Kafka consumers, writers, and APIs

## Geospatial Intelligence

Phase 2 additions:

- add `h3_index` to the telemetry pipeline
- add PostGIS-backed geospatial APIs
- support geofence evaluation
- support route risk scoring
- support flood, terrain, and infrastructure overlays

## AI Layer

Phase 3 additions:

- driver behavior scoring
- fatigue detection
- collision prediction
- anomaly detection
- predictive maintenance
- digital twin event generation

AI services consume from Kafka or analytical stores. They must not sit in the gateway hot path.

## Deployment Topology

```text
                    INTERNET / DEVICE NETWORK
                                |
                +---------------+----------------+
                |                                |
          JT808 TCP                         JT1078 Media
         7611 / 7612                           1078
                |                                |
                v                                v

        +--------------------------------------------+
        |      JT808 Gateway Cluster WebFlux         |
        |--------------------------------------------|
        | - session management                       |
        | - JT808 codec                              |
        | - command routing                          |
        | - heartbeat handling                       |
        | - Kafka publishing                         |
        +--------------------------------------------+
                                |
                                v

                    +--------------------+
                    |    Kafka Cluster    |
                    +--------------------+
                                |
            +-------------------+-------------------+
            |                   |                   |
            v                   v                   v

    Telemetry Service     Alarm Service      Media Service
            |                   |                   |
            v                   v                   v

      ClickHouse            Redis             RTVS/CVNet

            |
            v

    Geospatial / AI Services
            |
            v

       APIs / Dashboards
```

## Performance Targets

| Metric | Target |
| --- | --- |
| Concurrent devices | 100k+ |
| GPS ingestion | 50k messages/sec |
| Command latency | less than 100 ms |
| Availability | 99.9% |
| Horizontal scaling | linear |

## Migration Roadmap

### Phase 1: Reactive Gateway Foundation

- create multi-module Spring Boot platform structure
- extract protocol codec into standalone module
- implement Reactor Netty TCP gateway
- support registration, authentication, heartbeat, location, and terminal general response
- publish GPS and heartbeat events to Kafka
- keep the existing simulator as the load-test client

### Phase 2: Kafka, Redis, and ClickHouse Pipeline

- implement Kafka event contracts
- implement telemetry writer service
- write `vehicle_gps` from Kafka to ClickHouse
- add Redis online/session state
- add command correlation with TTL

### Phase 3: Media Signaling and Alarms

- implement RTVS/CVNet media signaling flow
- support `0x9101`, `0x9201`, and `0x9205`
- implement alarm event decoding
- write `vehicle_alarm`
- implement file/attachment metadata ingestion into `vehicle_alarm_file`

### Phase 4: Geospatial and AI Intelligence

- add H3 indexing
- add PostGIS-backed geospatial services
- introduce Flink for stream processing
- add AI inference services outside gateway hot paths
- build analytics APIs and dashboards

## Critical Anti-Patterns

Do not:

- use blocking JDBC in event loops
- store telemetry in Redis
- tightly couple media and telemetry
- write directly to ClickHouse from TCP handlers in the target gateway
- run AI inference synchronously
- use thread-per-device design
- combine TCP parsing, persistence, and API logic in one service

## Production Readiness Checklist

Infrastructure:

- Kubernetes deployments
- horizontal autoscaling
- rolling updates
- container health checks
- centralized logging

Security:

- TLS everywhere practical
- JWT/API authentication
- RBAC
- firewall isolation
- VPN/private routing

Reliability:

- retry queues
- Kafka replication
- ClickHouse replication
- Redis HA
- graceful shutdown handling
- command correlation expiry

Observability:

- Prometheus metrics
- Grafana dashboards
- distributed tracing
- alerting rules
- audit logs

## Final Architectural Goal

Transform the platform from:

```text
GPS Tracking Platform
```

into:

```text
Real-Time Mobility Intelligence Infrastructure
```

with:

- scalable reactive networking
- stream-native architecture
- geospatial intelligence
- AI-driven analytics
- digital twin readiness
- telecom-grade reliability
- intelligent transportation infrastructure support
