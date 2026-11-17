# Terraform: clústeres k3s por ambiente (Parte 2)

Cada ambiente (`dev`, `stage`, `prod`) es un clúster k3s de un nodo en su propia VPC. El mismo código sirve para los tres; solo cambian las variables y la clave del estado.

```
infra/terraform/
├── bootstrap/          # bucket S3 del estado y tabla DynamoDB de bloqueo (una sola vez)
├── modules/
│   ├── network/        # VPC, subred pública, IGW, peering con la VPC de CI y Security Group
│   ├── cluster/        # EC2 + Elastic IP; k3s se instala en el primer arranque (user_data)
│   ├── registry/       # repositorios ECR por servicio, escaneo y ciclo de vida
│   ├── storage/        # bucket S3 de adjuntos y respaldos, versionado y cifrado
│   └── observability/  # log groups de CloudWatch, alarmas del nodo y topic SNS
└── envs/
    ├── shared/         # main.tf, platform.tf, variables.tf, outputs.tf, versions.tf
    ├── dev/            # symlinks a ../shared/*.tf + backend.hcl + terraform.tfvars
    ├── stage/          # mismos symlinks; solo cambian backend.hcl y terraform.tfvars
    └── prod/
```

## Qué crea cada ambiente

| Recurso | dev | stage | prod |
|:---|:---|:---|:---|
| VPC | `10.10.0.0/16` | `10.20.0.0/16` | `10.30.0.0/16` |
| Nodo | `circleguard-k3s-dev` | `circleguard-k3s-stage` | `circleguard-k3s-prod` |
| Security Group | `circleguard-k3s-dev-sg` | `circleguard-k3s-stage-sg` | `circleguard-k3s-prod-sg` |

- Nodo `m7i-flex.large` (2 vCPU, 8 GB, el máximo del plan gratuito de AWS), disco gp3 cifrado de 40 GB, IMDSv2 obligatorio.
- Elastic IP: la IP del API server (6443) y del Ingress no cambia al apagar y encender, así que el certificado de k3s (`--tls-san`) y el TLS con `<ip>.sslip.io` siguen siendo válidos.
- Peering con la VPC de CI: Jenkins llega al API server por IP privada y el nodo descarga imágenes del registro de Nexus (host `tools`, puerto 5000), que queda declarado en `/etc/rancher/k3s/registries.yaml`.
- Reglas: 80 y 443 públicos (Ingress); 22 y 6443 solo desde Jenkins y desde las IP que cada integrante autoriza con `infra/cloud/aws/hosts.sh allow`. Las reglas son recursos sueltos, no inline, para que un `apply` no borre las que agrega `hosts.sh`.
- Todo lleva las etiquetas `Project=CircleGuard`, `Environment` y `ManagedBy=terraform`: la política IAM del equipo permite encender, apagar y autorizar IP por esa etiqueta.

## Módulos reutilizables

Los tres roots enlazan con symlinks los mismos `.tf` de `envs/shared/`, que invocan los cinco módulos. Así no se copian recursos ni configuración: lo único que cambia por ambiente es `backend.hcl` (la `key` del estado) y `terraform.tfvars` (los valores). Los symlinks requieren Linux/macOS o Git con `core.symlinks=true` en Windows.

| Módulo | Responsabilidad | README |
|:---|:---|:---|
| `network` | VPC, subred, IGW, peering con CI y Security Groups. | [`modules/network`](modules/network/README.md) |
| `cluster` | EC2 + Elastic IP y k3s por `user_data` (antes `k3s-node`). | [`modules/cluster`](modules/cluster/README.md) |
| `registry` | Un repositorio ECR por servicio, con escaneo y lifecycle. | [`modules/registry`](modules/registry/README.md) |
| `storage` | Bucket S3 de adjuntos y respaldos, versionado y cifrado. | [`modules/storage`](modules/storage/README.md) |
| `observability` | Log groups, alarmas del nodo y topic SNS. | [`modules/observability`](modules/observability/README.md) |

