# La imagen empaqueta el JAR que ya compiló y probó el pipeline (etapa "Build and test"):
# Docker no vuelve a compilar con Gradle. El contexto de build es build/docker del servicio,
# donde la tarea dockerContext deja app.jar:
#
#   ./gradlew :services:circleguard-form-service:assemble
#   docker build -f services/circleguard-form-service/Dockerfile \
#     --build-arg VERSION=1.0.0 -t circleguard/form-service:1.0.0 \
#     services/circleguard-form-service/build/docker

ARG RUNTIME_IMAGE=eclipse-temurin:21-jre-alpine

# Etapa 1 · layers: separa el JAR en las capas de Spring Boot
FROM ${RUNTIME_IMAGE} AS layers
WORKDIR /out
COPY app.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract \
 && mkdir -p dependencies spring-boot-loader snapshot-dependencies application \
 && rm app.jar

# Etapa 2 · runtime: solo JRE 21 + la aplicación, ejecutando como UID 10001
FROM ${RUNTIME_IMAGE} AS runtime

ARG VERSION=dev
LABEL org.opencontainers.image.title="circleguard-form-service" \
      org.opencontainers.image.description="CircleGuard · circleguard-form-service" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.vendor="CircleGuard"

RUN addgroup -S -g 10001 spring \
 && adduser -S -D -H -u 10001 -G spring -s /sbin/nologin spring \
 && install -d -o spring -g spring /app

WORKDIR /app

# Capas de Spring Boot de la menos a la más cambiante: un cambio de código solo
# reconstruye la última capa (application).
COPY --from=layers --chown=spring:spring /out/dependencies/ ./
COPY --from=layers --chown=spring:spring /out/spring-boot-loader/ ./
COPY --from=layers --chown=spring:spring /out/snapshot-dependencies/ ./
COPY --from=layers --chown=spring:spring /out/application/ ./

# UID numérico para que Kubernetes pueda verificar runAsNonRoot
USER 10001:10001

EXPOSE 8086

# La JVM lee JAVA_TOOL_OPTIONS sola; se puede sobrescribir con -e / env del compose
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
