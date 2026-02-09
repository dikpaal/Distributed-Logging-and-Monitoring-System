# Product Requirements Document (PRD)
## Distributed Logging and Monitoring System

---

## 1. Problem Statement

Modern distributed systems generate massive amounts of logs across multiple services. Engineers need a centralized, scalable solution to:
- Ingest logs from multiple sources at high throughput
- Store and query logs efficiently
- Monitor system health in real-time
- Alert on anomalies and threshold breaches

This project implements a production-grade log pipeline demonstrating event-driven architecture, reliability patterns, and horizontal scalability.

---

## 2. System Architecture

### 2.1 High-Level Design

```
┌─────────────────┐
│  Client / App   │
└────────┬────────┘
         │ REST API (JSON)
         v
┌─────────────────┐      ┌─────────────────┐      ┌─────────────────┐
│    Ingestion    │─────>│      Kafka      │─────>│   Processing    │
│    Service      │      │   (logs topic)  │      │    Service      │
└─────────────────┘      └────────┬────────┘      └────────┬────────┘
                                  │                        │
                                  │                        v
                         ┌────────┴────────┐      ┌─────────────────┐
                         │  Alert Service  │      │   PostgreSQL    │
                         │  (subscriber)   │      └────────┬────────┘
                         └─────────────────┘               │
                                                           v
                         ┌─────────────────┐      ┌─────────────────┐
                         │   Monitoring    │<─────│     Redis       │
                         │    Service      │      │   (hot cache)   │
                         └─────────────────┘      └─────────────────┘
```

### 2.2 Data Flow
1. Clients send logs via REST API to Ingestion Service
2. Ingestion Service validates, batches, and publishes to Kafka
3. Processing Service consumes from Kafka, enriches logs, writes to PostgreSQL
4. Recent logs cached in Redis for fast queries
5. Monitoring Service queries PostgreSQL/Redis for log retrieval and metrics
6. Alert Service subscribes to processed events, triggers alerts on rules

---

## 3. Service Specifications

### 3.1 Ingestion Service

**Responsibility**: Accept, validate, and forward logs to Kafka

**Endpoints**:
| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/logs` | Ingest single log |
| POST | `/api/v1/logs/batch` | Ingest batch of logs |
| GET | `/health` | Health check |
| GET | `/metrics` | Prometheus metrics |

**Request Schema** (POST /api/v1/logs):
```json
{
  "serviceName": "user-service",
  "severity": "ERROR",
  "message": "Failed to connect to database",
  "timestamp": "2024-01-15T10:30:00Z",
  "traceId": "abc-123-def",
  "host": "server-01",
  "metadata": {
    "userId": "12345",
    "endpoint": "/api/users"
  }
}
```

**Key Features**:
- Rate limiting (Bucket4j) - 1000 requests/sec per client
- Idempotency key support (header: `X-Idempotency-Key`)
- Async batching (configurable batch size/interval)
- Request validation (Jakarta Validation)
- Structured logging with trace ID propagation
- Micrometer metrics (request count, latency, Kafka publish rate)

**Design Decisions**:
- Does NOT write directly to database (decoupled via Kafka)
- Fire-and-forget to Kafka (async) for throughput
- Configurable batch window (default: 100ms or 50 messages)

---

### 3.2 Processing Service

**Responsibility**: Consume logs, enrich, persist, and cache

**Kafka Consumer Configuration**:
- Topic: `logs.ingested`
- Consumer Group: `log-processors`
- Manual offset commit (no auto-commit)
- Concurrency: Configurable (default: 3 threads)

**Processing Pipeline**:
1. Deserialize log event
2. Check idempotency (skip if already processed)
3. Enrich log:
   - Normalize timestamp to UTC
   - Parse and validate severity
   - Add processing metadata
4. Write to PostgreSQL (batch insert)
5. Cache in Redis (last N logs per service, TTL: 1 hour)
6. Commit Kafka offset
7. On failure: Send to dead letter topic (`logs.dlq`)

**Key Features**:
- Idempotent consumer (track processed IDs in Redis)
- Retry logic (3 attempts with exponential backoff)
- Dead letter topic for poison messages
- Transaction boundary control (DB + cache consistency)
- Graceful shutdown (finish in-flight, commit offsets)
- Backpressure handling

**Metrics**:
- Messages processed/sec
- Processing latency (p50, p95, p99)
- Error rate
- DLQ message count

---

### 3.3 Monitoring Service

**Responsibility**: Query logs and compute metrics

**Endpoints**:
| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/logs` | Query logs with filters |
| GET | `/api/v1/logs/{id}` | Get specific log |
| GET | `/api/v1/metrics/counts` | Log counts by severity |
| GET | `/api/v1/metrics/services` | Logs per service |
| GET | `/api/v1/metrics/timeline` | Logs over time |
| GET | `/health` | Health check |

