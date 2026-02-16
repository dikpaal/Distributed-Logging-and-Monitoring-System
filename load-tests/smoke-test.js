import http from 'k6/http';
import { check, sleep } from 'k6';
import { randomString } from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';

// Quick smoke test to verify system is working before full load test
export const options = {
    vus: 1,
    iterations: 10,
    thresholds: {
        http_req_failed: ['rate<0.1'],
        http_req_duration: ['p(95)<1000'],
    },
};

const INGESTION_URL = __ENV.INGESTION_URL || 'http://localhost';
const MONITORING_URL = __ENV.MONITORING_URL || 'http://localhost:8083';

export default function () {
    // Test 1: Health check (NGINX)
    let response = http.get(`${INGESTION_URL}/health`);
    check(response, {
        'nginx health ok': (r) => r.status === 200 || r.status === 502,
    });

    // Test 2: Single log ingestion
    const payload = {
        serviceName: 'smoke-test',
        severity: 'INFO',
        message: `Smoke test log - ${randomString(8)}`,
        timestamp: new Date().toISOString(),
        traceId: `smoke-${randomString(8)}`,
        host: 'smoke-test-host',
    };

    response = http.post(
        `${INGESTION_URL}/api/v1/logs`,
        JSON.stringify(payload),
        { headers: { 'Content-Type': 'application/json' } }
    );
    check(response, {
        'ingestion accepts log': (r) => r.status === 200,
    });

    // Test 3: Batch ingestion
    const batch = [payload, { ...payload, message: 'Batch log 2' }];
    response = http.post(
        `${INGESTION_URL}/api/v1/logs/batch`,
        JSON.stringify(batch),
        { headers: { 'Content-Type': 'application/json' } }
    );
    check(response, {
        'batch ingestion works': (r) => r.status === 200,
    });

    // Test 4: Monitoring API
    response = http.get(`${MONITORING_URL}/api/v1/logs?size=5`);
    check(response, {
        'monitoring api responds': (r) => r.status === 200,
    });

    // Test 5: Metrics endpoint
    response = http.get(`${MONITORING_URL}/api/v1/metrics/counts`);
    check(response, {
        'metrics endpoint works': (r) => r.status === 200,
    });

    sleep(0.5);
}

export function handleSummary(data) {
    const passed = data.metrics.checks.values.passes;
    const failed = data.metrics.checks.values.fails;
    const total = passed + failed;

    return {
        stdout: `
================================================================================
                           SMOKE TEST RESULTS
================================================================================
Checks Passed: ${passed}/${total}
Checks Failed: ${failed}/${total}
Status: ${failed === 0 ? '✓ ALL PASSED' : '✗ SOME FAILED'}
================================================================================
`,
    };
}
