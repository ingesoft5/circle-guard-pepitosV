# Prueba de las alertas críticas con fallas inyectadas (HU-41)

- **Fecha:** 22 de noviembre de 2026, de 23:28 a 23:48 UTC.
- **Ambiente:** stage (`circleguard-stage`), versión 0.7.0, clúster k3s de stage.
- **Procedimiento:** [`docs/operaciones/observability.md`](../../observability.md#provocar-cada-alerta).

Se provocó a propósito la condición de cada alerta crítica, se esperó el `FIRING` en Alertmanager
(que lo envía a Discord), se revirtió la falla y se esperó el `RESOLVED`. Un script consultó
Alertmanager y Prometheus cada 15 s y anotó cada cambio de estado en
[`linea-de-tiempo.log`](linea-de-tiempo.log). Los comandos, con su hora, están en
[`comandos.log`](comandos.log).

## Resultado

| Alerta | Severidad | Falla inyectada | Falla | Firing | Revertida | Resolved |
|:---|:---|:---|:---|:---|:---|:---|
| `ServiceDown` | critical | `file` deja de exponer `/actuator/prometheus` | 23:28:26 | 23:30:06 | 23:35:52 | 23:37:08 |
| `HighErrorRate` | critical | Neo4j apagado y tráfico a `promotion` (441 respuestas 500) | 23:28:27 | 23:31:11 | 23:35:52 | 23:40:16 |
| `CircuitBreakerOpen` | critical | Redis apagado y check-ins al gateway | 23:36:22 | 23:40:20 | 23:44:29 | 23:47:34 |
| `HighLatencyP95` | warning | La misma caída de Neo4j (respuestas lentas) | 23:28:27 | 23:34:11, **suprimida** | 23:35:52 | 23:40:16 |

`HighLatencyP95` se disparó pero quedó suprimida: la regla de inhibición de Alertmanager no envía
un `warning` de un servicio que ya tiene un `critical` activo, así que Discord no recibió un aviso
duplicado de `promotion`.

## Archivos

| Archivo | Contenido |
|:---|:---|
| `linea-de-tiempo.log` | Cambios de estado de cada alerta (`pending`, `active`, `suppressed`, `resolved`) |
| `comandos.log` | Comandos que inyectaron y revirtieron cada falla |
| `alertmanager-servicedown-higherrorrate.json` | Alertmanager con `ServiceDown` y `HighErrorRate` activas |
| `alertmanager-higherrorrate-latencia-suprimida.json` | `HighLatencyP95` suprimida por la inhibición |
| `alertmanager-circuitbreakeropen.json` | `CircuitBreakerOpen` activa |
| `circuito-redishealth-abierto.txt` | `resilience4j_circuitbreaker_state` por pod durante la falla |
| `gateway-respuestas.txt` | Respuestas del gateway con Redis caído y después de restaurarlo |
| `promotion-respuestas.txt` | Códigos HTTP de `promotion` con Neo4j caído |

Las capturas de Discord (`<alerta>-firing.png` y `<alerta>-resolved.png`) se agregan en esta misma
carpeta.

## Lo que encontró la falla de Redis

El primer intento con Redis apagado no se comportó como dice el patrón (23:36). El gateway dejó de
responder: el Ingress devolvió `502` y la sonda de readiness venció por tiempo. Hubo dos causas, y
las dos se corrigieron:

1. **Sin timeout hacia Redis.** Lettuce espera hasta 60 s por comando. Cada check-in retuvo un hilo
   durante un minuto, el circuito tardó en ver las fallas y el pod dejó de atender. Ahora
   `spring.data.redis.timeout` y `connect-timeout` valen 500 ms
   (`services/circleguard-gateway-service/src/main/resources/application.yml`).
2. **Readiness atada a Redis.** El gateway tenía `readinessGroup: readinessState,redis`. Con Redis
   caído, Kubernetes sacaba al pod del Service y nadie recibía la respuesta del circuito. Ahora es
   `readinessState` (`deploy/k8s/circleguard/values.yaml`): la caída de Redis la maneja el circuito.

En stage los dos valores se aplicaron con `kubectl set env` (ver `comandos.log`) y se repitió la
prueba (23:43). Con Redis caído, el gateway respondió `200` en unos 0,2 s con
`Access Denied: Health status unavailable` (falla cerrada), el circuito pasó a `open` y la alerta se
disparó. Al restaurar Redis respondió `GREEN` y el circuito volvió a `closed`. El siguiente
despliegue de stage aplica los mismos valores desde el chart y la imagen.
