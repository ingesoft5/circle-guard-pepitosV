# Pipeline multibranch de Jenkins

## Alcance

Esta guía cubre la HU-09 (`CGR-32`): job multibranch, webhook de GitHub, etapas iniciales del `Jenkinsfile` y check obligatorio en `dev` y `main`. La Shared Library reutilizable se implementa en la HU-10 (`CGR-33`) y vivirá en este mismo repositorio, bajo `jenkins/vars/`.

## Cómo funciona

- `infra/compose/ci/jenkins/casc.yaml` declara por JCasC el job multibranch `circleguard`, la credencial `github-pat` y la integración con GitHub.
- GitHub Branch Source escanea las ramas y los pull requests del repositorio y crea un build por rama o PR.
- El `Jenkinsfile` de la raíz ejecuta las etapas `Checkout`, `Compile` y `Unit tests`; el resto de etapas se agrega en las HU de empaquetado, SonarQube e integración.
- Jenkins publica el resultado como estado de commit en GitHub. Para builds de PR el contexto es `continuous-integration/jenkins/pr-merge`.
- El contenedor de Jenkins monta `/var/run/docker.sock` para que las pruebas con Testcontainers puedan iniciar contenedores y para el empaquetado posterior.

## Configuración que vive en el repositorio

| Archivo | Propósito |
|:---|:---|
| `Jenkinsfile` | Etapas iniciales del pipeline. |
| `infra/compose/ci/jenkins/plugins.txt` | Plugins de multibranch, GitHub y Job DSL. |
| `infra/compose/ci/jenkins/casc.yaml` | Credencial, servidor GitHub y job multibranch. |
| `infra/compose/ci/docker-compose.yml` | Variables de GitHub y acceso a Docker. |
| `infra/compose/ci/.env.example` | Plantilla de las variables de GitHub. |
| `infra/bootstrap/bootstrap-jenkins.sh` | Exporta las variables y el GID del socket de Docker. |

## Configuración manual en GitHub y Jenkins

Estos pasos no se pueden dejar solo en código porque dependen de credenciales y de la URL pública del host. Se hacen una vez por entorno.

### 1. Token de GitHub

1. Crear un token con permisos de lectura sobre el repositorio y `admin:repo_hook` para registrar el webhook.
2. En el host CI, editar `infra/compose/ci/.env`:

```bash
GITHUB_OWNER=ingesoft5
GITHUB_REPO=circle-guard-pepitosV
GITHUB_USER=<usuario-de-github>
GITHUB_TOKEN=<token>
```

3. Re-ejecutar `sudo ./infra/bootstrap/bootstrap-jenkins.sh`.

El token nunca se versiona: `.env` está ignorado por Git y el JCasC solo recibe las variables del contenedor.

### 2. URL pública de Jenkins

El webhook necesita una URL alcanzable desde Internet. Ajustar `CI_DOMAIN` en `infra/compose/ci/.env` y asegurar DNS y el puerto:

```bash
CI_DOMAIN=ci.circleguard.example.edu
JENKINS_PORT=8080
```

La URL efectiva del webhook es:

```text
https://<CI_DOMAIN>/github-webhook/
```

Si el host no tiene TLS, usar la URL `http://<IP-o-dominio>:8080/github-webhook/` solo para pruebas y documentarlo. El script `infra/cloud/aws/allow-github-webhooks.sh` abre el puerto de Jenkins a los rangos de GitHub.

### 3. Webhook en GitHub

Con `manageHooks: true` y un token con `admin:repo_hook`, GitHub Branch Source registra el hook automáticamente al crear o escanear el job. Verificar en **Settings → Webhooks** del repositorio.

Si el registro automático falla, crear el webhook a mano:

| Campo | Valor |
|:---|:---|
| Payload URL | `https://<CI_DOMAIN>/github-webhook/` |
| Content type | `application/json` |
| Events | `push` y `pull_request` |
| Secret | Opcional; si se usa, configurarlo también en Jenkins |

### 4. Check obligatorio en `dev` y `main`

Se requiere una sola vez y necesita permisos de administrador del repositorio. Se puede aplicar desde **Settings → Branches → Branch protection rules** o con el script del repositorio:

```bash
gh auth login
./infra/github/configure-branch-protection.sh
```

En la regla de cada rama:

1. Mantener `Require a pull request before merging` con 1 aprobación.
2. Mantener el check existente `commitlint`.
3. Agregar `continuous-integration/jenkins/pr-merge` en `Require status checks to pass before merging`.
4. Marcar el check como obligatorio y guardar.
5. Hacer que el job multibranch reporte al menos una vez para que GitHub muestre el contexto.

## Verificación

```bash
# El job y la credencial se aplicaron
sudo docker logs circleguard-ci-jenkins --tail 100

# El job existe y tiene builds
curl -fsS -u admin:<password> http://localhost:8080/job/circleguard/api/json

# Un push a una rama inicia el build en menos de un minuto
git push origin <rama-de-prueba>

# Los checks obligatorios quedaron configurados
gh api repos/ingesoft5/circle-guard-pepitosV/branches/dev/protection
gh api repos/ingesoft5/circle-guard-pepitosV/branches/main/protection
```

## Diagnóstico

| Síntoma | Causa probable | Acción |
|:---|:---|:---|
| El push no inicia el build | Webhook ausente o URL inalcanzable | Revisar **Settings → Webhooks** y las entregas recientes |
| GitHub muestra 404 en el webhook | `CI_DOMAIN` incorrecto o Security Group cerrado | Corregir DNS o ejecutar `allow-github-webhooks.sh` |
| El job no aparece en Jenkins | JCasC falló o faltan plugins | Revisar `docker logs circleguard-ci-jenkins` y re-ejecutar el bootstrap |
| El escaneo de ramas falla | Token inválido o sin permisos | Regenerar `GITHUB_TOKEN` y reiniciar Jenkins |
| Tests con Testcontainers fallan | Jenkins no accede al socket de Docker | Verificar el montaje de `/var/run/docker.sock` y `group_add` |
| GitHub no permite fusionar | El check aún no existe o no es obligatorio | Esperar un build del PR y revisar la regla de protección |

## Seguridad

Montar el socket de Docker da privilegios altos al contenedor de Jenkins. Es una decisión consciente para ejecutar Testcontainers y las etapas de empaquetado; el host CI es de un solo equipo y no se expone como servicio multiusuario. No se almacenan tokens en el repositorio ni en las imágenes.
