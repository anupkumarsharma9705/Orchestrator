# syntax=docker/dockerfile:1

# Build context must be the repository root because the server depends on the
# in-reactor modules engine-core and orchestrator-api-contract:
#   docker build -f orchestrator-server/Dockerfile -t java-companion-server .
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace

# Copy the reactor and build only what the server needs. The dependency
# resolution is cached in the layer while sources change more often.
COPY pom.xml ./
COPY engine-model/pom.xml engine-model/
COPY engine-llm/pom.xml engine-llm/
COPY engine-sandbox/pom.xml engine-sandbox/
COPY engine-verify/pom.xml engine-verify/
COPY engine-store/pom.xml engine-store/
COPY engine-core/pom.xml engine-core/
COPY engine-templates/pom.xml engine-templates/
COPY eval/pom.xml eval/
COPY orchestrator-api-contract/pom.xml orchestrator-api-contract/
COPY orchestrator-server/pom.xml orchestrator-server/
COPY app-cli/pom.xml app-cli/
RUN mvn -B -ntp -pl orchestrator-server -am dependency:go-offline -DskipTests || true

COPY engine-model/src engine-model/src
COPY engine-llm/src engine-llm/src
COPY engine-sandbox/src engine-sandbox/src
COPY engine-verify/src engine-verify/src
COPY engine-store/src engine-store/src
COPY engine-core/src engine-core/src
COPY engine-templates/src engine-templates/src
COPY eval/src eval/src
COPY orchestrator-api-contract/src orchestrator-api-contract/src
COPY orchestrator-server/src orchestrator-server/src

RUN mvn -B -ntp -pl orchestrator-server -am package -DskipTests

FROM eclipse-temurin:21-jre

RUN apt-get update \
    && apt-get install -y --no-install-recommends openssl curl ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 1001 orchestrator \
    && useradd --system --uid 1001 --gid orchestrator --home-dir /app --shell /usr/sbin/nologin orchestrator

WORKDIR /app
COPY --from=build /workspace/orchestrator-server/target/orchestrator-server-*.jar /app/app.jar
RUN chown -R orchestrator:orchestrator /app

USER orchestrator
EXPOSE 8443

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
