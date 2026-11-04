# Stack CI de CircleGuard

## Descripción

El stack de integración continua de CircleGuard se ejecuta en un único host y agrupa Jenkins, SonarQube y Nexus. Los tres servicios corren como contenedores Docker, comparten el mismo host y ciclo de vida, y mantienen volúmenes de datos independientes.

La provisión del host y la configuración de las herramientas están automatizadas con scripts Bash idempotentes. Una reconstrucción completa se realiza con `infra/bootstrap/bootstrap-all.sh`.

## Arquitectura

```
Host CI (EC2)
├── Jenkins          :8080 / :50000
├── SonarQube        :9000
├── PostgreSQL       (solo red interna)
└── Nexus            :8081 / :5000
```

Todos los servicios se comunican por la red Docker `circleguard-ci-net`. Solo los puertos documentados se publican en el host.

## Componentes y puertos

| Herramienta | URL | Puerto host |
|:---|:---|:---|
| Jenkins | `http://<host>:8080` | `8080` (UI) y `50000` (agentes) |
| SonarQube | `http://<host>:9000` | `9000` |
| Nexus | `http://<host>:8081` | `8081` |
| Registro Docker de Nexus | `<host>:5000/docker-hosted` | `5000` |

El Security Group del host expone únicamente estos puertos y el `22`, restringidos a `ADMIN_CIDR`. PostgreSQL no se publica.

## Requisitos

- Host con Ubuntu 24.04 LTS o Debian 12.
- Acceso SSH con permisos de `sudo`.
- Salida a Internet para descargar imágenes y plugins.
- AWS CLI configurado en la estación de trabajo, únicamente para crear el host.
- Un key pair y una subred existentes en AWS.

## Recursos y costos

La configuración por defecto es `t3.large` con disco raíz de 30 GB gp3. Los valores son aproximados para `us-east-1` en modalidad on-demand y pueden variar según la región y la fecha; la referencia vigente es la página de precios de EC2.

| Recurso | Costo aproximado |
|:---|:---|
| `t3.medium` (4 GB), 24/7 | ~$30 USD/mes |
| `t3.large` (8 GB), 24/7 | ~$61 USD/mes |
| `t3.xlarge` (16 GB), 24/7 | ~$121 USD/mes |
| EBS gp3, 30 GB | ~$2.40 USD/mes |
| IPv4 pública, 24/7 | ~$3.65 USD/mes |

Con uso intermitente (por ejemplo, 160 horas al mes) el costo de cómputo baja proporcionalmente, mientras que EBS se sigue cobrando. La instancia puede detenerse cuando no se utiliza: los datos permanecen en EBS y los contenedores se reinician al encenderla.

`t3.medium` es el mínimo absoluto, pero SonarQube, Nexus y Jenkins juntos suelen superar los 4 GB de RAM. `t3.large` es la opción recomendada; `t3.xlarge` deja margen adicional.

Una Elastic IP es opcional. Su costo es similar al de cualquier IPv4 pública (~$3.65 USD/mes) y se sigue cobrando mientras la instancia está detenida. Sin Elastic IP, la IP pública cambia al detener y encender la instancia.

## Aprovisionamiento del host

La creación del host se realiza con `infra/cloud/aws/provision-ci-hosts.sh`. El script crea el Security Group y la instancia de forma idempotente; si ya existen, los reutiliza.

Variables requeridas:

| Variable | Descripción |
|:---|:---|
| `ADMIN_CIDR` | Rango autorizado para SSH y las UIs, por ejemplo `<ip>/32` |
| `CI_SUBNET_ID` | Subred donde se crea la instancia |
| `CI_KEY_NAME` | Key pair existente para el acceso SSH |

Variables opcionales:

| Variable | Valor por defecto |
|:---|:---|
| `AWS_REGION` | `us-east-1` |
| `CI_INSTANCE_TYPE` | `t3.large` |
| `CI_ROOT_VOLUME_GB` | `30` |
| `CI_ROOT_VOLUME_TYPE` | `gp3` |
| `CI_AMI_ID` | Última Ubuntu 24.04 resuelta por SSM |
| `CI_INSTANCE_NAME` | `circleguard-ci` |
| `CI_SG_NAME` | `circleguard-ci-sg` |

Ejemplo:

```bash
export AWS_REGION=us-east-1
export ADMIN_CIDR="<ip>/32"
export CI_SUBNET_ID="subnet-xxxxxxxx"
export CI_KEY_NAME="mi-key-pair"

infra/cloud/aws/provision-ci-hosts.sh
```

Para la Elastic IP, los ambientes y la operación diaria, ver [infra/cloud/aws/README.md](../../cloud/aws/README.md).

