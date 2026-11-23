# Observabilidad en el clúster (HU-26 → HU-39/40/41)

Guía para instalar y operar Prometheus, Grafana y Alertmanager en un clúster k3s de CircleGuard.
El módulo Terraform `observability` (HU-26) instala el stack base; la recolección de métricas,
los dashboards y las alertas se configuran en HU-39, HU-40 y HU-41.

## Qué instala el módulo

| Pieza | Dónde |
|:---|:---|
| `kube-prometheus-stack`: Prometheus, Grafana, Alertmanager, node-exporter y kube-state-metrics | Namespace `monitoring` del clúster |
| `micrometer-registry-prometheus`, `/actuator/prometheus` y contadores de negocio | Código de los 8 servicios (HU-39) |
| ServiceMonitor de los 8 servicios | `deploy/k8s/circleguard/templates/servicemonitor.yaml` (HU-39) |
| Dashboards JSON y su aprovisionamiento | `observability/grafana/` (HU-40) |
| Reglas de alerta y envío a Discord | `observability/alerts/` (HU-41) |
| Ingress de Grafana, Prometheus y Alertmanager | HU-26, con TLS en HU-47 |
| Logs centralizados con Loki | HU-43 |

El módulo no configura el scraping ni crea paneles: deja el stack instalado y listo para que las
historias de observabilidad lo usen.

## Requisitos

1. **Credenciales AWS** con permisos de EC2, S3 sobre `circleguard-tfstate-*`, DynamoDB sobre
   `circleguard-tf-locks`, ECR, CloudWatch Logs, SNS y SSM.
2. **Clúster encendido**:
   ```bash
   infra/cloud/aws/hosts.sh start k3s
   ```
3. **Acceso SSH** desde donde se ejecuta Terraform. Si no estás en la VPC de CI, autoriza tu IP:
   ```bash
   infra/cloud/aws/hosts.sh allow
   ```
4. **Llave privada** del key pair `circleguard-admin-key` en `TF_VAR_prometheus_ssh_private_key`
   o en el agente SSH (`ssh-add`). Desde Jenkins, la conexión va por la IP privada:
   ```bash
   export TF_VAR_prometheus_ssh_host=<ip-privada-del-nodo>
   ```
5. **Namespace**: el stack se instala en `monitoring` con `--create-namespace`. Coordina con HU-29
   para no duplicar namespaces por ambiente.
6. **Capacidad**: el nodo tiene 8 GB. Los valores del chart limitan memoria y retención; si
   conviven los 8 servicios, el middleware, Jaeger y Loki, revisa que no falte memoria.

## Aplicar en un ambiente

```bash
export TF_VAR_prometheus_ssh_private_key="$(cat ../circleguard-admin-key.pem)"
export TF_VAR_prometheus_grafana_admin_password='<contraseña-de-grafana>'

cd infra/terraform/envs/dev
terraform init -reconfigure -backend-config=backend.hcl
terraform plan -out=dev.tfplan
terraform apply dev.tfplan
```

Repite con `stage` y `prod` usando su `backend.hcl`. En `prod`, el `apply` requiere la aprobación
de producción (HU-32).

Para desactivar la instalación en un ambiente:

```hcl
# terraform.tfvars
install_prometheus = false
```

## Verificar

```bash
terraform output node_public_ip
ssh ubuntu@<node_public_ip> 'sudo k3s kubectl -n monitoring get pods,svc'
```

Espera `Running` en Prometheus, Grafana, Alertmanager, node-exporter y kube-state-metrics.

Los tres servicios se publican por el Traefik del clúster en el puerto 80, que ya está abierto,
así que no se abre ningún puerto extra en el Security Group:

| Servicio | URL | Autenticación |
|:---|:---|:---|
| Grafana | `http://<node_public_ip>/grafana` | Login propio de Grafana (usuario `admin`) |
| Prometheus | `http://<node_public_ip>/prometheus` | Basic Auth de Traefik |
| Alertmanager | `http://<node_public_ip>/alertmanager` | Basic Auth de Traefik |

