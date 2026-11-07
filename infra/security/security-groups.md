# Security Groups - Ambientes de CircleGuard

Este documento define la exposición de red de cada ambiente.

## Modelo

- Un host AWS EC2 por ambiente (`dev`, `stage`, `prod`).
- Un Security Group por host, asociado únicamente a ese host.
- Ninguna regla permite tráfico entre los Security Groups de ambientes distintos.
- Solo los puertos públicos documentados se exponen a Internet; el resto se restringe a `ADMIN_CIDR` o queda privado.

## Reglas de entrada

| Puerto | Protocolo | Origen | Propósito | dev | stage | prod |
|:---|:---|:---|:---|:---:|:---:|:---:|
| 22 | TCP | `ADMIN_CIDR` | Administración por SSH | sí | sí | sí |
| 22 | TCP | IP privada del host CI (`JENKINS_CIDR`) | Despliegue desde Jenkins con el usuario `deploy` | sí | sí | sí |
| 80 | TCP | `0.0.0.0/0` | HTTP (redirección a HTTPS) | sí | sí | sí |
| 443 | TCP | `0.0.0.0/0` | Entrada pública HTTPS | sí | sí | sí |
| 8080 | TCP | `ADMIN_CIDR` | UI de Jenkins | host CI | host CI | host CI |
| 8080 | TCP | Rangos `hooks` de la API `meta` de GitHub | Webhooks de GitHub | host CI | host CI | host CI |
| 9000 | TCP | `ADMIN_CIDR` | UI de SonarQube | host CI | host CI | host CI |
| 8081 | TCP | `ADMIN_CIDR` | UI de Nexus | host CI | host CI | host CI |
| 5000 | TCP | `ADMIN_CIDR` e IP privada de cada ambiente | Registro Docker de Nexus | host CI | host CI | host CI |
| 8180, 8082-8088 | TCP | `ADMIN_CIDR` | APIs de los servicios (acceso directo solo para diagnóstico) | sí | sí | sí |

## Nunca expuestos públicamente

- PostgreSQL `5432`
- Neo4j `7474`, `7687`
- Kafka `9092`, Zookeeper `2181`
- Redis `6379`
- OpenLDAP `389`, `636`
- Socket / demonio de Docker `2375`, `2376`

## Reglas de salida

Se conserva la salida por defecto de AWS, que permite todo el tráfico. Limitarla a unos puertos bloquearía, por ejemplo, el acceso al registro de Nexus (`5000`) desde los ambientes.

## Defensa en profundidad

Posibles comandos para ejecutar usando scripts en cada EC2 replicando las reglas anteriores con `ufw`:

```bash
ufw default deny incoming
ufw default allow outgoing
ufw allow from "$ADMIN_CIDR" to any port 22 proto tcp
ufw allow 80/tcp
ufw allow 443/tcp
ufw --force enable
```

El script es idempotente: verifica cada regla antes de agregarla.