# Shared Library de Jenkins

La librería compartida de CircleGuard encapsula las etapas reutilizables del pipeline. Vive en este mismo repositorio, bajo `jenkins/vars/`, y se registra en Jenkins mediante JCasC.

## Estructura

```text
jenkins/
├── README.md
└── vars/
    ├── deployCompose.groovy
    ├── detectChangedServices.groovy
    ├── dockerBuild.groovy
    ├── dockerPublish.groovy
    ├── gradleBuild.groovy
    ├── resolveReleaseVersion.groovy
    ├── runTests.groovy
    └── sonarScan.groovy
```

Cada paso está en su propio archivo para que las historias de despliegue agreguen `deployK8s` y otros pasos sin modificar los existentes.

## Registro

`infra/compose/ci/jenkins/casc.yaml` registra la librería como `circleguard-shared-library` con:

- `libraryPath: "jenkins"`: indica que la raíz de la librería es la carpeta `jenkins/`, no la raíz del repositorio.
- `defaultVersion`: SHA completo del commit que contiene la versión estable de `jenkins/vars/`.
- `allowVersionOverride: false`: fija la versión y evita que un Jenkinsfile use otra.

El `Jenkinsfile` raíz la importa con:

```groovy
@Library('circleguard-shared-library') _
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

Ejecuta las pruebas y, por defecto, publica los resultados JUnit incluso cuando la tarea falla.

| Parámetro | Obligatorio | Valor por defecto | Descripción |
|:---|:---|:---|:---|
| `service` | No | Todos | Nombre del módulo que se quiere probar. |
| `testResults` | No | `**/build/test-results/test/*.xml` | Patrón de los reportes JUnit. |
| `publish` | No | `true` | Si es `false`, no publica JUnit; útil cuando se publica una sola vez tras ejecutar servicios en paralelo. |

```groovy
runTests()
runTests(service: 'circleguard-form-service')
runTests(service: 'circleguard-form-service', publish: false)
```

### `sonarScan`

Ejecuta el análisis de SonarQube dentro de `withSonarQubeEnv`. La etapa se activa en la HU-12, cuando el plugin de Sonar en Gradle, el servidor de Jenkins y el quality gate estén configurados.

| Parámetro | Obligatorio | Valor por defecto | Descripción |
|:---|:---|:---|:---|
| `server` | No | `SonarQube` | Nombre del servidor configurado en Jenkins. |
| `projectKey` | No | `env.JOB_NAME` | Clave del proyecto en SonarQube. |
| `projectName` | No | `projectKey` | Nombre visible del proyecto. |
| `extraArgs` | No | Vacío | Argumentos adicionales para Gradle o Sonar. |

```groovy
sonarScan(projectKey: 'circleguard', projectName: 'CircleGuard')
```

### `resolveReleaseVersion`

Devuelve la versión de release del commit actual a partir del tag de Git `vMAJOR.MINOR.PATCH` que apunta a `HEAD`, sin la `v` (por ejemplo `1.4.0`). Devuelve `null` si `HEAD` no tiene tag de release. Si un commit tiene varios, usa el mayor.

| Parámetro | Obligatorio | Valor por defecto | Descripción |
|:---|:---|:---|:---|
| `credentialsId` | No | `github-pat` | Credencial para traer los tags de GitHub. |

```groovy
def version = resolveReleaseVersion()
```

Hasta la HU-15 el tag se crea a mano (`git tag v1.0.0 && git push origin v1.0.0`); después lo crea el versionado automático.

### `dockerPublish`

Construye la imagen de un componente y la publica en el registro de Nexus con un único tag: la versión `MAJOR.MINOR.PATCH` (HU-07).

| Parámetro | Obligatorio | Valor por defecto | Descripción |
|:---|:---|:---|:---|
| `version` | Sí | | Versión `MAJOR.MINOR.PATCH`. `latest` u otro formato hace fallar la etapa. |
| `service` | Sí, si no se usa `image` | | Módulo de `services/`, por ejemplo `circleguard-auth-service`. |
| `image` | No | `service` sin el prefijo `circleguard-` | Nombre de la imagen en el registro. |
| `dockerfile` | No | `services/<service>/Dockerfile` | Dockerfile a construir. |
| `context` | No | `.` | Contexto de build. |
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
| `context` | No | `.` | Contexto de build. |
| `namespace` | No | `env.IMAGE_NAMESPACE` o `circleguard` | Prefijo de la imagen local. |
| `pull` | No | `true` | Ejecuta `docker build --pull`. |

```groovy
dockerBuild(service: 'circleguard-form-service')
dockerBuild(image: 'web-client', dockerfile: 'mobile/Dockerfile', context: 'mobile')
```

Comportamiento:

- Construye `<namespace>/<imagen>:<version>` y no hace `docker push`.
- Pasa la versión como build arg `VERSION` y etiqueta OCI de revisión.
- La publicación con semver sigue a cargo de `dockerPublish` en `main`.

### `deployCompose`

Despliega una versión ya publicada en un ambiente, por SSH:

```groovy
deployCompose(environment: 'dev', version: '1.4.0')
```

- Copia `deploy/compose` al host, sin tocar su `.env`, y ejecuta `deploy/compose/deploy.sh`.
- `deploy.sh` descarga las imágenes primero: si la versión no existe, el ambiente no cambia.
- Después espera a que los servicios queden `healthy` y prueba su salud a través del cliente web.
- Necesita la variable `DEPLOY_HOST_<AMBIENTE>` en `infra/compose/ci/.env` y la credencial `deploy-ssh-<ambiente>`.

## Actualizar la versión de la librería

1. Hacer el cambio en `jenkins/vars/`.
2. Crear el commit correspondiente.
3. Copiar el SHA completo del nuevo commit.
4. Actualizar `defaultVersion` en `infra/compose/ci/jenkins/casc.yaml`.
5. Re-ejecutar `sudo ./infra/bootstrap/bootstrap-jenkins.sh`.

No se usa una rama móvil como versión: así un build antiguo siempre carga la misma versión de la librería.

## Probar la librería

- Un pipeline que importe `@Library('circleguard-shared-library') _` debe resolver todos los pasos sin errores.
- Un cambio en `jenkins/vars/` no altera los builds que usan un SHA anterior hasta que se actualice `defaultVersion`.
- Un commit que solo toca `services/circleguard-form-service/**` debe procesar únicamente ese servicio.
- Un cambio en `build.gradle.kts` o `settings.gradle.kts` debe procesar los 8 servicios.
- `BUILD_ALL=true` debe procesar los 8 servicios y el web client.
- La documentación operativa completa está en [docs/operaciones/jenkins-pipeline.md](../docs/operaciones/jenkins-pipeline.md).