El script no instala Docker ni levanta las herramientas; solo prepara la infraestructura. Los cambios de tipo de instancia o tamaño de disco aplican únicamente a instancias nuevas.

## Preparación del host

El repositorio se copia al host y el stack se reconstruye con un solo comando:

```bash
tar czf - infra | ssh -i <clave>.pem ubuntu@<IP> \
  'mkdir -p ~/circleguard && tar xzf - -C ~/circleguard'

ssh -i <clave>.pem ubuntu@<IP>
cd ~/circleguard
sudo ./infra/bootstrap/bootstrap-all.sh
```

`bootstrap-all.sh` ejecuta en orden:

1. `bootstrap-jenkins.sh`: instala y levanta Jenkins, aplica JCasC e instala los plugins declarados.
2. `bootstrap-sonarqube.sh`: levanta SonarQube con PostgreSQL, cambia la contraseña inicial, genera el token de análisis y asegura el quality gate `CircleGuard Gate`.
3. `bootstrap-nexus.sh`: levanta Nexus, cambia la contraseña inicial y crea los repositorios `docker-hosted` y `raw-hosted`.

La primera ejecución genera `infra/compose/ci/.env` con contraseñas aleatorias. El archivo queda con permisos `600` y está excluido del repositorio.

## Credenciales

| Herramienta | Usuario | Ubicación |
|:---|:---|:---|
| Jenkins | `admin` | `JENKINS_ADMIN_PASSWORD` en `infra/compose/ci/.env` |
| SonarQube | `admin` | `SONAR_ADMIN_PASSWORD` en `infra/compose/ci/.env` |
| SonarQube (token) | `admin` | `SONAR_TOKEN` en `infra/compose/ci/.env` |
| Nexus | `admin` | `NEXUS_ADMIN_PASSWORD` en `infra/compose/ci/.env` |
| GitHub | `<usuario>` | `GITHUB_USER` y `GITHUB_TOKEN` en `infra/compose/ci/.env` |

El job multibranch y el webhook de GitHub se documentan en [docs/operaciones/jenkins-pipeline.md](../../../docs/operaciones/jenkins-pipeline.md).

## Operación

### Reconstrucción completa

```bash
sudo ./infra/bootstrap/bootstrap-all.sh
```

La ejecución es idempotente: instala lo que falte, conserva los volúmenes y no duplica contenedores ni configuración.

### Ejecución individual de una herramienta

```bash
sudo ./infra/bootstrap/bootstrap-jenkins.sh
sudo ./infra/bootstrap/bootstrap-sonarqube.sh
sudo ./infra/bootstrap/bootstrap-nexus.sh
```

### Inicio y detención de la instancia

```bash
aws ec2 stop-instances --instance-ids <instance-id>
aws ec2 start-instances --instance-ids <instance-id>
```

Al detener la instancia se conservan los datos en EBS. Al encenderla, Docker arranca los contenedores con política `restart: unless-stopped`. La IP pública cambia salvo que se use una Elastic IP.

### Verificación de idempotencia

```bash
sudo ./infra/bootstrap/verify-idempotency.sh
```

El script ejecuta `bootstrap-all.sh` dos veces, compara contenedores y volúmenes, comprueba la persistencia de un marcador en el volumen de Jenkins y valida los endpoints. La evidencia se guarda en `docs/evidencias/HU-03-HU-04/`.

## Diagnóstico

| Síntoma | Causa probable | Acción |
|:---|:---|:---|
| No responde `:8080`, `:9000` o `:8081` | Regla ausente en el Security Group o `ADMIN_CIDR` incorrecto | Revisar las reglas del Security Group y la IP de origen |
| SonarQube no arranca | Falta `vm.max_map_count` | `sudo sysctl -w vm.max_map_count=524288` |
| Jenkins no aplica JCasC | Plugin `configuration-as-code` no instalado | Revisar `docker logs circleguard-ci-jenkins` y re-ejecutar |
| Nexus solicita contraseña inicial | El `.env` se regeneró y la contraseña ya había cambiado | Restaurar el `.env` original o recrear el volumen |
| `permission denied` al usar Docker | El usuario no pertenece al grupo `docker` | Reiniciar la sesión SSH o ejecutar con `sudo` |

## Destrucción

```bash
# Detener el stack sin borrar datos
sudo docker compose --env-file infra/compose/ci/.env \
  -f infra/compose/ci/docker-compose.yml down

# Terminar la instancia y su disco raíz
aws ec2 terminate-instances --instance-ids <instance-id>
```

La terminación de la instancia elimina el disco raíz por defecto. Para conservar la configuración de las herramientas se requiere un snapshot de EBS. No se recomienda `docker compose down -v` salvo que se quiera eliminar los volúmenes.
