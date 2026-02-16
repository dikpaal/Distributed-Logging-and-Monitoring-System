import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Counter, Rate } from 'k6/metrics';

// Custom metrics
const queriesExecuted = new Counter('queries_executed');
const errorRate = new Rate('error_rate');

// Test configuration - read-heavy workload
export const options = {
    scenarios: {
        monitoring_queries: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '15s', target: 10 },
                { duration: '1m', target: 10 },
                { duration: '15s', target: 30 },
                { duration: '2m', target: 30 },
                { duration: '15s', target: 0 },
            ],
            gracefulRampDown: '10s',
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<200', 'p(99)<500'],
        error_rate: ['rate<0.05'],
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8083';
const SERVICES = ['user-service', 'payment-service', 'order-service'];
const SEVERITIES = ['INFO', 'WARN', 'ERROR'];

export default function () {
    // Mix of different query patterns
    const queryType = Math.random();

    if (queryType < 0.3) {
        // 30% - List logs with pagination
        group('list_logs', function () {
            const page = Math.floor(Math.random() * 5);
            const size = [10, 20, 50][Math.floor(Math.random() * 3)];
            const response = http.get(`${BASE_URL}/api/v1/logs?page=${page}&size=${size}`);

            queriesExecuted.add(1);
            const success = check(response, {
                'list logs status 200': (r) => r.status === 200,
                'has content': (r) => r.body.length > 0,
            });
            errorRate.add(success ? 0 : 1);
        });
    } else if (queryType < 0.5) {
        // 20% - Filter by service
        group('filter_by_service', function () {
            const service = SERVICES[Math.floor(Math.random() * SERVICES.length)];
            const response = http.get(`${BASE_URL}/api/v1/logs?serviceName=${service}&size=20`);

            queriesExecuted.add(1);
            const success = check(response, {
                'filter by service status 200': (r) => r.status === 200,
            });
            errorRate.add(success ? 0 : 1);
        });
    } else if (queryType < 0.7) {
        // 20% - Filter by severity
        group('filter_by_severity', function () {
            const severity = SEVERITIES[Math.floor(Math.random() * SEVERITIES.length)];
            const response = http.get(`${BASE_URL}/api/v1/logs?severity=${severity}&size=20`);

            queriesExecuted.add(1);
            const success = check(response, {
                'filter by severity status 200': (r) => r.status === 200,
            });
            errorRate.add(success ? 0 : 1);
        });
    } else if (queryType < 0.85) {
        // 15% - Get metrics counts
        group('metrics_counts', function () {
            const response = http.get(`${BASE_URL}/api/v1/metrics/counts`);

            queriesExecuted.add(1);
            const success = check(response, {
                'metrics counts status 200': (r) => r.status === 200,
            });
            errorRate.add(success ? 0 : 1);
        });
    } else {
        // 15% - Get service metrics
        group('metrics_services', function () {
            const response = http.get(`${BASE_URL}/api/v1/metrics/services`);

            queriesExecuted.add(1);
            const success = check(response, {
                'metrics services status 200': (r) => r.status === 200,
            });
            errorRate.add(success ? 0 : 1);
        });
    }

    sleep(Math.random() * 0.2 + 0.05);
}

export function handleSummary(data) {
    return {
        stdout: `
================================================================================
                     MONITORING API LOAD TEST RESULTS
================================================================================
Test Duration: ${(data.state.testRunDurationMs / 1000).toFixed(2)}s

THROUGHPUT
  Total Queries:      ${data.metrics.http_reqs.values.count}
  Queries/sec:        ${data.metrics.http_reqs.values.rate.toFixed(2)}

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
