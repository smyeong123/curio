# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace/app

# Install maven (no wrapper in project)
RUN apk add --no-cache maven

# Copy pom.xml first to cache dependency downloads
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and build
COPY src src
RUN mvn clean package -DskipTests -B && \
    mkdir -p target/dependency && \
    cd target/dependency && \
    jar -xf ../*.jar

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
VOLUME /tmp

# Create non-root user
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

ARG DEPENDENCY=/workspace/app/target/dependency
COPY --from=build ${DEPENDENCY}/BOOT-INF/lib /app/lib
COPY --from=build ${DEPENDENCY}/META-INF /app/META-INF
COPY --from=build ${DEPENDENCY}/BOOT-INF/classes /app

USER appuser

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=40s \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

# Digest day-boundaries and retention are computed in UTC (code pins the hot
# sites explicitly); keep the whole JVM on UTC so nothing else drifts either.
ENTRYPOINT ["java", \
  "-Duser.timezone=UTC", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-cp", "app:app/lib/*", \
  "com.curio.CurioApplication"]