Prometheus y Alertmanager no tienen login propio: el middleware `monitoring-basic-auth` los
protege. El secreto vive fuera de Git y se crea por clúster (ver abajo). El TLS es HU-47.

Si en algún momento se necesita un acceso sin Ingress, los túneles siguen disponibles como
alternativa:

```bash
ssh -L 9090:127.0.0.1:9090 ubuntu@<node_public_ip> \
  'sudo k3s kubectl -n monitoring port-forward svc/kube-prometheus-stack-prometheus 9090:9090'
```

Con un kubeconfig del clúster, el túnel también se abre con
`kubectl -n monitoring port-forward svc/kube-prometheus-stack-prometheus 9090:9090`.

## Basic Auth de Prometheus y Alertmanager

El Ingress de Prometheus y Alertmanager referencia el middleware `monitoring-basic-auth`
(`observability/ingress/basic-auth-middleware.yaml`). El secreto con el `htpasswd` **no** se
versiona; se crea con el script idempotente, en cada clúster:

```bash
# La contraseña se lee por stdin, no queda en argv ni en el repositorio.
ssh ubuntu@<node_public_ip> 'sudo ~/circleguard/infra/bootstrap/load-monitoring-auth.sh monitoring' \
  < ~/.config/circleguard/monitoring-auth-password
```

El script crea el namespace si falta, crea/actualiza el Secret `monitoring-basic-auth` y aplica el
Middleware. Después de cambiar `observability/prometheus/values.yaml`, aplica Terraform en el
ambiente (o corre `install-observability.sh`) para que Helm cree los dos Ingress:

```bash
cd infra/terraform/envs/dev
terraform init -reconfigure -backend-config=backend.hcl
terraform plan -out=dev.tfplan
terraform apply dev.tfplan
```

Verifica que las rutas ya no caen en el web client:

```bash
curl -s -o /dev/null -w '%{http_code}\n' http://<node_public_ip>/prometheus       # 401
curl -s -o /dev/null -w '%{http_code}\n' http://<node_public_ip>/alertmanager     # 401
curl -s -u monitoring:<password> -o /dev/null -w '%{http_code}\n' \
  http://<node_public_ip>/prometheus/-/ready                                     # 200
curl -s -u monitoring:<password> -o /dev/null -w '%{http_code}\n' \
  http://<node_public_ip>/alertmanager/-/healthy                                 # 200
```

El chart de la aplicación crea el ServiceMonitor `circleguard-services` (ver
`deploy/k8s/README.md`). En Prometheus, la página de *targets* muestra los 8 servicios en `UP`
con la etiqueta `environment`. En Grafana, el datasource de Prometheus queda configurado por el
chart.

## Ejecutar la instalación sin Terraform

El mismo script se puede correr desde Jenkins o desde una máquina con kubeconfig y Helm:

```bash
KUBECONFIG=/ruta/al/kubeconfig \
  bash infra/bootstrap/install-observability.sh \
  monitoring 65.5.1 observability/prometheus/values.yaml
```

Es idempotente (`helm upgrade --install`). Útil cuando el pipeline dispone de la credencial
`kubeconfig-<ambiente>` y se prefiere no pasar la llave SSH por Terraform.

## Métricas de los servicios (HU-39)

Los 8 servicios exponen `/actuator/prometheus` con Micrometer. Todas las métricas llevan la
etiqueta `application` (el nombre del servicio); Prometheus agrega `namespace`, `pod`, `service`,
`job` y `environment`.

| Grupo | Métricas | Servicios |
|:---|:---|:---|
| JVM y proceso | `jvm_*`, `process_*`, `system_*` | Los 8 |
| HTTP | `http_server_requests_seconds_*` | Los 8 |
| Base de datos | `hikaricp_*` | Los que usan PostgreSQL |
| Kafka | `kafka_producer_*`, `kafka_consumer_*` | `form`, `identity`, `notification`, `promotion` |

Las métricas de Kafka aparecen cuando el cliente se crea: las del productor, tras el primer envío.

