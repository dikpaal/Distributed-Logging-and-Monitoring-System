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
           └────────┬────────┘           └─────────────────┘
                    │
                    ▼
           ┌─────────────────┐
           │ monitoring-svc  │◀──── Kafka (live stream)
           │ (REST + WebSocket)
           └────────┬────────┘
                    │
                    ▼
           ┌─────────────────┐
           │ React Dashboard │
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
| processing-service | 8082 | Consumes from Kafka, persists to PostgreSQL, caches in Redis |
| monitoring-service | 8083 | Query APIs, metrics, and WebSocket for live logs |

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
./gradlew :processing-service:bootRun
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

## Load Testing Results

- Requests accepted: `11,680`
- Throughput: `194.46 req/s`
- Error rate: `0.00%`
- HTTP average latency: `702.28us`
- HTTP p95 latency: `924.04us`
- HTTP p99 latency: `2.39ms`

## Project Structure

```
distributed-logging-system/
├── common/                 # Shared DTOs and utilities
├── user-service/           # Log generator (calls payment-service)
├── payment-service/        # Log generator (calls order-service)
├── order-service/          # Log generator (leaf service)
├── ingestion-service/      # Log intake REST API
├── processing-service/     # Kafka consumer, DB writer
├── monitoring-service/     # Query APIs + WebSocket
├── dashboard/              # React frontend
├── load-tests/             # k6 scripts and runner
├── nginx/                  # Load balancer config
└── docker-compose.yml      # Infrastructure
```
