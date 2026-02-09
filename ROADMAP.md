# ROADMAP.md - Incremental Build Plan

> Build in layers. Each checkpoint = working system.

---

## Philosophy

**Don't build everything, then test.**
**Build a little, verify it works, then add more.**

Each phase ends with a checkpoint where you have something functional and demonstrable.

---

## Phase 0: Project Skeleton
**Goal**: Empty project that compiles

### Tasks
- [ ] Initialize Gradle multi-module project
- [ ] Create `build.gradle.kts` (root)
- [ ] Create `settings.gradle.kts`
- [ ] Create empty modules: `common`, `ingestion-service`
- [ ] Add Spring Boot plugin and dependencies
- [ ] Verify: `./gradlew build` succeeds

### Checkpoint 0
```bash
./gradlew build
# BUILD SUCCESSFUL
```

---

## Phase 1: Infrastructure
**Goal**: Docker containers running locally

### Tasks
- [ ] Create `docker-compose.yml`
  - [ ] Kafka + Zookeeper
  - [ ] PostgreSQL
  - [ ] Redis
- [ ] Verify each service starts
- [ ] Create `init.sql` for database schema

### Checkpoint 1
```bash
docker-compose up -d
docker-compose ps
# All containers healthy

# Test Kafka
docker exec -it kafka kafka-topics --list --bootstrap-server localhost:9092

# Test PostgreSQL
docker exec -it postgres psql -U postgres -c "SELECT 1"

# Test Redis
docker exec -it redis redis-cli ping
# PONG
```

---

## Phase 2: Hello World Ingestion
**Goal**: REST endpoint that returns 200 OK

### Tasks
- [ ] Create `ingestion-service` Spring Boot app
- [ ] Add simple health endpoint: `GET /health`
- [ ] Add log endpoint: `POST /api/v1/logs` (just returns 200, no logic)
- [ ] Create `LogRequest` DTO in `common` module
- [ ] Add request validation annotations
- [ ] Write first unit test

### Checkpoint 2
```bash
./gradlew :ingestion-service:bootRun

# In another terminal:
curl http://localhost:8080/health
# {"status": "UP"}

curl -X POST http://localhost:8080/api/v1/logs \
  -H "Content-Type: application/json" \
  -d '{"serviceName": "test", "severity": "INFO", "message": "hello"}'
# 200 OK
```

---

## Phase 3: Kafka Producer
**Goal**: Logs published to Kafka topic

### Tasks
- [ ] Add Kafka dependencies
- [ ] Create `KafkaConfig` with producer settings
- [ ] Create `LogProducer` service
- [ ] Wire controller → producer
- [ ] Create Kafka topic: `logs.ingested`
- [ ] Integration test with embedded Kafka (or Testcontainers)

### Checkpoint 3
```bash
# Start services
docker-compose up -d
./gradlew :ingestion-service:bootRun

# Send a log
curl -X POST http://localhost:8080/api/v1/logs \
  -H "Content-Type: application/json" \
  -d '{"serviceName": "test", "severity": "INFO", "message": "hello kafka"}'

# Verify in Kafka
docker exec -it kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic logs.ingested \
  --from-beginning
# Should see your message
```

---

## Phase 4: Basic Processing Service
**Goal**: Consume from Kafka, print to console

### Tasks
- [ ] Create `processing-service` module
- [ ] Add Kafka consumer dependencies
- [ ] Create `LogConsumer` that prints messages
- [ ] Configure consumer group
- [ ] Verify consumption works

### Checkpoint 4
```bash
# Terminal 1: Infrastructure
docker-compose up -d

# Terminal 2: Ingestion
./gradlew :ingestion-service:bootRun

# Terminal 3: Processing
./gradlew :processing-service:bootRun

# Terminal 4: Send log
curl -X POST http://localhost:8080/api/v1/logs \
  -H "Content-Type: application/json" \
  -d '{"serviceName": "test", "severity": "ERROR", "message": "test message"}'

# Check Terminal 3 - should see log printed
```

**Milestone: End-to-end flow works (REST → Kafka → Consumer)**

---

## Phase 5: PostgreSQL Persistence
**Goal**: Logs stored in database

### Tasks
- [ ] Add Spring Data JPA dependencies
- [ ] Create `LogEntity` entity
- [ ] Create `LogRepository`
- [ ] Wire consumer → repository
- [ ] Verify data in PostgreSQL

### Checkpoint 5
```bash
# Send some logs via curl...

# Check database
docker exec -it postgres psql -U postgres -d logging -c "SELECT * FROM logs;"
# Should see your logs
```

---

