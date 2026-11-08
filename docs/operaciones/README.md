# README operativo

Guía para dejar CircleGuard funcionando desde una cuenta de nube vacía y el repositorio clonado, y para operar el entorno que ya existe. Al terminar quedan el host de CI (Jenkins, SonarQube y el registro de Nexus) y un host por ambiente (`dev`, `stage` y `prod`).

Los scripts son idempotentes: repetir un paso no duplica instancias, contenedores ni reglas. No se versiona ningún secreto. Las contraseñas viven en un `.env` del host, con permisos `600`.

El detalle de cada pieza está en:

- [infra/cloud/aws/README.md](../../infra/cloud/aws/README.md): máquinas, Elastic IP y operación diaria.
- [infra/compose/ci/README.md](../../infra/compose/ci/README.md): stack de CI, costos y diagnóstico.
- [infra/security/security-groups.md](../../infra/security/security-groups.md): qué puerto se expone y desde dónde.
- [jenkins-pipeline.md](jenkins-pipeline.md): job multibranch, webhook, release y rollback.
- [../release-notes/etiquetado.md](../release-notes/etiquetado.md): cómo se calcula la versión.
- [../release-notes/politica-cambios.md](../release-notes/politica-cambios.md): quién aprueba cada ambiente.

## Prerrequisitos

En el equipo desde el que se aprovisiona:

- Git y el repositorio clonado.
- AWS CLI, con permisos de EC2 y de lectura de SSM, autenticado contra la cuenta (`aws sts get-caller-identity`).
- `jq` y `curl`. `jq` solo lo usa `allow-github-webhooks.sh`.
- OpenSSH y `tar`.
- Bash. En Windows, Git Bash o WSL: los scripts de `infra/` no corren en PowerShell.
- Un token de GitHub con escritura sobre el repositorio (classic: `repo`; fine-grained: *Contents: Read and write* y *Pull requests: Read and write*) y `admin:repo_hook` para registrar el webhook.

Cada host que crean los scripts es Ubuntu 24.04 y necesita salida a Internet. La región por defecto es `us-east-1`.

## Variables y secretos

Ningún valor de esta tabla va en Git. `__GENERATE__` lo sustituye el bootstrap por una contraseña aleatoria. `__SET_ME__` lo escribe quien opera, antes de volver a ejecutar el bootstrap de Jenkins.

### Equipo y cuenta

| Nombre | Dónde se usa | Quién lo define |
|:---|:---|:---|
| `AWS_REGION` | Todos los scripts de AWS | Opcional. Por defecto `us-east-1` |
| `ADMIN_CIDR` | Security Groups | Obligatorio. IP pública de quien administra, en CIDR, por ejemplo `<ip>/32` |
| `CI_SUBNET_ID`, `ENV_SUBNET_ID` | Alta de hosts | Obligatorio. Pueden ser la misma subred pública |
| `CI_KEY_NAME`, `ENV_KEY_NAME` | Alta de hosts | Obligatorio. Key pair ya creado en la cuenta |
| `CI_INSTANCE_TYPE` | Host de CI | Opcional. Por defecto `t3.large`. Si el plan lo rechaza, `m7i-flex.large` |
| `ENV_INSTANCE_TYPE` | Ambientes | Opcional. Por defecto `m7i-flex.large` |
| `ENV_ROOT_VOLUME_GB` | Ambientes | Opcional. Por defecto `30` |
| `JENKINS_CIDR` | SSH de Jenkins hacia cada ambiente | IP privada de CI con `/32` |

### Host de CI (`infra/compose/ci/.env`)

La plantilla es [infra/compose/ci/.env.example](../../infra/compose/ci/.env.example). El archivo real lo crea `bootstrap-all.sh`.

