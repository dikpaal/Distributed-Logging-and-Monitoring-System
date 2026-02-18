#!/bin/bash

# Load Testing Runner Script
# Usage: ./run-load-tests.sh [test-name] [options]
#
# Tests:
#   smoke        - Quick smoke test (verify system is working)
#   architecture - Full system test via NGINX (~2.5 min)
#   all          - Run smoke + architecture tests
#
# Options:
#   --quick      - Run shorter versions (30s)
#   --direct     - Bypass NGINX, hit ingestion directly (port 8080)
#   --debug      - Enable debug logging
#
# Examples:
#   ./run-load-tests.sh smoke                     # Verify system is up
#   ./run-load-tests.sh architecture              # Full system test
#   ./run-load-tests.sh architecture --quick      # Quick 30s test
#   ./run-load-tests.sh architecture --direct     # Bypass NGINX

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Default values
BASE_URL="${BASE_URL:-http://localhost}"
MONITORING_URL="${MONITORING_URL:-http://localhost:8083}"
QUICK_MODE=false
DEBUG_MODE=false
DIRECT_MODE=false

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

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
        --debug)
            DEBUG_MODE=true
            shift
            ;;
        --direct)
            DIRECT_MODE=true
            BASE_URL="http://localhost:8080"
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

# Generate test run ID
TEST_RUN_ID="run-$(date +%Y%m%d-%H%M%S)"

echo -e "${YELLOW}========================================${NC}"
echo -e "${YELLOW}  Distributed Logging System Load Tests${NC}"
echo -e "${YELLOW}========================================${NC}"
echo ""
echo -e "Test:           ${CYAN}$TEST_NAME${NC}"
echo -e "Base URL:       ${CYAN}$BASE_URL${NC}"
echo -e "Monitoring URL: ${CYAN}$MONITORING_URL${NC}"
echo -e "Quick Mode:     ${CYAN}$QUICK_MODE${NC}"
echo -e "Direct Mode:    ${CYAN}$DIRECT_MODE${NC}"
echo -e "Run ID:         ${CYAN}$TEST_RUN_ID${NC}"
echo ""

if [ "$DIRECT_MODE" = true ]; then
    echo -e "${YELLOW}⚠ Direct mode: Bypassing NGINX, no rate limiting${NC}"
    echo ""
fi

run_smoke_test() {
    echo -e "${GREEN}▶ Running: Smoke Test${NC}"
    echo "────────────────────────────────────────"

    k6 run \
        -e "BASE_URL=$BASE_URL" \
        -e "MONITORING_URL=$MONITORING_URL" \
        -e "INGESTION_URL=$BASE_URL" \
        "smoke-test.js"

    echo ""
}

run_architecture_test() {
    echo -e "${GREEN}▶ Running: Architecture Load Test${NC}"
    echo "────────────────────────────────────────"

    local k6_args=(
        -e "BASE_URL=$BASE_URL"
        -e "MONITORING_URL=$MONITORING_URL"
        -e "INGESTION_URL=$BASE_URL"
        -e "TEST_RUN_ID=$TEST_RUN_ID"
    )

    if [ "$DEBUG_MODE" = true ]; then
        k6_args+=(-e "DEBUG=true")
    fi

    if [ "$QUICK_MODE" = true ]; then
        k6_args+=(-e "QUICK_MODE=true")
    fi

    k6 run "${k6_args[@]}" "architecture-load-test.js"

    echo ""
}

case $TEST_NAME in
    smoke)
        run_smoke_test
        ;;
    architecture|arch)
        run_architecture_test
        ;;
    all)
        echo -e "${YELLOW}Running full test suite...${NC}"
        echo ""
        run_smoke_test
        run_architecture_test
        echo -e "${GREEN}✓ All tests completed!${NC}"
        ;;
    *)
        echo -e "${RED}Unknown test: $TEST_NAME${NC}"
        echo ""
        echo "Available tests:"
        echo "  smoke        - Quick smoke test (verify system is working)"
        echo "  architecture - Full system test via NGINX (~2.5 min)"
        echo "  all          - Run smoke + architecture tests"
        echo ""
        echo "Options:"
        echo "  --quick      - Run shorter versions (30s)"
        echo "  --direct     - Bypass NGINX (no rate limiting)"
        echo "  --debug      - Enable debug logging"
        exit 1
        ;;
esac