**Query Parameters** (GET /api/v1/logs):
| Param | Type | Description |
|-------|------|-------------|
| serviceName | string | Filter by service |
| severity | string | Filter by severity (INFO, WARN, ERROR) |
| startTime | ISO8601 | Start of time range |
| endTime | ISO8601 | End of time range |
| traceId | string | Filter by trace ID |
| page | int | Page number (default: 0) |
| size | int | Page size (default: 20, max: 100) |

**Response Schema**:
```json
{
  "content": [...],
  "page": 0,
  "size": 20,
  "totalElements": 1523,
  "totalPages": 77
}
```

**Key Features**:
- Pagination (Spring Data Page)
- Indexed queries (see Data Model)
- Cached metric results (Redis, TTL: 5 min)
- Efficient aggregation queries

---

### 3.4 Alert Service

**Responsibility**: Monitor processed logs and trigger alerts

**Alert Rules** (configurable):
```yaml
rules:
  - name: "high-error-rate"
    condition: "severity == ERROR AND count > 10"
    window: "1m"
    action: "log"  # Future: email, slack, webhook

  - name: "service-down"
    condition: "serviceName == 'critical-service' AND severity == ERROR"
    window: "30s"
    threshold: 5
    action: "log"
```

**Implementation**:
- Subscribe to Kafka topic (same consumer group or separate)
- Sliding window implementation (in-memory with Redis backup)
- Rule engine evaluation
- Alert deduplication (don't spam same alert)

**Key Features**:
- Configurable alert rules
- Sliding window counts
- Alert cooldown period
- Alert history persistence

**Endpoints**:
| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/alerts` | Get triggered alerts |
| GET | `/api/v1/alerts/rules` | List configured rules |
| POST | `/api/v1/alerts/rules` | Add new rule |
| DELETE | `/api/v1/alerts/rules/{id}` | Remove rule |

---

## 4. Data Model

### 4.1 PostgreSQL Schema

**Table: logs**
```sql
CREATE TABLE logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    service_name VARCHAR(100) NOT NULL,
    severity VARCHAR(10) NOT NULL,
    message TEXT NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL,
    trace_id VARCHAR(50),
    host VARCHAR(100),
    metadata JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    processed_at TIMESTAMPTZ DEFAULT NOW()
);

-- Indexes for common query patterns
CREATE INDEX idx_logs_service_timestamp ON logs(service_name, timestamp DESC);
CREATE INDEX idx_logs_severity_timestamp ON logs(severity, timestamp DESC);
CREATE INDEX idx_logs_trace_id ON logs(trace_id) WHERE trace_id IS NOT NULL;
CREATE INDEX idx_logs_timestamp ON logs(timestamp DESC);
```

**Table: alerts**
```sql
CREATE TABLE alerts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_name VARCHAR(100) NOT NULL,
    triggered_at TIMESTAMPTZ NOT NULL,
    message TEXT,
    log_count INT,
    resolved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
```

**Table: processed_ids** (for idempotency)
```sql
CREATE TABLE processed_ids (
    idempotency_key VARCHAR(100) PRIMARY KEY,
    processed_at TIMESTAMPTZ DEFAULT NOW()
);

