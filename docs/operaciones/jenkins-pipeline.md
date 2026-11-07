# Pipeline multibranch de Jenkins

## Alcance

Esta guía cubre la HU-09 (`CGR-32`), la HU-10 (`CGR-33`), la HU-11 (`CGR-34`), la HU-15 (`CGR-38`), la HU-16 (`CGR-39`) y la etapa E2E de la HU-20 (`CGR-43`): job multibranch, webhook de GitHub, etapas del `Jenkinsfile`, check obligatorio en `dev` y `main`, Shared Library reutilizable en `jenkins/vars/`, ejecución selectiva por servicio, versionado semántico automático, notificaciones de Discord y pruebas E2E.

## Cómo funciona

- `infra/compose/ci/jenkins/casc.yaml` declara por JCasC el job multibranch `circleguard`, la credencial `github-pat`, la integración con GitHub y la Global Pipeline Library.
- GitHub Branch Source escanea las ramas y los pull requests del repositorio y crea un build por rama o PR.
- El `Jenkinsfile` de la raíz importa la Shared Library con `@Library` y usa `detectChangedServices` para calcular qué servicios y si el web client deben procesarse.
- Cada servicio seleccionado se compila, prueba y empaqueta con `gradleBuild`, `runTests` y `dockerBuild` dentro de un `parallel` dinámico, en lotes de 3.
- El web client se empaqueta solo si cambió `mobile/` o si se ejecuta con `BUILD_ALL=true`.
- En `main`, `Release version` calcula la versión con `calculateVersion` a partir de los Conventional Commits desde el último tag (sin tags: `1.0.0`). Si no hay commits `feat`, `fix`, `perf` ni incompatibles, no hay release y las etapas siguientes se omiten.
- `Publish images` publica las imágenes de los 8 servicios y del web client con esa versión, y después `Tag release` crea y publica el tag `vX.Y.Z` en GitHub con `pushReleaseTag`. El orden garantiza que un tag en GitHub siempre tiene sus imágenes.
- En `main`, después de `Deploy to dev`, `Release notes` (HU-22) crea el GitHub Release con las notas generadas por `releaseNotes` y abre o actualiza el PR `chore(release): update changelog for vX.Y.Z` hacia `dev` con el `CHANGELOG.md` regenerado. No hace push directo porque `main` y `dev` están protegidas. Va antes de las E2E: el tag y las imágenes ya existen, así que el release queda documentado aunque fallen las pruebas.
- Después, la etapa `E2E tests on dev` corre las pruebas de Playwright contra dev y publica sus resultados (HU-20).
- Cuando un build falla o queda inestable, `notifyDiscord` envía un mensaje al canal de Discord con rama, servicio, etapa, autor y enlace al build. Si el build siguiente se recupera, envía un mensaje de recuperación.
- La librería se carga desde `jenkins/vars/` usando `libraryPath: "jenkins"`, en la versión de la misma rama que se está construyendo.
- Jenkins publica el resultado como estado de commit en GitHub. Para builds de PR el contexto es `continuous-integration/jenkins/pr-merge`.
- Una rama con un PR abierto se construye solo como `PR-<n>` (fusionada con su base) y no también como rama (`buildOriginBranchWithPR(false)`), porque Jenkins tiene un solo ejecutor. Las ramas sin PR, `dev` y `main` se siguen construyendo en cada push.
- El contenedor de Jenkins monta `/var/run/docker.sock` para que las pruebas con Testcontainers puedan iniciar contenedores y para el empaquetado posterior.

## Configuración que vive en el repositorio

