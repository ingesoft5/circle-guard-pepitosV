# Shared Library de Jenkins

La librería compartida de CircleGuard encapsula las etapas reutilizables del pipeline. Vive en este mismo repositorio, bajo `jenkins/vars/`, y se registra en Jenkins mediante JCasC.

## Estructura

```text
jenkins/
├── README.md
├── rollback.Jenkinsfile
└── vars/
    ├── calculateVersion.groovy
    ├── deployCompose.groovy
    ├── detectChangedServices.groovy
    ├── dockerBuild.groovy
    ├── dockerPublish.groovy
    ├── gradleBuild.groovy
    ├── notifyDiscord.groovy
    ├── openChangelogPr.groovy
    ├── publishCoverage.groovy
    ├── publishGithubRelease.groovy
    ├── pushReleaseTag.groovy
    ├── releaseNotes.groovy
    ├── resolvePrereleaseVersion.groovy
    ├── resolveReleaseVersion.groovy
    ├── validatePrSize.groovy
    ├── runTests.groovy
    ├── semver.groovy
    └── sonarScan.groovy
```

Cada paso está en su propio archivo para que las historias de despliegue agreguen `deployK8s` y otros pasos sin modificar los existentes.

## Registro

`infra/compose/ci/jenkins/casc.yaml` registra la librería como `circleguard-shared-library` con:

- `libraryPath: "jenkins"`: indica que la raíz de la librería es la carpeta `jenkins/`, no la raíz del repositorio.
- `allowVersionOverride: true`: permite que el `Jenkinsfile` elija la versión.
- `defaultVersion`: SHA de respaldo, solo para un `Jenkinsfile` que todavía use `@Library` sin versión.

El `Jenkinsfile` raíz la carga desde la misma rama que se está construyendo (en un PR, la rama de origen):

```groovy
library "circleguard-shared-library@${env.CHANGE_BRANCH ?: env.BRANCH_NAME}"
```

## Pasos disponibles

### `gradleBuild`

Ejecuta una tarea de Gradle para todos los servicios o para uno solo.

| Parámetro | Obligatorio | Valor por defecto | Descripción |
|:---|:---|:---|:---|
| `service` | No | Todos | Nombre del módulo, por ejemplo `circleguard-form-service`. |
| `tasks` | No | `assemble` | Tarea de Gradle que se ejecuta. |
| `gradleArgs` | No | Vacío | Argumentos adicionales para Gradle. |

```groovy
gradleBuild()
gradleBuild(service: 'circleguard-form-service', tasks: 'assemble')
```

### `detectChangedServices`

Calcula qué servicios y si el cliente web deben procesarse en el build actual. Compara `HEAD` contra el último build exitoso o contra la base del PR.

| Parámetro | Obligatorio | Valor por defecto | Descripción |
|:---|:---|:---|:---|
| `buildAll` | No | `false` | Fuerza los 8 servicios y el cliente web. |
| `services` | No | Los 8 módulos | Lista de servicios conocidos. |
| `baseCommit` | No | Automático | Commit base para el `git diff`. |

Devuelve:

```groovy
[services: ['circleguard-form-service'], web: false, all: false, base: 'abc123...']
```

Reglas:

- PR: usa `HEAD^1` del merge commit.
- Rama: usa `GIT_PREVIOUS_SUCCESSFUL_COMMIT`.
- Sin base, base no encontrada o no ancestro: procesa todo.
- `services/<servicio>/...`: solo ese servicio.
- `mobile/...`: cliente web.
- `build.gradle.kts`, `settings.gradle.kts`, `gradle/`, `gradlew`, `buildSrc/`, `libs/`: los 8 servicios.
- `Jenkinsfile`, `jenkins/`, `infra/compose/ci/jenkins/` o rutas desconocidas: los 8 servicios y el web client.
- Solo `docs/` o `*.md`: no procesa nada.
- `BUILD_ALL=true` o primer build: los 8 servicios y el web client.

```groovy
def changed = detectChangedServices(buildAll: params.BUILD_ALL ?: false, services: SERVICES)
```

### `runTests`

