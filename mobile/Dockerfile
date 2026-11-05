# Contexto de build: la carpeta mobile/
#   docker build -t circleguard/web-client:1.0.0 --build-arg VERSION=1.0.0 mobile
#
# La imagen es la misma para dev, stage y prod. El ambiente se elige al
# ejecutarla, con las variables *_UPSTREAM:
#   docker run -p 8080:8080 -e AUTH_UPSTREAM=auth-service:8180 ... circleguard/web-client:1.0.0

# Etapa 1 · build: genera los archivos estáticos con Expo
FROM node:22-bookworm-slim AS build
WORKDIR /app

ENV CI=1 \
    EXPO_NO_TELEMETRY=1

# Dependencias primero: esta capa queda en caché mientras no cambie el lockfile
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY . .

# Prefijo del API que Expo incrusta en el bundle (ver constants/Config.ts).
# "/svc" = mismo origen; el nginx de la etapa 2 lo enruta a cada servicio.
ARG EXPO_PUBLIC_API_PREFIX=/svc
ENV EXPO_PUBLIC_API_PREFIX=${EXPO_PUBLIC_API_PREFIX}

RUN npx expo export --platform web --output-dir dist

# Etapa 2 · runtime: solo los estáticos + nginx sin privilegios (UID 101, :8080)
FROM nginxinc/nginx-unprivileged:1.27-alpine AS runtime

ARG VERSION=dev
LABEL org.opencontainers.image.title="circleguard-web-client" \
      org.opencontainers.image.description="CircleGuard · cliente web (Expo)" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.vendor="CircleGuard"

# Host:puerto de cada servicio en el ambiente. Los valores por defecto son los
# nombres de servicio del docker-compose; cada ambiente puede sobrescribirlos.
ENV NGINX_ENTRYPOINT_LOCAL_RESOLVERS=1 \
    AUTH_UPSTREAM=auth-service:8180 \
    IDENTITY_UPSTREAM=identity-service:8083 \
    PROMOTION_UPSTREAM=promotion-service:8088 \
    NOTIFICATION_UPSTREAM=notification-service:8082 \
    FORM_UPSTREAM=form-service:8086 \
    FILE_UPSTREAM=file-service:8085 \
    GATEWAY_UPSTREAM=gateway-service:8087 \
    DASHBOARD_UPSTREAM=dashboard-service:8084

COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist/ /usr/share/nginx/html/

USER 101

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD wget -q -O /dev/null http://127.0.0.1:8080/healthz || exit 1