| Nombre | Usuario | Origen |
|:---|:---|:---|
| `JENKINS_ADMIN_USER` | `admin` | Fijo |
| `JENKINS_ADMIN_PASSWORD` | `admin` | `__GENERATE__` |
| `JENKINS_DEV_USER` | `dev` | Fijo. Usuario sin permisos de administrador (HU-21) |
| `JENKINS_DEV_PASSWORD` | `dev` | `__GENERATE__` |
| `GITHUB_OWNER` | | `ingesoft5` |
| `GITHUB_REPO` | | `circle-guard-pepitosV` |
| `GITHUB_USER` | cuenta de GitHub | `__SET_ME__` |
| `GITHUB_TOKEN` | esa cuenta | `__SET_ME__`. Sin él, Jenkins no escanea el repositorio |
| `CI_DOMAIN` | | IP pública de CI o su DNS. Define la URL del webhook |
| `DISCORD_WEBHOOK_URL` | | `__SET_ME__`. Sin ella no hay avisos a Discord; el resto del pipeline sigue |
| `E2E_USERNAME` | `health_user` | Usuario de prueba del cliente web |
| `E2E_PASSWORD` | ese usuario | `__SET_ME__`. Sin ella falla la etapa E2E |
| `SONAR_DB_USER` | `sonar` | Fijo |
| `SONAR_DB_PASSWORD` | `sonar` | `__GENERATE__` |
| `SONAR_ADMIN_PASSWORD` | `admin` | `__GENERATE__` |
| `SONAR_TOKEN` | `admin` | Lo escribe `bootstrap-sonarqube.sh` |
| `NEXUS_ADMIN_PASSWORD` | `admin` | `__GENERATE__` |
| `NEXUS_CI_USER` | `ci-publisher` | Fijo. Push y pull en el registro |
| `NEXUS_CI_PASSWORD` | `ci-publisher` | `__GENERATE__`. En Jenkins queda como `nexus-ci` |
| `NEXUS_PULL_USER` | `deploy-puller` | Fijo. Solo pull |
| `NEXUS_PULL_PASSWORD` | `deploy-puller` | `__GENERATE__`. Se pasa a `bootstrap-env.sh` |
| `NEXUS_REGISTRY` | | En CI, `localhost:5000`. Los ambientes usan `<ip-privada-de-ci>:5000` |
| `DEPLOY_HOST_DEV` | | IP privada de `dev` |
| `DEPLOY_HOST_STAGE` | | IP privada de `stage` |
| `DEPLOY_HOST_PROD` | | IP privada de `prod` |
| `ROLLBACK_BRANCH` | | Por defecto `main`. Solo se cambia para probar el job de rollback |

La llave SSH de cada ambiente no va en el `.env`. Se genera fuera del repositorio y `load-deploy-credential.sh` la guarda en `infra/compose/ci/secrets/` del host de CI. Jenkins la usa como `deploy-ssh-<ambiente>`.

### Cada ambiente (`deploy/compose/<ambiente>/.env`)

La plantilla es `deploy/compose/<ambiente>/.env.example`. `bootstrap-env.sh` genera el archivo en el host, en `/opt/circleguard/deploy/compose/<ambiente>/.env`.

| Nombre | Origen |
|:---|:---|
| `POSTGRES_PASSWORD` | `__GENERATE__` |
| `NEO4J_PASSWORD` | `__GENERATE__` |
| `REDIS_PASSWORD` | `__GENERATE__` |
| `LDAP_ADMIN_PASSWORD` | `__GENERATE__` |
| `JWT_SECRET` | `__GENERATE__` |
| `QR_SECRET` | `__GENERATE__` |
| `VAULT_SECRET`, `VAULT_SALT`, `VAULT_HASH_SALT` | `__GENERATE__` |
| `IMAGE_REGISTRY` | Se fija con `NEXUS_REGISTRY` al ejecutar `bootstrap-env.sh` |
| `IMAGE_TAG` | Lo cambia el despliegue. La plantilla trae `0.1.0` |
| `HTTP_PORT` | `80`. Es la URL pública de la aplicación |
| `ADMIN_CIDR` | El mismo CIDR de administración |

## URLs

La IP pública de CI no cambia: tiene Elastic IP. La IP pública de cada ambiente cambia cada vez que se enciende. La IP privada no cambia y es la que usa Jenkins para desplegar.

Para leer las IP actuales:

```bash
infra/cloud/aws/hosts.sh status
```

El comando escribe `infra/cloud/aws/hosts.env`. Ese archivo está ignorado por Git.

### Entorno en uso

Leído el 7 de noviembre de 2026. `dev`, `stage` y `prod` estaban apagados, así que no tenían IP pública. Antes de abrir una URL de ambiente, ejecutar `hosts.sh status` y usar la IP pública que aparezca.

| Qué | URL |
|:---|:---|
| Jenkins | `http://184.196.87.200:8080` |
| Webhook de GitHub | `http://184.196.87.200:8080/github-webhook/` |
| SonarQube | `http://184.196.87.200:9000` |
| Nexus | `http://184.196.87.200:8081` |
| Registro Docker | `184.196.87.200:5000` |
| CI, IP privada | `172.31.14.248` |
| Aplicación `dev` | `http://<DEV_PUBLIC_IP>/` |
| Aplicación `stage` | `http://<STAGE_PUBLIC_IP>/` |
| Aplicación `prod` | `http://<PROD_PUBLIC_IP>/` |
| `dev`, IP privada | `172.31.9.168` |
| `stage`, IP privada | `172.31.5.2` |
| `prod`, IP privada | `172.31.14.239` |

