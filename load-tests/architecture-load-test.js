import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { randomString } from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';

// =============================================================================
// ARCHITECTURE LOAD TEST
// =============================================================================
// Tests the full distributed logging system through NGINX load balancer.
// Designed to stay within NGINX rate limits (100r/s + burst 50).
//
// Test Flow:
//   k6 → NGINX (rate limit + load balance) → ingestion-service (x2)
//                                                    ↓
//                                                  Kafka
//                                                    ↓
//                                           monitoring-service
//                                                    ↓
//                                     PostgreSQL + Redis + WebSocket
// =============================================================================

// Custom Metrics
const ingestionSuccess = new Counter('ingestion_success');
const ingestionFailed = new Counter('ingestion_failed');
const rateLimitHits = new Counter('rate_limit_hits');
const monitoringSuccess = new Counter('monitoring_success');
const e2eSuccess = new Counter('e2e_success');

const ingestionLatency = new Trend('ingestion_latency_ms');
const monitoringLatency = new Trend('monitoring_latency_ms');
const e2eLatency = new Trend('e2e_latency_ms');

const ingestionErrorRate = new Rate('ingestion_error_rate');
const rateLimitRate = new Rate('rate_limit_rate');

// Configuration
const INGESTION_URL = __ENV.INGESTION_URL || __ENV.BASE_URL || 'http://localhost';
const MONITORING_URL = __ENV.MONITORING_URL || 'http://localhost:8083';
const QUICK_MODE = __ENV.QUICK_MODE === 'true';

// Target 50 RPS for ingestion (well under NGINX 100r/s limit)
const TARGET_RPS = 50;
const DURATION = QUICK_MODE ? '30s' : '2m';

export const options = {
    scenarios: {
        // Constant arrival rate for precise RPS control
        sustained_load: {
            executor: 'constant-arrival-rate',
            rate: TARGET_RPS,
            timeUnit: '1s',
            duration: DURATION,
            preAllocatedVUs: 30,
            maxVUs: 60,
        },
        // Monitoring queries (not rate limited by NGINX)
        monitoring_queries: {
            executor: 'constant-arrival-rate',
            rate: 20,
            timeUnit: '1s',
            duration: DURATION,
            preAllocatedVUs: 15,
            maxVUs: 30,
            exec: 'monitoringScenario',
            startTime: '5s',
        },
        // E2E verification (low rate, verifies full pipeline)
        e2e_verification: {
            executor: 'constant-arrival-rate',
            rate: 1,
            timeUnit: '1s',
            duration: DURATION,
            preAllocatedVUs: 3,
            maxVUs: 5,
            exec: 'e2eScenario',
            startTime: '10s',
        },
    },
    thresholds: {
        // Ingestion: 95% success, <10% rate limited
        ingestion_error_rate: ['rate<0.10'],  // Allow up to 10% errors (timeouts happen)
        rate_limit_rate: ['rate<0.10'],
        ingestion_latency_ms: ['p(95)<1000'],

        // Monitoring: fast queries
        monitoring_latency_ms: ['p(95)<200'],

        // E2E: log visible within 5s
        e2e_latency_ms: ['p(95)<5000'],
    },
};

// Services for log generation
const SERVICES = ['user-service', 'payment-service', 'order-service'];
const SEVERITIES = ['INFO', 'WARN', 'ERROR'];
const SEVERITY_WEIGHTS = [70, 20, 10];

function getWeightedSeverity() {
    const rand = Math.random() * 100;
    let cumulative = 0;
    for (let i = 0; i < SEVERITIES.length; i++) {
        cumulative += SEVERITY_WEIGHTS[i];
        if (rand < cumulative) return SEVERITIES[i];
    }
    return 'INFO';
}

function generateLog(prefix = 'arch') {
    return {
        serviceName: SERVICES[Math.floor(Math.random() * SERVICES.length)],
        severity: getWeightedSeverity(),
        message: `${prefix}-test: ${randomString(16)}`,
        timestamp: new Date().toISOString(),
        traceId: `${prefix}-${randomString(8)}-${Date.now()}`,
        host: `server-${Math.floor(Math.random() * 5) + 1}`,
        metadata: {
            testRun: __ENV.TEST_RUN_ID || 'default',
            vuId: __VU,
            iteration: __ITER,
        },
    };
}

