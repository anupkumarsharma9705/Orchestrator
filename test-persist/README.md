# test-persist

REST CRUD for Task entity with title and status

## Tech Stack

- **Java**: 21 LTS
- **Spring Boot**: 3.4.x
- **Database**: PostgreSQL 16 (Flyway for migrations)
- **Security**: JWT (JJWT), BCrypt
- **Build**: Maven 3.9+
- **Container**: Docker, Docker Compose
- **Testing**: JUnit 5, Testcontainers, Mockito
- **Quality**: Checkstyle, SpotBugs + FindSecBugs, JaCoCo, OWASP Dependency-Check

## Prerequisites

- Java 21 JDK
- Maven 3.9+
- Docker & Docker Compose
- PostgreSQL 16 (or use Docker Compose)

## Quick Start

### Using Docker Compose (Recommended)

```bash
# Set required environment variables
export JWT_SECRET="your-super-secret-key-at-least-32-chars"
export JWT_ISSUER="your-app"

# Start all services
docker-compose up -d

# View logs
docker-compose logs -f {{serviceName}}
```

### Local Development

```bash
# Start PostgreSQL only
docker-compose up -d postgres

# Run application
./mvnw spring-boot:run
```

## API Documentation

Once running, access Swagger UI at:
- http://localhost:8080/swagger-ui.html
- http://localhost:8080/v3/api-docs

## Actuator Endpoints

- Health: `GET /actuator/health`
- Info: `GET /actuator/info`
- Metrics: `GET /actuator/metrics`
- Prometheus: `GET /actuator/prometheus`

## Project Structure

```
src/
├── main/
│   ├── java/
│   │   └── com/example/test-persist/
│   │       ├── config/
│   │       ├── controller/
│   │       ├── domain/
│   │       ├── dto/
│   │       ├── exception/
│   │       ├── mapper/
│   │       ├── repository/
│   │       ├── security/
│   │       └── service/
│   └── resources/
│       ├── application.yml
│       └── db/migration/
└── test/
    └── java/
        └── com/example/test-persist/
```

## Building

```bash
# Run all checks (compile, test, quality gates)
./mvnw verify

# Skip tests
./mvnw clean package -DskipTests

# Build Docker image
./mvnw dockerfile:build -Pdocker
```

## Quality Gates

This project enforces:
- **Checkstyle**: Google Java Style + custom rules
- **SpotBugs + FindSecBugs**: Static analysis, security patterns
- **JaCoCo**: 80% line coverage, 70% branch coverage
- **OWASP Dependency-Check**: CVSS >= 7 fails build

Run all quality checks:
```bash
./mvnw verify
```

## Security

### Authentication
- JWT-based stateless authentication
- BCrypt password hashing
- SecureRandom for token generation

### Security Headers
- X-Content-Type-Options: nosniff
- X-Frame-Options: DENY
- X-XSS-Protection: 1; mode=block
- Content-Security-Policy: default-src 'self'

### CORS
Configured via `app.cors.allowed-origins` in application.yml

## Database Migrations

Flyway manages schema evolution:
- Place SQL migrations in `src/main/resources/db/migration/`
- Naming: `V{version}__{description}.sql`
- Run automatically on startup

## Testing

```bash
# Unit tests
./mvnw test

# Integration tests (requires PostgreSQL)
./mvnw verify -DskipUnitTests

# With Testcontainers (auto-starts PostgreSQL)
./mvnw verify
```

## Deployment

### Docker
```bash
docker build -t test-persist .
docker run -p 8080:8080 test-persist
```

### Kubernetes
Helm charts and K8s manifests can be generated from the Docker image.

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `JWT_SECRET` | JWT signing secret (min 32 chars) | Required |
| `JWT_ISSUER` | JWT issuer claim | companion |
| `DB_USERNAME` | PostgreSQL username | postgres |
| `DB_PASSWORD` | PostgreSQL password | Required |
| `CORS_ALLOWED_ORIGINS` | Comma-separated origins | http://localhost:3000 |

## License

Copyright © 2026. All rights reserved.