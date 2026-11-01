# Plan de desarrollo — HU-03 y HU-04 (E1.2 Infraestructura en la nube con Bash)

Este documento define cómo se implementarán las historias **HU-03** (`CGR-26`, 8 pts) y **HU-04** (`CGR-27`, 5 pts) del Sprint 0, correspondientes al entregable **E1.2** de la Parte 1. La implementación se divide en **5 commits coherentes**, cada uno con un alcance cerrado, verificable y alineado con la rúbrica.

---

## 1. Alcance de las historias

### HU-03 — Aprovisionar Jenkins, SonarQube y Nexus con scripts Bash idempotentes

**Como** equipo de plataforma, **quiero** aprovisionar Jenkins, SonarQube y el registro de artefactos (Nexus) en la nube mediante scripts Bash idempotentes, **para** reconstruir las herramientas desde cero con un solo comando y sin configuración manual.

Criterios de aceptación:

1. **Reconstrucción desde cero:** un servidor nuevo con SSH ejecuta `infra/bootstrap/bootstrap-all.sh` y Jenkins, SonarQube y Nexus quedan corriendo vía Docker Compose, cada uno en su URL/puerto documentado.
2. **Idempotencia:** una segunda ejecución termina con código `0`, no duplica contenedores, volúmenes ni configuración, y no pierde datos persistidos.
3. **Sin pasos manuales no documentados:** un integrante nuevo sigue el `README` y obtiene el mismo resultado.

### HU-04 — Ambientes dev, stage y prod aislados en la nube

**Como** equipo de plataforma, **quiero** tres ambientes aislados (`dev`, `stage`, `prod`) aprovisionados por script, **para** promover versiones entre ellos sin que un ambiente afecte a otro.

Criterios de aceptación:

1. **Aprovisionamiento parametrizado:** `infra/bootstrap/bootstrap-env.sh <dev|stage|prod>` prepara el host del ambiente con Docker y Docker Compose y crea su red y volúmenes propios.
2. **Aislamiento:** con los tres ambientes corriendo, detener o redesplegar `dev` no interrumpe `stage` ni `prod`; cada ambiente usa sus propias bases de datos y credenciales.
3. **Acceso restringido:** las reglas de red (security groups) de cada ambiente solo exponen los puertos públicos documentados.

---

## 2. Decisiones de diseño (optimizadas para la rúbrica)

| Decisión | Elección | Justificación en la rúbrica |
|:---|:---|:---|
| Proveedor | **AWS EC2** | El enunciado lo menciona explícitamente (`p. ej. AWS EC2`) y evita penalizaciones por infraestructura no nube. |
| Modelo de aislamiento | **3 hosts EC2 separados** (`dev`, `stage`, `prod`) | La rúbrica premia “tres ambientes aislados” y “solo los puertos públicos documentados”; hosts + Security Groups separados es el aislamiento más fuerte y demostrable. |
| SO | Ubuntu 24.04 LTS | Docker Engine y Compose plugin con soporte oficial; scripts reproducibles. |
| Contenedores | Jenkins LTS (`jenkins/jenkins:lts-jdk21`), SonarQube LTS Community + PostgreSQL propio, Nexus 3 | Versiones LTS reproducibles; Sonar con PostgreSQL evita H2 embebido (no soportado en producción). |
| Orquestación | Docker Compose por proyecto (`-p circleguard-<env>`) | Cumple E1.2 sin adelantar Terraform/Kubernetes de la Parte 2. |
| Puertos | Base por ambiente con **offset** (`dev +0`, `stage +1000`, `prod +2000`) | Permite validar en un solo host como fallback y evita colisiones si se consolida. |
| Secretos | `.env` generados localmente, `chmod 600`, fuera de Git; `.env.example` versionado | La rúbrica penaliza credenciales en el repositorio. |
| Alcance del despliegue | HU-04 aprovisiona host, red, volúmenes y **middleware** para demostrar aislamiento; los microservicios llegan con E1.3 | HU-04 no exige imágenes de la aplicación; el middleware basta para probar aislamiento sin bloquear por E1.3. |

