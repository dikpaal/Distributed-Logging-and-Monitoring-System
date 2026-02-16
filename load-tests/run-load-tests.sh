#!/bin/bash

# Load Testing Runner Script
# Usage: ./run-load-tests.sh [test-name] [options]
#
# Tests:
#   smoke       - Quick smoke test (10 iterations)
#   ingestion   - Single log ingestion load test (~6 min)
#   batch       - Batch log ingestion load test (~4 min)
#   monitoring  - Monitoring API load test (~4 min)
#   all         - Run all load tests sequentially
#
# Options:
#   --base-url URL  - Override base URL (default: http://localhost)
#   --quick         - Run shorter versions of tests

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Default values
BASE_URL="${BASE_URL:-http://localhost}"
MONITORING_URL="${MONITORING_URL:-http://localhost:8083}"
QUICK_MODE=false

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Parse arguments
TEST_NAME="${1:-smoke}"
shift || true

while [[ $# -gt 0 ]]; do
    case $1 in
        --base-url)
            BASE_URL="$2"
            shift 2
            ;;
        --monitoring-url)
            MONITORING_URL="$2"
            shift 2
            ;;
        --quick)
            QUICK_MODE=true
            shift
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

# Create results directory
mkdir -p results

echo -e "${YELLOW}========================================${NC}"
echo -e "${YELLOW}  Distributed Logging System Load Tests${NC}"
echo -e "${YELLOW}========================================${NC}"
echo ""
echo "Base URL:       $BASE_URL"
echo "Monitoring URL: $MONITORING_URL"
echo "Quick Mode:     $QUICK_MODE"
echo ""

run_test() {
    local test_file=$1
    local test_name=$2

    echo -e "${GREEN}Running: $test_name${NC}"
    echo "----------------------------------------"

    if [ "$QUICK_MODE" = true ]; then
        k6 run \
            -e BASE_URL="$BASE_URL" \
            -e MONITORING_URL="$MONITORING_URL" \
            -e INGESTION_URL="$BASE_URL" \
            --duration 30s \
            --vus 10 \
            "$test_file"
    else
        k6 run \
            -e BASE_URL="$BASE_URL" \
            -e MONITORING_URL="$MONITORING_URL" \
            -e INGESTION_URL="$BASE_URL" \
            "$test_file"
    fi

    echo ""
}

case $TEST_NAME in
    smoke)
        run_test "smoke-test.js" "Smoke Test"
        ;;
    ingestion)
        run_test "ingestion-load-test.js" "Ingestion Load Test"
        ;;
    batch)
        run_test "batch-load-test.js" "Batch Load Test"
        ;;
    monitoring)
        run_test "monitoring-load-test.js" "Monitoring API Load Test"
        ;;
    all)
        echo -e "${YELLOW}Running all load tests...${NC}"
        echo ""
        run_test "smoke-test.js" "Smoke Test"
        run_test "ingestion-load-test.js" "Ingestion Load Test"
        run_test "batch-load-test.js" "Batch Load Test"
        run_test "monitoring-load-test.js" "Monitoring API Load Test"
        echo -e "${GREEN}All tests completed!${NC}"
        ;;
    *)
        echo -e "${RED}Unknown test: $TEST_NAME${NC}"
        echo ""
        echo "Available tests:"
        echo "  smoke       - Quick smoke test"
        echo "  ingestion   - Single log ingestion load test"
        echo "  batch       - Batch log ingestion load test"
        echo "  monitoring  - Monitoring API load test"
        echo "  all         - Run all load tests"
        exit 1
        ;;
esac