Ejecuta las pruebas y, por defecto, genera el reporte de cobertura JaCoCo, verifica el mínimo de cobertura y publica los resultados JUnit incluso cuando la tarea falla.

| Parámetro | Obligatorio | Valor por defecto | Descripción |
|:---|:---|:---|:---|
| `service` | No | Todos | Nombre del módulo que se quiere probar. |
| `testResults` | No | `**/build/test-results/test/*.xml` | Patrón de los reportes JUnit. |
| `publish` | No | `true` | Si es `false`, no publica JUnit; útil cuando se publica una sola vez tras ejecutar servicios en paralelo. |
| `coverage` | No | `true` en unitarias, `false` si `integration` es `true` | Si es `true`, también ejecuta `jacocoTestReport` y `jacocoTestCoverageVerification`. La verificación falla si auth, identity, promotion, form o gateway quedan por debajo del 60 % de cobertura de líneas. |
| `integration` | No | `false` | Si es `true`, ejecuta la tarea `integrationTest` (Testcontainers) y publica `build/test-results/integrationTest/*.xml`. No entra en la cobertura. |

```groovy
runTests()
runTests(service: 'circleguard-form-service')
runTests(service: 'circleguard-form-service', publish: false)
runTests(integration: true, coverage: false)
```

### `publishCoverage`

Publica el reporte JaCoCo de cada servicio como un resultado de cobertura independiente (`coverage-<servicio>`), de modo que Jenkins muestra la cobertura de líneas por servicio. Requiere el plugin `coverage`; si no está instalado, el paso lo informa en el log y no falla el build.

| Parámetro | Obligatorio | Valor por defecto | Descripción |
|:---|:---|:---|:---|
| `services` | No | Ninguno | Lista de servicios cuyo reporte `build/reports/jacoco/test/jacocoTestReport.xml` se publica. |

```groovy
publishCoverage(services: ['circleguard-auth-service', 'circleguard-form-service'])
```

### `sonarScan`

Ejecuta el análisis de SonarQube de un servicio dentro de `withSonarQubeEnv`. El `Jenkinsfile` lo usa en la etapa `Sonar analysis and quality gate`, seguida de `waitForQualityGate abortPipeline: true`.

| Parámetro | Obligatorio | Valor por defecto | Descripción |
|:---|:---|:---|:---|
| `service` | No | Ninguno; ejecuta `sonar` en la raíz | Módulo que se analiza, por ejemplo `circleguard-form-service`. |
| `server` | No | `SonarQube` | Nombre del servidor configurado en Jenkins. |
| `projectKey` | No | No se pasa; `build.gradle.kts` define `circleguard-<servicio>` | Clave del proyecto en SonarQube. |
| `projectName` | No | No se pasa; `build.gradle.kts` define `CircleGuard <servicio>` | Nombre visible del proyecto. |
| `extraArgs` | No | Vacío | Argumentos adicionales para Gradle o Sonar. |

La clave y el nombre de cada proyecto se definen en `build.gradle.kts` como `circleguard-<servicio>` y `CircleGuard <servicio>`. El paso no los pasa salvo que se indiquen explícitamente.

```groovy
sonarScan(service: 'circleguard-form-service')
```

### `validatePrSize`

Valida el tamaño de un Pull Request antes de compilar. En builds PR-Merge compara la punta de la rama destino (segundo padre del merge commit, ver `prDiffBase`) contra el resultado integrado; en builds PR-Head calcula el merge-base con la rama destino. Excluye documentación Markdown, lockfiles, artefactos generados y archivos binarios comunes.

No se aplica al PR de promoción a `main` (desde `dev` o desde una rama `release/*` heredada): ese PR reúne todos los cambios que ya pasaron este límite al entrar a `dev`, así que su tamaño es el del release completo.

| Parámetro | Valor por defecto | Descripción |
|:---|:---|:---|
| `maxAdditions` | `700` | Máximo de líneas agregadas. |
| `maxDeletions` | `300` | Máximo de líneas eliminadas. |
| `maxChangedLines` | `1000` | Máximo de adiciones + eliminaciones. |

```groovy
validatePrSize()
validatePrSize(maxAdditions: 300, maxDeletions: 200, maxChangedLines: 500)
```

