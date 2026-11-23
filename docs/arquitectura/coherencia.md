# Revisión cruzada: documentación frente a lo desplegado (HU-54)

Se comparó la documentación de `docs/` y de los README del repositorio con lo que existe en AWS el
22 de noviembre de 2026: los tres clústeres k3s, los cuatro estados de Terraform y las instancias
EC2 con la etiqueta `Project=CircleGuard`. Versión desplegada: `0.7.0` en `dev`, `stage` y `prod`.

## Fuentes

| Fuente | Cómo se leyó | Qué devolvió |
|:---|:---|:---|
| Clústeres | `deploy/k8s/scripts/inventario.sh` en cada nodo (solo `kubectl get` y `helm list`; los Secrets por nombre y tipo) | Mismos 16 componentes en los tres; 6 releases de Helm en cada uno |
| Terraform | `terraform state list` y `terraform output` en `bootstrap`, `envs/dev`, `envs/stage` y `envs/prod` | `bootstrap`: 7 recursos; cada ambiente: 52 recursos y 8 lecturas, con las mismas direcciones |
| AWS | `aws ec2 describe-instances --filters Name=tag:Project,Values=CircleGuard` | 10 instancias: 7 encendidas y 3 detenidas |

## Componentes desplegados y dónde están documentados

| Componente | Existe en | Documentado en | Resultado |
|:---|:---|:---|:---|
| 8 servicios Spring y `web` | Los tres clústeres, imagen `0.7.0` | [`kubernetes.md`](kubernetes.md), [`README.md`](README.md), `deploy/k8s/README.md` | Coincide |
| `postgres`, `neo4j` (StatefulSet con PVC) | Los tres; PVC 2/2, 3/2 y 5/3 Gi en dev, stage y prod | [`kubernetes.md`](kubernetes.md) | Coincide con `values-<env>.yaml` |
| `kafka`, `zookeeper`, `redis`, `openldap` | Los tres | [`kubernetes.md`](kubernetes.md) | Coincide |
| `jaeger` | Los tres | [`kubernetes.md`](kubernetes.md), `patrones/trazas.md` | Coincide |
| HPA de `form` y `gateway` | 1–3 en dev y stage, 2–4 en prod | [`kubernetes.md`](kubernetes.md), `deploy/k8s/README.md` | Coincide |
| Ingress `circleguard` (HTTP) | Los tres | [`kubernetes.md`](kubernetes.md) | Coincide |
| SealedSecret y Secret `circleguard-secrets`, controller en `kube-system` | Los tres | [`seguridad.md`](seguridad.md), `operaciones/secretos.md` | Coincide |
| ServiceMonitor `circleguard-services` | Los tres | [`observabilidad.md`](observabilidad.md) | Coincide |
| kube-prometheus-stack, Loki, Promtail (`monitoring`) | Los tres, chart 65.5.1, 6.55.0 y 6.17.1 | [`observabilidad.md`](observabilidad.md), `operaciones/observability.md` | Coincide |
| `loki-canary` | Los tres | [`kubernetes.md`](kubernetes.md) | **No estaba documentado**; agregado |
| PrometheusRule `circleguard-alerts`, AlertmanagerConfig `circleguard` | Los tres | [`observabilidad.md`](observabilidad.md) | Coincide |
| Ingress de Grafana, Prometheus y Alertmanager con Middleware `basic-auth` | Los tres | [`observabilidad.md`](observabilidad.md) | Coincide |
| Traefik, CoreDNS, metrics-server, local-path (k3s) | Los tres | [`kubernetes.md`](kubernetes.md) | Coincide |
| VPC, subred, IGW, peering, Security Group, EC2, EIP | Estado de cada ambiente | [`infraestructura.md`](infraestructura.md), `infra/terraform/README.md` | Coincide |
| Repositorios ECR, bucket S3, log groups de CloudWatch | Estado de cada ambiente | [`infraestructura.md`](infraestructura.md#recursos-sin-consumidor) | **Se documentaban como usados**; corregido: existen, pero nadie los consume |
| Tema SNS de alarmas | Estado de cada ambiente, sin suscripción | [`infraestructura.md`](infraestructura.md#recursos-sin-consumidor) | **No se decía que no tiene suscriptores**; agregado |
| Bucket y tabla del estado | Estado de `bootstrap` | [`infraestructura.md`](infraestructura.md) | Coincide |
| `circleguard-ci`, `ci-agent-1`, `ci-agent-2`, `tools` | EC2, encendidas | [`README.md`](README.md#2-vista-de-despliegue-en-aws), `operaciones/jenkins-pipeline.md` | Coincide |
| `circleguard-dev`, `-stage`, `-prod` (Parte 1) | EC2, detenidas | [`README.md`](README.md#2-vista-de-despliegue-en-aws) | **`operaciones/README.md` los presentaba como los ambientes vigentes**; corregido |

## Componentes documentados que no existen en los clústeres

| Documentado | Dónde | Por qué no está | Corrección |
|:---|:---|:---|:---|
| cert-manager, emisor `circleguard-ca`, Certificate `circleguard-tls`, redirección a HTTPS, `monitoring-tls` | `operaciones/tls.md`, `deploy/k8s/README.md` | Están en `dev`; entran con `0.8.0`. La `0.7.0` no los tiene | Ambos documentos y [`kubernetes.md`](kubernetes.md#en-dev-todavía-sin-desplegar) dicen ahora en qué versión entran |
| Roles `circleguard-deployer` y `circleguard-viewer` | `deploy/k8s/README.md` | Igual: `dev`, entran con `0.8.0` | Ídem |
| Adjuntos de `file-service` en S3 | `executive-summary.md`, `README.md` raíz, `modules/storage/README.md` | `file-service` no tiene cliente S3; escribe en `/app/uploads` | Corregidos los tres |
| Diagrama de despliegue por SSH a hosts Compose | `docs/arquitectura/*.pdf` | Es el despliegue de la Parte 1 | Marcados como históricos; los diagramas vigentes están en [`README.md`](README.md) |
| SonarQube y Nexus en la IP de Jenkins | `operaciones/README.md` (tabla de URLs) | Corren en `circleguard-tools` | Tabla corregida |

Después de las correcciones, cada componente del inventario tiene su lugar en la documentación, y
lo que se documenta y no está desplegado dice explícitamente en qué versión entra.

## Cómo repetir la revisión

Con la llave del equipo y credenciales de AWS, desde la raíz del repositorio (Linux, macOS o Git
Bash):

```bash
mkdir -p .inventario
for ip in 52.204.64.188 3.221.162.144 34.227.254.255; do
  ssh -i "$KEY" "ubuntu@$ip" 'bash -s' < deploy/k8s/scripts/inventario.sh > ".inventario/k8s-$ip.txt"
done
for d in bootstrap envs/dev envs/stage envs/prod; do
  backend=backend.hcl; [ "$d" = bootstrap ] && backend=bootstrap.s3.tfbackend
  ( cd "infra/terraform/$d" && terraform init -input=false -backend-config="$backend" >/dev/null \
      && terraform state list ) > ".inventario/tf-${d##*/}.txt"
done
aws ec2 describe-instances --filters Name=tag:Project,Values=CircleGuard \
  --query 'Reservations[].Instances[].[Tags[?Key==`Name`]|[0].Value,State.Name]' --output table \
  > .inventario/aws-ec2.txt
```

`.inventario/` está en `.gitignore`: contiene nombres internos e IP privadas. La revisión se repite cuando
sale una versión que agrega o quita componentes, o cuando cambia Terraform.
