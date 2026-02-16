# Load Tests

Load testing suite for the Distributed Logging System using [k6](https://k6.io/).

## Prerequisites

Install k6:

```bash
brew install k6
```

## Test Scripts

| Script | Description | Duration |
|--------|-------------|----------|
| `smoke-test.js` | Quick verification that system is working | ~10s |
| `ingestion-load-test.js` | Single log ingestion under load | ~6 min |
| `batch-load-test.js` | Batch log ingestion under load | ~4 min |
| `monitoring-load-test.js` | Monitoring API queries under load | ~4 min |

## Running Tests

### Prerequisites

Ensure the system is running:

```bash
# Start infrastructure
docker-compose up -d

# Start services (in separate terminals)
./gradlew :ingestion-service:bootRun --args='--server.port=8080'
./gradlew :ingestion-service:bootRun --args='--server.port=8081'
./gradlew :processing-service:bootRun
./gradlew :monitoring-service:bootRun
```

### Using the Runner Script

```bash
cd load-tests

# Run smoke test (quick verification)
./run-load-tests.sh smoke

# Run specific load test
./run-load-tests.sh ingestion
./run-load-tests.sh batch
./run-load-tests.sh monitoring

# Run all tests
./run-load-tests.sh all

# Quick mode (30s with 10 VUs)
./run-load-tests.sh ingestion --quick

# Custom base URL
./run-load-tests.sh ingestion --base-url http://localhost:8080
```

### Running k6 Directly

```bash
# Smoke test
k6 run smoke-test.js

# Ingestion load test
k6 run ingestion-load-test.js

# With custom base URL
k6 run -e BASE_URL=http://localhost:8080 ingestion-load-test.js

# Quick test with custom VUs and duration
k6 run --vus 10 --duration 30s ingestion-load-test.js
```

## Test Configurations

### Ingestion Load Test

Ramp-up pattern:
- 0 → 10 VUs over 30s
- Hold 10 VUs for 1m
- 10 → 50 VUs over 30s
- Hold 50 VUs for 2m
- 50 → 100 VUs over 30s
- Hold 100 VUs for 1m
- Ramp down

Thresholds:
- p95 latency < 500ms
- p99 latency < 1000ms
- Error rate < 1%

### Batch Load Test

Tests batch ingestion with varying batch sizes (10, 25, 50, 100 logs per batch).

Thresholds:
- p95 latency < 2000ms
- p99 latency < 5000ms
- Error rate < 1%

### Monitoring Load Test

Tests read API with mixed query patterns:
- 30% - List logs with pagination
- 20% - Filter by service
- 20% - Filter by severity
- 15% - Metrics counts
- 15% - Service metrics

Thresholds:
- p95 latency < 200ms
- p99 latency < 500ms
- Error rate < 5%

## Interpreting Results

### Key Metrics

| Metric | Description | Target |
|--------|-------------|--------|
| `http_reqs` | Total requests made | Higher is better |
| `http_req_duration` | Request latency | Lower is better |
| `http_req_failed` | Failed requests | < 1% |
| `vus` | Virtual users | Configured |

### Latency Percentiles

- **p50 (median)**: 50% of requests are faster than this
- **p95**: 95% of requests are faster than this (key SLA metric)
- **p99**: 99% of requests are faster than this (tail latency)

### Example Output

```
THROUGHPUT
  Total Requests:     15,234
  Requests/sec:       423.17
  Logs Accepted:      15,200

LATENCY (ms)
  Average:            45.23
  p50:                32.15
  p95:                125.67
  p99:                245.89

ERROR RATE:           0.22%
```

## Performance Targets

Based on PRD requirements:

| Metric | Target |
|--------|--------|
| Ingestion throughput | 5,000+ logs/sec |
| End-to-end latency | < 5 seconds |
| Query response time (p95) | < 200ms |

## Troubleshooting

### Connection Refused

```
ERRO[0001] Request Failed error="Post ... connect: connection refused"
```

Ensure services are running:
```bash
curl http://localhost/health        # NGINX
curl http://localhost:8080/health   # Ingestion service
curl http://localhost:8083/health   # Monitoring service
```

### Rate Limited

If you see 429 responses, NGINX rate limiting is working. Adjust test parameters or NGINX config.

### High Error Rate

Check service logs for errors:
```bash
docker-compose logs -f kafka
docker-compose logs -f postgres
```