### Arquitectura objetivo

```
                         Internet / GitHub webhook
                                    |
                    +---------------+---------------+
                    |               |               |
              [EC2 dev]        [EC2 stage]      [EC2 prod]
              SG: dev          SG: stage        SG: prod
                    |               |               |
          Docker + Compose   Docker + Compose  Docker + Compose
          red: dev-net       red: stage-net    red: prod-net
          vols: dev-*        vols: stage-*     vols: prod-*
```

Cada host corre su propio stack; los Security Groups no permiten tráfico entre ambientes.

---

## 3. Estructura de archivos a crear

```
infra/
├── README.md                     # este plan (se completa en el commit 5)
├── bootstrap/
│   ├── bootstrap-all.sh          # HU-03: Jenkins + SonarQube + Nexus
│   ├── bootstrap-env.sh          # HU-04: host de un ambiente
│   ├── lib/
│   │   └── common.sh             # utilidades, idempotencia, Docker, healthchecks
│   └── README.md                 # guía operativa (commit 5)
├── compose/
│   └── ci/
│       └── docker-compose.yml    # stack CI (commit 2)
├── envs/
│   ├── dev.env.example
│   ├── stage.env.example
│   └── prod.env.example
├── security/
│   ├── security-groups.md        # definición de reglas por ambiente
│   └── apply-ufw.sh              # defensa en profundidad en el host
└── cloud/
    └── aws/
        └── provision-hosts.sh    # opcional: crea 3 EC2 + SGs por CLI

deploy/
└── compose/
    ├── base/docker-compose.yml           # middleware común
    ├── dev/docker-compose.yml            # overrides dev
    ├── stage/docker-compose.yml          # overrides stage
    └── prod/docker-compose.yml           # overrides prod

docs/
├── infra/arquitectura.md         # diagrama y decisiones
└── evidencias/HU-03-HU-04/       # capturas, salidas de verificación
```

---

## 4. Plan por commits

### Commit 1 — `chore(infra): scaffold base structure and bootstrap utilities`

**Objetivo:** preparar el terreno sin lógica de aprovisionamiento.

Archivos:

- `infra/bootstrap/lib/common.sh`: `set -Eeuo pipefail`, logging, `require_cmd`, `ensure_docker` (instalación idempotente por `command -v`), `load_env`, `flock`, `retry`, `wait_http`, `ensure_network`, `ensure_volume`.
- `infra/envs/*.env.example`: variables por ambiente (proyecto, red, prefijo de volúmenes, credenciales placeholder, offsets de puertos).
- `.gitignore`: ignorar `infra/**/.env`, `infra/**/.env.*` conservando `!*.env.example`.
- `infra/security/security-groups.md`: borrador de reglas por ambiente.

Criterios cubiertos: base de HU-03/HU-04 (idempotencia y secretos).

Verificación: `bash -n` y `shellcheck` sobre `common.sh`; `git check-ignore infra/envs/dev.env`.

---

### Commit 2 — `feat(infra): provision Jenkins, SonarQube and Nexus with idempotent Compose (HU-03)`

**Objetivo:** cerrar por completo HU-03.

Archivos:

- `infra/compose/ci/docker-compose.yml`:
  - `jenkins` — puertos `8080`/`50000`, volumen `jenkins_home`, healthcheck.
  - `sonarqube` — puerto `9000`, volúmenes `sonarqube_data/extensions/logs`, healthcheck, `depends_on: sonar-db`.
  - `sonar-db` — PostgreSQL 16 dedicado a Sonar, volumen `sonar_db`.
  - `nexus` — puerto `8081` (+ `5000` registry interno), volumen `nexus_data`, healthcheck.
