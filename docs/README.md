# Documentación técnica de CircleGuard

Punto de entrada para entender y auditar la solución. Cada tema remite a un documento; los
diagramas están en Mermaid, que GitHub muestra directamente. La documentación se comparó con los
clústeres, los estados de Terraform y AWS el 22 de noviembre de 2026, con la versión `0.7.0`
desplegada ([revisión cruzada](arquitectura/coherencia.md)).

## Por tema

| Tema | Documento | Qué responde |
|:---|:---|:---|
| Arquitectura | [`arquitectura/README.md`](arquitectura/README.md) | Componentes, despliegue en AWS, interior de un clúster y flujo de entrega, con diagramas |
| Infraestructura como código | [`arquitectura/infraestructura.md`](arquitectura/infraestructura.md) | Qué declara Terraform, qué crean los scripts y qué hay en cada estado |
| Kubernetes | [`arquitectura/kubernetes.md`](arquitectura/kubernetes.md) | Qué corre en cada clúster, de qué release sale y qué está en `dev` sin desplegar |
| Patrones | [`patrones/existentes.md`](patrones/existentes.md) | Catálogo de 17 patrones con evidencia en el código, y los tres de la Parte 2 |
| Observabilidad | [`arquitectura/observabilidad.md`](arquitectura/observabilidad.md) | Métricas, logs, trazas, paneles y alertas |
| Seguridad | [`arquitectura/seguridad.md`](arquitectura/seguridad.md) | Red, acceso, secretos, cargas de trabajo, cadena de suministro y brechas |
| Service mesh | [`arquitectura/service-mesh.md`](arquitectura/service-mesh.md) | Linkerd con mTLS entre servicios y canary de `gateway` con Flagger (en `dev`) |
| Análisis de pruebas | [`operaciones/informe-pruebas.md`](operaciones/informe-pruebas.md) | Cobertura y quality gate, integración, E2E, carga, ZAP y Trivy, con su análisis |
| Coherencia con lo desplegado | [`arquitectura/coherencia.md`](arquitectura/coherencia.md) | Matriz componente por componente y cómo repetirla |

## Patrones de la Parte 2

| Patrón | Documento |
|:---|:---|
| Circuit breaker y retry (Resilience4j) | [`patrones/resiliencia.md`](patrones/resiliencia.md) |
| Configuración externa y feature toggle | [`patrones/configuracion.md`](patrones/configuracion.md) |
| Transactional outbox | [`patrones/outbox.md`](patrones/outbox.md) |
| Logs JSON | [`patrones/logs.md`](patrones/logs.md) |
| Trazas distribuidas por Kafka | [`patrones/trazas.md`](patrones/trazas.md) |

## Operación

| Documento | Para qué |
|:---|:---|
| [`operaciones/README.md`](operaciones/README.md) | Accesos, reproducción desde cero y despliegues |
| [`operaciones/manual.md`](operaciones/manual.md) | Incidentes, alertas, rollback, rotación de secretos y respaldos |
| [`operaciones/jenkins-pipeline.md`](operaciones/jenkins-pipeline.md) | Etapas del pipeline, controlador y workers |
| [`operaciones/observability.md`](operaciones/observability.md) | Instalar, verificar y probar el monitoreo |
| [`operaciones/rollback.md`](operaciones/rollback.md) | Plan de rollback en Kubernetes |
| [`operaciones/tls.md`](operaciones/tls.md) | TLS con cert-manager (entra con `0.8.0`) |
| [`operaciones/secretos.md`](operaciones/secretos.md) | Escaneo del historial y Sealed Secrets |
| [`operaciones/terraform-state-lock.md`](operaciones/terraform-state-lock.md) | Bloqueo del estado de Terraform |

## Gestión ágil

| Documento | Para qué |
|:---|:---|
| [`agil/sprints-0-y-1.md`](agil/sprints-0-y-1.md) | Planeación, review y retrospectiva de los sprints 0 y 1 |
| [`agil/sprint-2.md`](agil/sprint-2.md) | Planeación, review, retrospectiva, burndown y comparación con el Sprint 1 |
| [`agil/branching.md`](agil/branching.md) | Estrategia de ramas |

## Releases y costos

| Documento | Para qué |
|:---|:---|
| [`release-notes/etiquetado.md`](release-notes/etiquetado.md) | Cómo se calcula cada versión y cómo se etiquetan las imágenes |
| [`release-notes/politica-cambios.md`](release-notes/politica-cambios.md) | Tipos de cambio y quién aprueba cada ambiente |
| [`release-notes/trazabilidad.md`](release-notes/trazabilidad.md) | Release notes por versión y registro de despliegues por ambiente |
| [`costos/README.md`](costos/README.md) | Estimación y costo real de la infraestructura |
| [`executive-summary.md`](executive-summary.md) | Resumen ejecutivo en inglés |

## Documentación junto al código

| Ruta | Contenido |
|:---|:---|
| [`infra/terraform/README.md`](../infra/terraform/README.md) | Uso de Terraform y un README por módulo |
| [`deploy/k8s/README.md`](../deploy/k8s/README.md) | Chart de Helm, secretos, sondas y despliegue a mano |
| [`observability/README.md`](../observability/README.md) | Valores, paneles y reglas versionados |
| [`infra/security/security-groups.md`](../infra/security/security-groups.md) | Puertos expuestos y desde dónde |
| [`infra/cloud/aws/README.md`](../infra/cloud/aws/README.md) | Hosts de CI y operación diaria en AWS |
| [`tests/e2e-playwright/README.md`](../tests/e2e-playwright/README.md) | Escenarios E2E y cómo correrlos |

Los PDFs de `arquitectura/` y las imágenes de la raíz de `docs/` (bocetos de UX y fundamentos
visuales) son de la Parte 1.
