# Observabilidad en el clúster

Contenido versionado del stack que corre dentro de cada clúster k3s. La
instalación la hace el módulo Terraform `infra/terraform/modules/observability`
(HU-26); la configuración de métricas, paneles y alertas es de las historias
HU-39, HU-40 y HU-41.

```text
observability/
├── prometheus/
│   └── values.yaml     # valores base de kube-prometheus-stack (HU-26)
├── ingress/
│   └── basic-auth-middleware.yaml  # BasicAuth de Prometheus y Alertmanager
├── loki/
│   └── values.yaml     # Loki de un solo binario
├── promtail/
│   └── values.yaml     # envío de logs de los pods a Loki
├── grafana/
│   └── datasources.yaml  # Loki y el enlace de una línea al trace de Jaeger
└── alerts/             # reglas de Alertmanager (HU-41)
```

## Qué instala HU-26

`kube-prometheus-stack` en el namespace `monitoring` de cada ambiente:
Prometheus, Grafana, Alertmanager, node-exporter y kube-state-metrics. Los
paneles y las reglas de alerta quedan vacíos a propósito para que HU-40 y
HU-41 los agreguen como código. El mismo módulo instala Loki y Promtail y
aplica el datasource que abre el `traceId` de una línea en Jaeger.

Grafana se publica en `http://<ip>/grafana` con su login. Prometheus y
Alertmanager se publican en `/prometheus` y `/alertmanager` detrás del middleware
`monitoring-basic-auth`; el secreto se crea fuera de Git con
`infra/bootstrap/load-monitoring-auth.sh`. El TLS es HU-47.

## Qué queda para HU-40 y HU-41

- **HU-40:** dashboards JSON en `observability/grafana/` y su ConfigMap.
- **HU-41:** reglas en `observability/alerts/` y la notificación de Alertmanager.

La guía de operación y verificación está en
[`docs/operaciones/observability.md`](../docs/operaciones/observability.md).
