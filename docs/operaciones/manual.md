# Manual de operaciones de CircleGuard

Guía para operar y recuperar CircleGuard ante incidentes comunes: servicio caído, alerta,
rollback y rotación de secretos. Está pensada para alguien que ya tiene acceso al host de CI
y a los clústeres. Los detalles de cada herramienta están en los documentos enlazados al
final.

**Alcance:** ambientes `dev`, `stage` y `prod` desplegados en k3s; Jenkins, SonarQube y Nexus;
infraestructura Terraform y estado remoto.

## 0. Contactos y escalamiento

| Integrante | Correo | Área principal |
|:---|:---|:---|
| Johan Suarez | jossuarperdom@gmail.com | Plataforma, CI e IaC. |
| David Chicué Romero | davegeroficial@gmail.com | Observabilidad, patrones y release. |
| Ismael Barrionuevo | ismaelbarrionuevo2006@gmail.com | Ambientes, Kubernetes, seguridad y rendimiento. |
| Jean Carlo Ocampo | jeank2823@gmail.com | Trazas, logs, configuración, resiliencia, secretos y escaneos. |

**Aprobación de producción:** `input` de Jenkins con el usuario `admin`; el correo del
aprobador se gestiona por el canal de operaciones del equipo. El canal de Discord del equipo
es el punto de aviso para incidentes y escalamiento.

Registrar cada incidente en Jira (proyecto `CGR`) con hora, ambiente, impacto, comandos
ejecutados y evidencia.

## 1. Primeros 5 minutos

1. Confirmar el ambiente afectado y el síntoma: URL, servicio, error y hora.
2. Revisar el último build de Jenkins de la rama correspondiente.
3. Revisar pods y eventos:
   ```bash
   kubectl -n circleguard-<env> get pods,deploy,svc,ingress,events --sort-by=.lastTimestamp
   ```
4. Revisar el nodo:
   ```bash
   kubectl get nodes
   infra/cloud/aws/hosts.sh status
   ```
5. Revisar las alertas en Alertmanager/Prometheus y los dashboards de Grafana.
6. Si el problema empezó con un despliegue, preparar rollback; si es una dependencia, revisar
   su pod; si es el nodo, revisar EC2/Security Group.
7. No ejecutar `force-unlock`, `terraform destroy`, `-lock=false` ni borrar volúmenes sin
   confirmar el impacto.

## 2. Servicio caído o errores 5xx

### Síntomas

- La URL pública no responde o devuelve 5xx.
- `/healthz` o `/svc/<servicio>/actuator/health` fallan.
- Un pod está en `CrashLoopBackOff`, `Pending`, `Error` o no está `Ready`.
- El smoke test o las E2E del pipeline fallan.

### Diagnóstico

```bash
kubectl -n circleguard-<env> get pods -o wide
kubectl -n circleguard-<env> get events --sort-by=.lastTimestamp | tail -n 40
kubectl -n circleguard-<env> describe pod <pod>
kubectl -n circleguard-<env> logs <pod> --tail=200
kubectl -n circleguard-<env> logs deploy/<servicio> --tail=200
kubectl -n circleguard-<env> get deploy <servicio> -o wide
helm -n circleguard-<env> history circleguard --max 10
```

Revisar también:

- Dependencias: pods de PostgreSQL, Neo4j, Kafka, Redis y OpenLDAP en el namespace.
- Nodo: `kubectl get nodes`, `hosts.sh status`, CPU/memoria y alarmas de CloudWatch.
- ConfigMaps/Secrets: que el Deployment monte las variables correctas.
- Último cambio: build de Jenkins, imagen y revisión de Helm.

### Resolución

- **Pod dañado:** `kubectl -n circleguard-<env> rollout restart deploy/<servicio>` y seguir
  el rollout.
- **Recursos:** revisar HPA, requests/limits y memoria del nodo; escalar si aplica.
- **Despliegue reciente:** ejecutar el job de rollback (sección 4) o
  `helm rollback` si Jenkins no está disponible.
- **Dependencia caída:** reiniciar el pod de la dependencia y revisar sus logs/volumen.
- **Nodo caído o detenido:** `hosts.sh start k3s` y esperar a que k3s arranque.
- **Base de datos corrupta o volumen perdido:** no improvisar; restaurar desde respaldo
  (sección 8) y escalar.

### Verificación

```bash
kubectl -n circleguard-<env> get pods
curl -fsS "http://<K8S_HOST>/healthz"
curl -fsS "http://<K8S_HOST>/svc/<servicio>/actuator/health"
```