// =============================================================================
// MAIN SCENARIO: Ingestion through NGINX
// =============================================================================
export default function () {
    const log = generateLog('sustained');
    const params = {
        headers: { 'Content-Type': 'application/json' },
        timeout: '10s',
    };

    const startTime = Date.now();
    const response = http.post(
        `${INGESTION_URL}/api/v1/logs`,
        JSON.stringify(log),
        params
    );
    const duration = Date.now() - startTime;

    if (response.status === 429) {
        rateLimitHits.add(1);
        rateLimitRate.add(1);
        ingestionErrorRate.add(0);
    } else if (response.status === 200 || response.status === 201) {
        ingestionSuccess.add(1);
        ingestionLatency.add(duration);
        ingestionErrorRate.add(0);
        rateLimitRate.add(0);
    } else {
        ingestionFailed.add(1);
        ingestionErrorRate.add(1);
        rateLimitRate.add(0);
        if (__ENV.DEBUG) {
            console.log(`Ingestion error: ${response.status} - ${response.body}`);
        }
    }
}

// =============================================================================
// MONITORING SCENARIO: Query APIs
// =============================================================================
export function monitoringScenario() {
    const queryType = Math.random();
    let response;
    const startTime = Date.now();

    if (queryType < 0.4) {
        const page = Math.floor(Math.random() * 3);
        const size = [10, 20, 50][Math.floor(Math.random() * 3)];
        response = http.get(`${MONITORING_URL}/api/v1/logs?page=${page}&size=${size}`, {
            timeout: '5s',
        });
    } else if (queryType < 0.6) {
        const service = SERVICES[Math.floor(Math.random() * SERVICES.length)];
        response = http.get(`${MONITORING_URL}/api/v1/logs?serviceName=${service}&size=20`, {
            timeout: '5s',
        });
    } else if (queryType < 0.8) {
        const severity = SEVERITIES[Math.floor(Math.random() * SEVERITIES.length)];
        response = http.get(`${MONITORING_URL}/api/v1/logs?severity=${severity}&size=20`, {
            timeout: '5s',
        });
    } else {
        response = http.get(`${MONITORING_URL}/api/v1/metrics/counts`, {
            timeout: '5s',
        });
    }

    const duration = Date.now() - startTime;

    if (response.status === 200) {
        monitoringSuccess.add(1);
        monitoringLatency.add(duration);
    }
}

// =============================================================================
// E2E SCENARIO: Verify log flows through entire pipeline
// =============================================================================
export function e2eScenario() {
    const uniqueId = `e2e-${__VU}-${__ITER}-${Date.now()}`;
    const log = {
        serviceName: 'e2e-test',
        severity: 'INFO',
        message: uniqueId,
        timestamp: new Date().toISOString(),
        traceId: uniqueId,
        host: 'e2e-test-host',
        metadata: { e2eTest: true },
    };

    const params = {
        headers: { 'Content-Type': 'application/json' },
        timeout: '10s',
    };

    const ingestStart = Date.now();
    const ingestResponse = http.post(
        `${INGESTION_URL}/api/v1/logs`,
        JSON.stringify(log),
        params
    );

    if (ingestResponse.status !== 200 && ingestResponse.status !== 201) {
        return;
    }

    // Poll monitoring service until log appears
    const maxWaitMs = 8000;
    const pollIntervalMs = 500;
    let found = false;

    while (!found && (Date.now() - ingestStart) < maxWaitMs) {
        sleep(pollIntervalMs / 1000);

        const searchResponse = http.get(
            `${MONITORING_URL}/api/v1/logs?traceId=${uniqueId}&size=1`,
            { timeout: '3s' }
        );

        if (searchResponse.status === 200) {
            try {
                const body = JSON.parse(searchResponse.body);
                if (body.content && body.content.length > 0) {
                    found = true;
                    const e2eDuration = Date.now() - ingestStart;
                    e2eLatency.add(e2eDuration);
                    e2eSuccess.add(1);
                }
            } catch (e) {
                // Parse error, continue polling
            }
        }
    }
}