Diferencias por ambiente (el resto usa los valores por defecto del módulo):

| Variable | dev | stage | prod |
|:---|:---|:---|:---|
| `registry_keep_last_images` | 10 | 20 | 50 |
| `log_retention_days` | 7 | 14 | 30 |
| `storage_force_destroy` | `true` | `false` | `false` |

El módulo de cómputo se llamaba `k3s-node`. Se renombró a `cluster` para cumplir el criterio de HU-26 sin mover el estado: la dirección sigue siendo `module.k3s`, porque el nombre del bloque no cambió, solo el `source`.

Validar cualquier módulo:

```bash
for d in infra/terraform/modules/*/; do
  ( cd "$d" && terraform init -backend=false -input=false >/dev/null && \
    terraform fmt -check && terraform validate ) || exit 1
done

for e in dev stage prod; do
  ( cd "infra/terraform/envs/$e" && terraform init -backend=false -input=false >/dev/null && \
    terraform fmt -check && terraform validate ) || exit 1
done
```

## Uso

Requisitos: Terraform ≥ 1.9 y una cuenta con permisos de EC2, S3 (`circleguard-tfstate-*`) y DynamoDB (`circleguard-tf-locks`).

1. Estado remoto, una sola vez. El bootstrap crea el bucket donde después guarda su propio estado, así que la primera vez se aplica con estado local (comentando el bloque `backend "s3" {}` de `main.tf`) y luego se migra:

   ```bash
   cd infra/terraform/bootstrap
   terraform init && terraform apply
   # se descomenta el backend
   terraform init -migrate-state -backend-config=bootstrap.s3.tfbackend
   ```

   Ya está hecho en la cuenta del equipo: basta `terraform init -backend-config=bootstrap.s3.tfbackend`.

2. Cada ambiente, cambiando `dev` por `stage` o `prod`:

   ```bash
   cd infra/terraform/envs/dev
   terraform init -reconfigure -backend-config=backend.hcl
   terraform plan -out=dev.tfplan
   terraform apply dev.tfplan
   ```

   Los tres roots comparten los mismos `.tf`; solo cambian `backend.hcl` (la `key` del
   estado) y `terraform.tfvars` (valores). Conserva las `key` existentes para no perder
   el estado. Un segundo `plan` sin cambios en el código debe responder `No changes`
   (idempotencia). Mientras alguien aplica, la tabla DynamoDB bloquea el estado de ese
   ambiente.

3. Dar acceso a la IP propia y leer las IP (`hosts.sh` también enciende y apaga los nodos: `start k3s`, `stop k3s`):

   ```bash
   infra/cloud/aws/hosts.sh allow
   infra/cloud/aws/hosts.sh status
   ```

El host de CI y el host `tools` (Jenkins, SonarQube y Nexus) se crearon en la Parte 1 con `infra/cloud/aws/provision-ci-hosts.sh`. Terraform solo los lee (`data`) para el peering y la regla del registro.

## Cambios que recrean el nodo

`user_data` cambia si cambian la versión de k3s (`k3s_version`) o el registro. En ese caso Terraform reemplaza el nodo (`user_data_replace_on_change`): el clúster se reconstruye desde el código y el pipeline vuelve a desplegar. Una AMI de Ubuntu más nueva no lo recrea (`ignore_changes = [ami]`).

## Acceso de Jenkins a los clústeres

Jenkins usa la credencial `kubeconfig-<ambiente>` (tipo archivo). Se carga desde el nodo sin pasar por el disco de quien la ejecuta:

```bash
source infra/cloud/aws/hosts.env
ssh ubuntu@$K3S_DEV_PUBLIC_IP 'sudo cat /etc/rancher/k3s/k3s.yaml' \
  | ssh ubuntu@$CI_PUBLIC_IP "sudo ~/circleguard/infra/bootstrap/load-kubeconfig.sh dev $K3S_DEV_PRIVATE_IP"
```

La imagen de Jenkins trae `kubectl` v1.30.6, Helm v3.16.2 y Terraform 1.9.8, con checksum verificado.
