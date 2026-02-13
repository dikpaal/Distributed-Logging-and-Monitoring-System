# Tests

This project uses JUnit 5 for unit and integration testing. Tests are located within each service module following standard Gradle/Maven conventions.

## Test Locations

| Service | Test Location |
|---------|---------------|
| ingestion-service | `ingestion-service/src/test/java/` |
| processing-service | `processing-service/src/test/java/` |
| monitoring-service | `monitoring-service/src/test/java/` |
| alert-service | `alert-service/src/test/java/` |
| user-service | `user-service/src/test/java/` |
| payment-service | `payment-service/src/test/java/` |
| order-service | `order-service/src/test/java/` |

## Running Tests

### Run All Tests

```bash
./tests/run-all-tests.sh
```

Or directly with Gradle:

```bash
./gradlew test
```

### Run Tests for a Specific Service

```bash
./tests/run-service-tests.sh ingestion-service
```

Or directly with Gradle:

```bash
./gradlew :ingestion-service:test
```

### Run with Verbose Output

```bash
./gradlew test --info
```

## Current Test Coverage

| Service | Tests | Description |
|---------|-------|-------------|
| ingestion-service | 9 | Controller tests for log ingestion, validation, idempotency |
| alert-service | 17 | Controller, service, and model tests for alerting |

## Test Stack

- **JUnit 5** - Test framework
- **Spring Boot Test** - Spring context testing
- **MockMvc** - REST endpoint testing
- **Mockito** - Mocking dependencies