Todos los pods `Running` y `Ready`, sin reinicios nuevos; errores y latencia de vuelta a la
línea base en Grafana.

## 3. Alerta disparada

### Dónde mirar

- Alertmanager: `http://<IP>/alertmanager` (Basic Auth).
- Prometheus: `http://<IP>/prometheus` (Basic Auth).
- Grafana: `http://<IP>/grafana`.
- Logs: `kubectl logs` y las herramientas de logs del clúster.
- Trazas: Jaeger según la configuración del ambiente.

### Procedimiento

1. Leer la alerta: nombre, severidad, ambiente, servicio y desde cuándo.
2. Abrir el dashboard del servicio en Grafana y comparar con la línea base.
3. Revisar `targets` en Prometheus: ¿el servicio está `UP`?
4. Revisar pods/eventos/logs del servicio y de sus dependencias.
5. Revisar si coincide con un despliegue, un pico de tráfico o un cambio de configuración.
6. Mitigar:
   - Rollback si empezó con un despliegue.
   - Reiniciar/reprogramar el pod si es un fallo transitorio.
   - Escalar o detener tráfico si el nodo está saturado.
   - Silenciar en Alertmanager **solo** mientras se mitiga, dejando comentario y fecha.
7. Verificar que la alerta se resuelve y que llega la notificación de recuperación.

### Evidencia

Guardar captura de la alerta, rango de tiempo, dashboard, logs relevantes y acción tomada.

## 4. Rollback

El procedimiento completo está en [`rollback.md`](rollback.md).

### Opción recomendada: job de Jenkins

1. Abrir `circleguard-rollback` → **Build with Parameters**.
2. `ENVIRONMENT`: `stage`, `dev` o `prod`.
3. `VERSION`: vacío para volver a la revisión anterior; o una versión publicada.
4. En `prod`, aprobar con `admin`.
5. Verificar `ROLLBACK OK`, pods `Ready`, smoke test y Grafana.

### Si Jenkins no está disponible

```bash
export KUBECONFIG=<kubeconfig-del-ambiente>
helm -n circleguard-<env> history circleguard
helm -n circleguard-<env> rollback circleguard <revisión> --wait --timeout 10m
```

Para un solo servicio:

```bash
kubectl -n circleguard-<env> rollout history deploy/<servicio>
kubectl -n circleguard-<env> rollout undo deploy/<servicio>
```

> Un rollback de imagen no deshace una migración de base de datos. Si la versión defectuosa
> aplicó una migración incompatible, restaurar el respaldo o desplegar un forward-fix.

## 5. Rotación de secretos

Los detalles del clúster están en [`secretos.md`](secretos.md). Nunca versionar secretos.

### Secretos de la aplicación (Kubernetes)

La fuente local es `~/.circleguard/k8s-<env>.env` (permisos `600`). El despliegue usa
`deploy/k8s/scripts/apply-secrets.sh`, que cifra con kubeseal y aplica un SealedSecret.

1. Cambiar el valor en la fuente local.
2. Reaplicar los secretos del ambiente:
   ```bash
   deploy/k8s/scripts/apply-secrets.sh <env>
   ```
3. Reiniciar los Deployments que consumen el secreto:
   ```bash
   kubectl -n circleguard-<env> rollout restart deploy
   ```
4. Verificar pods y health endpoints. No hace falta reconstruir imágenes.

### Credenciales de Jenkins

| Credencial | Loader / origen |
|:---|:---|
| SSH de despliegue por ambiente | `infra/bootstrap/load-deploy-credential.sh <env>` |
| kubeconfig por ambiente | `infra/bootstrap/load-kubeconfig.sh <env> <ip-privada>` |
| AWS, SSH de observability y contraseña de Grafana | `infra/bootstrap/load-terraform-credential.sh aws\|ssh\|grafana` |
| GitHub, Sonar, Nexus, Discord | `.env` de CI + `bootstrap-jenkins.sh` / recarga de JCasC |

Después de rotar, ejecutar un build de prueba y verificar que las etapas que usan la
credencial funcionan.

### Claves de AWS

1. Crear una access key nueva para el usuario IAM (`ci-terraform` o el que corresponda).
2. Cargarla en Jenkins con `load-terraform-credential.sh aws`.
3. Actualizar la copia local del operador.
4. Desactivar y eliminar la clave anterior.
5. Verificar un `terraform plan` desde Jenkins.

### Grafana y Basic Auth

- Basic Auth de Prometheus/Alertmanager:
  `infra/bootstrap/load-monitoring-auth.sh monitoring` (lee la contraseña por stdin).
