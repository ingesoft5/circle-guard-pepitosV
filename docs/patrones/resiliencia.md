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

Prometheus lee `GET /actuator/prometheus`. El medidor queda como `resilience4j_circuitbreaker_state` con las etiquetas `name=redisHealth` y `state`. Grafana carga el panel `redisHealth circuit` desde el ConfigMap del chart.

También se puede consultar el propio servicio:

- `GET /actuator/circuitbreakers`
- `GET /actuator/metrics/resilience4j.circuitbreaker.state?tag=name:redisHealth`

La prueba `RedisHealthCircuitBreakerTest` tumba Redis con un doble, comprueba que el circuito abre y que la llamada siguiente no toca Redis, espera el tiempo configurado y comprueba que una lectura correcta lo cierra. El medidor `resilience4j.circuitbreaker.state` pasa de `open` a `closed`.

El panel `CircleGuard redisHealth circuit` muestra si el circuito está abierto y la tasa de fallo.

## Prueba con la falla inyectada en stage

El 22 de noviembre de 2026 se apagó Redis en stage con tráfico real de check-ins
([evidencia](../operaciones/evidencias/hu-41/README.md)). El primer intento mostró dos fallas que
la prueba unitaria no podía ver:

- **Sin timeout hacia Redis.** Lettuce espera hasta 60 s por comando, así que cada check-in retenía
  un hilo un minuto y el pod dejaba de responder antes de que el circuito abriera. Se fijaron
  `spring.data.redis.timeout` y `connect-timeout` en 500 ms: el circuito solo protege si la llamada
  falla rápido.
- **Readiness atada a Redis.** Con `redis` en el grupo de readiness, Kubernetes sacaba al gateway
  del Service y los usuarios recibían `502` en lugar de la respuesta del circuito. El gateway quedó
  con `readinessState`; la caída de Redis la maneja el circuito.

Con los dos cambios, el gateway respondió en unos 0,2 s con `Access Denied: Health status
unavailable`, el circuito abrió, la alerta `CircuitBreakerOpen` llegó a Discord y, al volver Redis,
el circuito cerró solo.
