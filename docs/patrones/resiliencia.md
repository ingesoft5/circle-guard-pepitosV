# Patrón de resiliencia (Resilience4j)

El gateway lee el estado de salud en Redis antes de abrir el campus. Si Redis no responde, la llamada no debe quedarse esperando el timeout ni dejar pasar a alguien cuyo estado no se pudo comprobar.

## Dónde está

`HealthStatusLookup` en gateway-service protege esa lectura con un retry y un circuit breaker llamados `redisHealth`.

- El retry va por dentro: dos intentos, separados 50 ms, solo ante un fallo de acceso a Redis.
- El circuit breaker va por fuera. Cuenta una llamada como fallida cuando los reintentos se agotan.
- Con 4 fallos en una ventana de 6 llamadas y una tasa de fallo del 50 %, el circuito pasa a `OPEN`.
- En `OPEN` no se llama a Redis. El método de fallback responde al momento con `UNAVAILABLE`.
- A los 5 s pasa a `HALF_OPEN`. Una lectura correcta lo devuelve a `CLOSED`.

El portero convierte `UNAVAILABLE` en acceso denegado: `Access Denied: Health status unavailable`.

## Trade-off

Fallar cerrado protege el campus: sin el estado de salud no se puede distinguir a quien está en riesgo. El costo es que, mientras Redis esté caído, también se niega la entrada a quien estaría en verde. El retry corto evita que un corte de un instante abra el circuito.

## Cómo se ve el estado

Sin Prometheus todavía, el estado queda en el propio servicio:

- `GET /actuator/circuitbreakers`
- `GET /actuator/metrics/resilience4j.circuitbreaker.state?tag=name:redisHealth`

La prueba `RedisHealthCircuitBreakerTest` tumba Redis con un doble, comprueba que el circuito abre y que la llamada siguiente no toca Redis, espera el tiempo configurado y comprueba que una lectura correcta lo cierra. El medidor `resilience4j.circuitbreaker.state` pasa de `open` a `closed`.

El panel de Grafana queda para cuando Prometheus recolecte esas métricas (CGR-149).