- Contraseña de Grafana: actualizar el secreto de Grafana y, si es necesario,
  `grafana cli admin reset-admin-password --password-from-stdin` en el pod. Mantener
  `TF_VAR_prometheus_grafana_admin_password` alineado con el secreto.
- Verificar `/grafana`, `/prometheus` y `/alertmanager` después del cambio.

### Verificación de secretos

```bash
git grep -nE '(password|secret|token|access_key)' -- ':!docs/costos' || true
# El pipeline ejecuta secretScan/gitleaks en cada PR.
```

## 6. Terraform: lock atascado o plan stale

El detalle está en [`terraform-state-lock.md`](terraform-state-lock.md).

- `Error acquiring the state lock`: esperar a que termine el otro apply. No usar `-lock=false`.
- `Saved plan is stale`: regenerar el plan; no reutilizar el `.tfplan`.
- `force-unlock <LOCK_ID>`: solo si el proceso murió y no hay nadie aplicando; confirmar en
  DynamoDB/CloudTrail.
- Un `apply` rechazado por permisos se reintenta tras corregir IAM; no cambiar el código para
  evitarlo.

## 7. Nodo, CI o herramienta caída

### Nodo k3s

```bash
infra/cloud/aws/hosts.sh status
infra/cloud/aws/hosts.sh start k3s
ssh ubuntu@<ip> 'sudo systemctl status k3s --no-pager'
ssh ubuntu@<ip> 'sudo k3s kubectl get nodes'
```

Si el nodo no arranca, revisar EC2, EIP, Security Group y CloudWatch. Un cambio de
`user_data`/AMI puede recrear el nodo: planificar y respaldar antes.

### Jenkins, SonarQube y Nexus

En el host de CI/tools:

```bash
sudo docker compose --env-file infra/compose/ci/.env \
  -f infra/compose/ci/docker-compose.yml ps
sudo docker compose --env-file infra/compose/ci/.env \
  -f infra/compose/ci/docker-compose.yml logs --tail=200 <servicio>
sudo docker compose --env-file infra/compose/ci/.env \
  -f infra/compose/ci/docker-compose.yml up -d <servicio>
```

Revisar disco (`df -h`), memoria, Docker (`docker ps`, `docker system df`) y la conectividad
entre CI y tools. Si se cambió JCasC, recargar y revisar el log de Jenkins.

## 8. Bases de datos y respaldos

- PostgreSQL, Neo4j, Kafka, Redis y OpenLDAP usan volúmenes local-path en el nodo.
- No hay respaldo automático de bases de datos. Antes de una operación destructiva, exportar:
  ```bash
  kubectl -n circleguard-<env> exec <pod-postgres> -- \
    pg_dump -U <usuario> <base> > respaldo-<env>.sql
  kubectl -n circleguard-<env> exec <pod-neo4j> -- \
    neo4j-admin database dump neo4j --to-path=/tmp
  ```
- Los adjuntos viven en el bucket S3 con versionado; no borrar versiones sin revisar.
- Restaurar solo con un procedimiento probado y con el ambiente aislado.

## 9. Costos y apagado

- Cuando no se use un ambiente: `hosts.sh stop k3s`; los datos quedan en EBS.
- La destrucción es una decisión de cierre. En esta entrega **no se ejecutó**; `dev`, `stage`
  y `prod` siguen activos. El estado final y el procedimiento de referencia están en
  [`docs/costos/evidencia-destroy.md`](../costos/evidencia-destroy.md).
- Siguen cobrando CI/tools, los hosts legacy detenidos, los ambientes y el bucket/tabla del
  state. Ver [`docs/costos/README.md`](../costos/README.md).

## 10. Qué no hacer

- No usar `-lock=false` en Terraform.
- No editar el state a mano ni borrar el bucket/tabla del lock.
- No hacer `force-unlock` sin confirmar que no hay un apply en curso.
- No borrar volúmenes ni terminar instancias sin respaldo.
- No commitear `.env`, kubeconfigs, llaves, contraseñas ni `.tfplan`.
- No desplegar en `prod` sin aprobación de `admin`.
- No desactivar los checks de seguridad del pipeline para “salir del paso”.

## Referencias

- [README operativo](README.md)
- [Rollback](rollback.md)
- [Secretos](secretos.md)
- [Observabilidad](observability.md)
- [Terraform y state lock](terraform-state-lock.md)
- [Pipeline de Jenkins](jenkins-pipeline.md)
- [Costos](../costos/README.md)
- [Resumen ejecutivo](../executive-summary.md)