Jenkins, SonarQube, Nexus y el registro solo aceptan tráfico desde `ADMIN_CIDR` (el puerto `8080` de Jenkins también acepta los rangos de webhooks de GitHub). La aplicación de cada ambiente publica el puerto `80`. El `443` está abierto, pero el compose no termina TLS. Las APIs directas (`8180` y `8082`–`8088`) solo responden desde `ADMIN_CIDR`. PostgreSQL, Neo4j, Kafka, Redis, LDAP y el socket de Docker no se publican.

Si se repite el alta en otra cuenta, las IP de esta tabla no sirven. Las nuevas salen de los scripts y de `hosts.sh status`.

## Reproducción desde cero

Trabajar desde la raíz del clon. `<clave>.pem` es la llave del key pair, y no se copia al repositorio.

### 1. Red y llave en una cuenta vacía

Los scripts de alta no crean la VPC: necesitan una subred con salida a Internet y un key pair.

```bash
aws ec2 create-key-pair --region us-east-1 --key-name circleguard \
  --query 'KeyMaterial' --output text > circleguard.pem
chmod 600 circleguard.pem

VPC=$(aws ec2 create-vpc --cidr-block 10.0.0.0/16 --query 'Vpc.VpcId' --output text)
aws ec2 modify-vpc-attribute --vpc-id "$VPC" --enable-dns-support "{\"Value\":true}"
aws ec2 modify-vpc-attribute --vpc-id "$VPC" --enable-dns-hostnames "{\"Value\":true}"
IGW=$(aws ec2 create-internet-gateway --query 'InternetGateway.InternetGatewayId' --output text)
aws ec2 attach-internet-gateway --internet-gateway-id "$IGW" --vpc-id "$VPC"
SUBNET=$(aws ec2 create-subnet --vpc-id "$VPC" --cidr-block 10.0.1.0/24 \
  --query 'Subnet.SubnetId' --output text)
RT=$(aws ec2 create-route-table --vpc-id "$VPC" --query 'RouteTable.RouteTableId' --output text)
aws ec2 create-route --route-table-id "$RT" --destination-cidr-block 0.0.0.0/0 \
  --gateway-id "$IGW"
aws ec2 associate-route-table --route-table-id "$RT" --subnet-id "$SUBNET"

export AWS_REGION=us-east-1
export ADMIN_CIDR="$(curl -fsS https://checkip.amazonaws.com)/32"
export CI_SUBNET_ID="$SUBNET"
export ENV_SUBNET_ID="$SUBNET"
export CI_KEY_NAME=circleguard
export ENV_KEY_NAME=circleguard
```

`ADMIN_CIDR` tiene que ser la IP pública desde la que se abre SSH y las consolas. Si cambia, `infra/cloud/aws/hosts.sh allow` autoriza la IP nueva sin acumular reglas viejas del mismo dueño.

### 2. Host de CI y Elastic IP

```bash
infra/cloud/aws/provision-ci-hosts.sh
ALLOC=$(aws ec2 allocate-address --domain vpc --query 'AllocationId' --output text)
aws ec2 associate-address --instance-id <id-de-ci> --allocation-id "$ALLOC"
aws ec2 describe-addresses --allocation-ids "$ALLOC" \
  --query 'Addresses[0].PublicIp' --output text
```

El script imprime el id de la instancia (`i-...`) y una IP pública inicial. `<id-de-ci>` es ese id. La IP que imprime `describe-addresses` es la Elastic IP y es la que queda en las URL. Sin ella, apagar CI cambia la IP y rompe el webhook.

### 3. Jenkins, SonarQube y Nexus

```bash
tar czf - infra | ssh -i circleguard.pem ubuntu@<ip-publica-de-ci> \
  'mkdir -p ~/circleguard && tar xzf - -C ~/circleguard'

ssh -i circleguard.pem ubuntu@<ip-publica-de-ci>
cd ~/circleguard
sudo ./infra/bootstrap/bootstrap-all.sh
```

`bootstrap-all.sh` ejecuta, en orden, `bootstrap-jenkins.sh`, `bootstrap-sonarqube.sh` y `bootstrap-nexus.sh`. Crea `infra/compose/ci/.env` la primera vez. Al terminar responden:

- `http://localhost:8080` Jenkins
- `http://localhost:9000` SonarQube
- `http://localhost:8081` Nexus

Las contraseñas de `admin` están en ese `.env`. No se copian a la estación de trabajo ni al repositorio.

### 4. Token de GitHub y URL de Jenkins