| Archivo | Propósito |
|:---|:---|
| `Jenkinsfile` | Detección de cambios, ejecución selectiva y publicación. |
| `jenkins/vars/detectChangedServices.groovy` | Calcula servicios y web client modificados. |
| `jenkins/vars/gradleBuild.groovy` | Paso reutilizable de compilación Gradle. |
| `jenkins/vars/runTests.groovy` | Paso reutilizable de pruebas, cobertura JaCoCo y publicación JUnit opcional. |
| `jenkins/vars/publishCoverage.groovy` | Publica la cobertura JaCoCo de cada servicio en Jenkins. |
| `jenkins/vars/dockerBuild.groovy` | Construye imágenes sin publicarlas. |
| `jenkins/vars/dockerPublish.groovy` | Construye y publica imágenes semver en Nexus. |
| `jenkins/vars/resolveReleaseVersion.groovy` | Lee el tag de release de `HEAD`. |
| `jenkins/vars/calculateVersion.groovy` | Calcula la siguiente versión desde los Conventional Commits. |
| `jenkins/vars/pushReleaseTag.groovy` | Crea y publica el tag `vX.Y.Z` en GitHub. |
| `jenkins/vars/semver.groovy` | Funciones puras de versionado semántico. |
| `jenkins/vars/releaseNotes.groovy` | Genera las release notes y el `CHANGELOG.md` desde los commits y PR. |
| `jenkins/vars/publishGithubRelease.groovy` | Crea o actualiza el GitHub Release de cada versión. |
| `jenkins/vars/openChangelogPr.groovy` | Abre o actualiza el PR del `CHANGELOG.md` hacia `dev`. |
| `jenkins/vars/sonarScan.groovy` | Paso reutilizable de análisis SonarQube para HU-12. |
| `jenkins/vars/notifyDiscord.groovy` | Notificaciones de fallo, inestabilidad y recuperación en Discord. |
| `jenkins/README.md` | Contrato y ejemplos de uso de la librería. |
| `infra/compose/ci/jenkins/plugins.txt` | Plugins de multibranch, GitHub y Job DSL. |
| `infra/compose/ci/jenkins/casc.yaml` | Credencial, servidor GitHub, job multibranch y Global Pipeline Library. |
| `infra/compose/ci/docker-compose.yml` | Variables de GitHub y acceso a Docker. |
| `infra/compose/ci/.env.example` | Plantilla de las variables de GitHub. |
| `infra/bootstrap/bootstrap-jenkins.sh` | Exporta las variables y el GID del socket de Docker. |
| `tests/e2e-playwright/` | Pruebas E2E con Playwright, su imagen y el script que las corre en un contenedor. |

## Shared Library

La librería se declara en JCasC como `circleguard-shared-library`:

```yaml
globalLibraries:
  libraries:
    - name: "circleguard-shared-library"
      defaultVersion: "<SHA de respaldo>"
      implicit: false
      allowVersionOverride: true
      retriever:
        modernSCM:
          libraryPath: "jenkins"
          scm:
            git:
              remote: "https://github.com/${GITHUB_OWNER}/${GITHUB_REPO}.git"
              credentialsId: "github-pat"
```

Puntos importantes:

- `libraryPath: "jenkins"` hace que Jenkins busque `vars/` dentro de `jenkins/`; sin ese campo buscaría `vars/` en la raíz del repositorio.
- El `Jenkinsfile` carga la librería con `library "circleguard-shared-library@${env.CHANGE_BRANCH ?: env.BRANCH_NAME}"`: la rama del build o, en un PR, su rama de origen. Un cambio en `jenkins/vars/` se prueba en su propio PR sin actualizar JCasC.
- `defaultVersion` es solo un respaldo para un `Jenkinsfile` que todavía use `@Library` sin versión.
- Detalles y consideraciones de seguridad en [jenkins/README.md](../../jenkins/README.md).

## Ejecución selectiva (HU-11)

`detectChangedServices` calcula la lista de servicios y si el web client debe procesarse:

- PR: compara `HEAD` contra `HEAD^1` del merge commit.
- Rama: compara contra `GIT_PREVIOUS_SUCCESSFUL_COMMIT`.
- Sin base, base no encontrada o no ancestro de `HEAD`: procesa los 8 servicios y el web client.
- `BUILD_ALL=true` en un build manual: procesa los 8 servicios y el web client.

Reglas de archivos:

| Cambio | Resultado |
|:---|:---|
| `services/<servicio>/**` | Solo ese servicio |
| `mobile/**` | Solo web client |
| `build.gradle.kts`, `settings.gradle.kts`, `gradle/`, `gradlew`, `buildSrc/`, `libs/` | Los 8 servicios |
| `Jenkinsfile`, `jenkins/`, `infra/compose/ci/jenkins/` | Los 8 servicios y web client |
| Solo `docs/` o `*.md` | Nada |
| Ruta desconocida | Los 8 servicios y web client |

El `Jenkinsfile` ejecuta los servicios seleccionados en lotes de 3 (`MAX_PARALLEL_SERVICES`) para no agotar la memoria del host. Cada servicio ejecuta:

```text
Compile <servicio> -> gradleBuild
Test <servicio>    -> runTests(publish: false)
Package <servicio> -> dockerBuild
```

`runTests` también genera el reporte JaCoCo y verifica la cobertura mínima: auth, identity, promotion, form y gateway deben tener al menos 60 % de cobertura de líneas, o la etapa `Test <servicio>` falla. Las clases `*Application` y el código que genera Lombok no cuentan en la cobertura.