### Métricas de negocio

| Métrica | Servicio | Etiquetas | Qué cuenta |
|:---|:---|:---|:---|
| `circleguard_promotions_total` | `promotion` | `status` (`SUSPECT`, `PROBABLE`, `CONFIRMED`, `RECOVERED`) y `origin` (`source`: el usuario cuyo estado cambió; `contact`: un contacto alcanzado por la cascada) | Usuarios cuyo estado subió |
| `circleguard_containment_duration_seconds` | `promotion` | `status` | Tiempo de un cambio de estado: cascada en Neo4j, Redis y eventos de Kafka. Buckets de 0,25, 0,5, 1, 2 y 5 s; el de 1 s es el objetivo NFR-1. No incluye el viaje del evento de entrada por Kafka |
| `circleguard_surveys_total` | `form` | `symptoms` (`true` o `false`) | Cuestionarios enviados |
| `circleguard_checkins_total` | `gateway` | `result` (`granted`, `denied_health_risk`, `denied_status_unavailable`, `invalid_token`) | Validaciones de QR en el acceso |

Los contadores nacen en cero, así que `rate()` y los paneles funcionan desde el arranque. Volver
a `ACTIVE` no cuenta como promoción. Un estado desconocido se informa como `OTHER` en el tiempo de
contención y tampoco cuenta, para que un valor enviado por la API no cree series nuevas.

Consultas de ejemplo:

```promql
sum by (status) (increase(circleguard_promotions_total[1h]))
histogram_quantile(0.95, sum by (le) (rate(circleguard_containment_duration_seconds_bucket[5m])))
sum by (result) (rate(circleguard_checkins_total[5m]))
```

### ServiceMonitor

El chart de la aplicación crea el ServiceMonitor `circleguard-services` en el namespace
`circleguard-<ambiente>`. Selecciona los Services con la etiqueta `circleguard.io/metrics: "true"`,
que el chart pone a los servicios con `spring: true` en `values.yaml`, y lee `/actuator/prometheus`
en su puerto `http` cada 30 s. La etiqueta `environment` sale de la etiqueta
`circleguard.io/environment` del Service. Prometheus lo descubre porque
`observability/prometheus/values.yaml` deja vacíos los selectores de ServiceMonitor y de namespace.

- **Requiere los CRD** de Prometheus Operator (`monitoring.coreos.com/v1`), que instala
  `kube-prometheus-stack`. En un clúster sin el stack el chart omite el recurso y no falla; si
  instalas el stack después, repite el `helm upgrade` de la aplicación.
- **Con `helm template`**, agrega `--api-versions monitoring.coreos.com/v1/ServiceMonitor` o el
  recurso no se genera.
- **Para quitarlo:** `--set metrics.serviceMonitor.enabled=false`. El intervalo y la ruta están en
  `metrics.serviceMonitor` de `values.yaml`.

### Verificar el scraping

```bash
kubectl -n circleguard-dev get servicemonitor circleguard-services

# Un servicio a mano (métricas JVM, HTTP y contadores propios):
kubectl -n circleguard-dev port-forward svc/promotion 8088:8088
curl -s http://localhost:8088/actuator/prometheus \
  | grep -E '^(jvm_memory_used_bytes|http_server_requests_seconds_count|circleguard_)'
```

En Prometheus (`http://<node_public_ip>/prometheus`, con Basic Auth), **Status → Targets** lista
un target por pod: deben aparecer al menos los 8 servicios en `UP`. La consulta `count(up{namespace="circleguard-dev"} == 1)`
da el total de pods que responden.

### Seguridad de las métricas

- Los pods no piden autenticación en `/actuator/prometheus`, porque Prometheus las lee dentro del
  clúster. `identity` exige JWT en casi todo, así que se agregó esa ruta a las que permite sin él.
- Las métricas no se publican: el Ingress solo expone el cliente web, y su nginx responde 404 a
  `/svc/<servicio>/actuator/*`, salvo `/actuator/health`. Ese cambio de `mobile/nginx.conf` llega
  con la próxima imagen del cliente web.
