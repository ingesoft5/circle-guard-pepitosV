# Logs JSON

Cada servicio escribe en stdout una línea JSON por evento. Los campos fijos son `timestamp`, `level`, `service` y, cuando hay una traza activa, `traceId` y `spanId` copiados del MDC.

`service` sale de `spring.application.name`. El encoder no inventa un `traceId`: lo incluye cuando el MDC ya lo tiene (propagación de Kafka o el bridge de trazas). Promtail y el derived field de Grafana hacia Jaeger quedan para cuando el módulo de observabilidad esté en el clúster (CGR-219, CGR-221).
