#!/bin/bash

# Run tests for a specific service
# Usage: ./tests/run-service-tests.sh <service-name>
# Example: ./tests/run-service-tests.sh ingestion-service

set -e

if [ -z "$1" ]; then
    echo "Usage: $0 <service-name>"
    echo ""
    echo "Available services:"
    echo "  - ingestion-service"
    echo "  - processing-service"
    echo "  - monitoring-service"
    echo "  - user-service"
    echo "  - payment-service"
    echo "  - order-service"
    exit 1
fi

SERVICE="$1"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_ROOT"

export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}"

echo "Running tests for $SERVICE..."
echo "=============================="

./gradlew ":$SERVICE:test"

echo ""
echo "Tests passed for $SERVICE!"