### `resolvePrereleaseVersion`

Resuelve una versión única para cada build de integración:

- `dev`: `X.Y.Z-SNAPSHOT.BUILD.SHA`, donde `X.Y.Z` incorpora el incremento SemVer de los Conventional Commits desde el último release.
- PR de promoción cuyo `CHANGE_BRANCH` sea `release/X.Y.Z`: `X.Y.Z-rc.BUILD.SHA`; la versión candidata debe ser superior al último release estable y coincidir con el incremento SemVer calculado. Jenkins usa `CHANGE_BRANCH` porque el multibranch construye el PR de promoción como `PR-<n>`.

Los tags son compatibles con Nexus `allow_once` y llevan metadata OCI de versión y commit.

```groovy
def version = resolvePrereleaseVersion()
```

### `resolveReleaseVersion`

Devuelve la versión de release del commit actual a partir del tag de Git `vMAJOR.MINOR.PATCH` que apunta a `HEAD`, sin la `v` (por ejemplo `1.4.0`). Devuelve `null` si `HEAD` no tiene tag de release. Si un commit tiene varios, usa el mayor.

| Parámetro | Obligatorio | Valor por defecto | Descripción |
|:---|:---|:---|:---|
| `credentialsId` | No | `github-pat` | Credencial para traer los tags de GitHub. |

```groovy
def version = resolveReleaseVersion()
```

`calculateVersion` la usa para que re-ejecutar el build de un commit ya etiquetado devuelva la misma versión.

### `calculateVersion`

Calcula la siguiente versión a partir de los Conventional Commits desde el último tag `vMAJOR.MINOR.PATCH` del historial de `HEAD` (HU-15). Solo calcula; el tag lo crea `pushReleaseTag`.

| Situación | Resultado |
|:---|:---|
| No hay ningún tag de versión | `1.0.0` |
| Algún commit con `!` (`feat(auth)!: ...`) o pie `BREAKING CHANGE:` | Sube `MAJOR` (`1.2.3` → `2.0.0`) |
| Algún `feat` | Sube `MINOR` (`1.2.3` → `1.3.0`) |
| Algún `fix` o `perf` | Sube `PATCH` (`1.2.3` → `1.2.4`) |
| Solo otros tipos (`docs`, `chore`, `ci`, `build`, `refactor`, `test`...) | `null`: no hay release |
| `HEAD` ya tiene un tag de release | Esa versión, con `alreadyTagged: true` |

| Parámetro | Obligatorio | Valor por defecto | Descripción |
|:---|:---|:---|:---|
| `initialVersion` | No | `1.0.0` | Versión cuando aún no existe ningún tag. |
| `credentialsId` | No | `github-pat` | Credencial para traer los tags de GitHub. |

```groovy
def release = calculateVersion()
// [version: '1.3.0', previous: '1.2.3', bump: 'minor', alreadyTagged: false] o null
```

Los commits de merge se ignoran; cuentan los commits que trae cada PR (o el commit único de un squash merge, cuyo mensaje es el título del PR validado por commitlint).

### `pushReleaseTag`

Crea el tag anotado `vMAJOR.MINOR.PATCH` sobre `HEAD` y lo publica en GitHub con la credencial `github-pat`, que necesita permiso de escritura sobre el repositorio.

| Parámetro | Obligatorio | Valor por defecto | Descripción |
|:---|:---|:---|:---|
| `version` | Sí | | Versión `MAJOR.MINOR.PATCH`, sin `v`. |
| `credentialsId` | No | `github-pat` | Credencial con permiso de push. |

```groovy
pushReleaseTag(version: '1.3.0')
```

- Si el tag ya existe en `HEAD` (re-ejecución), solo lo vuelve a publicar.
- Si existe sobre otro commit, falla: una versión publicada no se mueve.

### `releaseNotes`

Genera las release notes de una versión a partir de los Conventional Commits entre su tag y el tag anterior (HU-22).

