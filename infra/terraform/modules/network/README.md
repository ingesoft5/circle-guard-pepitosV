# Módulo `network`

Red de un ambiente: VPC propia, subred pública, Internet Gateway, peering con la VPC de los hosts de CI (Jenkins y tools) y los Security Groups que permiten el acceso al clúster y la descarga de imágenes desde el registro de Nexus.

## Qué crea

- `aws_vpc` con DNS support y hostnames.
- `aws_internet_gateway`, `aws_subnet` pública y `aws_route_table` con salida a Internet y ruta hacia el CIDR de la VPC de CI.
- `aws_vpc_peering_connection` con la VPC de CI y la ruta de retorno en la route table del host de CI.
- `aws_security_group` del nodo y reglas sueltas:
  - 80 y 443 públicos (Ingress).
  - 22 y 6443 desde la IP privada de Jenkins y desde `admin_cidrs`.
  - 5000 del registro de Nexus desde el CIDR del ambiente.

## Variables

| Variable | Tipo | Por defecto | Uso |
|:---|:---|:---|:---|
| `name` | string | — | Prefijo de recursos. |
| `environment` | string | — | `dev`, `stage` o `prod`. |
| `vpc_cidr` | string | — | CIDR de la VPC; no debe solaparse con CI ni con otro ambiente. |
| `availability_zone` | string | `null` | Zona de la subred; `null` usa la primera disponible. |
| `ci_vpc_id` | string | — | VPC de los hosts de CI. |
| `ci_vpc_cidr` | string | — | CIDR de la VPC de CI. |
| `ci_route_table_id` | string | — | Route table del host de CI (ruta de retorno). |
| `ci_private_ip` | string | — | IP privada de Jenkins. |
| `registry_security_group_id` | string | — | Security Group del host tools (Nexus). |
| `registry_port` | number | `5000` | Puerto del registro. |
| `admin_cidrs` | list(string) | `[]` | CIDRs con SSH y API server. |

## Outputs

| Output | Uso |
|:---|:---|
| `vpc_id` | Referencia y outputs del ambiente. |
| `subnet_id` | Entrada del módulo `cluster`. |
| `security_group_id` | Entrada del módulo `cluster`. |

## Notas de diseño

- Las reglas son recursos sueltos (`aws_vpc_security_group_ingress_rule`), no inline, para que el siguiente `apply` no borre las que agrega `infra/cloud/aws/hosts.sh allow`.
- El peering y la regla del registro permiten que Jenkins llegue al API server por IP privada y que el nodo descargue imágenes de Nexus por el puerto 5000.
