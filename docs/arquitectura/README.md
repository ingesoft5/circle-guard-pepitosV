# Arquitectura de CircleGuard

Cómo está construida y desplegada la solución. Lo que se describe aquí se comparó con los tres
clústeres, los estados de Terraform y las instancias de AWS el 22 de noviembre de 2026, con la
versión `0.7.0` desplegada en `dev`, `stage` y `prod`. El resultado de esa comparación está en
[`coherencia.md`](coherencia.md).

| Tema | Documento |
|:---|:---|
| Infraestructura como código (Terraform) | [`infraestructura.md`](infraestructura.md) |
| Kubernetes (k3s y chart de Helm) | [`kubernetes.md`](kubernetes.md) |
| Observabilidad | [`observabilidad.md`](observabilidad.md) |
| Seguridad | [`seguridad.md`](seguridad.md) |
| Service mesh (Linkerd y canary con Flagger) | [`service-mesh.md`](service-mesh.md) |
| Patrones de diseño | [`../patrones/existentes.md`](../patrones/existentes.md) |
| Revisión cruzada con lo desplegado | [`coherencia.md`](coherencia.md) |

Los PDFs de esta carpeta (`circleguard_deployment.pdf`, `pipeline_flow.pdf`) son los diagramas de
la Parte 1. Describen el despliegue con Docker Compose por SSH en un host por ambiente y un solo
servidor de CI. Se conservan como historia; los diagramas vigentes son los de este documento.

## 1. Vista lógica

Ocho microservicios Spring Boot 3 (Java 21), un cliente web Expo servido por nginx y el middleware
de datos y mensajería. El nginx del cliente web es el único punto de entrada: sirve la aplicación y
reenvía `/svc/<servicio>/` a cada microservicio. Las flechas continuas son llamadas síncronas; las
punteadas, eventos de Kafka.

```mermaid
flowchart LR
    USER(["Navegador"])

    subgraph EDGE["Entrada"]
        WEB["web<br/>nginx :8080<br/>/svc/*"]
    end

    subgraph APP["Microservicios Spring Boot"]
        AUTH["auth :8180"]
        IDENT["identity :8083"]
        PROM["promotion :8088"]
        FORM["form :8086"]
        NOTI["notification :8082"]
        DASH["dashboard :8084"]
        GATE["gateway :8087"]
        FILE["file :8085"]
    end

    KAFKA{{"Kafka + Zookeeper"}}
    PG[("PostgreSQL<br/>una base por servicio")]
    NEO[("Neo4j<br/>grafo de contactos")]
    REDIS[("Redis<br/>estado de salud")]
    LDAP[("OpenLDAP")]
    JAEGER["Jaeger<br/>OTLP :4318"]

    USER --> WEB
    WEB -->|"/svc/auth, identity,<br/>promotion, form, gateway"| APP

    AUTH -->|REST| IDENT
    AUTH -->|LDAP| LDAP
    DASH -->|REST| PROM
    NOTI -->|REST| AUTH

    FORM -.->|"survey.submitted<br/>(outbox)"| KAFKA
    KAFKA -.-> PROM
    PROM -.->|"promotion.status.changed<br/>alert.priority, circle.fenced"| KAFKA
    KAFKA -.-> NOTI
    IDENT -.->|"audit.identity.accessed"| KAFKA

    AUTH --- PG
    IDENT --- PG
    PROM --- PG
    FORM --- PG
    DASH --- PG
    PROM --- NEO
    PROM -->|escribe estado| REDIS
    GATE -->|"lee estado<br/>(circuit breaker)"| REDIS

    FORM -. trazas .-> JAEGER
    PROM -. trazas .-> JAEGER
    NOTI -. trazas .-> JAEGER
```

| Componente | Responsabilidad | Datos |
|:---|:---|:---|
| `auth` | Login LDAP con respaldo local, JWT y QR firmado de acceso | `circleguard_auth` |
| `identity` | Bóveda de identidad: seudónimos con sal y cifrado | `circleguard_identity` |
| `promotion` | Máquina de estados de salud y cercos sobre el grafo de contactos | `circleguard_promotion`, Neo4j, Redis |
| `form` | Cuestionarios de salud; publica eventos con transactional outbox | `circleguard_form` |
| `notification` | Despacho multicanal de avisos a partir de eventos | — |
| `dashboard` | Analítica de focos con k-anonimato | `circleguard_dashboard` |
| `gateway` | Validación del QR en la entrada del campus | Redis (lectura) |
| `file` | Subida y descarga de certificados y documentos | Bucket S3 del ambiente, con el rol del nodo |
| `web` | Cliente web y reverse proxy | — |

