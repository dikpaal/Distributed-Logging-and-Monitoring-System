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

### Test Modes

Use two modes and interpret them differently:

1. **Through NGINX** (`BASE_URL=http://localhost`)
- Validates load balancing and rate limiting behavior.
- `429 Too Many Requests` is expected when traffic exceeds NGINX limits.
- `502 Bad Gateway` indicates backend saturation or service instability under sustained overload.

2. **Direct to ingestion service** (`BASE_URL=http://localhost:8080` or `:8081`)
- Measures ingestion service baseline without NGINX rate limiting.
- Best mode for capacity and latency benchmarking of the application layer.

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

## Latest Baseline Results (2026-02-16)

### Scenario A: Through NGINX at high load

- Rate limiting activated as designed (`429 Too Many Requests`).
- Backend eventually returned `502 Bad Gateway` under sustained overload.
- Successful-request latency stayed low before saturation:
  - p95 ~2.79ms
  - p99 ~6.71ms

### Scenario B: Direct ingestion baseline

Command:
```bash
k6 run --vus 10 --duration 60s -e BASE_URL=http://localhost:8080 ingestion-load-test.js
```

Results:
- Requests accepted: `11,680`
- Throughput: `194.46 req/s`
- Error rate: `0.00%`
- HTTP avg latency: `702.28us`
- HTTP p95 latency: `924.04us`
- HTTP p99 latency: `2.39ms`

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

If you see 429 responses, NGINX rate limiting is working as designed. For throughput benchmarking, run against ingestion directly (`--base-url http://localhost:8080`).

### High Error Rate

If error rate spikes with 502 responses, backend services are saturated under current load profile. Check service logs and resource limits:
```bash
docker-compose logs -f kafka
docker-compose logs -f postgres
docker-compose logs -f nginx
```
