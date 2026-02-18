# Distributed Logging & Monitoring System

A scalable, event-driven log ingestion and monitoring pipeline demonstrating microservice communication, load balancing, distributed tracing, and Kafka-based processing. Includes a React dashboard for log querying and real-time streaming.

## Tech Stack

| Component | Technology |
|-----------|------------|
| Backend | Java 21, Spring Boot 3.x |
| Frontend | React 18, TypeScript, TailwindCSS |
| Build | Gradle (Kotlin DSL), Vite |
| Load Balancer | NGINX |
| Message Broker | Apache Kafka |
| Database | PostgreSQL (+ Hibernate) |
| Cache | Redis |

## Architecture

```
┌─────────────────┐       ┌─────────────────┐       ┌─────────────────┐
│  user-service   │──────▶│ payment-service │──────▶│  order-service  │
│     :9001       │       │     :9002       │       │     :9003       │
└────────┬────────┘       └────────┬────────┘       └────────┬────────┘
         │                         │                         │
         │         (shared trace IDs - distributed tracing)  │
         └─────────────────────────┼─────────────────────────┘
                                   │
                                   ▼
                          ┌─────────────────┐
                          │     NGINX       │  :80
                          │  Load Balancer  │
                          └────────┬────────┘
                                   │
                    ┌──────────────┴──────────────┐
                    ▼                              ▼
           ┌─────────────────┐           ┌─────────────────┐
           │ ingestion-svc-1 │           │ ingestion-svc-2 │
           │     :8080       │           │     :8081       │
           └────────┬────────┘           └────────┬────────┘
                    └──────────────┬──────────────┘
                                   ▼
                          ┌─────────────────┐
                          │     Kafka       │  :9092
                          └────────┬────────┘
                                   ▼
                          ┌─────────────────┐
                          │ monitoring-svc  │  :8083
                          │ (consumer +     │
                          │  REST + WS)     │
                          └────────┬────────┘
                                   │
                    ┌──────────────┼──────────────┐
                    ▼              │              ▼
           ┌─────────────────┐     │     ┌─────────────────┐
           │   PostgreSQL    │     │     │     Redis       │
           │   :5434 (host)  │     │     │     :6379       │
           └─────────────────┘     │     └─────────────────┘
                                   ▼
                          ┌─────────────────┐
                          │ React Dashboard │  :5173
                          └─────────────────┘
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
| monitoring-service | 8083 | Kafka consumer, persistence, query APIs, WebSocket for live logs |

### Frontend
| Component | Port | Description |
|-----------|------|-------------|
| dashboard | 5173 | React dashboard for log querying and live streaming |

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
- **Live Log Streaming**: WebSocket-based real-time log updates
- **React Dashboard**: Search, filter, and monitor logs visually

## Quick Start

### One-Command Startup (Recommended)

```bash
# Start everything (kills existing services, rebuilds, and starts fresh)
./start.sh

# Options:
./start.sh --skip-build    # Skip Gradle build (faster restart)
./start.sh --infra-only    # Only start Docker containers
./start.sh --stop          # Stop all services
```

This will start all infrastructure (Kafka, PostgreSQL, Redis, NGINX), all backend services, and the React dashboard. Logs are written to `./logs/`.

### Manual Startup

```bash
# 1) Java 21
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home

# 2) Build backend
./gradlew build

# 3) Start infrastructure
docker-compose up -d

# 4) Start backend services (separate terminals)
./gradlew :ingestion-service:bootRun --args='--server.port=8080'
./gradlew :ingestion-service:bootRun --args='--server.port=8081'
./gradlew :monitoring-service:bootRun
./gradlew :order-service:bootRun
./gradlew :payment-service:bootRun
./gradlew :user-service:bootRun

# 5) Start dashboard
cd dashboard && npm install && npm run dev
```

Open http://localhost:5173 in your browser to access the dashboard.

## Dashboard Features

- **Search Logs**: Filter by service, severity, trace ID with pagination
- **Live Logs**: Real-time log streaming via WebSocket
- **Metrics**: Severity and service distribution charts

## Load Testing

Load tests use [k6](https://k6.io/) to benchmark the system.

### Prerequisites

```bash
brew install k6
```

### Running Load Tests

```bash
cd load-tests

# Quick smoke test (verify system is working)
./run-load-tests.sh smoke

# Run specific load tests
./run-load-tests.sh ingestion     # Single log ingestion (~6 min)
./run-load-tests.sh batch         # Batch ingestion (~4 min)
./run-load-tests.sh monitoring    # Query APIs (~4 min)
./run-load-tests.sh all           # Run all tests

# Quick mode (30s with 10 VUs)
./run-load-tests.sh ingestion --quick

# Direct to ingestion service (bypasses NGINX rate limiting)
./run-load-tests.sh ingestion --base-url http://localhost:8080
```

### Test Configurations

| Test | Duration | Max VUs | Thresholds |
|------|----------|---------|------------|
| smoke | ~10s | 1 | p95 < 1s |
| ingestion | ~6 min | 100 | p95 < 500ms, p99 < 1s |
| batch | ~4 min | 20 | p95 < 2s, p99 < 5s |
| monitoring | ~4 min | 30 | p95 < 200ms, p99 < 500ms |

### Baseline Results

| Metric | Value |
|--------|-------|
| Requests accepted | 11,680 |
| Throughput | 194.46 req/s |
| Error rate | 0.00% |
| Avg latency | 702.28us |
| p95 latency | 924.04us |
| p99 latency | 2.39ms |

## Project Structure

```
distributed-logging-system/
├── common/                 # Shared DTOs and utilities
├── user-service/           # Log generator (calls payment-service)
├── payment-service/        # Log generator (calls order-service)
├── order-service/          # Log generator (leaf service)
├── ingestion-service/      # Log intake REST API
├── monitoring-service/     # Kafka consumer, persistence, query APIs, WebSocket
├── dashboard/              # React frontend
├── load-tests/             # k6 scripts and runner
├── nginx/                  # Load balancer config
└── docker-compose.yml      # Infrastructure
```