- Agrupa en **Breaking changes**, **Features**, **Fixes** y **Performance**; `docs`, `chore`, `ci` y demás tipos no se listan.
- Cada entrada enlaza a su commit y, si llegó por un pull request, al PR. El PR se detecta por el merge commit `Merge pull request #N` (se usa el PR de la rama de trabajo, no el de `dev` → `main`) o por el sufijo `(#N)` de un squash merge.
- En los cambios incompatibles muestra el texto del pie `BREAKING CHANGE:` si existe.

```groovy
String notes = releaseNotes(version: '1.3.0')   // sección "## [1.3.0](compare...) (fecha)"
String changelog = releaseNotes.changelog()      // CHANGELOG.md completo, de la versión más nueva a la más antigua
String repo = releaseNotes.repository()          // owner/repo del remoto origin
```

### `publishGithubRelease`

Crea el GitHub Release del tag `vX.Y.Z` con las notas, o las actualiza si el release ya existe (re-ejecución). Usa la credencial `github-pat`.

| Parámetro | Obligatorio | Valor por defecto | Descripción |
|:---|:---|:---|:---|
| `version` | Sí | | Versión `MAJOR.MINOR.PATCH`; el tag debe existir en GitHub. |
| `notes` | Sí | | Notas de `releaseNotes`; el título `## ...` se omite porque el release ya lo muestra. |
| `repository` | No | `releaseNotes.repository()` | `owner/repo`. |
| `credentialsId` | No | `github-pat` | Credencial con permiso de escritura. |

```groovy
publishGithubRelease(version: '1.3.0', notes: releaseNotes(version: '1.3.0'))
```

### `openChangelogPr`

Actualiza `CHANGELOG.md` mediante un pull request, porque `main` y `dev` están protegidas y el pipeline no puede hacer push directo.

1. Crea un worktree aparte sobre `origin/dev` (no toca el checkout del build).
2. Escribe el `CHANGELOG.md` completo y, si cambió, lo publica con force push en la rama `chore/changelog`.
3. Abre el PR `chore(release): update changelog for vX.Y.Z` hacia `dev`, o actualiza el que ya esté abierto.

Como el archivo se regenera completo, un PR anterior sin fusionar no pierde versiones. Si `dev` ya tiene ese contenido, no hace nada. Al fusionar el PR, el cambio llega a `main` con la siguiente promoción; como es `chore`, no genera release.

| Parámetro | Obligatorio | Valor por defecto | Descripción |
|:---|:---|:---|:---|
| `version` | Sí | | Versión del release, para el título del PR. |
| `changelog` | Sí | | Contenido de `releaseNotes.changelog()`. |
| `base` | No | `dev` | Rama destino del PR. |
| `branch` | No | `chore/changelog` | Rama del PR. |

```groovy
openChangelogPr(version: '1.3.0', changelog: releaseNotes.changelog())
```

### `semver`

Funciones puras (`@NonCPS`) que usan los pasos anteriores: `releaseType(mensajes)`, `bump(versión, tipo)`, `highest(versiones)`, `highestBelow(versiones, versión)`, `sortDesc(versiones)`, `fromTags(salidaDeGitTag)`, `isVersion(texto)`.

```groovy
semver.bump('1.2.3', semver.releaseType(['feat(promotion): ...']))  // '1.3.0'
```

### `dockerPublish`

Construye la imagen de un componente y la publica en el registro de Nexus con un único tag: la versión `MAJOR.MINOR.PATCH` (HU-07).

| Parámetro | Obligatorio | Valor por defecto | Descripción |
|:---|:---|:---|:---|
| `version` | Sí | | Versión `MAJOR.MINOR.PATCH`. `latest` u otro formato hace fallar la etapa. |
| `service` | Sí, si no se usa `image` | | Módulo de `services/`, por ejemplo `circleguard-auth-service`. |
| `image` | No | `service` sin el prefijo `circleguard-` | Nombre de la imagen en el registro. |
| `dockerfile` | No | `services/<service>/Dockerfile` | Dockerfile a construir. |
| `context` | No | `services/<service>/build/docker` con `service`; `.` con `image` | Contexto de build. Con `service` es la carpeta donde la tarea Gradle `dockerContext` (parte de `assemble`, etapa *Compile*) deja `app.jar`: la imagen empaqueta el JAR ya compilado y probado, sin volver a compilar en Docker. |
| `registry` | No | `env.NEXUS_REGISTRY` | Registro destino (variable global definida en JCasC). |
| `namespace` | No | `env.IMAGE_NAMESPACE` o `circleguard` | Prefijo de las imágenes. |
| `credentialsId` | No | `nexus-ci` | Credencial de Jenkins con permiso de push. |

