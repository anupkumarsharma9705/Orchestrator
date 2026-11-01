# Sandbox Docker Image
# Contains: JDK 21, Maven 3.9+, Scanners (SpotBugs, Checkstyle, OWASP DepCheck, Trivy, Semgrep)
# Runs as non-root user (1000:1000)

FROM eclipse-temurin:21-jdk-alpine AS base

# Install system dependencies
RUN apk add --no-cache \
    bash \
    curl \
    git \
    openssh-client \
    docker-cli \
    docker-cli-compose \
    && rm -rf /var/cache/apk/*

# Install Maven
ENV MAVEN_VERSION=3.9.6
ENV MAVEN_HOME=/opt/maven
RUN mkdir -p ${MAVEN_HOME} \
    && curl -fsSL https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz \
    | tar -xz -C ${MAVEN_HOME} --strip-components=1 \
    && ln -s ${MAVEN_HOME}/bin/mvn /usr/bin/mvn

# Install Trivy
ENV TRIVY_VERSION=0.53.0
RUN curl -fsSL https://github.com/aquasecurity/trivy/releases/download/v${TRIVY_VERSION}/trivy_${TRIVY_VERSION}_Linux-64bit.tar.gz \
    | tar -xz -C /usr/local/bin trivy \
    && chmod +x /usr/local/bin/trivy

# Install Semgrep
RUN pip3 install --no-cache-dir semgrep==1.100.0

# Install OWASP Dependency-Check
ENV DEPCHECK_VERSION=9.2.1
RUN curl -fsSL https://github.com/jeremylong/DependencyCheck/releases/download/v${DEPCHECK_VERSION}/dependency-check-${DEPCHECK_VERSION}-release.zip \
    -o /tmp/dependency-check.zip \
    && unzip -q /tmp/dependency-check.zip -d /opt \
    && ln -s /opt/dependency-check/bin/dependency-check.sh /usr/local/bin/dependency-check \
    && rm /tmp/dependency-check.zip

# Install SpotBugs
ENV SPOTBUGS_VERSION=4.8.6
RUN curl -fsSL https://github.com/spotbugs/spotbugs/releases/download/${SPOTBUGS_VERSION}/spotbugs-${SPOTBUGS_VERSION}.tgz \
    | tar -xz -C /opt \
    && ln -s /opt/spotbugs-${SPOTBUGS_VERSION}/bin/spotbugs /usr/local/bin/spotbugs

# Install Checkstyle
ENV CHECKSTYLE_VERSION=10.17
RUN curl -fsSL https://github.com/checkstyle/checkstyle/releases/download/checkstyle-${CHECKSTYLE_VERSION}/checkstyle-${CHECKSTYLE_VERSION}-all.jar \
    -o /usr/local/bin/checkstyle.jar \
    && echo '#!/bin/sh\njava -jar /usr/local/bin/checkstyle.jar "$@"' > /usr/local/bin/checkstyle \
    && chmod +x /usr/local/bin/checkstyle

# Create non-root user
RUN addgroup -g 1000 -S appgroup && \
    adduser -u 1000 -S appuser -G appgroup -h /home/appuser -s /bin/bash

# Create workspace directory
RUN mkdir -p /workspace && chown -R 1000:1000 /workspace

# Create Maven cache directory
RUN mkdir -p /root/.m2 && chown -R 1000:1000 /root/.m2

# Switch to non-root user
USER 1000:1000

# Set working directory
WORKDIR /workspace

# Default command
CMD ["/bin/sh"]