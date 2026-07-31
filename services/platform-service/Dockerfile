# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:25.0.3_9-jdk-ubi10-minimal AS build

WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY modules/tenant-access/pom.xml modules/tenant-access/pom.xml
COPY services/platform-service/pom.xml services/platform-service/pom.xml
COPY modules/tenant-access/src/ modules/tenant-access/src/
COPY services/platform-service/src/ services/platform-service/src/

RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw --batch-mode --no-transfer-progress \
      -pl services/platform-service \
      -am \
      -DskipTests \
      package \
    && cp \
      services/platform-service/target/platform-service-*.jar \
      /workspace/platform-service.jar

FROM eclipse-temurin:25.0.3_9-jre-ubi10-minimal AS runtime

WORKDIR /opt/eoc

COPY --from=build \
  --chown=10001:10001 \
  /workspace/platform-service.jar \
  platform-service.jar

USER 10001:10001

EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=6 \
  CMD ["wget", "--quiet", "--tries=1", "--output-document=/dev/null", "http://127.0.0.1:8080/actuator/health/readiness"]

ENTRYPOINT ["java", "-jar", "/opt/eoc/platform-service.jar"]
