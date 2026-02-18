#!/bin/bash

# Start/Restart the Distributed Logging & Monitoring System
# Usage: ./start.sh [options]
#
# Options:
#   --skip-build    Skip Gradle build
#   --infra-only    Only start infrastructure (Docker containers)
#   --stop          Stop all services and exit

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Options
SKIP_BUILD=false
INFRA_ONLY=false
STOP_ONLY=false

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --skip-build)
            SKIP_BUILD=true
            shift
            ;;
        --infra-only)
            INFRA_ONLY=true
            shift
            ;;
        --stop)
            STOP_ONLY=true
            shift
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            exit 1
            ;;
    esac
done

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  Distributed Logging & Monitoring System${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Function to stop all services
stop_all() {
    echo -e "${YELLOW}Stopping all services...${NC}"

    # Stop Docker containers
    echo "  Stopping Docker containers..."
    docker-compose down 2>/dev/null || true

    # Kill Gradle bootRun processes
    echo "  Stopping Gradle processes..."
    pkill -f "gradle.*bootRun" 2>/dev/null || true
    pkill -f "GradleDaemon" 2>/dev/null || true

    # Kill Java Spring Boot processes (our services run on specific ports)
    echo "  Stopping Spring Boot services..."
    for port in 8080 8081 8083 9001 9002 9003; do
        pid=$(lsof -ti:$port 2>/dev/null) || true
        if [ -n "$pid" ]; then
            kill -9 $pid 2>/dev/null || true
        fi
    done

    # Kill npm/vite dev server
    echo "  Stopping dashboard dev server..."
    pkill -f "vite" 2>/dev/null || true
    pkill -f "node.*dashboard" 2>/dev/null || true

    echo -e "${GREEN}All services stopped.${NC}"
    echo ""
}

# Function to wait for a port to be available
wait_for_port() {
    local port=$1
    local name=$2
    local max_attempts=60
    local attempt=0

    while ! nc -z localhost $port 2>/dev/null; do
        attempt=$((attempt + 1))
        if [ $attempt -ge $max_attempts ]; then
            echo -e "${RED}Timeout waiting for $name on port $port${NC}"
            return 1
        fi
        sleep 1
    done
    echo -e "${GREEN}  $name is ready on port $port${NC}"
}

# Stop everything first
stop_all

if [ "$STOP_ONLY" = true ]; then
    exit 0
fi

# Set Java 21 - MUST be set before any Gradle commands
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"

# Verify Java version
JAVA_VERSION=$("$JAVA_HOME/bin/java" -version 2>&1 | head -1)
echo -e "${BLUE}Using JAVA_HOME: $JAVA_HOME${NC}"
echo -e "${BLUE}Java version: $JAVA_VERSION${NC}"
echo ""

# Stop any Gradle daemons that might be using wrong Java version
echo -e "${YELLOW}Stopping Gradle daemons...${NC}"
./gradlew --stop 2>/dev/null || true
echo ""

# Build if not skipped
if [ "$SKIP_BUILD" = false ]; then
    echo -e "${YELLOW}Building project...${NC}"
    ./gradlew build -x test --quiet
    echo -e "${GREEN}Build complete.${NC}"
    echo ""
fi

# Start Docker infrastructure
echo -e "${YELLOW}Starting infrastructure (Docker)...${NC}"
docker-compose up -d

# Wait for infrastructure to be ready
echo "Waiting for infrastructure..."
sleep 5
wait_for_port 9092 "Kafka"
wait_for_port 5434 "PostgreSQL"
wait_for_port 6379 "Redis"
wait_for_port 80 "NGINX"
echo ""

if [ "$INFRA_ONLY" = true ]; then
    echo -e "${GREEN}Infrastructure is ready.${NC}"
    exit 0
fi

# Start backend services in background
echo -e "${YELLOW}Starting backend services...${NC}"

# Create logs directory
mkdir -p logs

# Ingestion service instance 1
echo "  Starting ingestion-service (port 8080)..."
./gradlew :ingestion-service:bootRun --args='--server.port=8080' > logs/ingestion-1.log 2>&1 &

# Ingestion service instance 2
echo "  Starting ingestion-service (port 8081)..."
./gradlew :ingestion-service:bootRun --args='--server.port=8081' > logs/ingestion-2.log 2>&1 &

# Monitoring service (consumes from Kafka, persists, provides query APIs + WebSocket)
echo "  Starting monitoring-service (port 8083)..."
./gradlew :monitoring-service:bootRun > logs/monitoring.log 2>&1 &

# Log generator services
echo "  Starting order-service (port 9003)..."
./gradlew :order-service:bootRun > logs/order.log 2>&1 &

echo "  Starting payment-service (port 9002)..."
./gradlew :payment-service:bootRun > logs/payment.log 2>&1 &

echo "  Starting user-service (port 9001)..."
./gradlew :user-service:bootRun > logs/user.log 2>&1 &

# Wait for services to start
echo ""
echo -e "${YELLOW}Waiting for services to start (this may take 30-60 seconds)...${NC}"
sleep 30

wait_for_port 8080 "ingestion-service-1"
wait_for_port 8081 "ingestion-service-2"
wait_for_port 8083 "monitoring-service"
wait_for_port 9003 "order-service"
wait_for_port 9002 "payment-service"
wait_for_port 9001 "user-service"
echo ""

# Start dashboard
echo -e "${YELLOW}Starting dashboard...${NC}"
cd dashboard
npm install --silent 2>/dev/null
npm run dev > ../logs/dashboard.log 2>&1 &
cd ..

sleep 3
wait_for_port 5173 "dashboard"
echo ""

# Summary
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  All services are running!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo "Services:"
echo "  - Dashboard:          http://localhost:5173"
echo "  - NGINX (LB):         http://localhost:80"
echo "  - Ingestion API:      http://localhost:8080, http://localhost:8081"
echo "  - Monitoring API:     http://localhost:8083"
echo ""
echo "Infrastructure:"
echo "  - Kafka:              localhost:9092"
echo "  - PostgreSQL:         localhost:5434"
echo "  - Redis:              localhost:6379"
echo ""
echo "Logs are in: ./logs/"
echo ""
echo -e "${YELLOW}To stop all services: ./start.sh --stop${NC}"
