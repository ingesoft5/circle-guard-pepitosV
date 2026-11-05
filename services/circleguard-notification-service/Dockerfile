#   docker build -f services/circleguard-notification-service/Dockerfile \
#     --build-arg VERSION=1.0.0 -t circleguard/notification-service:1.0.0 .

ARG GRADLE_IMAGE=gradle:8.14.3-jdk21-alpine
ARG RUNTIME_IMAGE=eclipse-temurin:21-jre-alpine

# Etapa 1 · build: compila con JDK 21 + Gradle y extrae las capas del JAR
FROM ${GRADLE_IMAGE} AS build
WORKDIR /workspace

ENV GRADLE_OPTS="-Dorg.gradle.daemon=false -Dkotlin.compiler.execution.strategy=in-process"

# 1) Solo los descriptores de build: esta capa (dependencias) queda en caché
#    mientras no cambien los build.gradle.kts.
COPY settings.gradle.kts build.gradle.kts ./
COPY services/circleguard-notification-service/build.gradle.kts services/circleguard-notification-service/
RUN gradle --quiet :services:circleguard-notification-service:dependencies > /dev/null

# 2) Código fuente y empaquetado (las pruebas corren en su propia etapa del pipeline)
COPY services/circleguard-notification-service/src services/circleguard-notification-service/src
RUN gradle :services:circleguard-notification-service:bootJar -x test \
 && mkdir -p /out \
 && find services/circleguard-notification-service/build/libs -name '*.jar' ! -name '*-plain.jar' -exec cp {} /out/app.jar \; \
 && cd /out \
 && java -Djarmode=layertools -jar app.jar extract \
 && mkdir -p dependencies spring-boot-loader snapshot-dependencies application \
 && rm app.jar

# Etapa 2 · runtime: solo JRE 21 + la aplicación, ejecutando como UID 10001
FROM ${RUNTIME_IMAGE} AS runtime

ARG VERSION=dev
LABEL org.opencontainers.image.title="circleguard-notification-service" \
      org.opencontainers.image.description="CircleGuard · circleguard-notification-service" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.vendor="CircleGuard"

RUN addgroup -S -g 10001 spring \
 && adduser -S -D -H -u 10001 -G spring -s /sbin/nologin spring \
 && install -d -o spring -g spring /app

WORKDIR /app

# Capas de Spring Boot de la menos a la más cambiante: un cambio de código solo
# reconstruye la última capa (application).
COPY --from=build --chown=spring:spring /out/dependencies/ ./
COPY --from=build --chown=spring:spring /out/spring-boot-loader/ ./
COPY --from=build --chown=spring:spring /out/snapshot-dependencies/ ./
COPY --from=build --chown=spring:spring /out/application/ ./

# UID numérico para que Kubernetes pueda verificar runAsNonRoot
USER 10001:10001

EXPOSE 8082

# La JVM lee JAVA_TOOL_OPTIONS sola; se puede sobrescribir con -e / env del compose
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
