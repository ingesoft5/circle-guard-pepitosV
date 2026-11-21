# Trazas form, Kafka, promotion y notification

form-service, promotion-service y notification-service exportan trazas por OTLP. En el clúster el chart levanta `jaegertracing/all-in-one:1.57` y esas tres cargas apuntan a `http://jaeger:4318/v1/traces`. La interfaz no se publica en el Ingress; se abre con `kubectl port-forward svc/jaeger 16686:16686`. Para una prueba en la máquina, el mismo colector sirve:

```text
docker run --rm -p 16686:16686 -p 4318:4318 jaegertracing/all-in-one:1.57
```

La interfaz queda en `http://localhost:16686`. La variable `OTEL_EXPORTER_OTLP_ENDPOINT` apunta a otro colector sin cambiar la imagen.

## Propagación

Al publicar en Kafka, el interceptor copia el span activo al header `traceparent` (`00-<traceId>-<spanId>-01`). Al consumir, el mismo header deja el `traceId` en el MDC, así que promotion y notification siguen la traza que abrió form. `TraceparentPropagationTest` comprueba que el consumidor recupera exactamente el `traceId` del productor y que lo borra al terminar el registro. En Jaeger esa continuidad se ve como una sola traza.
