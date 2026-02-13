#!/bin/bash

# Run all tests across all service modules
# Usage: ./tests/run-all-tests.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_ROOT"

export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}"

echo "Running all tests..."
echo "===================="

./gradlew test

echo ""
echo "All tests passed!"