- Las etiquetas no llevan identificadores de personas: `anonymousId` no se usa como etiqueta.
- El stack de Compose no incluye Prometheus; el scraping es solo de los clústeres k3s.

## Dashboards (HU-40)

Los dashboards están versionados en `observability/grafana/dashboards/` y aparecen en Grafana con
la etiqueta `circleguard`:

| Dashboard | Qué muestra | Filtros |
|:---|:---|:---|
| **CircleGuard · Servicios** (`circleguard-services`) | Targets arriba, solicitudes por segundo, resultados, errores 5xx, latencia p95 (por servicio y por endpoint), CPU, memoria, reinicios, réplicas, heap, tiempo en GC, hilos, conexiones a PostgreSQL y lag de Kafka | Ambiente (namespace) y servicio, con selección múltiple |
| **CircleGuard · Negocio** (`circleguard-business`) | Promociones por minuto por estado y origen, contactos por caso, cuestionarios con y sin síntomas, check-ins por resultado y tiempo de contención (p50, p95, p99 y porcentaje bajo 1 s) | Ambiente |

Los paneles HTTP no cuentan `/actuator`, para que las sondas y el scraping no inflen el tráfico.
El tiempo de contención excluye los cambios a `ACTIVE` y los estados desconocidos (`OTHER`), así
que mide solo promociones. El panel del circuito de Redis sigue en el chart de la aplicación
(`deploy/k8s/circleguard/files/redis-health.json`).

### Cómo se aprovisionan

`observability/grafana/kustomization.yaml` convierte cada JSON en un ConfigMap del namespace
`monitoring` con la etiqueta `grafana_dashboard`. El sidecar de Grafana (activado en
`observability/prometheus/values.yaml`) carga esos ConfigMaps en segundos, sin reiniciar.

- **En cada despliegue**, `deployK8s` aplica la carpeta después de Helm. Si el clúster no tiene
  el namespace `monitoring`, se salta el paso; si falla, la etapa queda `UNSTABLE` y el
  despliegue no se revierte.
- **A mano**, con el kubeconfig del clúster:

  ```bash
  kubectl apply -k observability/grafana
  kubectl -n monitoring get configmap -l grafana_dashboard=1
  ```

Los dashboards usan la variable `datasource`, que toma el datasource de Prometheus por defecto
(el que crea `kube-prometheus-stack`). Si agregaste otro a mano, se puede elegir en el selector.

### Latencia p95

El p95 sale de `http_server_requests_seconds_bucket`. Los 8 servicios publican buckets de 25 ms,
50 ms, 100 ms, 250 ms, 500 ms, 1 s, 2 s y 5 s (`management.metrics.distribution.slo` en su
`application.yml`). Son pocos a propósito: el p95 es una aproximación entre dos buckets y cada
endpoint agrega solo 9 series. Las imágenes anteriores a este cambio no publican buckets, así que
el p95 queda vacío hasta desplegar la versión nueva.

### Cambiar un dashboard

Grafana no guarda cambios en un dashboard aprovisionado. Para modificarlo:

1. Edítalo en Grafana y usa **Save dashboard → Save JSON to file** (o **Export → Export as JSON**).
2. Reemplaza el archivo en `observability/grafana/dashboards/` sin cambiar el `uid`.
3. Déjalo con un panel por línea:

   ```bash
   python3 observability/grafana/format_dashboard.py observability/grafana/dashboards/*.json
   ```

   Un JSON exportado tiene más de mil líneas, y el control de tamaño del PR cuenta los `.json`.
   Con un panel por línea el archivo baja a unas 100 líneas y el diff muestra cada panel cambiado.
4. Haz el commit. El siguiente despliegue lo aplica; para verlo antes, usa `kubectl apply -k`.

Un dashboard nuevo necesita su JSON y una entrada en `configMapGenerator` de la kustomización.

## Alertas (HU-41)

