# Módulo `observability`

Observabilidad del lado AWS para un ambiente: un log group de CloudWatch por servicio, alarmas del nodo del clúster y un tema SNS para notificaciones. El stack dentro del clúster (Prometheus, Grafana, Loki) se instala con Helm en sus propias historias; este módulo declara solo lo que Terraform puede poseer sin duplicarlo.

## Qué crea

- `aws_cloudwatch_log_group` por servicio, con nombre `/circleguard/<environment>/<servicio>` y retención configurable.
- `aws_sns_topic` de alertas y una suscripción por email opcional.
- `aws_cloudwatch_metric_alarm` para CPU alta del nodo (por encima de `cpu_alarm_threshold`).
- `aws_cloudwatch_metric_alarm` para `StatusCheckFailed` de EC2.

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

## Outputs

| Output | Uso |
|:---|:---|
| `log_group_names` | Nombre del log group por servicio. |
| `alarm_topic_arn` | Tema SNS que recibe las alarmas. |
| `cpu_alarm_name` | Nombre de la alarma de CPU. |
| `status_check_alarm_name` | Nombre de la alarma de estado de EC2. |

## Separación de responsabilidades

- Este módulo cubre lo que vive en AWS (retención de logs, alarmas de la instancia, notificaciones).
- Las métricas de Micrometer/Prometheus (HU-39), los dashboards de Grafana (HU-40) y las alertas de Alertmanager (HU-41) se despliegan dentro del clúster y consumen el `instance_id` que expone el módulo `cluster`.
- Las etiquetas `Project`, `Environment` y `ManagedBy` las aplica `default_tags` del provider en el root.
