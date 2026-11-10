# {{projectName}}

{{projectDescription}}

> **Generated skeleton.** This project is a Spring Boot scaffold, not a finished
> implementation of the requested feature. The domain model, CRUD endpoints,
> database migrations, login endpoint, token issuance, and authorization rules
> are not generated. Replace the placeholder packages and add those pieces
> before deploying.

## Tech Stack

- **Java**: 21 LTS
- **Spring Boot**: {{spring.boot.version}}
- **Database**: PostgreSQL 16 (Flyway enabled; no migrations are generated)
- **Security**: Spring Security scaffold with bearer-token validation (JJWT) and a BCrypt encoder. Login and token issuance are not included.
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
# Set the required signing secret and database password.
export JWT_SECRET="replace-with-a-random-secret-of-at-least-32-bytes"
export DB_PASSWORD="replace-with-a-strong-database-password"

# Start PostgreSQL and the application
docker compose up -d

# View logs
docker compose logs -f {{serviceName}}
```

### Local Development

```bash
# Start PostgreSQL only
docker compose up -d postgres

# JWT_SECRET and DB_PASSWORD are required; neither has a default.
export JWT_SECRET="replace-with-a-random-secret-of-at-least-32-bytes"
export DB_PASSWORD="replace-with-a-strong-database-password"

# Run application
mvn spring-boot:run
```

## Actuator Endpoints

- Health: `GET /actuator/health`
- Info: `GET /actuator/info`
- Metrics: `GET /actuator/metrics`

## Project Structure

```
src/
├── main/
│   ├── java/
│   │   └── com/example/{{projectName}}/
│   │       ├── {{className}}Application.java
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
│       └── application.yml
└── test/
    └── java/
        └── com/example/{{projectName}}/
```

## Building

```bash
# Run all checks (compile, test, Checkstyle, SpotBugs)
mvn verify

# Skip tests
mvn clean package -DskipTests

# Build Docker image
mvn dockerfile:build -Pdocker
```

## Quality Gates

This project enforces:
- **Checkstyle**: Google Java Style + custom rules
- **SpotBugs + FindSecBugs**: Static analysis, security patterns

Optional gates:
- **JaCoCo**: 80% line coverage, 70% branch coverage (`mvn -Pcoverage verify`)
- **OWASP Dependency-Check**: CVSS >= 7 fails build (`mvn -Psecurity verify`)

Run all quality checks:
```bash
mvn verify
```

## Security

### Authentication
- `JwtAuthenticationFilter` validates bearer tokens signed with `JWT_SECRET`.
- **No login endpoint, user store, refresh flow, or token issuance is included.**
  Until you add one, a new protected controller cannot be called successfully.
- The `PasswordEncoder` bean is provided for a future login flow.
- **No rate limiting is included.** Add it at the API gateway or in the
  application before exposing authentication endpoints.

### Authorization
- All endpoints except the actuator health/info probes require authentication.
- When you add a login endpoint, permit it explicitly in `SecurityConfig`.
- `@EnableMethodSecurity` is enabled, but no role or permission rules are
  generated beyond the optional `roles` claim read by the JWT filter.

### Security Headers
- Spring Security's default response headers are enabled
- Add Content-Security-Policy and HSTS explicitly if you serve browser clients over HTTPS

### CORS
Configured via `app.cors.allowed-origins` in application.yml

## Database Migrations

Flyway is enabled, but this skeleton ships no migrations yet:
- Add SQL migrations under `src/main/resources/db/migration/`
- Naming: `V{version}__{description}.sql`
- They run automatically on startup

## Testing

```bash
# Unit tests
mvn test

# Default build: compile, test, Checkstyle, SpotBugs, package
mvn verify

# Coverage gate (enable once the project has tests)
mvn -Pcoverage verify

# OWASP dependency check (downloads vulnerability feeds)
mvn -Psecurity verify
```

## Deployment

### Docker
```bash
# Provide JWT_SECRET and DB_PASSWORD; compose starts PostgreSQL too.
export JWT_SECRET="replace-with-a-random-secret-of-at-least-32-bytes"
export DB_PASSWORD="replace-with-a-strong-database-password"
docker compose up -d --build
```

### Kubernetes
Use the built image as the basis for your own manifests and provide
`JWT_SECRET`, `DB_PASSWORD`, and the datasource settings as secrets.

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `JWT_SECRET` | JWT signing secret (min 32 bytes) | Required |
| `JWT_ISSUER` | JWT issuer claim | companion |
| `DB_USERNAME` | PostgreSQL username | postgres |
| `DB_PASSWORD` | PostgreSQL password | Required (no default) |
| `CORS_ALLOWED_ORIGINS` | Comma-separated origins | http://localhost:3000 |

## License

Copyright © {{year}}. All rights reserved.