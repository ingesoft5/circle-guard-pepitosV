# Objetivos de rendimiento

El host no va en estos archivos. Locust lo recibe por CLI (`--host`). En el clúster es el Ingress (`http://$K8S_HOST_STAGE`). El Ingress publica el cliente web, y el cliente reenvía `/svc/gateway/` y `/svc/form/` a cada servicio.

## Validación de acceso (QR)

Escenario: `access_locustfile.py`, `POST /api/v1/gate/validate`.

| Objetivo | Valor |
|---|---|
| Usuarios concurrentes | 50 |
| p95 máximo | 500 ms |
| Throughput mínimo | 30 req/s |
| Tasa de error máxima | 1 % |

## Envío de cuestionarios

Escenario: `surveys_locustfile.py`, `POST /api/v1/surveys`.

| Objetivo | Valor |
|---|---|
| Usuarios concurrentes | 30 |
| p95 máximo | 800 ms |
| Throughput mínimo | 15 req/s |
| Tasa de error máxima | 1 % |

## Estrés de cuestionarios

La misma locustfile sube la carga por tramos: 10, 30, 60 y 120 usuarios. El punto de quiebre es el primer tramo donde el p95 pasa de 2 s o la tasa de error pasa de 5 %.

## Corrida del 21 de noviembre de 2026

k3s-stage no tiene el release de CircleGuard, así que la corrida usó el Ingress de k3s-dev. Locust 2.32.4, headless, contra `http://web:8080` dentro del namespace `circleguard-dev`. Ese servicio es el que publica el Ingress.

Acceso: 50 usuarios, 30 segundos, 2739 peticiones, 0 % de error, p95 13 ms, 92 req/s. Cumple los cuatro objetivos.

Cuestionarios: la rampa llegó a 120 usuarios. 12748 peticiones, 0 % de error, p95 97 ms, máximo 961 ms. No hubo punto de quiebre. El HPA de `form` subió a 3 réplicas, su máximo, con la CPU al 160 % del objetivo. `gateway` se quedó en 1 réplica.
