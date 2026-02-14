# Distributed Logging & Monitoring System

A scalable, event-driven log ingestion and monitoring pipeline demonstrating microservice communication, load balancing, distributed tracing, and Kafka-based processing.

> **Status**: In Development

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
| PostgreSQL | 5432 | Log persistence |
| Redis | 6379 | Hot cache |

## Key Features

- **Distributed Tracing**: Trace IDs propagate across service calls
- **Load Balancing**: NGINX distributes traffic across ingestion instances
- **Rate Limiting**: Protection against traffic spikes
- **Idempotent Processing**: Duplicate logs are rejected
- **Dead Letter Queue**: Failed messages are preserved for debugging
- **Manual Offset Commits**: No data loss on consumer crashes


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
├── nginx/                  # Load balancer config
└── docker-compose.yml      # Infrastructure
```

Current Status: Load testing