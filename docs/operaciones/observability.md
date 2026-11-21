# Observabilidad en el clúster (HU-26 → HU-39/40/41)

Guía para instalar y operar Prometheus, Grafana y Alertmanager en un clúster k3s de CircleGuard.
El módulo Terraform `observability` (HU-26) instala el stack base; la recolección de métricas,
los dashboards y las alertas se configuran en HU-39, HU-40 y HU-41.

## Qué instala el módulo

| Pieza | Dónde |
|:---|:---|
| `kube-prometheus-stack`: Prometheus, Grafana, Alertmanager, node-exporter y kube-state-metrics | Namespace `monitoring` del clúster |
| `micrometer-registry-prometheus`, `/actuator/prometheus`, contadores de negocio y ServiceMonitors | Código de los 8 servicios (HU-39) |
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

## Problemas comunes

| Síntoma | Causa | Solución |
|:---|:---|:---|
| `terraform apply` falla con `dial tcp ...:22: i/o timeout` | El clúster está apagado o la IP no está autorizada | `hosts.sh start k3s`, `hosts.sh allow` |
| `Permission denied (publickey)` | La llave no coincide con el key pair | Usa `circleguard-admin-key.pem` o `ssh-add` |
| `helm` falla al conectar al API | k3s aún no terminó de arrancar | El script espera hasta 5 minutos; revisa `sudo k3s kubectl get nodes` |
| Prometheus sin targets | Los ServiceMonitors de HU-39 no están aplicados | Aplica los ServiceMonitors de HU-39 |
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
