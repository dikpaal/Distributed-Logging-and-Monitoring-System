# Distributed Logging & Monitoring System

A scalable, event-driven log ingestion and monitoring pipeline built with Java 21 and Spring Boot.

> **Status**: In Development

## Tech Stack

- **Language**: Java 21
- **Framework**: Spring Boot 3.x
- **Build**: Gradle (Kotlin DSL)
- **Message Broker**: Apache Kafka
- **Database**: PostgreSQL
- **Cache**: Redis

## Architecture

```
[Client / App]
       │
       ▼
┌─────────────────┐      ┌─────────────────┐      ┌─────────────────┐
│    Ingestion    │─────▶│      Kafka      │─────▶│   Processing    │
│    Service      │      │                 │      │    Service      │
└─────────────────┘      └────────┬────────┘      └────────┬────────┘
                                  │                        │
                                  ▼                        ▼
                         ┌─────────────────┐      ┌─────────────────┐
                         │  Alert Service  │      │  PostgreSQL +   │
                         │                 │      │     Redis       │
                         └─────────────────┘      └────────┬────────┘
                                                           │
                                                           ▼
                                                  ┌─────────────────┐
                                                  │   Monitoring    │
                                                  │    Service      │
                                                  └─────────────────┘
```

**Services**:
- **Ingestion Service** - REST API for log intake, publishes to Kafka
- **Processing Service** - Consumes from Kafka, persists to PostgreSQL, caches in Redis
- **Monitoring Service** - Query APIs for logs and metrics
- **Alert Service** - Threshold-based alerting on log patterns