En el host de CI, editar `infra/compose/ci/.env` y reemplazar los `__SET_ME__` de `GITHUB_USER` y `GITHUB_TOKEN`. Fijar `CI_DOMAIN` a la Elastic IP, sin puerto:

```bash
sudo sed -i 's/^CI_DOMAIN=.*/CI_DOMAIN=<ip-elastica-de-ci>/' infra/compose/ci/.env
sudo ./infra/bootstrap/bootstrap-jenkins.sh
```

`GITHUB_OWNER` y `GITHUB_REPO` ya vienen con `ingesoft5` y `circle-guard-pepitosV`.

### 5. Webhook

Desde la estación de trabajo, con la misma región y con `jq` instalado:

```bash
infra/cloud/aws/allow-github-webhooks.sh
```

Con el token de escritura y `manageHooks`, Jenkins registra el webhook al escanear el job `circleguard`. La URL es `http://<CI_DOMAIN>:8080/github-webhook/`. Comprobarla en **Settings → Webhooks** del repositorio. El content type es `application/json` y los eventos son `push` y `pull_request`.

### 6. Los tres ambientes

`JENKINS_CIDR` es la IP privada de CI, no la pública. Sale de `hosts.sh status` o de la consola de EC2.

```bash
JENKINS_CIDR=<ip-privada-de-ci>/32 infra/cloud/aws/provision-env-hosts.sh
```

El script crea `circleguard-dev`, `circleguard-stage` y `circleguard-prod`, abre el `80` y el `443`, y permite que cada IP privada llegue al registro en el puerto `5000`.

Generar una llave de despliegue por ambiente, fuera del repositorio:

```bash
ssh-keygen -t ed25519 -N '' -C circleguard-deploy-dev -f ~/.ssh/circleguard-deploy-dev
ssh-keygen -t ed25519 -N '' -C circleguard-deploy-stage -f ~/.ssh/circleguard-deploy-stage
ssh-keygen -t ed25519 -N '' -C circleguard-deploy-prod -f ~/.ssh/circleguard-deploy-prod
```

Copiar el repositorio y aprovisionar cada host. `<ip>` es la IP pública de ese host. `<NEXUS_PULL_PASSWORD>` es el valor de `NEXUS_PULL_PASSWORD` en el `.env` de CI. Repetir el bloque para `stage` y para `prod`: cambian la IP, el archivo `.pub` (`circleguard-deploy-stage.pub`, `circleguard-deploy-prod.pub`) y el argumento final del script.

```bash
tar czf - --exclude='*.env' infra deploy | ssh -i circleguard.pem ubuntu@<ip> \
  'sudo mkdir -p /opt/circleguard && sudo tar xzf - -C /opt/circleguard'

ssh -i circleguard.pem ubuntu@<ip> "cd /opt/circleguard && sudo env \
  DEPLOY_SSH_PUBLIC_KEY='$(cat ~/.ssh/circleguard-deploy-dev.pub)' \
  ADMIN_CIDR=${ADMIN_CIDR} NEXUS_REGISTRY=<ip-privada-de-ci>:5000 \
  NEXUS_PULL_USER=deploy-puller NEXUS_PULL_PASSWORD=<NEXUS_PULL_PASSWORD> \
  ./infra/bootstrap/bootstrap-env.sh dev"
```

Eso crea el usuario `deploy`, la red Docker, los volúmenes, el `.env` del ambiente y el acceso de solo lectura al registro. La aplicación todavía no está levantada: entra con el primer despliegue.

### 7. Que Jenkins pueda desplegar

Cargar cada llave privada en el host de CI. El id de la credencial queda `deploy-ssh-<ambiente>`:

```bash
ssh -i circleguard.pem ubuntu@<ip-de-ci> \
  'sudo ~/circleguard/infra/bootstrap/load-deploy-credential.sh dev' \
  < ~/.ssh/circleguard-deploy-dev
```

Repetir con `stage` y `prod`.

En `infra/compose/ci/.env` del host de CI, escribir la IP privada de cada ambiente en `DEPLOY_HOST_DEV`, `DEPLOY_HOST_STAGE` y `DEPLOY_HOST_PROD`. Volver a aplicar Jenkins:

```bash
sudo ./infra/bootstrap/bootstrap-jenkins.sh
```

### 8. Comprobar

Desde una IP cubierta por `ADMIN_CIDR`:

```bash
curl -fsS -o /dev/null -w '%{http_code}\n' http://<ip-de-ci>:8080/login
curl -fsS http://<ip-de-ci>:9000/api/system/status
curl -fsS http://<ip-de-ci>:8081/service/rest/v1/status
curl -fsS -o /dev/null -w '%{http_code}\n' http://<ip-de-ci>:5000/v2/
```

