# Máquinas de CircleGuard en AWS

Una máquina para CI (Jenkins, SonarQube y Nexus) y una por ambiente (`dev`, `stage` y `prod`), en la misma VPC y subred. Los Security Groups están en [security-groups.md](../../security/security-groups.md).

## Requisitos

- AWS CLI configurado, con permisos de EC2 y lectura de SSM.
- Un key pair y una subred pública existentes.
- `jq`, solo para `allow-github-webhooks.sh`.

## Variables

| Variable | Script | Obligatoria | Descripción | Valor por defecto |
|:---|:---|:---|:---|:---|
| `ADMIN_CIDR` | Ambos `provision-*` | Sí | IP autorizada para administrar, por ejemplo `<ip>/32` | |
| `CI_SUBNET_ID`, `ENV_SUBNET_ID` | CI, ambientes | Sí | Subred | |
| `CI_KEY_NAME`, `ENV_KEY_NAME` | CI, ambientes | Sí | Key pair de administración | |
| `CI_INSTANCE_TYPE` | CI | No | Tipo de máquina | `t3.large` |
| `ENV_INSTANCE_TYPE` | Ambientes | No | Tipo de máquina | `m7i-flex.large` |
| `ENV_ROOT_VOLUME_GB` | Ambientes | No | Disco en GB | `30` |
| `JENKINS_CIDR` | Ambientes | No | IP privada de CI, `<ip>/32`, para el SSH de Jenkins | |
| `AWS_REGION` | Todos | No | Región | `us-east-1` |

Si la cuenta tiene un plan que rechaza `t3.large`, usar `CI_INSTANCE_TYPE=m7i-flex.large`, también de 8 GB.

## Montaje desde cero

**1. Crear CI y fijar su IP.** La Elastic IP evita que cambien las URL y el webhook:

```bash
infra/cloud/aws/provision-ci-hosts.sh
aws ec2 allocate-address --domain vpc
aws ec2 associate-address --instance-id <id-de-ci> --allocation-id <id-de-la-ip>
```

**2. Instalar el stack de CI:** seguir [infra/compose/ci/README.md](../../compose/ci/README.md). Después, definir la URL de Jenkins y aplicarla:

```bash
sudo sed -i 's/^CI_DOMAIN=.*/CI_DOMAIN=<ip-de-ci>/' infra/compose/ci/.env
sudo docker compose --env-file infra/compose/ci/.env -f infra/compose/ci/docker-compose.yml up -d jenkins
```

**3. Permitir los webhooks de GitHub:**

```bash
infra/cloud/aws/allow-github-webhooks.sh
```

**4. Crear los ambientes.** El script también permite que cada uno llegue al registro de Nexus:

```bash
JENKINS_CIDR=<ip-privada-de-ci>/32 infra/cloud/aws/provision-env-hosts.sh
```

**5. Crear una llave de despliegue por ambiente,** fuera del repositorio:

```bash
ssh-keygen -t ed25519 -N '' -C circleguard-deploy-dev -f ~/.ssh/circleguard-deploy-dev
```

**6. Aprovisionar cada ambiente:**

```bash
tar czf - --exclude='*.env' infra deploy | ssh -i <clave>.pem ubuntu@<ip> \
  'sudo mkdir -p /opt/circleguard && sudo tar xzf - -C /opt/circleguard'

ssh -i <clave>.pem ubuntu@<ip> "cd /opt/circleguard && sudo env \
  DEPLOY_SSH_PUBLIC_KEY='$(cat ~/.ssh/circleguard-deploy-dev.pub)' \
  ADMIN_CIDR=<ip>/32 NEXUS_REGISTRY=<ip-privada-de-ci>:5000 \
  NEXUS_PULL_USER=deploy-puller NEXUS_PULL_PASSWORD=<NEXUS_PULL_PASSWORD de infra/compose/ci/.env> \
  ./infra/bootstrap/bootstrap-env.sh dev"
```

El script genera un `.env` propio con credenciales distintas, y crea el usuario `deploy`, la red y los volúmenes. Además agrega el registro a `insecure-registries` de `/etc/docker/daemon.json` (sin borrar otras opciones) y deja al usuario `deploy` autenticado en el registro con la cuenta de solo lectura `deploy-puller`. Puede ejecutarse varias veces.

**7. Cargar cada llave en Jenkins,** desde tu equipo. Queda con el ID `deploy-ssh-<ambiente>`:

```bash
ssh -i <clave>.pem ubuntu@<ip-de-ci> \
  'sudo ~/circleguard/infra/bootstrap/load-deploy-credential.sh dev' < ~/.ssh/circleguard-deploy-dev
```

Jenkins debe conectarse a los ambientes por su IP privada, que no cambia al apagarlos.

Esa IP va en `DEPLOY_HOST_DEV`, `DEPLOY_HOST_STAGE` y `DEPLOY_HOST_PROD`, en `infra/compose/ci/.env` del host de CI. Después hay que volver a ejecutar `sudo ./infra/bootstrap/bootstrap-jenkins.sh`.

## Operación diaria

```bash
infra/cloud/aws/hosts.sh status           # estado e IP
infra/cloud/aws/hosts.sh start envs       # ci, envs o all
infra/cloud/aws/hosts.sh stop envs        # ci, envs o all
infra/cloud/aws/hosts.sh allow             # autoriza la IP actual de este equipo
infra/cloud/aws/hosts.sh allow <ip> <dueño> # autoriza una IP a nombre de esa persona
```

Cada comando, salvo `allow`, escribe las IP actuales en `infra/cloud/aws/hosts.env`, un archivo ignorado por Git. La IP pública de los ambientes cambia al encenderlos; la de CI, con Elastic IP, no.

`allow` guarda el dueño en la descripción de cada regla. Por defecto, el dueño es el nombre del usuario IAM sin el prefijo `circleguard-`. Primero agrega la regla nueva y luego borra las anteriores de ese dueño, así que el acceso no se interrumpe y las reglas no se acumulan. No toca reglas de otros dueños ni reglas sin descripción. Para reemplazar reglas, el usuario necesita los permisos `ec2:DescribeSecurityGroupRules`, `ec2:UpdateSecurityGroupRuleDescriptionsIngress` y `ec2:RevokeSecurityGroupIngress`. Sin ellos, el comando igual agrega la regla nueva y avisa que no pudo borrar la anterior.

Una máquina apagada no cobra cómputo, pero sí su disco. La Elastic IP se cobra aunque CI esté apagada.