`file` guarda los archivos en el bucket S3 del ambiente con el rol de IAM del nodo, sin llaves
(entra con la próxima versión; la `0.7.0` todavía los deja en un `emptyDir` del pod). Sin bucket
configurado, como en Docker Compose, los guarda en el disco local.

El detalle de cada patrón (database-per-service, eventos, cache-aside, máquina de estados,
seudonimización, circuit breaker, configuración externa, outbox) y su evidencia en el código está en
[`../patrones/existentes.md`](../patrones/existentes.md).

## 2. Vista de despliegue en AWS

Todo corre en `us-east-1`, en instancias `m7i-flex.large` (2 vCPU, 8 GB). Hay dos grupos:

- **CI**, en la VPC por defecto (`172.31.0.0/16`), creado con los scripts de
  `infra/cloud/aws/` y Docker Compose: Jenkins (controlador y dos workers), SonarQube y Nexus.
- **Ambientes**, uno por VPC, creados con Terraform: un clúster k3s de un nodo con Elastic IP.

Cada VPC de ambiente tiene un peering con la VPC de CI. Por ahí Jenkins llega al API server
(6443) y al SSH del nodo, y el nodo descarga las imágenes del registro de Nexus (`5000`) por IP
privada.

```mermaid
flowchart TB
    DEV_USER(["Equipo"])
    GH["GitHub<br/>repo, PR, releases,<br/>deployments"]
    DISCORD["Discord<br/>pipeline y alertas"]
    INTERNET(["Usuarios"])

    subgraph CIVPC["VPC de CI · 172.31.0.0/16 · scripts de infra/cloud/aws"]
        CI["circleguard-ci<br/>Jenkins controlador :8080"]
        AG1["circleguard-ci-agent-1<br/>worker"]
        AG2["circleguard-ci-agent-2<br/>worker"]
        TOOLS["circleguard-tools<br/>SonarQube :9000<br/>Nexus :8081, registro :5000"]
        LEGACY["circleguard-dev / stage / prod<br/>hosts Compose de la Parte 1<br/>(detenidos)"]
    end

    subgraph DEVVPC["VPC dev · 10.10.0.0/16 · Terraform"]
        K3DEV["circleguard-k3s-dev<br/>k3s 1.30 · EIP"]
    end
    subgraph STGVPC["VPC stage · 10.20.0.0/16 · Terraform"]
        K3STG["circleguard-k3s-stage<br/>k3s 1.30 · EIP"]
    end
    subgraph PRDVPC["VPC prod · 10.30.0.0/16 · Terraform"]
        K3PRD["circleguard-k3s-prod<br/>k3s 1.30 · EIP"]
    end

    subgraph AWSSVC["Servicios de AWS por ambiente · Terraform"]
        S3STATE[("S3 + DynamoDB<br/>estado y bloqueo")]
        CW["CloudWatch<br/>alarmas del nodo · SNS"]
        ECR["ECR<br/>(sin consumidor)"]
        S3FILES[("Bucket S3 de archivos<br/>rol del nodo")]
    end

    DEV_USER -->|push / PR| GH
    GH -->|webhook| CI
    CI --> AG1 & AG2
    AG1 & AG2 -->|imágenes| TOOLS
    CI -->|"helm, kubectl :6443<br/>peering"| K3DEV & K3STG & K3PRD
    K3DEV & K3STG & K3PRD -->|"pull :5000<br/>peering"| TOOLS
    CI -->|tags, releases, deployments| GH
    CI -->|fallos del pipeline| DISCORD
    K3DEV & K3STG & K3PRD -->|Alertmanager| DISCORD
    INTERNET -->|"80 / 443"| K3DEV & K3STG & K3PRD
    CW -.-> K3DEV & K3STG & K3PRD
    K3DEV & K3STG & K3PRD -->|"file-service<br/>IMDSv2"| S3FILES
```

| Host | Rol | Red | Lo crea |
|:---|:---|:---|:---|
| `circleguard-ci` | Jenkins controlador (orquesta, despliega, aprueba) y caché de Gradle | VPC de CI, Elastic IP | `provision-ci-hosts.sh` + `infra/compose/ci` |
| `circleguard-ci-agent-1`, `-2` | Workers de Jenkins (compilan, prueban, publican) | VPC de CI | `provision-ci-hosts.sh` (`CI_AGENT_COUNT`) + `infra/compose/ci-agent` |
| `circleguard-tools` | SonarQube, Nexus y registro Docker | VPC de CI | `provision-ci-hosts.sh` + `bootstrap-all.sh` (`CI_ROLE=tools`) |
| `circleguard-k3s-<env>` | Clúster k3s del ambiente | VPC propia, Elastic IP | Terraform, `infra/terraform/envs/<env>` |
| `circleguard-dev`, `-stage`, `-prod` | Ambientes Compose de la Parte 1 | VPC de CI | `provision-env-hosts.sh`; detenidos, no reciben despliegues |