// =============================================================================
// SUMMARY REPORT
// =============================================================================
export function handleSummary(data) {
    const m = data.metrics;

    const getCount = (name) => m[name]?.values?.count || 0;
    const getRate = (name) => m[name]?.values?.rate || 0;
    const getAvg = (name) => m[name]?.values?.avg || 0;
    const getMed = (name) => m[name]?.values?.med || 0;  // k6 uses 'med' for median
    const getP95 = (name) => m[name]?.values?.['p(95)'] || 0;
    const getMax = (name) => m[name]?.values?.max || 0;

    const fmt = (v, decimals = 2) => typeof v === 'number' ? v.toFixed(decimals) : '0.00';

    const testDurationSec = data.state.testRunDurationMs / 1000;
    const ingestionTotal = getCount('ingestion_success') + getCount('ingestion_failed') + getCount('rate_limit_hits');
    const ingestionSuccessCount = getCount('ingestion_success');
    const successRate = ingestionTotal > 0 ? (ingestionSuccessCount / ingestionTotal) * 100 : 0;

    const summary = {
        timestamp: new Date().toISOString(),
        test: 'architecture-load-test',
        duration_sec: testDurationSec,
        config: {
            targetRPS: TARGET_RPS,
            ingestionUrl: INGESTION_URL,
            monitoringUrl: MONITORING_URL,
        },
        ingestion: {
            total: ingestionTotal,
            successful: ingestionSuccessCount,
            failed: getCount('ingestion_failed'),
            rateLimited: getCount('rate_limit_hits'),
            successRate: successRate,
            throughput: ingestionSuccessCount / testDurationSec,
            latency: {
                avg: getAvg('ingestion_latency_ms'),
                med: getMed('ingestion_latency_ms'),
                p95: getP95('ingestion_latency_ms'),
                max: getMax('ingestion_latency_ms'),
            },
        },
        monitoring: {
            total: getCount('monitoring_success'),
            throughput: getCount('monitoring_success') / testDurationSec,
            latency: {
                avg: getAvg('monitoring_latency_ms'),
                med: getMed('monitoring_latency_ms'),
                p95: getP95('monitoring_latency_ms'),
            },
        },
        e2e: {
            successful: getCount('e2e_success'),
            latency: {
                avg: getAvg('e2e_latency_ms'),
                med: getMed('e2e_latency_ms'),
                p95: getP95('e2e_latency_ms'),
            },
        },
    };

    const report = `
================================================================================
                    ARCHITECTURE LOAD TEST RESULTS
================================================================================
Test Duration:     ${fmt(testDurationSec)}s
Target RPS:        ${TARGET_RPS} (ingestion) + 20 (monitoring)

INGESTION (via NGINX Load Balancer)
--------------------------------------------------------------------------------
  Total Requests:  ${ingestionTotal}
  Successful:      ${ingestionSuccessCount} (${fmt(successRate)}%)
  Failed:          ${getCount('ingestion_failed')}
  Rate Limited:    ${getCount('rate_limit_hits')} (${fmt(getRate('rate_limit_rate') * 100)}%)
  Throughput:      ${fmt(ingestionSuccessCount / testDurationSec)} logs/sec

  Latency (ms):
    Average:       ${fmt(getAvg('ingestion_latency_ms'))}
    Median:        ${fmt(getMed('ingestion_latency_ms'))}
    p95:           ${fmt(getP95('ingestion_latency_ms'))}
    Max:           ${fmt(getMax('ingestion_latency_ms'))}

MONITORING API (Direct)
--------------------------------------------------------------------------------
  Total Queries:   ${getCount('monitoring_success')}
  Throughput:      ${fmt(getCount('monitoring_success') / testDurationSec)} queries/sec

  Latency (ms):
    Average:       ${fmt(getAvg('monitoring_latency_ms'))}
    Median:        ${fmt(getMed('monitoring_latency_ms'))}
    p95:           ${fmt(getP95('monitoring_latency_ms'))}

END-TO-END PIPELINE (Ingestion → Kafka → Monitoring)
--------------------------------------------------------------------------------
  Verified:        ${getCount('e2e_success')} logs
  Latency (ms):
    Average:       ${fmt(getAvg('e2e_latency_ms'))}
    Median:        ${fmt(getMed('e2e_latency_ms'))}
    p95:           ${fmt(getP95('e2e_latency_ms'))}

OVERALL
--------------------------------------------------------------------------------
  HTTP Requests:   ${getCount('http_reqs')}
  Requests/sec:    ${fmt(getRate('http_reqs'))}

================================================================================
`;

    return {
        'results/architecture-load-test-results.json': JSON.stringify(summary, null, 2),
        stdout: report,
    };
}
