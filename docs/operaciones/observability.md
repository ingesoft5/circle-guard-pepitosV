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
| Reglas de Alertmanager | `observability/alerts/` (HU-41) |
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
`deploy/k8s/README.md`). En Prometheus, la página de *targets* muestra los 8 servicios con la
etiqueta `environment`; pasan a `UP` cuando cada servicio expone `/actuator/prometheus` (HU-39). En Grafana, el datasource de Prometheus queda configurado por el chart.

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

## Problemas comunes

| Síntoma | Causa | Solución |
|:---|:---|:---|
| `terraform apply` falla con `dial tcp ...:22: i/o timeout` | El clúster está apagado o la IP no está autorizada | `hosts.sh start k3s`, `hosts.sh allow` |
| `Permission denied (publickey)` | La llave no coincide con el key pair | Usa `circleguard-admin-key.pem` o `ssh-add` |
| `helm` falla al conectar al API | k3s aún no terminó de arrancar | El script espera hasta 5 minutos; revisa `sudo k3s kubectl get nodes` |
| Prometheus sin targets | El chart se desplegó antes que el stack (sin CRD) o con `metrics.serviceMonitor.enabled=false` | Repite el `helm upgrade` de la aplicación y comprueba `kubectl -n circleguard-<ambiente> get servicemonitor` |
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
