# Security Groups — CircleGuard environments (draft)

This document defines the network exposure of each environment. It is completed and enforced in Commit 3 (`feat(infra): provision isolated dev, stage and prod environments (HU-04)`).

## Model

- One AWS EC2 host per environment (`dev`, `stage`, `prod`).
- One Security Group per host, attached only to that host.
- No rule allows traffic between environment Security Groups.
- Only documented public ports are exposed to the Internet; everything else is restricted to `ADMIN_CIDR` or kept private.

## Inbound rules

| Port | Protocol | Source | Purpose | dev | stage | prod |
|:---|:---|:---|:---|:---:|:---:|:---:|
| 22 | TCP | `ADMIN_CIDR` | SSH administration | yes | yes | yes |
| 80 | TCP | `0.0.0.0/0` | HTTP (redirect to HTTPS) | yes | yes | yes |
| 443 | TCP | `0.0.0.0/0` | HTTPS public entry point | yes | yes | yes |
| 8080 | TCP | `ADMIN_CIDR` | Jenkins UI / webhooks | CI host | CI host | CI host |
| 9000 | TCP | `ADMIN_CIDR` | SonarQube UI | CI host | CI host | CI host |
| 8081 | TCP | `ADMIN_CIDR` | Nexus UI | CI host | CI host | CI host |
| 5000 | TCP | `ADMIN_CIDR` | Nexus Docker registry | CI host | CI host | CI host |
| 8180, 8082-8088 | TCP | `ADMIN_CIDR` | Service APIs (direct access only for diagnostics) | yes | yes | yes |

## Never exposed publicly

- PostgreSQL `5432`
- Neo4j `7474`, `7687`
- Kafka `9092`, Zookeeper `2181`
- Redis `6379`
- OpenLDAP `389`, `636`
- Docker socket / daemon `2375`, `2376`

## Outbound rules

| Port | Protocol | Destination | Purpose |
|:---|:---|:---|:---|
| 443 | TCP | `0.0.0.0/0` | Package repositories, Docker Hub, GitHub |
| 80 | TCP | `0.0.0.0/0` | OS package mirrors |
| 9418/22 | TCP | GitHub | Git over HTTPS/SSH (only if required) |

## Defense in depth

`infra/security/apply-ufw.sh` mirrors these rules on the host with `ufw`:

```bash
ufw default deny incoming
ufw default allow outgoing
ufw allow from "$ADMIN_CIDR" to any port 22 proto tcp
ufw allow 80/tcp
ufw allow 443/tcp
ufw --force enable
```

The script is idempotent: rules are checked before being added.

## Verification checklist

- [ ] Each environment has its own Security Group.
- [ ] No rule references another environment's Security Group as source.
- [ ] Only the documented ports appear as public.
- [ ] Middleware ports are reachable only from inside the environment network.
