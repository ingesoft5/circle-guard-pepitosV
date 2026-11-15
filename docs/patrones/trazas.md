# Trazas form, Kafka, promotion y notification

form-service, promotion-service y notification-service exportan trazas por OTLP. Mientras no haya Jaeger en el clúster, un Jaeger local recibe las mismas trazas:

```text
docker run --rm -p 16686:16686 -p 4318:4318 jaegertracing/all-in-one:1.57
```

La interfaz queda en `http://localhost:16686`. La variable `OTEL_EXPORTER_OTLP_ENDPOINT` apunta a otro colector sin cambiar la imagen.

## Propagación

Al publicar en Kafka, el interceptor copia el span activo al header `traceparent` (`00-<traceId>-<spanId>-01`). Al consumir, el mismo header deja el `traceId` en el MDC, así que promotion y notification siguen la traza que abrió form.

El despliegue de Jaeger dentro del clúster queda para cuando exista el namespace (CGR-216).