## Phase 6: Redis Caching
**Goal**: Recent logs cached in Redis

### Tasks
- [ ] Add Spring Data Redis dependencies
- [ ] Create `LogCacheService`
- [ ] Cache last N logs per service
- [ ] Set TTL (1 hour)
- [ ] Wire into processing pipeline

### Checkpoint 6
```bash
# Send logs...

# Check Redis
docker exec -it redis redis-cli
> LRANGE logs:recent:test-service 0 -1
# Should see cached logs
```

**Milestone: Full processing pipeline (Kafka → PostgreSQL + Redis)**

---

## Phase 7: Basic Query API
**Goal**: Read logs back via REST

### Tasks
- [ ] Create `monitoring-service` module
- [ ] Add `GET /api/v1/logs` endpoint
- [ ] Query PostgreSQL
- [ ] Basic filtering (serviceName, severity)
- [ ] Pagination

### Checkpoint 7
```bash
./gradlew :monitoring-service:bootRun

curl "http://localhost:8081/api/v1/logs?serviceName=test&severity=ERROR"
# Returns logs from database
```

---

## Phase 8: Idempotency
**Goal**: Duplicate logs rejected

### Tasks
- [ ] Add `X-Idempotency-Key` header support in ingestion
- [ ] Track processed keys in Redis (with TTL)
- [ ] Skip duplicates in processing service
- [ ] Unit tests for idempotency

### Checkpoint 8
```bash
# Send same request twice with same idempotency key
curl -X POST http://localhost:8080/api/v1/logs \
  -H "Content-Type: application/json" \
  -H "X-Idempotency-Key: test-123" \
  -d '{"serviceName": "test", "severity": "INFO", "message": "duplicate test"}'

# Send again with same key
curl -X POST http://localhost:8080/api/v1/logs \
  -H "Content-Type: application/json" \
  -H "X-Idempotency-Key: test-123" \
  -d '{"serviceName": "test", "severity": "INFO", "message": "duplicate test"}'

# Database should have only ONE entry
```

---

## Phase 9: Error Handling & DLQ
**Goal**: Failed messages go to dead letter queue

### Tasks
- [ ] Create `logs.dlq` topic
- [ ] Add retry logic (3 attempts)
- [ ] Send to DLQ after failures
- [ ] Add error logging
- [ ] Test with malformed message

### Checkpoint 9
```bash
# Manually publish bad message to Kafka
docker exec -it kafka kafka-console-producer \
  --bootstrap-server localhost:9092 \
  --topic logs.ingested
> {"invalid": "json structure"}

# Check DLQ
docker exec -it kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic logs.dlq \
  --from-beginning
# Should see the bad message
```

**Milestone: Reliability features complete**

---

## Phase 10: Manual Offset Commits
**Goal**: No data loss on crash

### Tasks
- [ ] Disable auto-commit
- [ ] Commit after successful DB write
- [ ] Test crash recovery
- [ ] Add acknowledgment modes

### Checkpoint 10
```bash
# Send logs, kill processing-service mid-processing
# Restart processing-service
# Verify no messages lost (re-consumed from last commit)
```

---

## Phase 11: Rate Limiting
**Goal**: Protect ingestion service from overload

### Tasks
- [ ] Add Bucket4j dependency
- [ ] Configure rate limit (1000 req/sec)
- [ ] Return 429 when exceeded
- [ ] Add rate limit headers

### Checkpoint 11
```bash
# Rapid fire requests
for i in {1..100}; do
  curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8080/api/v1/logs \
    -H "Content-Type: application/json" \
    -d '{"serviceName": "test", "severity": "INFO", "message": "load test"}'
done
# Should see some 429 responses
```

---

## Phase 12: Health Checks & Metrics
**Goal**: Observability endpoints

### Tasks
- [ ] Add Actuator dependency
- [ ] Configure health checks (Kafka, DB, Redis)
- [ ] Add Micrometer metrics
- [ ] Expose `/metrics` endpoint

### Checkpoint 12
```bash
curl http://localhost:8080/actuator/health
# {"status": "UP", "components": {...}}

curl http://localhost:8080/actuator/metrics
# List of available metrics
```

**Milestone: Production-ready ingestion & processing**

---

## Phase 13: Aggregation Endpoints
**Goal**: Metrics and analytics

### Tasks
- [ ] `GET /api/v1/metrics/counts` - logs by severity
- [ ] `GET /api/v1/metrics/services` - logs per service
- [ ] `GET /api/v1/metrics/timeline` - logs over time
- [ ] Cache results in Redis