Las reglas están en `observability/alerts/circleguard-rules.yaml` (un `PrometheusRule`).
Prometheus las evalúa cada 30 s y Alertmanager envía al canal de Discord del equipo las que llevan
`project=circleguard`. Las alertas propias del stack (nodo, kubelet, etc.) se ven en
`/alertmanager` pero no se envían.

La ruta y el receptor están en `observability/alerts/alertmanager-config.yaml`, un
`AlertmanagerConfig` que `observability/prometheus/values.yaml` usa como configuración completa de
Alertmanager (`alertmanagerConfiguration`). El operador lee la URL del webhook del Secret
`alertmanager-discord` y la escribe en la configuración que genera, así que nunca está en Git. No
se usa `webhook_url_file` en `alertmanager.config` porque el operador de esta versión lo rechaza.

| Alerta | Condición | Espera | Severidad |
|:---|:---|:---|:---|
| `ServiceDown` | Prometheus no puede leer `/actuator/prometheus` de un pod (`up == 0`) | 1 min | `critical` |
| `HighErrorRate` | Más del 5 % de respuestas 5xx en 5 min, con al menos 3 solicitudes por minuto | 2 min | `critical` |
| `HighLatencyP95` | p95 HTTP por encima de 1 s en 5 min, con al menos 3 solicitudes por minuto | 5 min | `warning` |
| `CircuitBreakerOpen` | Un circuit breaker de Resilience4j (hoy `redisHealth` del gateway) estuvo `open` o `half_open` en el último minuto | 1 min | `critical` |

Las tres alertas HTTP excluyen `/actuator`. El mínimo de tráfico evita que una sola solicitud fallida o lenta en
un servicio sin uso dispare la alerta. Mientras un servicio tiene una alerta `critical`, sus
`warning` no se envían. Alertmanager agrupa por alerta, namespace y servicio, repite cada 4 horas
si sigue activa y avisa cuando se resuelve.

### Configurar el canal de Discord

1. En Discord: **Editar canal → Integraciones → Webhooks → Nuevo webhook**, y copia la URL. Puede
   ser el mismo canal de las notificaciones de Jenkins.
2. Guarda la URL fuera del repo (por ejemplo en `~/.config/circleguard/alertmanager-discord-webhook`)
   y crea el Secret en cada clúster. La URL va por stdin, no queda en argv:

   ```bash
   ssh ubuntu@<node_public_ip> 'sudo ~/circleguard/infra/bootstrap/load-alertmanager-discord.sh' \
     < ~/.config/circleguard/alertmanager-discord-webhook
   ```

   Si el nodo no tiene el repo en `~/circleguard`, copia antes el script y `infra/bootstrap/lib/`.
3. Aplica las reglas y el `AlertmanagerConfig`. `deployK8s` lo hace en cada despliegue; a mano:

   ```bash
   kubectl apply -k observability/alerts
   kubectl -n monitoring get prometheusrule circleguard-alerts
   kubectl -n monitoring get alertmanagerconfig circleguard
   ```

   En Prometheus, **Alerts** debe listar las cuatro reglas en `Inactive`.
4. Aplica Terraform en el ambiente (o corre `install-observability.sh`) para que Alertmanager use
   el `AlertmanagerConfig`. Hazlo después del paso 3: si el objeto o el Secret no existen, el
   operador no actualiza Alertmanager y lo registra en su log.
5. Comprueba que el operador generó la configuración:

   ```bash
   kubectl -n monitoring get secret alertmanager-kube-prometheus-stack-alertmanager-generated \
     -o jsonpath='{.data.alertmanager\.yaml\.gz}' | base64 -d | gunzip | grep -c discord_configs
   kubectl -n monitoring logs deploy/kube-prometheus-stack-operator --since=10m | grep -i error
   ```

   El primero debe dar `1` y el segundo no debe mostrar errores de `alertmanager`.

Volver a correr el script cambia la URL; el operador regenera la configuración en uno o dos
minutos, sin reiniciar Alertmanager.

### Probar el canal

Antes de provocar las alertas, comprueba que Discord recibe algo. Esta alerta manual llega en
unos 30 s y se resuelve sola a los 5 minutos:

