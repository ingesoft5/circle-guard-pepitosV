# Terraform: clústeres k3s por ambiente (Parte 2)

Cada ambiente (`dev`, `stage`, `prod`) es un clúster k3s de un nodo en su propia VPC. El mismo código sirve para los tres; solo cambian las variables y la clave del estado.

```
infra/terraform/
├── bootstrap/          # bucket S3 del estado y tabla DynamoDB de bloqueo (una sola vez)
├── modules/
│   ├── network/        # VPC, subred pública, IGW, peering con la VPC de CI y Security Group
│   └── k3s-node/       # EC2 + Elastic IP; k3s se instala en el primer arranque (user_data)
└── environment/        # root de un ambiente: <env>.tfvars y <env>.s3.tfbackend
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
   cd infra/terraform/environment
   terraform init -reconfigure -backend-config=dev.s3.tfbackend
   terraform plan -var-file=dev.tfvars -out=dev.tfplan
   terraform apply dev.tfplan
   ```

   Un segundo `plan` sin cambios en el código debe responder `No changes` (idempotencia). Mientras alguien aplica, la tabla DynamoDB bloquea el estado de ese ambiente.

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
