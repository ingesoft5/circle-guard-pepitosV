# Kubernetes

Qué corre en cada clúster y de dónde sale. El chart y su uso están en
[`deploy/k8s/README.md`](../../deploy/k8s/README.md). Inventario del 22 de noviembre de 2026 con
`kubectl get` y `helm list` en los tres nodos; los tres tienen los mismos recursos y la versión
`0.7.0` de la aplicación.

## Clúster

| | Valor |
|:---|:---|
| Distribución | k3s `v1.30.6+k3s1`, un nodo `control-plane` por ambiente |
| Sistema | Ubuntu 24.04.5 LTS, containerd 1.7.22 |
| Red de Services | `10.43.0.0/16` (por defecto de k3s) |
| Ingress | Traefik 2.11.10, incluido en k3s, Service `LoadBalancer` de klipper-lb en 80 y 443 |
| Almacenamiento | `local-path` (disco del nodo) |
| Métricas de recursos | metrics-server de k3s, que usa el HPA |
| Imágenes de la aplicación | Nexus en `172.31.3.197:5000` (IP privada de `tools`, por el peering) |

## Namespaces y releases

| Namespace | Contenido | Lo instala |
|:---|:---|:---|
| `circleguard-<env>` | Aplicación y middleware | Release `circleguard` (chart `circleguard-0.1.0`), `deployK8s` en Jenkins |
| `monitoring` | Prometheus, Grafana, Alertmanager, Loki, Promtail | Releases `kube-prometheus-stack` 65.5.1, `loki` 6.55.0 y `promtail` 6.17.1, Terraform (`modules/observability`) |
| `kube-system` | Traefik, CoreDNS, metrics-server, local-path, Sealed Secrets | k3s, salvo `sealed-secrets-controller` 0.41.2 (`deploy/k8s/scripts/install-sealed-secrets.sh`) |

## Aplicación: `circleguard-<env>`

Cada componente se declara una vez en `values.yaml`; `templates/workloads.yaml` genera su
ConfigMap `<componente>-config`, su Deployment o StatefulSet y su Service `ClusterIP`.

| Componente | Tipo | Imagen | Puerto | Readiness | Estado o escalado |
|:---|:---|:---|---:|:---|:---|
| `auth` | Deployment | `auth-service:0.7.0` | 8180 | `readinessState,db` | 1 réplica |
| `identity` | Deployment | `identity-service:0.7.0` | 8083 | `readinessState,db` | 1 réplica |
| `promotion` | Deployment | `promotion-service:0.7.0` | 8088 | `readinessState,db,redis` | 1 réplica |
| `form` | Deployment | `form-service:0.7.0` | 8086 | `readinessState,db` | HPA 1–3 (prod 2–4), CPU 70 % |
| `gateway` | Deployment | `gateway-service:0.7.0` | 8087 | `readinessState` | HPA 1–3 (prod 2–4), CPU 70 % |
| `notification` | Deployment | `notification-service:0.7.0` | 8082 | `readinessState` | 1 réplica |
| `dashboard` | Deployment | `dashboard-service:0.7.0` | 8084 | `readinessState,db` | 1 réplica |
| `file` | Deployment | `file-service:0.7.0` | 8085 | `readinessState` | 1 réplica; archivos en `emptyDir` (S3 desde la próxima versión) |
| `web` | Deployment | `web-client:0.7.0` | 8080 | `/healthz` | 1 réplica |
| `postgres` | StatefulSet | `postgres:16` | 5432 | `pg_isready` | PVC 2 Gi (stage 3 Gi, prod 5 Gi) |
| `neo4j` | StatefulSet | `neo4j:5.26` | 7687 | TCP | PVC 2 Gi (prod 3 Gi) |
| `kafka` | Deployment | `confluentinc/cp-kafka:7.6.0` | 9092 | TCP | Sin disco persistente |
| `zookeeper` | Deployment | `confluentinc/cp-zookeeper:7.6.0` | 2181 | TCP | Sin disco persistente |
| `redis` | Deployment | `redis:7.2` | 6379 | `redis-cli ping` | Sin persistencia (`--save ""`) |
| `openldap` | Deployment | `osixia/openldap:1.5.0` | 389 | `ldapsearch` | Sin disco persistente |
| `jaeger` | Deployment | `jaegertracing/all-in-one:1.57` | 4318, 16686 | TCP | Trazas en memoria (2000) |

En total, 16 componentes. En prod corren 18 pods, porque `form` y `gateway` tienen 2 réplicas
mínimas. Los servicios Spring usan `startupProbe` y `livenessProbe` sobre
`/actuator/health/liveness` y `readinessProbe` sobre `/actuator/health/readiness`. El grupo de
readiness de cada uno dice qué dependencias lo sacan del Service sin reiniciarlo. Requests
`100m`/`256Mi` y límite de `768Mi` por servicio.

### Configuración y secretos

