# Objetivos de rendimiento

El host no va en estos archivos. Locust lo recibe por CLI (`--host`), que en stage es `http://$DEPLOY_HOST_STAGE`. La corrida headless y el HPA quedan para cuando ese entorno exista (CGR-237).

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

La misma locustfile sube la carga por tramos: 10, 30, 60 y 120 usuarios. El punto de quiebre es el primer tramo donde el p95 pasa de 2 s o la tasa de error pasa de 5 %. Comparar ese tramo con el HPA es parte de la ejecución en stage, no de este archivo.
