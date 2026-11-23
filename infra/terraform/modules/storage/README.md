# Módulo `storage`

Almacenamiento de objetos de un ambiente: un bucket S3 para los archivos de `file-service` y respaldos. Versionado, cifrado y bloqueo de acceso público quedan declarados una vez y se reutilizan en `dev`, `stage` y `prod` con distintos valores.

## Qué crea

- `aws_s3_bucket` con nombre `<name_prefix>-files-<account_id>`.
- `aws_s3_bucket_ownership_controls` con `BucketOwnerEnforced` (sin ACLs).
- `aws_s3_bucket_public_access_block` con las cuatro protecciones activadas.
- `aws_s3_bucket_versioning`, `aws_s3_bucket_server_side_encryption_configuration` (AES256) y `aws_s3_bucket_lifecycle_configuration` para expirar versiones antiguas.

## Variables

| Variable | Tipo | Por defecto | Uso |
|:---|:---|:---|:---|
| `environment` | string | — | `dev`, `stage` o `prod`. |
| `name_prefix` | string | — | Prefijo del bucket. |
| `force_destroy` | bool | `false` | Borrar el contenido al destruir (útil en `dev`). |
| `versioning_enabled` | bool | `true` | Guardar versiones anteriores de cada objeto. |
| `noncurrent_version_expiration_days` | number | `30` | Días para expirar versiones no vigentes. |

## Outputs

| Output | Uso |
|:---|:---|
| `bucket_id` | Nombre del bucket para ConfigMap/Secret del `file-service`. |
| `bucket_arn` | Políticas IAM y outputs del ambiente. |
| `bucket_domain_name` | Endpoint regional del bucket. |

## Notas

- **Uso actual:** `file-service` guarda los archivos bajo `uploads/` con el rol del nodo (módulo `cluster`, política `<name>-files`). El nombre del bucket le llega por `CIRCLEGUARD_STORAGE_S3_BUCKET` en `deploy/k8s/circleguard/values-<env>.yaml`.

- El bucket es privado por diseño; la aplicación accede con su rol IAM, no con credenciales en el repositorio.
- `force_destroy = true` solo en `dev` para que `terraform destroy` no falle; en `stage` y `prod` se conserva el contenido.
- Las etiquetas `Project`, `Environment` y `ManagedBy` las aplica `default_tags` del provider en el root.
