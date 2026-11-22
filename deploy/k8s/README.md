# Despliegue en Kubernetes

Chart de Helm que despliega CircleGuard completo en un ambiente: los 8 microservicios, el
cliente web y el middleware (PostgreSQL, Neo4j, Zookeeper, Kafka, Redis y OpenLDAP). Cubre
HU-29 (despliegue por namespace), HU-30 (ConfigMaps, Secrets e Ingress) y HU-31 (sondas y HPA).

```text
deploy/k8s/
├── circleguard/
│   ├── Chart.yaml
│   ├── values.yaml            # base común: servicios, middleware, sondas, recursos, HPA
│   ├── values-dev.yaml        # solo lo que cambia por ambiente
│   ├── values-stage.yaml
│   ├── values-prod.yaml
│   ├── files/init-db.sql      # bases de cada servicio (igual que en Compose)
│   └── templates/
│       ├── workloads.yaml     # ConfigMap + Deployment/StatefulSet + Service por componente
│       ├── hpa.yaml
│       ├── servicemonitor.yaml  # scraping de /actuator/prometheus (HU-39)
│       └── ingress.yaml
└── scripts/apply-secrets.sh   # crea el Secret del ambiente sin versionar valores
```

## Cómo está organizado

- **Un clúster k3s por ambiente** (Terraform, HU-27) y, dentro de cada uno, el namespace
  `circleguard-<env>`. La aplicación de dev corre en `circleguard-dev` del clúster `k3s-dev`, y
  así con stage y prod. El monitoreo (HU-26) vive aparte, en `monitoring`.
- **Una sola definición:** cada componente se describe una vez en `values.yaml` y las
  plantillas generan sus recursos. Los `values-<env>.yaml` solo cambian tamaños de disco y
  réplicas mínimas del HPA; no copian recursos.
- **Misma imagen en todos los ambientes:** la versión llega con `--set image.tag=<versión>`.
  Entre dev y prod solo cambian los ConfigMaps y el Secret.
- **Nada expuesto con NodePort:** todos los Services son `ClusterIP`. El tráfico entra por el
  Ingress (Traefik, incluido en k3s) al cliente web, cuyo nginx reenvía `/svc/<servicio>/` a
  cada microservicio.

## Configuración y secretos (HU-30)

- **ConfigMap `<componente>-config`:** variables no sensibles (URLs de bases, Kafka, Redis,
  LDAP, CORS, grupos de salud). Un cambio en el ConfigMap reinicia los pods del componente
  (anotación `checksum/config`).
- **Secret `circleguard-secrets`:** credenciales de bases de datos, LDAP, JWT, QR y la bóveda.
  El chart solo lo referencia. Lo crea `scripts/apply-secrets.sh`:

  ```bash
  export KUBECONFIG=<kubeconfig del clúster>
  deploy/k8s/scripts/apply-secrets.sh dev
  ```

  La primera vez genera `~/.circleguard/k8s-dev.env` (permisos 600) desde
  `deploy/compose/dev/.env.example` con valores aleatorios, como el bootstrap de Compose. Las
  siguientes veces reutiliza ese archivo, así que volver a ejecutarlo no cambia las contraseñas
  de una base que ya tiene datos.

## Sondas y autoescalado (HU-31)

| Sonda | Ruta | Qué decide |
|:---|:---|:---|
| `startupProbe` | `/actuator/health/liveness` | Da hasta 5 minutos al arranque de la JVM antes de vigilarla |
| `livenessProbe` | `/actuator/health/liveness` | Reinicia el contenedor tras 3 fallos seguidos |
| `readinessProbe` | `/actuator/health/readiness` | Saca el pod del Service si una dependencia cae |

`readinessGroup` define qué dependencias cuentan para cada servicio. Por ejemplo, `identity`
usa `readinessState,db`: si PostgreSQL cae, el pod deja de estar Ready sin reiniciarse, y
vuelve solo cuando la base regresa. La liveness no incluye dependencias, para que una caída de
la base no reinicie todos los servicios en cadena.

`gateway` y `form` tienen HPA por CPU (70 % del `request`), de 1 a 3 réplicas (2 a 4 en prod).
k3s ya trae metrics-server. Tras la carga, el HPA reduce réplicas a los 2 minutos.

## Métricas para Prometheus (HU-39)

Los Services de los 8 servicios Spring llevan la etiqueta `circleguard.io/metrics: "true"` y el
chart crea un ServiceMonitor, `circleguard-services`, que lee `/actuator/prometheus` en el puerto
`http` cada 30 s y agrega la etiqueta `environment`. Se configura en `metrics.serviceMonitor` de
`values.yaml`.

El ServiceMonitor solo se crea si el clúster tiene las CRD del Prometheus Operator (namespace
`monitoring`, instalado por `infra/terraform/modules/observability`). En un clúster sin monitoreo
el chart se instala igual, sin ese recurso, y lo crea en el siguiente despliegue.

Con `helm template`, agrega `--api-versions monitoring.coreos.com/v1/ServiceMonitor` o el recurso
no se genera. Las métricas, sus etiquetas y cómo verificarlas están en
[`docs/operaciones/observability.md`](../../docs/operaciones/observability.md#métricas-de-los-servicios-hu-39).

## Desplegar a mano

```bash
export KUBECONFIG=<kubeconfig del clúster>
deploy/k8s/scripts/apply-secrets.sh dev
helm upgrade --install circleguard deploy/k8s/circleguard \
  --namespace circleguard-dev --create-namespace \
  -f deploy/k8s/circleguard/values-dev.yaml \
  --set image.registry=<ip-privada-tools>:5000 \
  --set image.tag=<versión>
kubectl -n circleguard-dev get pods
```

El nodo descarga las imágenes de Nexus por la IP privada del host `tools` (peering de VPC;
`registries.yaml` lo deja Terraform). La aplicación queda en `http://<ip-pública-del-nodo>/`.

## Verificación hecha en k3s-dev (versión 0.5.0)

- Los 15 pods quedaron `Running` y `Ready` (los 8 servicios, el cliente web y el middleware).
- `/` y `/svc/{auth,identity,promotion,form,gateway}/actuator/health` respondieron 200 por el
  Ingress.
- Con PostgreSQL en 0 réplicas, `identity`, `auth` y `form` pasaron a `0/1` y salieron de sus
  Endpoints; `gateway` siguió Ready. Ninguno se reinició. Al volver la base, todos quedaron Ready.
- Con carga sobre `gateway`, el HPA subió a 3 réplicas y, al terminar, bajó a 2 y luego a 1.
- Memoria del nodo con la aplicación y el monitoreo: ~4,7 GB de 8 GB.

Las imágenes 0.5.0 de `notification`, `file` y `dashboard` son anteriores a su dependencia de
Actuator (`file` y `dashboard` la reciben en este cambio), así que la prueba usó sondas TCP
para esos tres. Desde la próxima versión usan las sondas HTTP como el resto.
