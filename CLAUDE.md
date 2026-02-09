# CLAUDE.md - Project Context

## Project Overview
A distributed logging and monitoring system built with Java 21 and Spring Boot. This project demonstrates senior-level backend engineering skills through proper microservice architecture, event-driven design, and production-ready patterns.

**Goal**: Build a scalable, event-driven log ingestion and monitoring pipeline.

## Architecture

```
[Client / App]
       |
       v
  Ingestion Service  --->  Kafka  --->  Processing Service  --->  PostgreSQL
                                     |
                                     v
                                  Redis (hot cache)

Monitoring Service  --->  Reads from DB + Redis
Alert Service       --->  Subscribes to processed events
```

### Services
1. **ingestion-service** - REST API for log intake, validates, batches, publishes to Kafka
2. **processing-service** - Consumes from Kafka, enriches, stores to PostgreSQL, caches in Redis
3. **monitoring-service** - Query APIs for logs, aggregations, metrics
4. **alert-service** - Subscribes to events, triggers alerts based on rules

### Shared Module
- **common** - Shared DTOs, utilities, constants

## Tech Stack
- **Language**: Java 21 (LTS)
- **Framework**: Spring Boot 3.x
- **Build**: Gradle (Kotlin DSL)
- **Message Broker**: Apache Kafka
- **Database**: PostgreSQL
- **Cache**: Redis
- **Testing**: JUnit 5, Testcontainers, MockMvc
- **Metrics**: Micrometer + Prometheus

## Project Structure
```
distributed-logging-system/
├── build.gradle.kts              # Root build file
├── settings.gradle.kts           # Module definitions
├── docker-compose.yml            # Kafka, PostgreSQL, Redis
├── common/                       # Shared library
│   └── src/main/java/
├── ingestion-service/
│   └── src/main/java/
├── processing-service/
│   └── src/main/java/
├── monitoring-service/
│   └── src/main/java/
├── alert-service/
│   └── src/main/java/
├── CLAUDE.md
├── PRD.md
└── SESSION_NOTES.md
```

## Build & Run Commands

```bash
# Build all modules
./gradlew build

# Run tests
./gradlew test

# Run specific service
./gradlew :ingestion-service:bootRun
./gradlew :processing-service:bootRun
./gradlew :monitoring-service:bootRun
./gradlew :alert-service:bootRun

# Start infrastructure (Kafka, PostgreSQL, Redis)
docker-compose up -d

# Stop infrastructure
docker-compose down

# Run integration tests
./gradlew integrationTest

# Generate test coverage report
./gradlew jacocoTestReport
```

## Code Conventions

### Package Structure (per service)
```
com.logging.{service}/
├── config/          # Spring configuration classes
├── controller/      # REST controllers
├── service/         # Business logic
├── repository/      # Data access
├── kafka/           # Kafka producers/consumers
├── model/           # Domain entities
├── dto/             # Data transfer objects
└── exception/       # Custom exceptions
```

### Naming Conventions
- **Classes**: PascalCase (e.g., `LogIngestionService`)
- **Methods**: camelCase (e.g., `processLog`)
- **Constants**: SCREAMING_SNAKE_CASE
- **Packages**: lowercase

### Key Patterns to Follow
1. **Dependency Injection**: Constructor injection only
2. **Logging**: Use SLF4J with structured logging (MDC for trace IDs)
3. **Exceptions**: Custom exceptions with proper HTTP status mapping
4. **DTOs**: Separate request/response DTOs from domain models
5. **Validation**: Use Jakarta Validation annotations
6. **Configuration**: Externalize all configs via `application.yml`

### Kafka Patterns
- Manual offset commits (no auto-commit)
- Idempotent consumers (use idempotency keys)
- Dead letter topics for failed messages
- Graceful shutdown handling

### Database Patterns
- Use transactions appropriately
- Index frequently queried columns
- Batch inserts for performance
- Connection pooling (HikariCP)

## Testing Approach
- **Unit Tests**: Mock dependencies, test business logic
- **Integration Tests**: Use Testcontainers for real Kafka/PostgreSQL/Redis
- **Target**: 80%+ code coverage

## Important Files Reference
- `docker-compose.yml` - Infrastructure setup
- `common/src/main/java/.../dto/LogEvent.java` - Core log DTO
- `ingestion-service/.../config/KafkaConfig.java` - Kafka producer config
- `processing-service/.../kafka/LogConsumer.java` - Kafka consumer
- `monitoring-service/.../controller/LogQueryController.java` - Query API

## Session Continuity
See `SESSION_NOTES.md` for current progress and next steps.
See `PRD.md` for full requirements specification.