Jenkins debe mostrar el job `circleguard` y el job `circleguard-rollback`. SonarQube responde `"status":"UP"`. Nexus responde en el status y el registro responde `200` en `/v2/`.

Encender un ambiente apagado y leer su IP pública:

```bash
infra/cloud/aws/hosts.sh start envs
infra/cloud/aws/hosts.sh status
```

La URL de la aplicación responde cuando ese ambiente tiene una versión desplegada. Hasta entonces el host está aprovisionado y el puerto `80` todavía no sirve el cliente.

## Despliegues

Un push directo a `dev` o a `main` lo rechaza GitHub. El trabajo entra por pull request hacia `dev`, y una versión sale de un pull request de `dev` hacia `main`, fusionado con **Create a merge commit**.

En `main`, si los commits desde el último tag incluyen `feat`, `fix`, `perf` o un cambio incompatible, el pipeline:

1. Calcula la versión (`Release version`).
2. Publica las imágenes en el registro (`Publish images`). El tag de imagen es `MAJOR.MINOR.PATCH`, sin `v` y sin `latest`.
3. Crea el tag `vX.Y.Z` en GitHub (`Tag release`).
4. Despliega esa versión en `dev` (`Deploy to dev`).
5. Publica las release notes y abre el PR del changelog.
6. Corre las pruebas E2E contra `dev`.
7. Si las E2E pasan, despliega la misma versión en `stage` (`Deploy to stage`).
8. Se detiene en `Approve production`; al aprobar, despliega en `prod` (`Deploy to prod`).

El job `circleguard-rollback` sigue disponible para redesplegar una versión ya publicada en cualquiera de los tres ambientes: **Build with Parameters**, `ENVIRONMENT` en `dev`, `stage` o `prod`, y `VERSION` con o sin `v` (`0.1.0` o `v0.1.0`). En `prod` el job se detiene en **Approve production** hasta que alguien confirma. Solo el usuario `admin` puede confirmar, como en el `input` del pipeline (ver [política de cambios](../release-notes/politica-cambios.md)).

El despliegue copia `deploy/compose` al host sin tocar su `.env` y ejecuta `deploy/compose/deploy.sh`. Ese script descarga las imágenes primero: si el registro no tiene la versión, el ambiente no cambia. Después espera a que los servicios queden `healthy` y prueba el cliente web en el puerto `HTTP_PORT`.

También se puede lanzar a mano, en el host del ambiente y como usuario `deploy`:

```bash
cd /opt/circleguard/deploy/compose
./deploy.sh dev 1.4.0
```

`1.4.0` tiene que existir en el registro. La promoción automática a `stage` después de las E2E y el `input` de producción ya están en el `Jenkinsfile`; `Deploy to dev` sigue siendo el primer despliegue de cada versión y `stage` y `prod` reciben el mismo tag.

## Rollback

El mismo job `circleguard-rollback` vuelve a una versión anterior. La versión tiene que estar completa en el registro: si falta una imagen de `deploy/compose/stack.yml`, el job falla antes de abrir SSH.

1. Abrir `circleguard-rollback` → **Build with Parameters**.
2. Elegir el ambiente y la versión de destino.
3. En `prod`, confirmar el paso **Approve production**.
4. El build deja en la descripción `stage: 0.2.0 -> 0.1.0 by <usuario>` (con el ambiente que se haya elegido) y el log termina en `ROLLBACK OK` o `ROLLBACK FAILED`, con la versión de origen, la de destino y quién lo lanzó.

El job lee su pipeline de `ROLLBACK_BRANCH` (por defecto `main`). No hace falta apagar el ambiente ni borrar volúmenes: `deploy.sh` sustituye el `IMAGE_TAG` y recrea los contenedores. Los datos de PostgreSQL y Neo4j se conservan.

## Operación diaria

```bash
infra/cloud/aws/hosts.sh status
infra/cloud/aws/hosts.sh stop envs
infra/cloud/aws/hosts.sh start ci
infra/cloud/aws/hosts.sh allow
```

Una máquina apagada no cobra cómputo y sí cobra el disco. La Elastic IP de CI se cobra también apagada. Al encender CI, Docker levanta Jenkins, SonarQube y Nexus (`restart: unless-stopped`). La IP pública de un ambiente hay que volver a leerla con `hosts.sh status`.

Para reconstruir el stack de CI sin perder volúmenes: `sudo ./infra/bootstrap/bootstrap-all.sh` en el host de CI.
