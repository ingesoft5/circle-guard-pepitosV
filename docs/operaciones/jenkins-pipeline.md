# Pipeline multibranch de Jenkins

## Alcance


Esta guía cubre la HU-09 (`CGR-32`), la HU-10 (`CGR-33`), la HU-11 (`CGR-34`), la HU-12 (`CGR-35`),la HU-15 (`CGR-38`), la HU-16 (`CGR-39`), la etapa E2E de la HU-20 (`CGR-43`) y la promoción a stage y prod de la HU-14 (`CGR-37`): job multibranch, webhook de GitHub, etapas del `Jenkinsfile`, check obligatorio en `dev` y `main`, Shared Library reutilizable en `jenkins/vars/`, ejecución selectiva por servicio, política de tamaño de PR, versionado semántico y pre-releases automáticos, notificaciones de Discord, pruebas E2E y aprobación manual de producción.

## Cómo funciona

- `infra/compose/ci/jenkins/casc.yaml` declara por JCasC el job multibranch `circleguard`, la credencial `github-pat`, la integración con GitHub y la Global Pipeline Library.
- GitHub Branch Source escanea las ramas y los pull requests del repositorio y crea un build por rama o PR.
- El `Jenkinsfile` de la raíz importa la Shared Library con `@Library` y usa `detectChangedServices` para calcular qué servicios y si el web client deben procesarse.
- Jenkins trabaja como controlador y workers (ver [Controlador y workers](#controlador-y-workers)). La etapa `Build, test and package` abre un carril por servicio en los workers: compila (`gradleBuild`), prueba (`runTests`), analiza con Sonar y espera el quality gate (`sonarScan`), y después empaqueta (`dockerBuild`) o publica (`dockerPublish`) la imagen. Todos los carriles corren en paralelo, y a la vez el controlador ejecuta `Static checks` (escaneo de dependencias, validación de la librería, escaneo de IaC y planes de Terraform). La etapa padre, `Checks, build and package`, usa `failFast`: el primer fallo detiene el resto.
- Si el quality gate de un servicio falla, su carril se detiene antes de empaquetar o publicar la imagen, y el build falla.
- El web client se empaqueta solo si cambió `mobile/` o si se ejecuta con `BUILD_ALL=true`.
- `PR size policy` valida los cambios de cada Pull Request antes de compilar: máximo predeterminado de 700 adiciones, 300 eliminaciones y 1000 líneas modificadas; excluye Markdown, `docs/`, lockfiles, build output, generated output e imágenes/PDF. Los tres límites se configuran con `PR_MAX_ADDITIONS`, `PR_MAX_DELETIONS` y `PR_MAX_CHANGED_LINES` en el bloque `environment` del `Jenkinsfile`. Se omite solo para el PR de promoción `release/X.Y.Z` hacia `main`, cuyo pipeline ejecuta la validación completa del candidato.
- En `dev`, el carril de cada componente afectado publica los componentes afectados como `X.Y.Z-SNAPSHOT.BUILD.SHA`. En el PR de promoción cuyo `CHANGE_BRANCH` es `release/X.Y.Z`, publica los 8 servicios y el cliente web como `X.Y.Z-rc.BUILD.SHA`. Los sufijos de build y SHA mantienen únicos los tags y compatibles con `allow_once` en Nexus.
- En `main`, `Release version` calcula la versión con `calculateVersion` a partir de los Conventional Commits desde el último tag (sin tags: `1.0.0`). Si no hay commits `feat`, `fix`, `perf` ni incompatibles, no hay release y las etapas siguientes se omiten.
- `Publish images` publica las imágenes de los 8 servicios y del web client con esa versión, y después `Tag release` crea y publica el tag `vX.Y.Z` en GitHub con `pushReleaseTag`. El orden garantiza que un tag en GitHub siempre tiene sus imágenes.
- En `main`, después de `Deploy to dev`, `Release notes` (HU-22) crea el GitHub Release con las notas generadas por `releaseNotes` y abre o actualiza el PR `chore(release): update changelog for vX.Y.Z` hacia `dev` con el `CHANGELOG.md` regenerado. No hace push directo porque `main` y `dev` están protegidas. Va antes de las E2E: el tag y las imágenes ya existen, así que el release queda documentado aunque fallen las pruebas.
- Después, la etapa `E2E tests on dev` corre las pruebas de Playwright contra dev y publica sus resultados (HU-20).
- Si las E2E pasan, `Deploy to stage` despliega la misma versión en `stage`; si fallan, el pipeline se detiene y la versión no se promueve.
- `Approve production` detiene el pipeline en un `input` de 15 minutos. Solo el usuario `admin` puede aprobarlo (`submitter: 'admin'`, HU-21); el usuario `dev` no puede. Ver la [política de cambios](../release-notes/politica-cambios.md). Al aprobar, `Deploy to prod` despliega la misma versión en `prod`; si se rechaza o expira, prod no cambia.
- Cada despliegue (`deployK8s`) y cada rollback quedan registrados, salgan bien o mal, con `recordDeployment` (HU-51): un GitHub Deployment del ambiente y una fila en `docs/release-notes/registro-ambientes.md` de la rama `registro-despliegues`, con fecha, versión, ambiente, aprobador y resultado. La revisión de Helm lleva los mismos datos en su descripción. Ver [trazabilidad](../release-notes/trazabilidad.md).
- Cuando un build falla o queda inestable, `notifyDiscord` envía un mensaje al canal de Discord con rama, servicio, etapa, autor y enlace al build. Si el build siguiente se recupera, envía un mensaje de recuperación.
- La librería se carga desde `jenkins/vars/` usando `libraryPath: "jenkins"`, en la versión de la misma rama que se está construyendo.
- Jenkins publica el resultado como estado de commit en GitHub. Para builds de PR el contexto es `continuous-integration/jenkins/pr-merge`.
- Una rama con un PR abierto se construye solo como `PR-<n>` (fusionada con su base) y no también como rama (`buildOriginBranchWithPR(false)`), para no construir dos veces el mismo cambio. Las ramas sin PR, `dev` y `main` se siguen construyendo en cada push.
- El contenedor de Jenkins monta `/var/run/docker.sock` para que las pruebas con Testcontainers puedan iniciar contenedores y para el empaquetado posterior.

## Configuración que vive en el repositorio

| Archivo | Propósito |
|:---|:---|
| `Jenkinsfile` | Detección de cambios, validación del tamaño del PR, ejecución selectiva y publicación. |
| `jenkins/vars/validatePrSize.groovy` | Bloquea PR que superan los límites de adiciones, eliminaciones o líneas totales. |
| `jenkins/vars/resolvePrereleaseVersion.groovy` | Calcula versiones inmutables `SNAPSHOT` y `rc`; en el PR de promoción usa `CHANGE_BRANCH` para reconocer `release/X.Y.Z`. |
| `.github/workflows/release-request.yml` | Crea la rama de release y el PR de promoción al agregar la etiqueta `release`; elimina la rama después del merge. |
| `.github/scripts/resolve-release-version.mjs` | Replica las reglas de `semver.groovy` para calcular la versión desde el SHA solicitado. |
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
| `jenkins/vars/recordDeployment.groovy` | Registra cada despliegue en GitHub Deployments y en `registro-ambientes.md` (HU-51). |
| `jenkins/release-notes.Jenkinsfile` | Job `circleguard-release-notes`: comprueba y completa las release notes de cada tag (HU-51). |
| `jenkins/vars/sonarScan.groovy` | Paso reutilizable de análisis SonarQube por servicio (HU-12). |
| `jenkins/vars/notifyDiscord.groovy` | Notificaciones de fallo, inestabilidad y recuperación en Discord. |
| `jenkins/rollback.Jenkinsfile` | Job `circleguard-rollback`: redespliega una versión publicada (HU-17). |
| `jenkins/README.md` | Contrato y ejemplos de uso de la librería. |
| `infra/compose/ci/jenkins/plugins.txt` | Plugins de multibranch, GitHub y Job DSL. |
| `infra/compose/ci/jenkins/casc.yaml` | Credenciales, servidor GitHub, servidor SonarQube, job multibranch y Global Pipeline Library. |
| `infra/compose/ci/docker-compose.yml` | Variables de GitHub, SonarQube y acceso a Docker. |
| `infra/compose/ci/.env.example` | Plantilla de las variables de GitHub, SonarQube y Nexus. |
| `infra/bootstrap/bootstrap-sonarqube.sh` | Levanta SonarQube, genera el token y asegura el quality gate y el webhook. |
| `infra/bootstrap/bootstrap-jenkins.sh` | Exporta las variables, incluido `SONAR_TOKEN`, y el GID del socket de Docker. |
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

El `Jenkinsfile` abre un carril por servicio seleccionado en un worker (`node('worker')`). Cada carril hace su propio checkout y ejecuta:

```text
Compile <servicio>             -> gradleBuild
Test <servicio>                -> runTests + publishCoverage
Sonar <servicio>               -> sonarScan con -Dsonar.qualitygate.wait=true
Package o Publish <servicio>   -> dockerBuild (PR y ramas) o dockerPublish (SNAPSHOT y RC)
```

`runTests` también genera el reporte JaCoCo y verifica la cobertura mínima: auth, identity, promotion, form y gateway deben tener al menos 60 % de cobertura de líneas, o la etapa `Test <servicio>` falla. Las clases `*Application` y el código que genera Lombok no cuentan en la cobertura.

Cada carril publica sus resultados JUnit y su cobertura (`publishCoverage`) como un resultado independiente. El web client tiene su propio carril si aplica.

En otro carril en paralelo, `Integration tests` (HU-19, CGR-42) ejecuta `./gradlew integrationTest` cuando el build toca auth, form, promotion o gateway, o cuando el cambio obliga a procesar todos los servicios. Esa tarea es distinta de `test`: levanta Kafka, Neo4j, OpenLDAP, PostgreSQL y Redis con Testcontainers y publica sus reportes JUnit. No cuenta para el mínimo de cobertura.

En `main` con tag de release, `Publish images` sigue publicando los 8 servicios y el web client con la versión semver. La ejecución selectiva aplica a los builds de PR y ramas; no reduce la publicación de un release. En el PR de promoción cuyo origen es `release/X.Y.Z`, Jenkins también procesa todos los componentes, publica el RC y lo valida en stage antes de permitir la fusión.

## Análisis SonarQube y quality gate (HU-12)

En el carril de cada servicio, `Sonar <servicio>` se ejecuta después de las pruebas y antes de empaquetar o publicar la imagen. `sonarScan` corre `:services:<servicio>:sonar` con la credencial `sonar-token` y el servidor `SonarQube` declarados por JCasC, y con `-Dsonar.qualitygate.wait=true`: el propio scanner espera el resultado del gate (hasta 10 minutos) y falla si está en rojo. Se usa en lugar de `waitForQualityGate` porque ese paso toma el último análisis del build, y con carriles en paralelo podría leer el de otro servicio. Si el gate falla, la imagen de ese servicio no se empaqueta ni se publica.

Cada servicio tiene su propio proyecto (`circleguard-<servicio>`) y `build.gradle.kts` envía a SonarQube los reportes JUnit y el XML de JaCoCo. El gate `CircleGuard Gate` se crea y se marca como predeterminado desde `bootstrap-sonarqube.sh`, que también registra el webhook `Jenkins` hacia `http://jenkins:8080/sonarqube-webhook/`.

## Notificaciones de Discord (HU-16)

La integración con Discord se apoya en un webhook del canal de alertas. La URL del webhook se guarda en `DISCORD_WEBHOOK_URL` dentro de `infra/compose/ci/.env` y no se versiona. JCasC la convierte en la credencial `discord-webhook` de tipo Secret text, de modo que Jenkins la enmascara en los logs y la recrea en cada bootstrap; la credencial no se administra a mano en la interfaz.

`infra/compose/ci/jenkins/casc.yaml` declara esa credencial y `bootstrap-jenkins.sh` exporta la variable antes de aplicar JCasC. Si la variable no está definida o conserva el valor de plantilla, el bootstrap advierte que las notificaciones no se enviarán.

`notifyDiscord` se invoca desde el bloque `post` del `Jenkinsfile` y su comportamiento es:

- `failure`: mensaje `Pipeline FAILED`.
- `unstable`: mensaje `Pipeline UNSTABLE`.
- `success` después de un build `FAILURE` o `UNSTABLE` en el mismo job: mensaje `Pipeline RECOVERED`.

El mensaje incluye rama o PR, servicio afectado, etapa fallida, autor del último commit y enlace al build. Si Discord no responde, el pipeline no falla por la notificación.

La evidencia de funcionamiento se obtiene de builds controlados: un fallo en un servicio produce `Pipeline FAILED` con el servicio y la etapa correctos, y la corrección posterior produce `Pipeline RECOVERED`. Las capturas y los enlaces de esos builds se conservan como evidencia.

## Pruebas E2E (HU-20)

Las etapas E2E corren `tests/e2e-playwright/run-e2e.sh` en dos puntos del ciclo:

- En el PR de promoción `release/X.Y.Z` -> `main`, después de publicar el RC y desplegarlo en stage, contra `DEPLOY_HOST_STAGE`. El check obligatorio de Jenkins falla si E2E no pasa y bloquea la fusión.
- En `main`, después de `Deploy to dev` y `Release notes`, contra `DEPLOY_HOST_DEV`, conservando la validación existente de la versión estable.

Los escenarios, las variables y cómo leer los resultados están en [`tests/e2e-playwright/README.md`](../../tests/e2e-playwright/README.md).

### Credencial `e2e-user`

Como `discord-webhook`, la declara JCasC a partir del `.env` del host de CI:

1. Agregar al `.env` del host el usuario de prueba y su contraseña:

```bash
E2E_USERNAME=health_user
E2E_PASSWORD=<contraseña-del-usuario-de-prueba>
```

2. Re-ejecutar `sudo ./infra/bootstrap/bootstrap-jenkins.sh`.

Sin esta credencial la etapa falla con `Set E2E_USERNAME`.

## Promoción a stage y prod (HU-14)

Para los RC, `Deploy RC to stage` despliega la imagen preliminar en stage antes de ejecutar E2E. Para la versión estable, `Deploy to stage` conserva el flujo actual: corre después de `E2E tests on dev` y usa el mismo `releaseVersion` que `Deploy to dev`. Si las E2E fallan, la etapa estable no se ejecuta. Si el despliegue a stage falla, el pipeline se detiene y no llega a producción.

`Approve production` es un `input` de 15 minutos. Solo el usuario `admin` puede aprobarlo (`submitter: 'admin'`, HU-21); el usuario `dev` no puede. Ver la [política de cambios](../release-notes/politica-cambios.md). Al aprobar, `Deploy to prod` despliega la misma versión en `prod`; si se rechaza o expira, prod no cambia.

Desde la HU-34 todos los despliegues usan `deployK8s`: `helm upgrade --install --atomic --wait` del chart `deploy/k8s/circleguard` en el clúster k3s del ambiente, y después `k8sSmokeTest` por el Ingress. Las E2E corren contra `K8S_HOST_<ENV>`. Si un rollout no termina en 10 minutos, Helm restaura la revisión anterior, la etapa falla y `post { failure }` avisa a Discord.

Mientras el `input` espera aprobación ocupa un ejecutor del controlador, que tiene 4; los demás builds siguen. El timeout global del pipeline sigue en 60 minutos y el `input` tiene un límite de 15.

## Terraform en el pipeline (HU-32)

Cuando `detectChangedServices` detecta cambios bajo `infra/terraform/` (y no solo documentación):

- **PR:** `Terraform plan (PR)` ejecuta `terraform plan` para `dev`, `stage` y `prod`; el resultado queda en el log del build. No aplica nada.
- **Merge a `main`:** `Terraform plan (main)` genera los tres planes; `Terraform apply dev` y `Terraform apply stage` aplican solo si el plan tiene cambios.
- **Producción:** `Approve Terraform prod` es un `input` de 15 minutos que solo `admin` puede aprobar; después, `Terraform apply prod` aplica el plan guardado.
- Si un ambiente no tiene cambios, el `apply` de ese ambiente se omite.
- El `.tfplan` se aplica tal cual. Si el state cambió entre el plan y el apply, Terraform falla con `Saved plan is stale` y no aplica algo distinto a lo revisado.
- El `.tfplan` no se archiva como artefacto porque puede contener valores sensibles; viaja por `stash`/`unstash` dentro del mismo build.
- La detección usa el diff del PR o `HEAD^1` en `main`, no el fallback de "todos los servicios": un merge solo ve los archivos de `infra/terraform/` que trajo su PR. Los `.md` no cuentan.

Credenciales de Jenkins que usa (ver `casc.yaml`):

| Credencial | Uso |
|:---|:---|
| `aws-terraform` | Archivo de credenciales AWS del usuario que aplica la infraestructura. |
| `prometheus-ssh-key` | Llave privada para el provisioner de observability. |
| `prometheus-grafana-password` | Contraseña de Grafana del release de observability. |

El paso usa `K8S_HOST_<AMBIENTE>` como `TF_VAR_prometheus_ssh_host`. Debe ser la **IP privada** del nodo: el Security Group solo abre el puerto 22 desde la IP privada de Jenkins.

Carga manual en el host de CI (una vez, o al rotar credenciales):

```bash
sudo ./infra/bootstrap/load-terraform-credential.sh aws    < ~/.aws/credentials-terraform
sudo ./infra/bootstrap/load-terraform-credential.sh ssh    < ~/.ssh/circleguard-admin-key
sudo ./infra/bootstrap/load-terraform-credential.sh grafana < ~/grafana-admin-password.txt
```

Cada comando escribe el archivo en `infra/compose/ci/secrets/` y recarga JCasC. El usuario AWS necesita permisos sobre el bucket/tabla del state y sobre los recursos del proyecto (EC2, ECR, S3, CloudWatch, SNS, SSM). El usuario `circleguard-terraform-ci` ya los tiene.

## Rollback (HU-17, HU-52)

El job `circleguard-rollback` (fuera del multibranch, declarado en `casc.yaml`) vuelve atrás un ambiente en su clúster k3s. Se lanza desde Jenkins con **Build with Parameters**:

| Parámetro | Valores |
|:---|:---|
| `ENVIRONMENT` | `stage`, `dev` o `prod` |
| `VERSION` | Vacío: `helm rollback` a la revisión anterior. O una versión publicada, con o sin `v` (`0.1.0` o `v0.1.0`) |

Etapas:

1. **Read current release:** muestra `helm history`, lee la versión que corre y, si `VERSION` está vacío, la última revisión `superseded` y su versión.
2. **Validate version:** solo con `VERSION`. Comprueba en Nexus que existen las 9 imágenes de la aplicación (`deploy/k8s/circleguard/values.yaml`) con esa versión. Si falta alguna, el job falla **antes de tocar el ambiente**.
3. **Approve production:** solo en `prod`, un paso `input` que solo `admin` puede aprobar.
4. **Roll back:** `helm rollback --wait` a la revisión elegida, o `deployK8s` con la versión pedida. En ambos casos termina con `k8sSmokeTest`.

Evidencia: la descripción del build queda como `stage: 0.6.0 -> revision 7 (0.5.0) by <usuario>` y el log termina con `ROLLBACK OK` o `ROLLBACK FAILED`. El plan completo está en [rollback.md](rollback.md).

El job toma su pipeline y la Shared Library de la rama `ROLLBACK_BRANCH` del `.env` de CI (por defecto `main`, la versión estable). Solo se cambia para probar el job desde otra rama, y se vuelve a `main` después.

## Configuración manual en GitHub y Jenkins

Estos pasos no se pueden dejar solo en código porque dependen de credenciales y de la URL pública del host. Se hacen una vez por entorno.

### 1. Token de GitHub

1. Crear un token con permiso de **escritura** sobre el repositorio (classic: `repo`; fine-grained: *Contents: Read and write*) y `admin:repo_hook` para registrar el webhook. La escritura la necesitan `pushReleaseTag` para publicar los tags (HU-15) y `publishGithubRelease` y `openChangelogPr` para crear releases, la rama `chore/changelog` y su PR (HU-22). Con un token fine-grained, agregar también *Pull requests: Read and write* y, para el registro de despliegues de HU-51, *Deployments: Read and write*.
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

### 3. Automatización de solicitud de release por etiqueta

1. En la primera instalación, publicar `.github/workflows/release-request.yml` y `.github/scripts/resolve-release-version.mjs` en `main` mediante un PR exclusivo `ci(github): add label-triggered release automation`. GitHub necesita que el workflow exista en la rama base para recibir el evento de etiqueta. Este commit `ci` no genera una versión estable.
2. Integrar el resto de los cambios de Jenkins y Shared Library en `dev`.
3. Crear la etiqueta de Pull Request `release` en GitHub.
4. En **Settings → Secrets and variables → Actions**, crear `RELEASE_AUTOMATION_TOKEN` con un fine-grained token limitado a este repositorio y permisos `Contents: Read and write`, `Pull requests: Read and write` e `Issues: Read and write`.
5. El flujo `.github/workflows/release-request.yml` se activa cuando se agrega `release` a un PR `dev` -> `main`. Calcula la versión con `.github/scripts/resolve-release-version.mjs`, crea `release/X.Y.Z` desde el SHA del PR, abre el PR de promoción hacia `main` y cierra la solicitud original.
6. Jenkins debe descubrir PRs de origen y ejecutar el check `continuous-integration/jenkins/pr-merge`. En el PR `release/X.Y.Z` -> `main`, `CHANGE_BRANCH` hace que Jenkins publique el RC, lo despliegue a stage y ejecute Playwright E2E. La fusión queda bloqueada hasta que el check pase.
7. Al fusionarse el PR de promoción, el mismo workflow elimina la rama `release/X.Y.Z`.

El token `RELEASE_AUTOMATION_TOKEN` es exclusivo de GitHub Actions. Jenkins continúa usando la credencial `github-pat` almacenada en JCasC.

### 4. Webhook en GitHub

Con `manageHooks: true` y un token con `admin:repo_hook`, GitHub Branch Source registra el hook automáticamente al crear o escanear el job. Verificar en **Settings → Webhooks** del repositorio.

Si el registro automático falla, crear el webhook a mano:

| Campo | Valor |
|:---|:---|
| Payload URL | `https://<CI_DOMAIN>/github-webhook/` |
| Content type | `application/json` |
| Events | `push` y `pull_request` |
| Secret | Opcional; si se usa, configurarlo también en Jenkins |

### 5. Check obligatorio en `dev` y `main`

Se requiere una sola vez y necesita permisos de administrador del repositorio. Se puede aplicar desde **Settings → Branches → Branch protection rules** o con el script del repositorio:

```bash
gh auth login
./infra/github/configure-branch-protection.sh
```

En la regla de cada rama:

1. Mantener `Require a pull request before merging` con 1 aprobación.
2. Mantener el check existente `commitlint`.
3. Agregar `continuous-integration/jenkins/pr-merge` y `unit-tests` en `Require status checks to pass before merging`.
4. Marcar los checks como obligatorios y guardar.
5. Hacer que el job multibranch reporte al menos una vez para que GitHub muestre el contexto.

### 6. Aplicar la Shared Library

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

## Controlador y workers

Jenkins sigue el patrón controlador y workers, con tres máquinas `m7i-flex.large` (2 vCPU y 8 GB, el máximo del plan gratuito):

| Máquina | Nodo en Jenkins | Etiqueta | Ejecutores | Qué corre |
|:---|:---|:---|:---|:---|
| `circleguard-ci` | Built-In Node | `controller` | 4 | Orquestación: checkout, escaneos, Terraform, despliegues, E2E y aprobaciones. Un ejecutor por build en curso. |
| `circleguard-ci-agent-1` | `ci-agent-1` | `worker` | 2 | Carriles de `Build, test and package` |
| `circleguard-ci-agent-2` | `ci-agent-2` | `worker` | 2 | Carriles de `Build, test and package` |

SonarQube y Nexus siguen en `circleguard-tools`.

- **Un build:** sus carriles (8 servicios, el cliente web y las pruebas de integración) se reparten entre los 4 ejecutores de los workers. Antes corrían en lotes de 3 en una sola máquina, con Sonar en serie después.
- **Orden de la cola:** los carriles más largos piden worker primero (integración, cliente web, promotion, identity, notification…), para que un carril largo no arranque al final y alargue el build.
- **Daemon de Gradle:** los workers reutilizan un daemon por ejecutor en lugar de arrancar una JVM en cada llamada (cada carril hace tres). Los helpers usan `JENKINS_NODE_COOKIE=dontKillMe` para que Jenkins no lo mate al terminar el paso, y `gradle.properties` lo apaga tras 30 minutos sin uso.
- **Caché de Gradle compartida:** el servicio `gradle-cache` (Gradle Build Cache Node, en el host de CI, puerto 5071 solo dentro de `circleguard-ci-sg`) guarda compilaciones y resultados de pruebas. Jenkins publica `GRADLE_BUILD_CACHE_URL` en todos los nodos y `settings.gradle.kts` lo usa como caché remota: lo que compiló o probó un worker lo reutiliza el otro. Solo Jenkins escribe; en un equipo de desarrollo, sin la variable, Gradle usa solo su caché local. Guarda hasta 4 GB y descarta lo menos usado.
- **Dos a cuatro builds:** cada uno ocupa un ejecutor del controlador y sus carriles se turnan en los workers. Ningún build espera a otro para empezar.
- Los workers son `EXCLUSIVE`: solo reciben trabajo que pide la etiqueta `worker`. Cada carril hace su propio checkout y no mueve JAR entre máquinas.
- Los despliegues y Terraform se quedan en el controlador, que es la única IP admitida por los clústeres k3s.

Cada worker usa la misma imagen que el controlador (`infra/compose/ci/jenkins/Dockerfile`), iniciada como agente WebSocket por `infra/compose/ci-agent/docker-compose.yml`. Usa el Docker de su host, confía en Nexus como registro y guarda las dependencias de Gradle en su propio volumen. Para crear o recrear un worker:

```bash
# 1. Instancia (idempotente; CI_AGENT_COUNT define cuántos workers)
CI_AGENT_COUNT=2 ADMIN_CIDR=<ip>/32 CI_SUBNET_ID=<subred> CI_KEY_NAME=<llave> \
  infra/cloud/aws/provision-ci-hosts.sh
# 2. En el worker, con el secreto del nodo (Manage Jenkins > Nodes > ci-agent-<n>)
sudo JENKINS_URL=http://<ip-privada-ci>:8080/ JENKINS_AGENT_NAME=ci-agent-<n> \
  NEXUS_REGISTRY=<ip-privada-tools>:5000 JENKINS_AGENT_SECRET=<secreto> \
  infra/bootstrap/bootstrap-ci-agent.sh
```

Los nodos están declarados en `casc.yaml`. `hosts.sh start ci` y `hosts.sh stop ci` encienden y apagan también los workers.

