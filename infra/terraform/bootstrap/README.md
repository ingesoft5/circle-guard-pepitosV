# Bootstrap del estado remoto de Terraform

Stack que se aplica **una sola vez** por cuenta de AWS. Crea el bucket S3 donde viven los
`terraform.tfstate` de CircleGuard y la tabla DynamoDB que bloquea el estado mientras alguien
aplica. Es el requisito de la HU-28 (`CGR-138`): el estado queda fuera del repositorio, cifrado,
versionado y protegido contra escrituras concurrentes.

Los roots `envs/dev`, `envs/stage` y `envs/prod` no crean este stack: solo apuntan a él con su
`backend.hcl`.

## Qué crea

| Recurso | Configuración | Para qué |
|:---|:---|:---|
| `aws_s3_bucket.state` | `circleguard-tfstate-<account-id>`, `prevent_destroy` | Guarda todos los estados. |
| `aws_s3_bucket_versioning.state` | `Enabled` | Permite volver a una versión anterior del estado. |
| `aws_s3_bucket_server_side_encryption_configuration.state` | SSE-S3 (`AES256`) | Cifra el estado en reposo. |
| `aws_s3_bucket_public_access_block.state` | Cuatro flags en `true` | El estado nunca es público. |
| `aws_s3_bucket_policy.state` | `DenyInsecureTransport` | Rechaza cualquier acceso sin TLS. |
| `aws_s3_bucket_lifecycle_configuration.state` | `expire-noncurrent-state` | Caduca versiones antiguas sin apagar el versionado. |
| `aws_dynamodb_table.locks` | `circleguard-tf-locks`, `PAY_PER_REQUEST`, hash key `LockID`, PITR, `prevent_destroy` | Bloqueo de estado y recuperación a un punto en el tiempo. |

El nombre del bucket se deriva de la cuenta (`data.aws_caller_identity`) para no hardcodearlo.
La tabla y el bucket tienen `prevent_destroy`: `terraform destroy` falla a propósito.

## Variables

| Variable | Default | Descripción |
|:---|:---|:---|
| `region` | `us-east-1` | Región del bucket y de la tabla. |
| `lock_table_name` | `circleguard-tf-locks` | Tabla DynamoDB que usa el backend S3. |
| `noncurrent_state_expiration_days` | `90` | Días que se conservan las versiones antiguas del estado. |

## Outputs

| Output | Uso |
|:---|:---|
| `state_bucket` | Valor de `bucket` en cada `backend.hcl` y en `bootstrap.s3.tfbackend`. |
| `lock_table` | Valor de `dynamodb_table` en cada `backend.hcl` y en `bootstrap.s3.tfbackend`. |

```bash
cd infra/terraform/bootstrap
terraform output
```

## Primer apply (solo si el stack aún no existe)

El bucket no puede guardar su propio estado antes de existir, así que la primera vez se aplica
con estado local y luego se migra al bucket que acaba de crear. **Ese estado local nunca se
versiona** (`.gitignore` excluye `*.tfstate`).

```bash
cd infra/terraform/bootstrap

# 1. Comenta temporalmente el bloque backend "s3" {} de main.tf
terraform init
terraform apply

# 2. Descomenta el bloque backend "s3" {} y migra el estado al bucket recién creado
terraform init -migrate-state -backend-config=bootstrap.s3.tfbackend
```

Al terminar, `terraform state list` debe leer el estado desde S3
(`bootstrap/terraform.tfstate`).

> En la cuenta del equipo el stack ya está aplicado: basta
> `terraform init -backend-config=bootstrap.s3.tfbackend`. Un `plan` posterior debe responder
> `No changes`.

## Uso diario

Solo se toca el bootstrap si cambia la configuración del bucket o de la tabla:

```bash
cd infra/terraform/bootstrap
terraform init -reconfigure -backend-config=bootstrap.s3.tfbackend
terraform plan
terraform apply
```

Después, cada ambiente usa el mismo bucket y la misma tabla con su propia `key`:

| Root | `key` en el backend |
|:---|:---|
| `bootstrap` | `bootstrap/terraform.tfstate` |
| `envs/dev` | `environments/dev/terraform.tfstate` |
| `envs/stage` | `environments/stage/terraform.tfstate` |
| `envs/prod` | `environments/prod/terraform.tfstate` |

```bash
cd infra/terraform/envs/dev
terraform init -reconfigure -backend-config=backend.hcl
terraform plan
```

## Bloqueo del estado

El backend usa DynamoDB porque Terraform 1.9.x no soporta el bloqueo nativo de S3
(`use_lockfile` llegó en 1.10). Mientras un `apply` o `plan` tiene el lock, un segundo proceso
sobre el mismo ambiente falla con `Error acquiring the state lock` y no modifica nada.

El procedimiento completo para demostrarlo y guardar la evidencia está en
[`../../../docs/operaciones/terraform-state-lock.md`](../../../docs/operaciones/terraform-state-lock.md).

Reglas:

- **Nunca** uses `-lock=false` en `apply`.
- `terraform force-unlock <LOCK_ID>` es el último recurso: solo si el lock quedó huérfano por un
  proceso muerto y no hay nadie aplicando.

## Desmontar el stack

`prevent_destroy` en el bucket y en la tabla impide un `terraform destroy` accidental. Para
desmontarlo hay que quitar esos bloques en un cambio revisado, vaciar el bucket (incluidas las
versiones) y destruir. Solo se hace si se está retirando el proyecto completo de la cuenta.

## Validar sin AWS

```bash
cd infra/terraform/bootstrap
terraform init -backend=false -input=false
terraform fmt -check
terraform validate
```
