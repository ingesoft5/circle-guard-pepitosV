# Stack CI de CircleGuard - Jenkins, SonarQube y Nexus

Guía operativa para reconstruir el stack de integración continua desde cero con un solo comando, sin pasos manuales no documentados.

## Componentes y puertos

| Herramienta | URL | Puerto host |
|:---|:---|:---|
| Jenkins | `http://<host>:8080` | `8080` (UI) y `50000` (agentes) |
| SonarQube | `http://<host>:9000` | `9000` |
| Nexus | `http://<host>:8081` | `8081` |
| Registro Docker de Nexus | `<host>:5000/docker-hosted` | `5000` |

Solo estos puertos se abren en el Security Group, y únicamente hacia `ADMIN_CIDR`. PostgreSQL de SonarQube no se publica.

## Requisitos

- Ubuntu 24.04 LTS o Debian 12 en el host CI.
- Acceso SSH con `sudo`.
- Salida a Internet para descargar imágenes y plugins.
- AWS CLI configurado en tu máquina (solo para crear el host con `infra/cloud/aws/provision-ci-hosts.sh`).
- Un key pair y una subred existentes en AWS.

## 1. Crear el host CI (desde tu máquina)

```bash
export AWS_REGION=us-east-1
export ADMIN_CIDR="<tu-ip>/32"
export CI_SUBNET_ID="subnet-xxxxxxxx"
export CI_KEY_NAME="mi-key-pair"
# Opcionales:
# export CI_INSTANCE_TYPE=t3.medium
# export CI_AMI_ID=ami-xxxxxxxx

infra/cloud/aws/provision-ci-hosts.sh
```

El script crea el Security Group y la instancia de forma idempotente y muestra la IP pública y el comando SSH. No instala Docker ni levanta las herramientas.

## 2. Copiar el repositorio al host CI

```bash
tar czf - infra | ssh -i <clave>.pem ubuntu@<IP> \
  'mkdir -p ~/circleguard && tar xzf - -C ~/circleguard'
```

## 3. Reconstruir el stack con un comando

```bash
ssh -i <clave>.pem ubuntu@<IP>
cd ~/circleguard
sudo ./infra/bootstrap/bootstrap-all.sh
```

`bootstrap-all.sh` ejecuta en orden:

1. `bootstrap-jenkins.sh`: instala/levanta Jenkins, aplica JCasC e instala los plugins de `plugins.txt`.
2. `bootstrap-sonarqube.sh`: levanta SonarQube + PostgreSQL, cambia la contraseña inicial, genera el token de análisis y asegura el quality gate `CircleGuard Gate`.
3. `bootstrap-nexus.sh`: levanta Nexus, cambia la contraseña inicial y crea los repositorios `docker-hosted` (puerto `5000`) y `raw-hosted`.

La primera ejecución genera `infra/compose/ci/.env` con contraseñas aleatorias. El archivo queda con permisos `600` y está ignorado por Git.

## Credenciales

| Herramienta | Usuario | Dónde está la contraseña |
|:---|:---|:---|
| Jenkins | `admin` | `JENKINS_ADMIN_PASSWORD` en `infra/compose/ci/.env` |
| SonarQube | `admin` | `SONAR_ADMIN_PASSWORD` en `infra/compose/ci/.env` |
| SonarQube (token) | `admin` | `SONAR_TOKEN` en `infra/compose/ci/.env` |
| Nexus | `admin` | `NEXUS_ADMIN_PASSWORD` en `infra/compose/ci/.env` |


## 4. Verificar idempotencia

```bash
sudo ./infra/bootstrap/verify-idempotency.sh
```

El script:

- Ejecuta `bootstrap-all.sh` dos veces.
- Compara contenedores y volúmenes antes/después.
- Escribe un marcador en el volumen de Jenkins y comprueba que persiste.
- Valida que Jenkins, SonarQube y Nexus respondan.
- Guarda la evidencia en `docs/evidencias/HU-03-HU-04/`.

## 5. Re-ejecutar sin perder datos

```bash
sudo ./infra/bootstrap/bootstrap-all.sh
```

Es seguro ejecutarlo de nuevo: instala lo que falte, conserva volúmenes y no duplica contenedores ni configuración.

## Solución de problemas

| Síntoma | Causa probable | Acción |
|:---|:---|:---|
| No responde `:8080`, `:9000` o `:8081` | Security Group sin la regla o `ADMIN_CIDR` incorrecto | Revisar reglas del SG y tu IP pública |
| SonarQube no arranca | Falta `vm.max_map_count` | `sudo sysctl -w vm.max_map_count=524288` |
| Jenkins no aplica JCasC | Plugin `configuration-as-code` no instalado | Revisar `docker logs circleguard-ci-jenkins` y re-ejecutar |
| Nexus pide contraseña inicial | La contraseña cambió y el `.env` se regeneró | Restaurar el `.env` original o reiniciar el volumen (pierde datos) |
| `permission denied` al usar Docker | El usuario no está en el grupo `docker` | Cerrar y abrir sesión SSH, o usar `sudo` |

## Apagar y destruir

```bash
# Detener el stack sin borrar datos
sudo docker compose --env-file infra/compose/ci/.env \
  -f infra/compose/ci/docker-compose.yml down

# Destruir la instancia (desde tu máquina)
aws ec2 terminate-instances --instance-ids <instance-id>
```

Evita `docker compose down -v` salvo que quieras borrar volúmenes y perder la configuración de las herramientas.