-- Auto-cleanup old entries (partition or scheduled job)
```

### 4.2 Redis Schema

**Keys**:
| Key Pattern | Type | TTL | Description |
|-------------|------|-----|-------------|
| `logs:recent:{serviceName}` | List | 1h | Last 100 logs per service |
| `processed:{idempotencyKey}` | String | 24h | Idempotency tracking |
| `metrics:counts:{date}` | Hash | 24h | Daily severity counts |
| `alerts:cooldown:{ruleName}` | String | varies | Alert cooldown tracker |

---

## 5. Non-Functional Requirements

### 5.1 Performance Targets
| Metric | Target |
|--------|--------|
| Ingestion throughput | 5,000+ logs/sec |
| End-to-end latency (ingest to queryable) | < 5 seconds |
| Query response time (p95) | < 200ms |
| System availability | 99.9% |

### 5.2 Reliability Requirements
- No data loss (at-least-once delivery)
- Graceful degradation under load
- Automatic recovery from transient failures
- Dead letter queue for unprocessable messages

### 5.3 Scalability
- Horizontal scaling via Kafka partitions + consumer instances
- Stateless services (except Redis/PostgreSQL)
- Connection pooling for database

---

## 6. Scope Boundaries

### 6.1 In Scope
- REST API for log ingestion
- Kafka-based event streaming
- PostgreSQL persistence with proper indexing
- Redis caching for hot data
- Basic query and aggregation APIs
- Threshold-based alerting
- Health checks and metrics endpoints
- Docker Compose for local development
- Comprehensive unit and integration tests
- Load testing with documented results
- Architecture documentation

### 6.2 Out of Scope
- UI/Dashboard (API only)
- Kubernetes deployment
- ElasticSearch integration
- Multi-region replication
- User authentication/authorization
- Log compression/archival
- Full-text search
- Machine learning anomaly detection

---

## 7. Success Metrics

### 7.1 Technical Metrics
- [ ] Achieves 5,000+ logs/sec sustained throughput
- [ ] p95 query latency under 200ms
- [ ] Zero message loss under normal operation
- [ ] Successful recovery from Kafka/DB failures
- [ ] 80%+ test coverage

### 7.2 Resume Impact Metrics
- [ ] Clean architecture diagram
- [ ] Documented tradeoffs and decisions
- [ ] Load test results with graphs
- [ ] Clear README with system design section
- [ ] Demonstrates: Kafka, Redis, idempotency, DLQ, manual offsets

---

## 8. Milestones (6 Weeks)

### Week 1: Foundations
- Multi-module Gradle project setup
- Docker Compose (Kafka, PostgreSQL, Redis)
- Ingestion Service (REST → Kafka)
- Basic unit tests

### Week 2: Processing Pipeline
- Kafka consumer implementation
- PostgreSQL persistence
- Redis caching
- Integration tests with Testcontainers

### Week 3: Reliability
- Retry logic and DLQ
- Idempotency handling
- Graceful shutdown
- Health checks

### Week 4: Monitoring & Alerting
- Query APIs with pagination
- Aggregation endpoints
- Alert rule engine
- Sliding window implementation

### Week 5: Performance
- Load testing (k6 or JMeter)
- Performance optimization
- Document throughput/latency
- Horizontal scaling demonstration

### Week 6: Polish
- Architecture diagrams
- README with system design
- Tradeoff documentation
- Final test coverage push

---

## Appendix A: API Error Responses

```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/v1/logs",
  "details": [
    {
      "field": "severity",
      "message": "must be one of: INFO, WARN, ERROR"
    }
  ]
}
```

## Appendix B: Kafka Topics

| Topic | Partitions | Retention | Description |
|-------|------------|-----------|-------------|
| logs.ingested | 6 | 7 days | Raw ingested logs |
| logs.processed | 6 | 7 days | Processed logs (for Alert Service) |
| logs.dlq | 3 | 30 days | Dead letter queue |
