# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:25.0.3_9-jdk-ubi10-minimal AS build

WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY services/web-bff/pom.xml services/web-bff/pom.xml
COPY services/web-bff/src/ services/web-bff/src/

RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw --batch-mode --no-transfer-progress \
      --file services/web-bff/pom.xml \
      -DskipTests \
      package \
    && cp \
      services/web-bff/target/web-bff-*.jar \
      /workspace/web-bff.jar

FROM eclipse-temurin:25.0.3_9-jre-ubi10-minimal AS runtime

WORKDIR /opt/eoc

COPY --from=build \
  --chown=10001:10001 \
  /workspace/web-bff.jar \
  web-bff.jar

USER 10001:10001

EXPOSE 8081

HEALTHCHECK --interval=10s --timeout=3s --start-period=20s --retries=6 \
  CMD ["wget", "--quiet", "--tries=1", "--output-document=/dev/null", "http://127.0.0.1:8081/actuator/health/readiness"]

ENTRYPOINT ["java", "-jar", "/opt/eoc/web-bff.jar"]