```groovy
dockerPublish(service: 'circleguard-auth-service', version: '1.4.0')
dockerPublish(image: 'web-client', dockerfile: 'mobile/Dockerfile', context: 'mobile', version: '1.4.0')
```

Comportamiento:

- Publica `<registro>/<namespace>/<imagen>:<versión>` con las etiquetas OCI de versión y commit.
- Si el tag ya existe con el mismo commit (re-ejecución del build), lo omite; si existe con otro commit, falla porque las versiones publicadas son inmutables.
- El `docker login` usa un `DOCKER_CONFIG` propio del build y se borra al terminar.

### `dockerBuild`

Construye la imagen de un componente sin publicarla. Se usa en builds selectivos de PRs y ramas.

| Parámetro | Obligatorio | Valor por defecto | Descripción |
|:---|:---|:---|:---|
| `version` | No | `ci-<BUILD_NUMBER>` | Tag local de la imagen. |
| `service` | Sí, si no se usa `image` | | Módulo de `services/`, por ejemplo `circleguard-form-service`. |
| `image` | No | `service` sin el prefijo `circleguard-` | Nombre de la imagen local. |
| `dockerfile` | No | `services/<service>/Dockerfile` | Dockerfile a construir. |
| `context` | No | `services/<service>/build/docker` con `service`; `.` con `image` | Contexto de build. Con `service` es la carpeta donde la tarea Gradle `dockerContext` (parte de `assemble`, etapa *Compile*) deja `app.jar`: la imagen empaqueta el JAR ya compilado y probado, sin volver a compilar en Docker. |
| `namespace` | No | `env.IMAGE_NAMESPACE` o `circleguard` | Prefijo de la imagen local. |
| `pull` | No | `true` | Ejecuta `docker build --pull`. |
| `keep` | No | `false` | Conserva la imagen en el host de CI después de construirla. |

```groovy
dockerBuild(service: 'circleguard-form-service')
dockerBuild(image: 'web-client', dockerfile: 'mobile/Dockerfile', context: 'mobile')
```

Comportamiento:

- Construye `<namespace>/<imagen>:<version>` y no hace `docker push`.
- Pasa la versión como build arg `VERSION` y etiqueta OCI de revisión.
- La publicación con semver sigue a cargo de `dockerPublish` en `main`.
- Borra la imagen al terminar, porque solo comprueba que la imagen se construye. Sin esto cada build de PR dejaba una imagen por servicio (unos 3 GB) en el disco de CI. Un paso que necesite la imagen después, por ejemplo un escaneo, usa `keep: true` y la borra al terminar.
- Las capas quedan en la caché de Docker, así que el siguiente build sigue siendo rápido. La caché de más de 24 horas se limpia en cada release de `main`.

### `deployCompose`

Despliega una versión ya publicada en un ambiente, por SSH:

```groovy
deployCompose(environment: 'dev', version: '1.4.0')
```

- Copia `deploy/compose` al host, sin tocar su `.env`, y ejecuta `deploy/compose/deploy.sh`.
- `deploy.sh` descarga las imágenes primero: si la versión no existe, el ambiente no cambia.
- Después espera a que los servicios queden `healthy` y prueba su salud a través del cliente web.
- Necesita la variable `DEPLOY_HOST_<AMBIENTE>` en `infra/compose/ci/.env` y la credencial `deploy-ssh-<ambiente>`.

### `notifyDiscord`

Envía un mensaje informativo a un canal de Discord cuando el pipeline falla, queda inestable o se recupera.