- `infra/bootstrap/bootstrap-all.sh`: preflight → `ensure_docker` → `sysctl vm.max_map_count` idempotente → red/volúmenes → `docker compose -p circleguard-ci up -d --remove-orphans` → espera de healthchecks → resumen de URLs.
- `.env.example` del stack CI y generación de `.env` solo si no existe.

Criterios cubiertos: HU-03 criterios 1 y 2.

Verificación: ejecutar dos veces y comprobar `0` de salida, mismos contenedores/volúmenes, datos persistidos; `curl` a `:8080`, `:9000`, `:8081`.

---

### Commit 3 — `feat(infra): provision isolated dev, stage and prod environments (HU-04)`

**Objetivo:** cerrar el aprovisionamiento y aislamiento de HU-04.

Archivos:

- `infra/bootstrap/bootstrap-env.sh`: valida `dev|stage|prod`, carga `infra/envs/<env>.env`, instala Docker, crea red `circleguard-<env>` y volúmenes `circleguard-<env>-*`, despliega `-p circleguard-<env>` y espera healthchecks.
- `deploy/compose/base/docker-compose.yml`: PostgreSQL, Neo4j, Kafka/Zookeeper, Redis y OpenLDAP.
- `deploy/compose/{dev,stage,prod}/docker-compose.yml`: overrides con credenciales, nombres de red/volumen y offset de puertos.
- `infra/security/apply-ufw.sh`: política `deny incoming`, permite solo `22` (CIDR admin), `80/443` y puertos documentados; idempotente.
- `infra/cloud/aws/provision-hosts.sh` (opcional): crea 3 EC2 + 3 Security Groups vía AWS CLI.
- `infra/envs/*.env.example` completos.

Criterios cubiertos: HU-04 criterios 1, 2 y 3.

Verificación: tres redes/volúmenes/credenciales distintas; `docker compose -p circleguard-dev down` no afecta a stage/prod; revisión de reglas de Security Group/ufw.

---

### Commit 4 — `test(infra): add automated idempotency and isolation checks`

**Objetivo:** convertir los criterios de aceptación en evidencia ejecutable.

Archivos:

- `infra/bootstrap/verify-idempotency.sh`: ejecuta el bootstrap dos veces, compara IDs de contenedores/volúmenes y hace `curl` a los endpoints; falla si hay duplicados o pérdida de datos.
- `infra/bootstrap/verify-isolation.sh`: levanta/valida los tres ambientes, detiene `dev`, comprueba `stage` y `prod` por HTTP y restaura `dev`.
- `docs/evidencias/HU-03-HU-04/`: salidas esperadas y checklist.

Criterios cubiertos: HU-03 criterio 2 y HU-04 criterio 2 de forma automatizada.

Verificación: ambos scripts terminan con `0` y dejan log en `docs/evidencias/HU-03-HU-04/`.

---

### Commit 5 — `docs(infra): document operations, ports, costs and rubric evidence`

**Objetivo:** cumplir el criterio “sin pasos manuales no documentados” y facilitar la evaluación.

Archivos:

- `infra/bootstrap/README.md`: requisitos, creación de hosts, ejecución paso a paso, URLs/puertos, obtención de passwords iniciales, re-ejecución, troubleshooting y destrucción de recursos.
- `docs/infra/arquitectura.md`: diagrama de hosts, redes, volúmenes y Security Groups.
- `infra/README.md`: se reemplaza este plan por la guía definitiva (o se enlaza).
- Checklist de evidencias para el informe PDF: capturas de `docker ps`, volúmenes, re-ejecución con código `0`, prueba de aislamiento y reglas de red.
- Nota de costos y apagado/destrucción de recursos (requisito del enunciado).

Criterios cubiertos: HU-03 criterio 3 y documentación E1.8.

Verificación: un integrante nuevo reproduce el entorno siguiendo solo el README.

---

## 5. Matriz de trazabilidad con la rúbrica

