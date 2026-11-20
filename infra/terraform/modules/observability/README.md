# Módulo `observability`

Observabilidad de un ambiente: por un lado, lo que vive en AWS (log group de CloudWatch por servicio, alarmas del nodo y un tema SNS); por otro, la instalación de `kube-prometheus-stack` dentro del clúster k3s (Prometheus, Grafana, Alertmanager, node-exporter y kube-state-metrics). La configuración de métricas, los dashboards y las reglas de alerta son de HU-39, HU-40 y HU-41.

## Qué crea

- `aws_cloudwatch_log_group` por servicio, con nombre `/circleguard/<environment>/<servicio>` y retención configurable.
- `aws_sns_topic` de alertas y una suscripción por email opcional.
- `aws_cloudwatch_metric_alarm` para CPU alta del nodo (por encima de `cpu_alarm_threshold`).
- `aws_cloudwatch_metric_alarm` para `StatusCheckFailed` de EC2.
- `terraform_data.prometheus`: sube `observability/prometheus/values.yaml` y `infra/bootstrap/install-observability.sh` al nodo y ejecuta `helm upgrade --install` de `kube-prometheus-stack` en el namespace configurado.

## Variables

| Variable | Tipo | Por defecto | Uso |
|:---|:---|:---|:---|
| `environment` | string | — | `dev`, `stage` o `prod`. |
| `name_prefix` | string | — | Prefijo de alarmas y topic. |
| `service_names` | list(string) | — | Servicios con log group propio. |
| `log_retention_days` | number | `14` | Retención de CloudWatch. |
| `alarm_email` | string | `""` | Correo suscrito; vacío desactiva la suscripción. |
| `cpu_alarm_threshold` | number | `80` | Umbral de CPU (%). |
| `instance_id` | string | — | Instancia EC2 del clúster a monitorear. |
| `prometheus_ssh_host` | string | — | Host de la conexión SSH: IP pública desde un portátil, IP privada desde Jenkins. |
| `install_prometheus` | bool | `true` | Instala `kube-prometheus-stack` en el clúster. |
| `prometheus_namespace` | string | `monitoring` | Namespace del stack. |
| `prometheus_chart_version` | string | `65.5.1` | Versión fija del chart. |
| `prometheus_grafana_admin_password` | string (sensitive) | `""` | Contraseña de Grafana; se pasa con `TF_VAR_`, nunca en Git. |
| `prometheus_ssh_private_key` | string (sensitive) | `""` | Llave para SSH; vacía usa el agente SSH. |

## Outputs

| Output | Uso |
|:---|:---|
| `log_group_names` | Nombre del log group por servicio. |
| `alarm_topic_arn` | Tema SNS que recibe las alarmas. |
| `cpu_alarm_name` | Nombre de la alarma de CPU. |
| `status_check_alarm_name` | Nombre de la alarma de estado de EC2. |
| `monitoring_namespace` | Namespace de `kube-prometheus-stack`, o `null` si no se instaló. |

## Separación de responsabilidades

- Este módulo cubre lo que vive en AWS (retención de logs, alarmas de la instancia, notificaciones) y deja instalado el stack base de observabilidad en el clúster (HU-26).
- HU-39 agrega `micrometer-registry-prometheus`, `/actuator/prometheus`, los contadores de negocio y los ServiceMonitors que Prometheus descubre.
- HU-40 versiona los dashboards JSON en `observability/grafana/` y los aprovisiona como ConfigMaps.
- HU-41 versiona las reglas de Alertmanager en `observability/alerts/`.
- Las etiquetas `Project`, `Environment` y `ManagedBy` las aplica `default_tags` del provider en el root.

## Requisitos de aplicación

- Credenciales AWS con permisos sobre S3/DynamoDB del estado y EC2.
- El clúster encendido y accesible por SSH (`22`) desde donde se aplica. Desde un portátil se usa la IP pública (y la IP propia debe autorizarse con `infra/cloud/aws/hosts.sh allow`); desde Jenkins se usa la IP privada (`TF_VAR_prometheus_ssh_host`).
- La llave privada de `circleguard-admin-key` en `TF_VAR_prometheus_ssh_private_key` o en el agente SSH.
- El namespace `monitoring` se crea con `--create-namespace`; coordinar con HU-29 para no duplicar namespaces por ambiente.

El `terraform_data` es idempotente: `helm upgrade --install` se puede repetir. Si el nodo, la versión del chart o los valores cambian, Terraform vuelve a ejecutar la instalación. Un cambio solo de contraseña de Grafana no dispara el recurso; se cambia con `helm upgrade` o editando el secreto del namespace.
