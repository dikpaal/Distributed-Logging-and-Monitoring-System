import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { randomString } from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';

// Custom metrics
const logsSent = new Counter('logs_sent');
const logsAccepted = new Counter('logs_accepted');
const logsFailed = new Counter('logs_failed');
const errorRate = new Rate('error_rate');
const ingestionLatency = new Trend('ingestion_latency', true);

// Test configuration
export const options = {
    scenarios: {
        // Ramp-up test
        ramp_up: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 10 },   // Ramp up to 10 users
                { duration: '1m', target: 10 },    // Stay at 10 users
                { duration: '30s', target: 50 },   // Ramp up to 50 users
                { duration: '2m', target: 50 },    // Stay at 50 users
                { duration: '30s', target: 100 },  // Ramp up to 100 users
                { duration: '1m', target: 100 },   // Stay at 100 users
                { duration: '30s', target: 0 },    // Ramp down
            ],
            gracefulRampDown: '10s',
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<500', 'p(99)<1000'], // 95% < 500ms, 99% < 1s
        error_rate: ['rate<0.01'],                       // Error rate < 1%
        logs_accepted: ['count>1000'],                   // At least 1000 logs accepted
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost';
const SERVICES = ['user-service', 'payment-service', 'order-service', 'api-gateway', 'auth-service'];
const SEVERITIES = ['INFO', 'WARN', 'ERROR'];
const SEVERITY_WEIGHTS = [70, 20, 10]; // Weighted distribution

function getWeightedSeverity() {
    const rand = Math.random() * 100;
    let cumulative = 0;
    for (let i = 0; i < SEVERITIES.length; i++) {
        cumulative += SEVERITY_WEIGHTS[i];
        if (rand < cumulative) {
            return SEVERITIES[i];
        }
    }
    return 'INFO';
}

function generateLogPayload() {
    const service = SERVICES[Math.floor(Math.random() * SERVICES.length)];
    const severity = getWeightedSeverity();
    const traceId = `trace-${randomString(8)}-${Date.now()}`;

    const messages = {
        'INFO': [
            'Request processed successfully',
            'User authenticated',
            'Database query completed',
            'Cache hit for key',
            'Service health check passed',
        ],
        'WARN': [
            'High latency detected',
            'Cache miss, falling back to database',
            'Retry attempt initiated',
            'Connection pool running low',
        ],
        'ERROR': [
            'Failed to connect to database',
            'Authentication failed',
            'Request timeout exceeded',
            'Service unavailable',
        ],
    };

    const messageList = messages[severity];
    const message = messageList[Math.floor(Math.random() * messageList.length)];

    return {
        serviceName: service,
        severity: severity,
        message: message,
        timestamp: new Date().toISOString(),
        traceId: traceId,
        host: `server-${Math.floor(Math.random() * 10) + 1}`,
        metadata: {
            requestId: randomString(12),
            userId: `user-${Math.floor(Math.random() * 10000)}`,
            endpoint: `/api/v1/${randomString(6)}`,
            durationMs: Math.floor(Math.random() * 500),
        },
    };
}

export default function () {
    const payload = generateLogPayload();
    const params = {
        headers: {
            'Content-Type': 'application/json',
        },
    };

    const startTime = Date.now();
    const response = http.post(`${BASE_URL}/api/v1/logs`, JSON.stringify(payload), params);
    const duration = Date.now() - startTime;

    logsSent.add(1);
    ingestionLatency.add(duration);

    const success = check(response, {
        'status is 200': (r) => r.status === 200,
        'response has status': (r) => {
            try {
                const body = JSON.parse(r.body);
                return body.status !== undefined;
            } catch {
                return false;
            }
        },
    });

    if (success) {
        logsAccepted.add(1);
        errorRate.add(0);
    } else {
        logsFailed.add(1);
        errorRate.add(1);
        console.log(`Failed request: ${response.status} - ${response.body}`);
    }

    // Small sleep to prevent overwhelming the system
    sleep(Math.random() * 0.1);
}

export function handleSummary(data) {
    const summary = {
        timestamp: new Date().toISOString(),
        test: 'ingestion-load-test',
        duration: data.state.testRunDurationMs,
        metrics: {
            totalRequests: data.metrics.http_reqs.values.count,
            requestsPerSecond: data.metrics.http_reqs.values.rate,
            avgLatency: data.metrics.http_req_duration.values.avg,
            p50Latency: data.metrics.http_req_duration.values['p(50)'],
            p95Latency: data.metrics.http_req_duration.values['p(95)'],
            p99Latency: data.metrics.http_req_duration.values['p(99)'],
            maxLatency: data.metrics.http_req_duration.values.max,
            logsSent: data.metrics.logs_sent ? data.metrics.logs_sent.values.count : 0,
            logsAccepted: data.metrics.logs_accepted ? data.metrics.logs_accepted.values.count : 0,
            logsFailed: data.metrics.logs_failed ? data.metrics.logs_failed.values.count : 0,
            errorRate: data.metrics.error_rate ? data.metrics.error_rate.values.rate : 0,
        },
    };

    return {
        'results/ingestion-load-test-results.json': JSON.stringify(summary, null, 2),
        stdout: textSummary(data, { indent: ' ', enableColors: true }),
    };
}

function textSummary(data, opts) {
    const metrics = data.metrics;
    return `
================================================================================
                        INGESTION LOAD TEST RESULTS
================================================================================

Test Duration: ${(data.state.testRunDurationMs / 1000).toFixed(2)}s

THROUGHPUT
  Total Requests:     ${metrics.http_reqs.values.count}
  Requests/sec:       ${metrics.http_reqs.values.rate.toFixed(2)}
  Logs Sent:          ${metrics.logs_sent ? metrics.logs_sent.values.count : 'N/A'}
  Logs Accepted:      ${metrics.logs_accepted ? metrics.logs_accepted.values.count : 'N/A'}
  Logs Failed:        ${metrics.logs_failed ? metrics.logs_failed.values.count : 'N/A'}

LATENCY (ms)
  Average:            ${metrics.http_req_duration.values.avg.toFixed(2)}
  Median (p50):       ${metrics.http_req_duration.values['p(50)'].toFixed(2)}
  p95:                ${metrics.http_req_duration.values['p(95)'].toFixed(2)}
  p99:                ${metrics.http_req_duration.values['p(99)'].toFixed(2)}
  Max:                ${metrics.http_req_duration.values.max.toFixed(2)}

ERROR RATE
  Failed Requests:    ${metrics.http_req_failed.values.rate.toFixed(4)} (${(metrics.http_req_failed.values.rate * 100).toFixed(2)}%)

================================================================================
`;
}
