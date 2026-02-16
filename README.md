# Distributed Logging & Monitoring System

A scalable, event-driven log ingestion and monitoring pipeline demonstrating microservice communication, load balancing, distributed tracing, and Kafka-based processing.

## Tech Stack

| Component | Technology |
|-----------|------------|
| Language | Java 21 |
| Framework | Spring Boot 3.x |
| Build | Gradle (Kotlin DSL) |
| Load Balancer | NGINX |
| Message Broker | Apache Kafka |
| Database | PostgreSQL |
| Cache | Redis |

## Architecture

```
┌─────────────────┐       ┌─────────────────┐       ┌─────────────────┐
│  user-service   │──────▶│ payment-service │──────▶│  order-service  │
│ (log generator) │       │ (log generator) │       │ (log generator) │
└────────┬────────┘       └────────┬────────┘       └────────┬────────┘
         │                         │                         │
         │         (shared trace IDs - distributed tracing)  │
         └─────────────────────────┼─────────────────────────┘
                                   │
                                   ▼
                          ┌─────────────────┐
                          │     NGINX       │
                          │  Load Balancer  │
                          │  + Rate Limit   │
                          └────────┬────────┘
                                   │
                    ┌──────────────┴──────────────┐
                    ▼                             ▼
           ┌─────────────────┐           ┌─────────────────┐
           │ ingestion-svc-1 │           │ ingestion-svc-2 │
           └────────┬────────┘           └────────┬────────┘
                    └──────────────┬──────────────┘
                                   ▼
                          ┌─────────────────┐
                          │     Kafka       │
                          └────────┬────────┘
                                   ▼
                          ┌─────────────────┐
                          │ processing-svc  │
                          └────────┬────────┘
                                   │
                    ┌──────────────┴──────────────┐
                    ▼                              ▼
           ┌─────────────────┐           ┌─────────────────┐
           │   PostgreSQL    │           │     Redis       │
           └─────────────────┘           └─────────────────┘
                    │
          ┌─────────┴─────────┐
          ▼                   ▼
┌─────────────────┐ ┌─────────────────┐
│ monitoring-svc  │ │   alert-svc     │
└─────────────────┘ └─────────────────┘
```

## Services

### Log Generator Services (Simulated Microservices)
| Service | Port | Description |
|---------|------|-------------|
| user-service | 9001 | Generates auth logs, calls payment-service |
| payment-service | 9002 | Generates transaction logs, calls order-service |
| order-service | 9003 | Generates order logs (leaf service) |

### Core Services
| Service | Port | Description |
|---------|------|-------------|
| ingestion-service | 8080, 8081 | REST API for log intake, publishes to Kafka (2 instances) |
| processing-service | 8082 | Consumes from Kafka, persists to PostgreSQL, caches in Redis |
| monitoring-service | 8083 | Query APIs for logs and metrics |
| alert-service | 8084 | Threshold-based alerting on log patterns |

### Infrastructure
| Component | Port | Description |
|-----------|------|-------------|
| NGINX | 80 | Load balancer + rate limiter |
| Kafka | 9092 | Message broker |
| PostgreSQL | 5434 (host) / 5432 (container) | Log persistence |
| Redis | 6379 | Hot cache |

## Key Features

- **Distributed Tracing**: Trace IDs propagate across service calls
- **Load Balancing**: NGINX distributes traffic across ingestion instances
- **Rate Limiting**: Protection against traffic spikes
- **Idempotent Processing**: Duplicate logs are rejected
- **Dead Letter Queue**: Failed messages are preserved for debugging
- **Manual Offset Commits**: No data loss on consumer crashes

## Current Progress

| Phase | Status |
|------|--------|
| Phase 0-11 | COMPLETE |
| Phase 12 | SKIPPED (health endpoints already present) |
| Phase 13 | COMPLETE (k6 load tests implemented and documented) |
| Phase 14 | IN PROGRESS |

## Quick Start

```bash
# 1) Java 21
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home

# 2) Build
./gradlew build

# 3) Start infrastructure
docker-compose up -d

# 4) Start services (separate terminals)
./gradlew :ingestion-service:bootRun --args='--server.port=8080'
./gradlew :ingestion-service:bootRun --args='--server.port=8081'
./gradlew :processing-service:bootRun
./gradlew :monitoring-service:bootRun
./gradlew :alert-service:bootRun
./gradlew :order-service:bootRun
./gradlew :payment-service:bootRun
./gradlew :user-service:bootRun
```

## Load Testing Results (Phase 13)

### Scenario A: Through NGINX (`BASE_URL=http://localhost`)
- NGINX rate limiting triggered as designed (`429 Too Many Requests`).
- Under sustained overload, backend saturation produced `502 Bad Gateway`.
- Successful-request latency remained low before saturation (p95 ~2.79ms, p99 ~6.71ms).

### Scenario B: Direct ingestion baseline (`BASE_URL=http://localhost:8080`)

Command:
```bash
cd load-tests
k6 run --vus 10 --duration 60s -e BASE_URL=http://localhost:8080 ingestion-load-test.js
```

Results:
- Requests accepted: `11,680`
- Throughput: `194.46 req/s`
- Error rate: `0.00%`
- HTTP average latency: `702.28us`
- HTTP p95 latency: `924.04us`
- HTTP p99 latency: `2.39ms`

## Tradeoffs and Bottlenecks

- NGINX rate limiting (100 req/s per IP) protects ingestion but caps throughput on the load-balanced entry path.
- Direct-ingestion results isolate app performance; they should be used for service-level baseline measurements.
- Observed `502` responses at very high load indicate current saturation points and guide scaling/tuning work (JVM, connection pools, Kafka/DB backpressure).
- PRD throughput target (`5,000+ logs/sec`) has not yet been reached with current tested profiles; additional tuning and horizontal scaling are needed.

## Test Coverage (2026-02-16)

Command:
```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./gradlew test jacocoTestReport --continue
```

Line coverage (modules with tests):
- `ingestion-service`: `38.71%` (24/62 lines)
- `alert-service`: `48.59%` (121/249 lines)
- Overall (reported modules): `46.62%` (145/311 lines)

Instruction coverage (modules with tests):
- `ingestion-service`: `43.26%` (122/282 instructions)
- `alert-service`: `44.35%` (483/1089 instructions)
- Overall (reported modules): `44.13%` (605/1371 instructions)

Current status vs roadmap target:
- Target: `80%+`
- Current: `46.62%` line coverage
- Gap: expand tests in `processing-service`, `monitoring-service`, and generator services, then increase branch-path coverage in existing test suites.

## Project Structure

```
distributed-logging-system/
├── common/                 # Shared DTOs and utilities
├── user-service/           # Log generator (calls payment-service)
├── payment-service/        # Log generator (calls order-service)
├── order-service/          # Log generator (leaf service)
├── ingestion-service/      # Log intake REST API
├── processing-service/     # Kafka consumer, DB writer
├── monitoring-service/     # Query APIs
├── alert-service/          # Alerting rules
├── load-tests/             # k6 scripts and runner
├── nginx/                  # Load balancer config
└── docker-compose.yml      # Infrastructure
```
