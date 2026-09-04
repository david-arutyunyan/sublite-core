# ---- Build stage ----
# eclipse-temurin:21-jdk, not a maven:* image: the project's own Maven
# Wrapper (.mvn/wrapper) already pins the exact Maven version (3.9.9) -
# using it here keeps the build reproducible with the same Maven version
# every dev/CI run uses locally, instead of whatever a maven:* image tag
# happens to bundle.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Dependencies before source: this layer only invalidates when pom.xml
# changes, so a rebuild after a plain code edit reuses the cached
# (already-downloaded) dependency layer instead of re-fetching the whole
# repository every time.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B dependency:go-offline

COPY src/ src/
# Tests are skipped here on purpose, not just for build speed: the
# Testcontainers-backed integration suite needs a Docker daemon to start
# Postgres/Redis containers, which this build stage doesn't have access
# to (no Docker-in-Docker). Tests run via `mvn verify` in CI/locally;
# this build only packages a jar from code that already passed there.
RUN ./mvnw -B clean package -DskipTests

# ---- Runtime stage ----
# -jre, not -jdk: no compiler/dev tools needed to just run a jar. alpine
# keeps the base small; curl is added deliberately for the HEALTHCHECK
# below (an alpine jre image has neither curl nor wget by default).
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN apk add --no-cache curl \
    && addgroup -S sublite && adduser -S sublite -G sublite

COPY --from=build /app/target/sublite-core-*.jar app.jar

# Non-root: a compromised app process shouldn't run as root inside its
# own container just because nobody bothered to create a user for it.
USER sublite

EXPOSE 8080

# /actuator/health/readiness, not /health or /actuator/health/liveness:
# readiness is the one that actually checks DB/Redis connectivity (see
# SecurityConfig, application.yml) - what Compose's depends_on elsewhere
# in this file cares about is "can this container serve real traffic",
# not just "did the JVM start".
HEALTHCHECK --interval=10s --timeout=5s --start-period=30s --retries=5 \
    CMD curl -f http://localhost:8080/actuator/health/readiness || exit 1

# No explicit -Xmx/-XX flags: JDK 21 reads the container's cgroup memory
# limit automatically (UseContainerSupport, default since JDK 10) and
# sizes the heap off that - manual tuning is a later optimization, not a
# correctness requirement here.
ENTRYPOINT ["java", "-jar", "app.jar"]