```bash
curl -u monitoring:<password> -H 'Content-Type: application/json' \
  http://<node_public_ip>/alertmanager/api/v2/alerts -d '[{
    "labels": {"alertname": "PruebaDelCanal", "project": "circleguard",
               "severity": "warning", "namespace": "circleguard-dev"},
    "annotations": {"summary": "Prueba del canal de alertas",
                    "description": "Enviada a mano para comprobar el webhook."}}]'
```

Si no llega, revisa `kubectl -n monitoring logs alertmanager-kube-prometheus-stack-alertmanager-0 -c alertmanager`.

### Provocar cada alerta

Hazlo en dev o en stage, con una versión desplegada que incluya HU-39 y HU-40 (métricas y
buckets). Los ejemplos usan `circleguard-dev`; cambia el namespace para stage. Los
comandos van en el nodo, con `sudo k3s kubectl` o con tu kubeconfig. Cada prueba deja dos
mensajes en Discord: `FIRING` y, al revertir, `RESOLVED`.

**ServiceDown.** Quita el endpoint de métricas a `file`. El pod sigue sano, pero
`/actuator/prometheus` responde 404, y al minuto se dispara la alerta:

```bash
kubectl -n circleguard-dev set env deployment/file MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health
# Espera unos 3 minutos (nuevo pod + 1 minuto). Para revertir:
kubectl -n circleguard-dev set env deployment/file MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE-
```

**HighErrorRate.** Apaga Neo4j y genera tráfico a un endpoint de `promotion` que lo usa. Cada
solicitud responde 500:

```bash
kubectl -n circleguard-dev scale statefulset/neo4j --replicas=0
for i in $(seq 1 600); do
  curl -s -o /dev/null -w '%{http_code}\n' http://<node_public_ip>/svc/promotion/api/v1/circles/user/alert-test
  sleep 0.5
done | sort | uniq -c
kubectl -n circleguard-dev scale statefulset/neo4j --replicas=1
```

La alerta llega a los 2 o 3 minutos; el bucle dura 5. `promotion` sigue listo porque su
readiness no depende de Neo4j.

**HighLatencyP95.** Agrega 700 ms de retraso a la red de Neo4j, en el nodo, y genera el mismo
tráfico. Cada consulta hace al menos dos idas y vueltas, así que las respuestas pasan de 1 s sin
fallar:

```bash
CID=$(sudo k3s crictl ps --name neo4j -q)
PID=$(sudo k3s crictl inspect --output go-template --template '{{.info.pid}}' "$CID")
sudo modprobe sch_netem   # si falla: sudo apt-get install -y linux-modules-extra-$(uname -r)
sudo nsenter -t "$PID" -n tc qdisc add dev eth0 root netem delay 700ms

for i in $(seq 1 960); do
  curl -s -o /dev/null -w '%{time_total}\n' http://<node_public_ip>/svc/promotion/api/v1/circles/user/alert-test
  sleep 0.5
done | sort -n | tail -n 3

sudo nsenter -t "$PID" -n tc qdisc del dev eth0 root
```

La alerta llega a los 6 o 7 minutos (5 de espera más la ventana de 5 minutos); el bucle dura
unos 8. Si Neo4j se reinicia durante la prueba, el retraso desaparece con el contenedor viejo:
vuelve a calcular `CID` y `PID`.

**CircuitBreakerOpen.** Apaga Redis y envía check-ins al gateway con un token QR válido (el
cliente web lo genera en `/api/v1/auth/qr/generate`). Cada respuesta es `Access Denied: Health
status unavailable`, el circuito `redisHealth` abre y la alerta llega a los 2 o 3 minutos:

```bash
kubectl -n circleguard-dev scale deployment/redis --replicas=0
for i in $(seq 1 360); do
  curl -s -X POST -H 'Content-Type: application/json' -d "{\"token\":\"$QR_TOKEN\"}" \
    http://<node_public_ip>/svc/gateway/api/v1/gate/validate; echo
  sleep 0.5
done | sort | uniq -c
kubectl -n circleguard-dev scale deployment/redis --replicas=1
```

