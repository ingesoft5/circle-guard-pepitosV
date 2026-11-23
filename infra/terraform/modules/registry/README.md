# Módulo `registry`

Registro de imágenes de un ambiente. Declara un repositorio ECR por servicio, con escaneo al hacer push y una política de ciclo de vida que expira imágenes antiguas, para no duplicar configuración entre `dev`, `stage` y `prod`.

**Uso actual:** el pipeline publica las imágenes en Nexus y los clústeres las descargan de `tools:5000` por el peering. Los repositorios ECR existen en los tres ambientes, pero están vacíos. Ver [`docs/arquitectura/infraestructura.md`](../../../../docs/arquitectura/infraestructura.md#recursos-sin-consumidor).

## Qué crea

- Un `aws_ecr_repository` por cada nombre en `repository_names`, con nombre `<name_prefix>/<servicio>`.
- Escaneo de vulnerabilidades al hacer push (`scan_on_push`).
- Una `aws_ecr_lifecycle_policy` por repositorio que conserva solo las últimas `keep_last_images` imágenes.

## Variables

| Variable | Tipo | Por defecto | Uso |
|:---|:---|:---|:---|
| `environment` | string | — | `dev`, `stage` o `prod`. |
| `name_prefix` | string | — | Prefijo de los repositorios. |
| `repository_names` | list(string) | — | Servicios que tendrán repositorio. |
| `image_tag_mutability` | string | `MUTABLE` | `MUTABLE` o `IMMUTABLE`. |
| `scan_on_push` | bool | `true` | Escanear imágenes al publicarlas. |
| `keep_last_images` | number | `30` | Imágenes que conserva la política. |

## Outputs

| Output | Uso |
|:---|:---|
| `registry_host` | Host ECR de la cuenta y región. |
| `repository_urls` | URL de cada repositorio por servicio. |
| `repository_arns` | ARN de cada repositorio por servicio. |

## Decisión: ECR vs Nexus

El pipeline de la Parte 1 publica en el registro de Nexus del host `tools`, que no es por ambiente. Este módulo deja declarado el registro nativo de AWS por ambiente, de modo que migrar el pipeline a ECR sea solo un cambio de destino, sin reescribir la infraestructura. Mientras esa migración no ocurra, el módulo no altera el flujo actual de Nexus.

## Notas

- Las etiquetas `Project`, `Environment` y `ManagedBy` las aplica `default_tags` del provider en el root.
- El cifrado usa AES256 (SSE de ECR); no requiere KMS propio.
