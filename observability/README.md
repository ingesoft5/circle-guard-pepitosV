# Observabilidad en el clúster

Contenido versionado del stack que corre dentro de cada clúster k3s. La
instalación la hace el módulo Terraform `infra/terraform/modules/observability`
(HU-26); la configuración de métricas, paneles y alertas es de las historias
HU-39, HU-40 y HU-41.

```text
observability/
├── prometheus/
│   └── values.yaml     # valores base de kube-prometheus-stack (HU-26)
├── grafana/            # dashboards JSON versionados y aprovisionados (HU-40)
└── alerts/             # reglas de Alertmanager (HU-41)
```

## Qué instala HU-26

`kube-prometheus-stack` en el namespace `monitoring` de cada ambiente:
Prometheus, Grafana, Alertmanager, node-exporter y kube-state-metrics. Los
paneles y las reglas de alerta quedan vacíos a propósito para que HU-40 y
HU-41 los agreguen como código.

## Qué queda para HU-39, HU-40 y HU-41

- **HU-39:** dependencia `micrometer-registry-prometheus` en los 8 servicios,
  endpoint `/actuator/prometheus`, contadores de negocio y ServiceMonitors.
- **HU-40:** dashboards JSON en `observability/grafana/` y su ConfigMap.
- **HU-41:** reglas en `observability/alerts/` y la notificación de Alertmanager.

La guía de operación y verificación está en
[`docs/operaciones/observability.md`](../docs/operaciones/observability.md).