Después de restaurar Redis, envía unos check-ins más: un circuito abierto solo pasa a
`half_open` cuando recibe una llamada, y cada pod del gateway tiene el suyo.

### Evidencia

La prueba del 22 de noviembre de 2026 en stage, con la línea de tiempo, los comandos y el estado de
Alertmanager, está en [`evidencias/hu-41/`](evidencias/hu-41/README.md).

Guarda en `docs/operaciones/evidencias/hu-41/` una captura del mensaje `FIRING` y otra del
`RESOLVED` de cada alerta, más la vista **Alerts** de Prometheus con la regla en `Firing`. Usa
nombres como `servicedown-firing.png` y `servicedown-resolved.png`, y anota la fecha y el ambiente
de cada prueba en el PR.

## Problemas comunes

| Síntoma | Causa | Solución |
|:---|:---|:---|
| `terraform apply` falla con `dial tcp ...:22: i/o timeout` | El clúster está apagado o la IP no está autorizada | `hosts.sh start k3s`, `hosts.sh allow` |
| `Permission denied (publickey)` | La llave no coincide con el key pair | Usa `circleguard-admin-key.pem` o `ssh-add` |
| `helm` falla al conectar al API | k3s aún no terminó de arrancar | El script espera hasta 5 minutos; revisa `sudo k3s kubectl get nodes` |
| Prometheus sin targets | El chart se desplegó antes que el stack (sin CRD) o con `metrics.serviceMonitor.enabled=false` | Repite el `helm upgrade` de la aplicación y comprueba `kubectl -n circleguard-<ambiente> get servicemonitor` |
| Los dashboards no aparecen en Grafana | El ConfigMap no existe o no tiene la etiqueta `grafana_dashboard` | `kubectl apply -k observability/grafana` y revisa `kubectl -n monitoring logs deploy/kube-prometheus-stack-grafana -c grafana-sc-dashboard` |
| Latencia p95 vacía | La imagen del servicio es anterior a los buckets de HU-40 | Despliega una versión que incluya `management.metrics.distribution.slo` |
| Las alertas se disparan pero no llegan a Discord | Falta el Secret `alertmanager-discord`, la URL es inválida o falta el `AlertmanagerConfig` | Corre `load-alertmanager-discord.sh`, `kubectl apply -k observability/alerts` y revisa `kubectl -n monitoring logs deploy/kube-prometheus-stack-operator` |
| Llegan alertas `ServiceDown` de todos los servicios | Las imágenes desplegadas son anteriores a HU-39 y no exponen `/actuator/prometheus` | Despliega una versión que incluya HU-39 |
| Un servicio aparece `DOWN` con `401` | Su seguridad exige autenticación en `/actuator/prometheus` | Permite esa ruta en su `SecurityConfig` (como en `identity`) |
| Pods en `Pending` o `OOMKilled` | El nodo de 8 GB quedó corto | Baja retención y límites, o desactiva componentes que no uses |

## Seguridad

- No versiones la contraseña de Grafana, la de Basic Auth ni la llave SSH. Usa `TF_VAR_`,
  `load-monitoring-auth.sh` y credenciales de Jenkins.
- Grafana se publica por el Ingress en `/grafana`; el puerto 3000 no se expone.
- Prometheus y Alertmanager se publican en `/prometheus` y `/alertmanager` detrás de Basic Auth;
  sin credenciales responden 401. El TLS es HU-47.
- El state de Terraform está cifrado en S3 y bloqueado en DynamoDB; no lo descargues al repo.

## Validación

```bash
terraform fmt -check -recursive infra/terraform
for e in dev stage prod; do
  ( cd "infra/terraform/envs/$e" && \
    terraform init -backend=false -input=false >/dev/null && \
    terraform validate ) || exit 1
done
bash -n infra/bootstrap/install-observability.sh
```

No versiones llaves, contraseñas, kubeconfig ni planes de Terraform.