| Criterio de evaluación | Evidencia generada | Commit |
|:---|:---|:---|
| Tres ambientes aislados | 3 EC2 + 3 SGs + redes/volúmenes por ambiente | 3 |
| Scripts idempotentes que reconstruyen desde cero | `bootstrap-all.sh` + `verify-idempotency.sh` | 2, 4 |
| Jenkins, SonarQube y registro operativos | Compose CI + capturas de URLs | 2 |
| Puertos públicos documentados | `security-groups.md`, `apply-ufw.sh`, README | 3, 5 |
| Sin credenciales en el repositorio | `.env` ignorados y `.env.example` | 1, 2, 3 |
| Reproducibilidad por un tercero | README operativo + evidencias | 5 |

---

## 6. Estrategia de idempotencia

1. `set -Eeuo pipefail` y `flock` para evitar ejecuciones concurrentes.
2. Instalación condicionada a `command -v docker` / `docker compose version`.
3. Nombres fijos de proyecto, red y volúmenes; `docker compose up -d --remove-orphans` converge sin recrear innecesariamente.
4. Nunca `docker compose down -v` en los scripts.
5. Archivos de configuración y `.env` se generan **solo si no existen**.
6. `sysctl` y reglas de firewall con verificación previa.
7. Healthchecks + `wait_http`/`docker inspect` con reintentos antes de reportar éxito.
8. Código de salida `0` en la segunda ejecución y datos persistidos.

---

## 7. Puertos de referencia

| Componente | Puerto(s) | Exposición |
|:---|:---|:---|
| Jenkins | 8080, 50000 | UI restringida / agente |
| SonarQube | 9000 | UI restringida |
| Nexus | 8081, 5000 (registry) | UI restringida / registry interno |
| auth-service | 8180 | Según ambiente |
| identity-service | 8083 | Según ambiente |
| promotion-service | 8088 | Según ambiente |
| form-service | 8086 | Según ambiente |
| gateway-service | 8087 | Según ambiente |
| dashboard-service | 8084 | Según ambiente |
| file-service | 8085 | Según ambiente |
| notification-service | 8082 | Según ambiente |
| PostgreSQL | 5432 | Solo red interna |
| Neo4j | 7474, 7687 | Solo red interna |
| Kafka | 9092 | Solo red interna |
| Redis | 6379 | Solo red interna |
| OpenLDAP | 389, 636 | Solo red interna |

Solo `22` (CIDR del equipo), `80/443` y los puertos explícitamente documentados se exponen a Internet.

---

## 8. Riesgos y mitigaciones

| Riesgo | Mitigación |
|:---|:---|
| Costo de 3 EC2 | `t3.medium` para dev/stage y `t3.large` para prod; apagado fuera de horario y `terraform destroy`/terminación al finalizar. |
| SonarQube no arranca por `vm.max_map_count` | El bootstrap aplica el `sysctl` de forma idempotente y valida con healthcheck. |
| Jenkins inaccesible por webhook | Documentar IP elástica/URL pública y regla de Security Group; opcional túnel para pruebas. |
| Script no idempotente | `verify-idempotency.sh` como puerta en el pipeline (HU posterior). |
| Credenciales filtradas | `.env` fuera de Git y `.env.example` solo con placeholders. |

---

## 9. Definición de terminado (DoD)

- [ ] `bootstrap-all.sh` y `bootstrap-env.sh` pasan `shellcheck` y `bash -n`.
- [ ] Segunda ejecución de ambos scripts devuelve código `0` sin duplicar recursos ni perder datos.
- [ ] Jenkins, SonarQube y Nexus responden en sus URLs documentadas.
- [ ] Los tres ambientes usan redes, volúmenes y credenciales independientes.
- [ ] Detener `dev` no afecta a `stage` ni `prod`.
- [ ] Solo los puertos documentados están expuestos.
- [ ] No hay secretos en el repositorio.
- [ ] Un integrante nuevo reproduce el entorno siguiendo únicamente el README.
- [ ] Evidencias y capturas archivadas en `docs/evidencias/HU-03-HU-04/`.