| Objeto | Qué lleva |
|:---|:---|
| `<componente>-config` (16 ConfigMaps) | URLs de bases, Kafka, Redis, LDAP, OTLP, CORS y parámetros como `QR_EXPIRATION` |
| `postgres-init-sql` | Crea una base por servicio (`files/init-db.sql`) |
| `circleguard-redis-health` | Panel de Grafana del circuito `redisHealth` (lo recoge el sidecar de Grafana) |
| SealedSecret `circleguard-secrets` | Credenciales de PostgreSQL, Neo4j, Redis, LDAP, JWT, QR y la bóveda. El controller crea el Secret del mismo nombre |

Un cambio en un ConfigMap reinicia su componente (anotación `checksum/config`). El Secret se
genera con `deploy/k8s/scripts/apply-secrets.sh`, que cifra con `kubeseal`; el texto plano no entra
al repositorio ni al chart.

### Entrada

El Ingress `circleguard` (clase `traefik`, sin host, puerto 80) envía `/` al Service `web`. El nginx
del cliente web sirve la aplicación y reenvía `/svc/<servicio>/` a `auth`, `identity`, `promotion`,
`form` y `gateway` (y a `file` desde la próxima versión: en la `0.7.0` su upstream apunta al nombre de
Compose y no resuelve). Ningún Service usa `NodePort`. Jaeger no se publica: su interfaz se abre con
`kubectl port-forward svc/jaeger 16686:16686`.

### Métricas

El ServiceMonitor `circleguard-services` lee `/actuator/prometheus` de los ocho servicios Spring
cada 30 s y agrega la etiqueta `environment`.

## Monitoreo: `monitoring`

| Componente | Tipo | Publicado |
|:---|:---|:---|
| Prometheus 2.55.0 | StatefulSet (operator) | `/prometheus`, Basic Auth |
| Alertmanager 0.27.0 | StatefulSet (operator) | `/alertmanager`, Basic Auth |
| Grafana 11.2.2 | Deployment | `/grafana`, login propio |
| prometheus-operator 0.77.2, kube-state-metrics 2.13.0 | Deployment | No |
| node-exporter 1.8.2 | DaemonSet | No |
| Loki 3.6.7 | StatefulSet, PVC 5 Gi | No |
| loki-canary | DaemonSet (del chart de Loki) | No |
| Promtail 3.5.1 | DaemonSet | No |

Objetos propios del proyecto en ese namespace: el PrometheusRule `circleguard-alerts`, el
AlertmanagerConfig `circleguard`, los ConfigMaps `circleguard-dashboard-services`,
`circleguard-dashboard-business` y `grafana-loki-jaeger`, el Middleware de Traefik `basic-auth` y
los Secrets `monitoring-basic-auth` y `alertmanager-discord`. Lo cuenta
[`observabilidad.md`](observabilidad.md).

## En `dev`, todavía sin desplegar

La rama `dev` ya trae cambios que entran con la próxima versión (`0.8.0`). La `0.7.0` que corre hoy
no los tiene. Por eso estos objetos no aparecen en ningún clúster:

| Cambio | Archivos | Qué agrega al desplegarse |
|:---|:---|:---|
| TLS (HU-47) | `deploy/k8s/scripts/install-cert-manager.sh`, `deploy/k8s/cert-manager/issuers.yaml`, `templates/certificate.yaml`, `templates/ingress.yaml`, `observability/tls/` | cert-manager 1.18.6, `ClusterIssuer` `circleguard-ca`, Certificate `circleguard-tls`, Ingress `circleguard-http` con redirección a HTTPS, Middleware `https-redirect`, certificado `monitoring-tls` |
| RBAC del namespace | `templates/rbac.yaml`, `deploy/k8s/scripts/check-rbac.sh` | ServiceAccounts, Roles y RoleBindings `circleguard-deployer` y `circleguard-viewer` |
| Archivos en S3 | `services/circleguard-file-service`, `values-<env>.yaml`, `modules/cluster` | `file-config` con `CIRCLEGUARD_STORAGE_S3_BUCKET`; `web-config` con `FILE_UPSTREAM`, para que `/svc/file/` llegue al Service `file` |

`deployK8s` instala cert-manager antes del `helm upgrade`, así que el primer despliegue de la `0.8.0`
deja estos objetos en cada ambiente sin pasos manuales. Detalle en
[`../operaciones/tls.md`](../operaciones/tls.md).

## Comprobarlo

```bash
sudo k3s kubectl get deploy,statefulset,daemonset -A
sudo env KUBECONFIG=/etc/rancher/k3s/k3s.yaml helm list -A
sudo k3s kubectl -n circleguard-<env> get pods,svc,ingress,hpa,pvc
```

El inventario completo de los tres nodos se saca con `inventario-k8s.sh`, como se describe en
[`coherencia.md`](coherencia.md#cómo-repetir-la-revisión).