## 3. Dentro de un clúster

Los tres clústeres son iguales; cambian el tamaño de los discos y las réplicas mínimas del HPA
(ver [`kubernetes.md`](kubernetes.md)).

```mermaid
flowchart LR
    NET(["Internet :80 / :443"])

    subgraph NODE["Nodo k3s"]
        subgraph KS["kube-system"]
            TRAEFIK["Traefik<br/>Ingress"]
            SEALED["sealed-secrets<br/>controller"]
            MS["metrics-server"]
        end

        subgraph APPNS["circleguard-&lt;env&gt;"]
            WEB2["web"]
            SVCS["8 servicios Spring<br/>HPA en form y gateway"]
            MW["postgres · neo4j (PVC)<br/>kafka · zookeeper<br/>redis · openldap · jaeger"]
            SEC["Secret circleguard-secrets<br/>(desde SealedSecret)"]
        end

        subgraph MON["monitoring"]
            PROMETHEUS["Prometheus<br/>/prometheus"]
            GRAFANA["Grafana<br/>/grafana"]
            ALERT["Alertmanager<br/>/alertmanager"]
            LOKI["Loki + Promtail"]
        end
    end

    NET --> TRAEFIK
    TRAEFIK -->|"/"| WEB2
    TRAEFIK -->|"Basic Auth"| PROMETHEUS & ALERT
    TRAEFIK --> GRAFANA
    WEB2 -->|"/svc/*"| SVCS
    SVCS --> MW
    SEALED -->|crea| SEC
    SEC -.-> SVCS & MW
    PROMETHEUS -->|ServiceMonitor| SVCS
    PROMETHEUS --> ALERT
    LOKI -->|logs de los pods| APPNS
    GRAFANA --> PROMETHEUS & LOKI
    MS --> SVCS
```

## 4. Flujo de entrega

El `Jenkinsfile` construye solo lo que cambió y promueve la misma imagen por los tres ambientes. El
detalle de cada etapa está en [`../operaciones/jenkins-pipeline.md`](../operaciones/jenkins-pipeline.md).

```mermaid
flowchart LR
    PR["PR hacia dev"] --> CHK["Secret scan · tamaño del PR<br/>Trivy fs e IaC<br/>build, tests, Sonar por servicio"]
    CHK -->|merge| DEVB["dev<br/>imágenes X.Y.Z-SNAPSHOT"]
    DEVB -->|"etiqueta release"| REL["release/X.Y.Z → main<br/>RC X.Y.Z-rc en stage + E2E"]
    REL -->|merge| MAIN["main<br/>promueve el RC a X.Y.Z<br/>tag vX.Y.Z"]
    MAIN --> D1["deploy dev<br/>release notes + E2E"]
    D1 --> D2["deploy stage"]
    D2 --> APPROVE{"Approve production<br/>solo admin"}
    APPROVE --> D3["deploy prod"]
    D1 & D2 & D3 -.-> LOG["registro de despliegues<br/>GitHub Deployments"]
```

Cada despliegue usa `helm upgrade --atomic --wait`: si un rollout no termina, Helm vuelve a la
revisión anterior. El rollback manual es el job `circleguard-rollback`
([`../operaciones/rollback.md`](../operaciones/rollback.md)).

## 5. Decisiones y límites

| Decisión | Por qué | Costo |
|:---|:---|:---|
| Un clúster k3s de un nodo por ambiente | Aislamiento completo entre ambientes dentro del plan gratuito de AWS | Sin alta disponibilidad: si el nodo cae, cae el ambiente |
| Middleware dentro del clúster | Un solo chart levanta el ambiente completo, sin servicios gestionados | Datos en `local-path` del nodo; respaldos manuales |
| Imágenes en Nexus, no en ECR | Nexus ya existía en la Parte 1 y el peering da acceso privado | El módulo `registry` crea repositorios ECR que no se usan |
| Hosts de CI fuera de Terraform | Se crearon en la Parte 1 con scripts idempotentes | Terraform solo los lee (`data`) para el peering |
| TLS con CA de laboratorio (en `dev`, entra con la versión `0.8.0`) | No hay dominio propio y `sslip.io` choca con el límite de Let's Encrypt | El navegador no confía en el certificado sin instalar la CA; hoy el Ingress sirve HTTP |

El resumen ejecutivo y los próximos pasos están en [`../executive-summary.md`](../executive-summary.md).