### Checkpoint 13
```bash
curl http://localhost:8081/api/v1/metrics/counts
# {"INFO": 150, "WARN": 45, "ERROR": 12}

curl http://localhost:8081/api/v1/metrics/services
# {"user-service": 100, "api-gateway": 80, ...}
```

---

## Phase 14: Alert Service (Basic)
**Goal**: Detect threshold breaches

### Tasks
- [ ] Create `alert-service` module
- [ ] Subscribe to processed logs topic
- [ ] Implement simple counter (errors in last N minutes)
- [ ] Log alert when threshold exceeded
- [ ] Store alerts in database

### Checkpoint 14
```bash
./gradlew :alert-service:bootRun

# Send 15 ERROR logs quickly
for i in {1..15}; do
  curl -X POST http://localhost:8080/api/v1/logs \
    -H "Content-Type: application/json" \
    -d '{"serviceName": "test", "severity": "ERROR", "message": "error '$i'"}'
done

# Check alert-service logs
# Should see: "ALERT: high-error-rate triggered (15 errors in 1 minute)"
```

---

## Phase 15: Sliding Window
**Goal**: Accurate time-windowed counting

### Tasks
- [ ] Implement sliding window (not tumbling)
- [ ] Use Redis sorted sets for timestamps
- [ ] Configurable window size
- [ ] Alert cooldown (don't spam)

### Checkpoint 15
```bash
# Verify window slides correctly
# Send errors spread over time
# Alerts should only fire when window threshold exceeded
```

**Milestone: Full system functional**

---

## Phase 16: Load Testing
**Goal**: Document performance

### Tasks
- [ ] Set up k6 or JMeter
- [ ] Write load test scripts
- [ ] Run sustained load (5 min)
- [ ] Measure throughput, latency (p50, p95, p99)
- [ ] Document results

### Checkpoint 16
```bash
k6 run load-test.js
# Results:
# - Throughput: X logs/sec
# - p50 latency: X ms
# - p95 latency: X ms
# - Error rate: X%
```

---

## Phase 17: Horizontal Scaling Test
**Goal**: Prove scalability

### Tasks
- [ ] Run multiple processing-service instances
- [ ] Verify partition assignment
- [ ] Measure throughput improvement
- [ ] Document scaling behavior

### Checkpoint 17
```bash
# Run 3 consumer instances
./gradlew :processing-service:bootRun --args='--server.port=8090'
./gradlew :processing-service:bootRun --args='--server.port=8091'
./gradlew :processing-service:bootRun --args='--server.port=8092'

# Run load test
# Verify all 3 are processing (check logs)
# Measure combined throughput
```

---

## Phase 18: Documentation & Polish
**Goal**: Resume-ready

### Tasks
- [ ] Architecture diagram (Mermaid or draw.io)
- [ ] Update README with full documentation
- [ ] Document tradeoffs and decisions
- [ ] Add "Future Improvements" section
- [ ] Test coverage report (80%+)
- [ ] Code cleanup and formatting

### Checkpoint 18
```bash
./gradlew test jacocoTestReport
# Coverage: 80%+

# README complete with:
# - System design section
# - How to run
# - Architecture diagram
# - Performance results
# - Tradeoffs discussed
```

---

## Final Checklist

### Technical
- [ ] All services start without errors
- [ ] End-to-end flow works
- [ ] Error handling robust
- [ ] Tests passing (80%+ coverage)
- [ ] Load test results documented

### Resume Bullet
```
Designed and implemented a distributed log ingestion and monitoring pipeline
using Spring Boot and Kafka, achieving X logs/sec throughput with idempotent
processing, retry handling, and horizontal scalability.
```

### Interview Talking Points
- [ ] Can explain architecture decisions
- [ ] Can discuss Kafka offset management
- [ ] Can explain idempotency approach
- [ ] Can discuss scaling strategy
- [ ] Know the bottlenecks and tradeoffs

---

## Quick Reference: What's Working at Each Phase

| Phase | What Works |
|-------|------------|
| 0 | Project compiles |
| 1 | Infrastructure runs |
| 2 | REST endpoint responds |
| 3 | Logs reach Kafka |
| 4 | Consumer receives logs |
| 5 | Logs in PostgreSQL |
| 6 | Logs cached in Redis |
| 7 | Query API works |
| 8 | Duplicates rejected |
| 9 | Failures go to DLQ |
| 10 | Crash recovery works |
| 11 | Rate limiting active |
| 12 | Health/metrics exposed |
| 13 | Analytics available |
| 14 | Alerts firing |
| 15 | Accurate windowing |
| 16 | Performance documented |
| 17 | Scaling proven |
| 18 | Resume ready |
