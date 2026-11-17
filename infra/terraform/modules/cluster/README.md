# Módulo `cluster`

Cómputo de un ambiente: un clúster k3s de un solo nodo (servidor y cargas) con una Elastic IP, para que la IP del API server (6443) y del Ingress no cambie al apagar y encender. Es el módulo `compute`/`cluster` que exige HU-26.

## Qué crea

- Instancia EC2 Ubuntu 24.04 (AMI resuelta por SSM; se puede sobrescribir con `ami_id`), con root volume gp3 cifrado de `root_volume_gb` y IMDSv2 obligatorio.
- Elastic IP y su asociación a la instancia.
- `user_data` que instala k3s en el primer arranque con la versión fijada (`k3s_version`), configura `--tls-san` con la IP pública y declara el registro de Nexus en `/etc/rancher/k3s/registries.yaml`.

## Variables

| Variable | Tipo | Por defecto | Uso |
|:---|:---|:---|:---|
| `name` | string | — | Prefijo de recursos, p. ej. `circleguard-k3s-dev`. |
| `subnet_id` | string | — | Subred pública del ambiente. |
| `security_group_id` | string | — | Security Group del nodo. |
| `key_name` | string | — | Key pair EC2 para SSH. |
| `instance_type` | string | `m7i-flex.large` | Tipo de instancia. |
| `root_volume_gb` | number | `40` | Tamaño del root volume. |
| `k3s_version` | string | — | Versión fijada de k3s, p. ej. `v1.30.6+k3s1`. |
| `registry` | string | — | `host:port` del registro de Nexus. |
| `ami_id` | string | `null` | AMI manual; `null` usa la última Ubuntu 24.04 de SSM. |

## Outputs

| Output | Uso |
|:---|:---|
| `instance_id` | Dimensionar alarmas de CloudWatch e inspección operativa. |
| `public_ip` | API server (6443) y host del Ingress (`<ip>.sslip.io`). |
| `private_ip` | Acceso desde Jenkins por la VPC de CI. |

## Notas de diseño

- `user_data_replace_on_change = true`: cambiar `k3s_version` o `registry` recrea el nodo y el clúster se reconstruye desde el código, no se parchea a mano.
- `ignore_changes = [ami]`: una AMI más nueva en SSM no recrea un clúster en ejecución.
- Las etiquetas `Project`, `Environment` y `ManagedBy` las pone `default_tags` del provider en el root de `environment/`.
