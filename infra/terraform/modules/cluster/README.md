# Módulo `cluster`

Cómputo de un ambiente: un clúster k3s de un solo nodo (servidor y cargas) con una Elastic IP, para que la IP del API server (6443) y del Ingress no cambie al apagar y encender. Es el módulo `compute`/`cluster` que exige HU-26.

## Qué crea

- Instancia EC2 Ubuntu 24.04 (AMI resuelta por SSM; se puede sobrescribir con `ami_id`), con root volume gp3 cifrado de `root_volume_gb` e IMDSv2 obligatorio con hop limit 2, para que los pods lleguen al rol del nodo.
- Rol de IAM `<name>-node` con su instance profile. La única política, `<name>-files`, permite `s3:ListBucket` sobre el bucket del ambiente y `s3:GetObject`, `s3:PutObject` y `s3:DeleteObject` sobre sus objetos. `file-service` la usa sin llaves, con la cadena de credenciales por defecto del SDK.
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
| `storage_bucket_arn` | string | — | ARN del bucket del ambiente (módulo `storage`) al que accede el rol del nodo. |

## Outputs

| Output | Uso |
|:---|:---|
| `instance_id` | Dimensionar alarmas de CloudWatch e inspección operativa. |
| `public_ip` | API server (6443) y host del Ingress (`<ip>.sslip.io`). |
| `private_ip` | Acceso desde Jenkins por la VPC de CI. |
| `node_role_name` | Rol de IAM del nodo, el que usan los pods por IMDSv2. |

## Notas de diseño

- `user_data_replace_on_change = true`: cambiar `k3s_version` o `registry` recrea el nodo y el clúster se reconstruye desde el código, no se parchea a mano.
- `ignore_changes = [ami]`: una AMI más nueva en SSM no recrea un clúster en ejecución.
- El instance profile y el hop limit cambian en el lugar: agregarlos a un nodo existente no lo recrea ni toca `user_data`.
- Cualquier pod del nodo puede usar el rol, no solo `file-service`. Por eso la política se limita al bucket del ambiente. Aplicar este módulo exige permisos de IAM (`iam:CreateRole`, `iam:PutRolePolicy`, `iam:CreateInstanceProfile`, `iam:AddRoleToInstanceProfile`, `iam:PassRole`) y `ec2:AssociateIamInstanceProfile`.
- Las etiquetas `Project`, `Environment` y `ManagedBy` las pone `default_tags` del provider en el root de `envs/<ambiente>/`.
