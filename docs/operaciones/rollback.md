# Plan de rollback en Kubernetes

Cómo devolver un ambiente a una versión sana cuando una versión desplegada falla (HU-52). Cada
ambiente es una release de Helm `circleguard` en el namespace `circleguard-<env>` de su clúster
k3s. Cada despliegue crea una revisión y Helm conserva las anteriores.

## Cuándo revertir

Se revierte, sin esperar a corregir el código, cuando después de un despliegue pasa cualquiera de
estas cosas:

- El smoke test del pipeline o las E2E fallan contra el ambiente.
- Un servicio no queda `Ready` o se reinicia en bucle (`CrashLoopBackOff`).
- Grafana o Alertmanager muestran un aumento de errores 5xx o de latencia que empezó con el
  despliegue.
- Un usuario reporta que un flujo principal (login, formulario, promoción) dejó de funcionar.

Si el despliegue no termina, no hace falta hacer nada: `deployK8s` usa `helm --atomic`, así que
Helm vuelve solo a la revisión anterior y el build queda `FAILED` con notificación a Discord.

## Cómo revertir: job `circleguard-rollback`

1. Abrir `circleguard-rollback` → **Build with Parameters**.
2. Elegir `ENVIRONMENT` (`stage`, `dev` o `prod`).
3. `VERSION`:
   - **Vacío (caso normal):** `helm rollback` a la revisión anterior. El job busca la última
     revisión `superseded` y muestra su versión antes de actuar.
   - **Una versión** (`0.4.0` o `v0.4.0`): la vuelve a desplegar con `helm upgrade`. El job
     comprueba primero que las 9 imágenes de esa versión estén en Nexus; si falta una, no toca el
     ambiente.
4. En `prod`, el job se detiene en **Approve production**. Solo `admin` puede aprobar.

El job espera a que todos los rollouts terminen (`--wait`) y corre el smoke test por el Ingress.
La descripción del build queda como `stage: 0.6.0 -> revision 7 (0.5.0) by <usuario>`, y el log
termina en `ROLLBACK OK` o `ROLLBACK FAILED`. El resultado queda en el registro de despliegues
(HU-51), con quien lanzó el job como aprobador, o quien aprobó en prod
([`trazabilidad.md`](../release-notes/trazabilidad.md)).

### A mano, si Jenkins no está disponible

```bash
export KUBECONFIG=<kubeconfig del clúster>
helm -n circleguard-stage history circleguard
helm -n circleguard-stage rollback circleguard <revisión> --wait --timeout 10m
```

Para un solo servicio, sin tocar el resto:

```bash
kubectl -n circleguard-stage rollout history deployment/gateway
kubectl -n circleguard-stage rollout undo deployment/gateway
```

Ese atajo deja la release de Helm desalineada con lo que corre. El siguiente `helm upgrade` o
`helm rollback` la vuelve a alinear.

## Verificación posterior

1. `helm -n circleguard-<env> history circleguard`: la última revisión dice
   `Rollback to <n>` y está `deployed`.
2. `kubectl -n circleguard-<env> get pods`: todos `Running` y `Ready`, sin reinicios nuevos.
3. `kubectl -n circleguard-<env> get deploy -o wide`: la columna `IMAGES` muestra la versión de
   destino.
4. `http://<host>/healthz` y `/svc/<servicio>/actuator/health` responden 200 (el job ya lo
   comprueba).
5. En Grafana, la tasa de errores y la latencia vuelven al nivel previo al despliegue.

Los datos se conservan: PostgreSQL y Neo4j guardan su información en volúmenes persistentes que
el rollback no toca. Si la versión defectuosa aplicó una migración de base de datos, el rollback
del código no la deshace; esas migraciones deben ser compatibles hacia atrás.

## Evidencia del ensayo

El ensayo se hizo en `stage` el 23 de noviembre de 2026 con el job `circleguard-rollback`:
stage pasó de 0.7.0 a 0.6.0 (build #13, 2 min 11 s). El smoke test por HTTPS respondió `UP` en
los cinco servicios núcleo. Después, el mismo job devolvió stage a 0.7.0 (build #14).

Las capturas están en [`docs/evidencias/rollback/`](../evidencias/rollback/):

| Captura | Qué muestra |
|:---|:---|
| `01-formulario-stage-0.6.0.jpg` | El formulario del job con `ENVIRONMENT=stage` y `VERSION=0.6.0` |
| `02-etapas-build-13.jpg` | Las etapas del build #13 en verde |
| `03-build-13-exitoso.jpg` | El build #13: `stage: 0.7.0 -> 0.6.0 by admin`, rama `dev` |
| `04-consola-rollback-ok.jpg` | La consola con `ROLLBACK OK` y `Finished: SUCCESS` |