| Parámetro | Obligatorio | Valor por defecto | Descripción |
|:---|:---|:---|:---|
| `status` | No | `FAILED` | `FAILED`, `UNSTABLE` o `RECOVERED`. |
| `service` | No | `N/A` | Servicio afectado. |
| `stage` | No | `unknown` | Etapa donde falló el pipeline. |
| `branch` | No | Rama del build | Rama o PR. |
| `author` | No | Autor del commit | Autor del último commit. |
| `buildUrl` | No | `env.BUILD_URL` | Enlace al build de Jenkins. |
| `buildNumber` | No | `env.BUILD_NUMBER` | Número del build. |
| `credentialsId` | No | `discord-webhook` | Credencial Secret text con la Webhook URL. |

```groovy
notifyDiscord(status: 'FAILED', service: 'circleguard-form-service',
              stage: 'Test circleguard-form-service')
```

- La Webhook URL se lee de la credencial `discord-webhook`, declarada por JCasC desde `DISCORD_WEBHOOK_URL` en el `.env`; nunca se versiona.
- El mensaje incluye rama, servicio, etapa, autor y enlace al build.
- Si Discord no responde, el pipeline no falla por la notificación.

## Actualizar la librería

Basta con cambiar `jenkins/vars/` en la rama de trabajo: el build de esa rama y el de su PR ya usan la versión nueva, y al fusionar pasa a `dev` y `main`. No hay que mover ningún SHA ni re-ejecutar `bootstrap-jenkins.sh`.

Consideraciones:

- Re-ejecutar un build antiguo carga la librería actual de su rama, no la que tenía cuando corrió.
- La librería global corre fuera del sandbox de Groovy (la necesita, por ejemplo, `detectChangedServices`). Como cualquier rama la puede cambiar, solo se construyen ramas de este repositorio y nunca PR de forks (`buildForkPRMerge(false)`).

## Probar la librería

- Un pipeline que importe `@Library('circleguard-shared-library') _` debe resolver todos los pasos sin errores.
- Un cambio en `jenkins/vars/` se usa en el build de su propia rama y de su PR, sin tocar `casc.yaml`.
- Un commit que solo toca `services/circleguard-form-service/**` debe procesar únicamente ese servicio.
- Un cambio en `build.gradle.kts` o `settings.gradle.kts` debe procesar los 8 servicios.
- `BUILD_ALL=true` debe procesar los 8 servicios y el web client.
- Un build fallido debe enviar el mensaje `Pipeline FAILED` al canal de Discord.
- Un build exitoso después de uno fallido debe enviar `Pipeline RECOVERED`.
- Un merge a `main` con un `feat` crea el tag con `MINOR` incrementado y publica las imágenes con esa versión; un merge con solo `docs` o `chore` no crea release.
- Cada release crea su GitHub Release con las notas agrupadas y abre o actualiza el PR `chore(release): update changelog for vX.Y.Z` hacia `dev`.
- La documentación operativa completa está en [docs/operaciones/jenkins-pipeline.md](../docs/operaciones/jenkins-pipeline.md).

### `imageLabels`

Etiquetas de auditoría que `dockerBuild` y `dockerPublish` ponen en cada imagen. Se fijan al construir, así que la promoción RC → estable no cambia el digest.

| Etiqueta | Contenido |
|:---|:---|
| `org.opencontainers.image.title`, `description`, `vendor` | Nombre del componente |
| `org.opencontainers.image.source`, `url` | Repositorio en GitHub |
| `org.opencontainers.image.revision` | Commit |
| `org.opencontainers.image.version`, `ref.name` | Versión con la que se construyó (en un RC, el tag RC) |
| `org.opencontainers.image.created` | Fecha y hora UTC del build |
| `io.circleguard.service` | Imagen (`auth-service`, `web-client`, ...) |
| `io.circleguard.base-version` | `X.Y.Z` a la que pertenece: en una imagen estable promovida dice la versión estable aunque `version` diga el RC |
| `io.circleguard.source-tree` | Árbol de código (identifica el RC por contenido) |
| `io.circleguard.branch`, `change-id` | Rama y número de PR |
| `io.circleguard.build-number`, `build-url`, `job` | Build de Jenkins que la produjo |

```bash
docker image inspect -f '{{ json .Config.Labels }}' <registro>/circleguard/auth-service:0.5.0
```

Al promover en `main`, `promoteRelease` archiva además `promotion-<versión>.json`: por imagen, el tag estable, el tag RC y el digest, con el commit, el árbol y el build.