Los resultados JUnit se publican una sola vez al final, y `publishCoverage` publica la cobertura de cada servicio seleccionado como un resultado independiente. El web client se empaqueta después de los servicios si aplica.

En `main` con tag de release, `Publish images` sigue publicando los 8 servicios y el web client con la versión semver. La ejecución selectiva aplica a los builds de PR y ramas; no reduce la publicación de un release.

## Notificaciones de Discord (HU-16)

### 1. Crear el webhook

En Discord:

1. Abrir el canal de alertas, por ejemplo `#circle-guard-ci`.
2. **Editar canal → Integraciones → Webhooks → Nuevo webhook**.
3. Copiar la Webhook URL.

### 2. Configurar la credencial por JCasC

JCasC administra las credenciales y elimina las que no están declaradas. No crees `discord-webhook` a mano: se recrea en cada bootstrap a partir del `.env` del host.

1. Agregar la Webhook URL al `.env` del host:

```bash
DISCORD_WEBHOOK_URL=<webhook-real>
```

2. `infra/compose/ci/jenkins/casc.yaml` declara la credencial `string` con ID `discord-webhook`.
3. Re-ejecutar `sudo ./infra/bootstrap/bootstrap-jenkins.sh`.

La URL no se versiona: vive en el `.env` del host y en la credencial de Jenkins. Jenkins la enmascara en los logs.

### 3. Comportamiento

`notifyDiscord` se llama desde el `post` del `Jenkinsfile`:

- `failure`: mensaje `Pipeline FAILED`.
- `unstable`: mensaje `Pipeline UNSTABLE`.
- `success` después de un build `FAILURE` o `UNSTABLE` en el mismo job: mensaje `Pipeline RECOVERED`.

El mensaje incluye:

- Rama o PR.
- Servicio afectado.
- Etapa fallida.
- Autor del último commit.
- Enlace al build.

Si Discord no responde, el pipeline no falla por la notificación.

### 4. Probar

1. Probar la Webhook URL con `curl` desde un equipo autorizado.
2. Crear una rama o PR temporal con un fallo controlado en un servicio.
3. Verificar que llega `Pipeline FAILED` con el servicio y la etapa correctos.
4. Corregir el fallo y verificar `Pipeline RECOVERED`.
5. Guardar capturas y enlaces de los builds como evidencia.

## Pruebas E2E (HU-20)

La etapa `E2E tests on dev` corre `tests/e2e-playwright/run-e2e.sh` en `main` después de `Deploy to dev` y `Release notes`. Los escenarios, las variables y cómo leer los resultados están en [`tests/e2e-playwright/README.md`](../../tests/e2e-playwright/README.md).

### Credencial `e2e-user`

Como `discord-webhook`, la declara JCasC a partir del `.env` del host de CI:

1. Agregar al `.env` del host el usuario de prueba y su contraseña:

```bash
E2E_USERNAME=health_user
E2E_PASSWORD=<contraseña-del-usuario-de-prueba>
```

2. Re-ejecutar `sudo ./infra/bootstrap/bootstrap-jenkins.sh`.

Sin esta credencial la etapa falla con `Set E2E_USERNAME`.

## Configuración manual en GitHub y Jenkins

Estos pasos no se pueden dejar solo en código porque dependen de credenciales y de la URL pública del host. Se hacen una vez por entorno.

### 1. Token de GitHub

1. Crear un token con permiso de **escritura** sobre el repositorio (classic: `repo`; fine-grained: *Contents: Read and write*) y `admin:repo_hook` para registrar el webhook. La escritura la necesitan `pushReleaseTag` para publicar los tags (HU-15) y `publishGithubRelease` y `openChangelogPr` para crear releases, la rama `chore/changelog` y su PR (HU-22). Con un token fine-grained, agregar también *Pull requests: Read and write*.
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

### 5. Aplicar la Shared Library

No se crea a mano: JCasC la registra al re-ejecutar el bootstrap.

```bash
sudo ./infra/bootstrap/bootstrap-jenkins.sh
```

Verificar en **Manage Jenkins → System → Global Pipeline Libraries** que existe `circleguard-shared-library`, con `Allow default version to be overridden` activo y sin errores de carga. La rama que se construye debe estar publicada en GitHub, porque de ahí se descarga la librería.

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

# Build selectivo: un cambio solo en form-service debe mostrar solo ese servicio
# Build con BUILD_ALL=true debe mostrar los 8 servicios y el web client
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
