import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { randomString } from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';

// Custom metrics
const batchesSent = new Counter('batches_sent');
const batchesAccepted = new Counter('batches_accepted');
const totalLogsSent = new Counter('total_logs_sent');
const errorRate = new Rate('error_rate');

// Test configuration
export const options = {
    scenarios: {
        batch_load: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '20s', target: 5 },
                { duration: '1m', target: 5 },
                { duration: '20s', target: 20 },
                { duration: '2m', target: 20 },
                { duration: '20s', target: 0 },
            ],
            gracefulRampDown: '10s',
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<2000', 'p(99)<5000'],
        error_rate: ['rate<0.01'],
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost';
const BATCH_SIZES = [10, 25, 50, 100];
const SERVICES = ['user-service', 'payment-service', 'order-service', 'api-gateway', 'auth-service'];
const SEVERITIES = ['INFO', 'WARN', 'ERROR'];

function generateLogPayload(traceId) {
    const service = SERVICES[Math.floor(Math.random() * SERVICES.length)];
    const severity = SEVERITIES[Math.floor(Math.random() * SEVERITIES.length)];

    return {
        serviceName: service,
        severity: severity,
        message: `Batch test log - ${randomString(8)}`,
        timestamp: new Date().toISOString(),
        traceId: traceId,
        host: `server-${Math.floor(Math.random() * 10) + 1}`,
        metadata: {
            requestId: randomString(12),
            batchTest: true,
        },
    };
}

function generateBatch() {
    const batchSize = BATCH_SIZES[Math.floor(Math.random() * BATCH_SIZES.length)];
    const traceId = `trace-batch-${randomString(8)}-${Date.now()}`;
    const logs = [];

    for (let i = 0; i < batchSize; i++) {
        logs.push(generateLogPayload(traceId));
    }

    return logs;
}

export default function () {
    const batch = generateBatch();
    const params = {
        headers: { 'Content-Type': 'application/json' },
    };

    const response = http.post(`${BASE_URL}/api/v1/logs/batch`, JSON.stringify(batch), params);

    batchesSent.add(1);
    totalLogsSent.add(batch.length);

    const success = check(response, {
        'status is 200': (r) => r.status === 200,
    });

    if (success) {
        batchesAccepted.add(1);
        errorRate.add(0);
    } else {
        errorRate.add(1);
    }

    sleep(Math.random() * 0.5 + 0.1);
}

export function handleSummary(data) {
    return {
        stdout: `
================================================================================
                        BATCH LOAD TEST RESULTS
================================================================================
Test Duration: ${(data.state.testRunDurationMs / 1000).toFixed(2)}s

THROUGHPUT
  Batch Requests:     ${data.metrics.http_reqs.values.count}
  Batches/sec:        ${data.metrics.http_reqs.values.rate.toFixed(2)}
  Total Logs Sent:    ${data.metrics.total_logs_sent ? data.metrics.total_logs_sent.values.count : 'N/A'}

LATENCY (ms)
  Average:            ${data.metrics.http_req_duration.values.avg.toFixed(2)}
  p50:                ${data.metrics.http_req_duration.values['p(50)'].toFixed(2)}
  p95:                ${data.metrics.http_req_duration.values['p(95)'].toFixed(2)}
  p99:                ${data.metrics.http_req_duration.values['p(99)'].toFixed(2)}

ERROR RATE:           ${(data.metrics.http_req_failed.values.rate * 100).toFixed(2)}%
================================================================================
`,
    };
}
