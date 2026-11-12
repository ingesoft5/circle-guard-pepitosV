# Esquema de Etiquetado y Versionado Automático (SemVer)

Este repositorio utiliza **Conventional Commits** junto con Jenkins para automatizar el ciclo de vida de las versiones y las imágenes de contenedor.

## Reglas del Formato `vMAJOR.MINOR.PATCH`

El pipeline analiza los mensajes de commit en la rama `main` para determinar el incremento del tag:

*   **`MAJOR` (Incompatible):** Se incrementa cuando se introduce un cambio rompedor en la API o arquitectura. 
    *   *Detonante:* Commits que incluyan un signo de exclamación `!` o la sección `BREAKING CHANGE:` en el pie.
    *   *Ejemplo:* `feat(auth)!: migración a nuevo proveedor de identidad`
*   **`MINOR` (Features):** Se incrementa cuando se añade funcionalidad de manera retrocompatible.
    *   *Detonante:* Commits de tipo `feat`.
    *   *Ejemplo:* `feat(promotion): añadir calculadora de descuentos masivos`
*   **`PATCH` (Fixes):** Se incrementa si se corrigen errores de manera retrocompatible.
    *   *Detonante:* Commits de tipo `fix`.
    *   *Ejemplo:* `fix(gateway): corregir desbordamiento de memoria en peticiones proxy`

*   **Sin release:** commits solo de otros tipos (`docs`, `chore`, `ci`, `build`, `refactor`, `test`, `style`) no generan versión nueva. `perf` cuenta como `PATCH`.
*   **Versión inicial:** si el repositorio no tiene ningún tag `vMAJOR.MINOR.PATCH`, el primer pipeline en `main` crea `v1.0.0`.
*   Si varios commits llegan juntos, se aplica el incremento mayor (por ejemplo, un `fix` y un `feat` dan `MINOR`).

## Cómo lo aplica el pipeline (HU-15)

En cada build de `main`:

1.  **`Release version`** (`calculateVersion`): busca el último tag `vX.Y.Z` del historial, lee los commits posteriores y calcula la versión. Si `HEAD` ya tenía tag (re-ejecución del build), reutiliza esa versión.
2.  **`Publish images`** (`dockerPublish`): publica las 9 imágenes con esa versión.
3.  **`Tag release`** (`pushReleaseTag`): crea el tag anotado `vX.Y.Z` sobre el commit y lo publica en GitHub.

El tag se publica después de las imágenes: si un tag existe en GitHub, sus imágenes existen en el registro. No hace falta crear tags a mano.
4.  **`Deploy to dev`**: despliega esa versión en el ambiente dev.
5.  **`Release notes`** (HU-22): crea el **GitHub Release** del tag con las notas agrupadas en *Breaking changes*, *Features*, *Fixes* y *Performance*, donde cada entrada enlaza a su commit y a su PR. Además abre o actualiza el PR `chore(release): update changelog for vX.Y.Z` hacia `dev` con el [`CHANGELOG.md`](../../CHANGELOG.md) regenerado; al fusionarlo, el archivo llega a `main` con la siguiente promoción.

## Relación con los Tags de Imágenes Docker

Cada vez que el proceso genera un tag de release en GitHub (por ejemplo, `v1.3.4`), el pipeline publica las imágenes de los 8 servicios y del cliente web con **un único tag: la misma versión sin la `v`** (`1.3.4`).

```text
<registro>/circleguard/<nombre>:<MAJOR.MINOR.PATCH>
localhost:5000/circleguard/auth-service:1.3.4
localhost:5000/circleguard/web-client:1.3.4
```

Reglas (HU-07):

*   **Sin `latest` ni tags flotantes.** El paso `dockerPublish` falla si recibe `latest` o un tag fuera de los formatos permitidos: `MAJOR.MINOR.PATCH`, `MAJOR.MINOR.PATCH-SNAPSHOT.BUILD.SHA` o `MAJOR.MINOR.PATCH-rc.BUILD.SHA`. Un tag flotante impide saber qué código corre en cada ambiente y hace que un rollback no sea exacto.
*   **Versiones inmutables.** Una versión publicada no se sobrescribe: `dockerPublish` falla si el tag ya existe con otro commit, y el repositorio `docker-hosted` de Nexus usa `writePolicy: allow_once`. Para corregir una imagen se publica una versión nueva.
*   **Trazabilidad.** Cada imagen lleva las etiquetas OCI `org.opencontainers.image.version` (la versión) y `org.opencontainers.image.revision` (el commit).
*   **Desarrollo local.** Para pruebas locales se construye la imagen con cualquier tag local (`docker build -t auth-service:dev ...`); esas imágenes no se publican en el registro.

## Versiones de integración y candidatos (SNAPSHOT / RC)

Además de los releases estables `MAJOR.MINOR.PATCH`, Jenkins publica imágenes preliminares e inmutables:

| Origen | Formato Docker | Propósito |
|:---|:---|:---|
| PR hacia `dev` o `main` | No publica | Valida el cambio sin distribuir artefactos |
| Rama `dev` | `X.Y.Z-SNAPSHOT.BUILD.SHA` | Integración continua; solo publica los servicios/cliente afectados |
| Rama `release/X.Y.Z` | `X.Y.Z-rc.BUILD.SHA` | Release Candidate; publica los 8 servicios y el web client |
| Rama `main` | `X.Y.Z` | Release estable; conserva el flujo automático existente |

Ejemplos:

```text
localhost:5000/circleguard/auth-service:1.4.0-SNAPSHOT.52.a83f21c
localhost:5000/circleguard/auth-service:1.4.0-rc.61.a83f21c
localhost:5000/circleguard/auth-service:1.4.0
```

`BUILD` es el número de ejecución de Jenkins y `SHA` los primeros siete caracteres del commit. Por tanto, cada publicación tiene un tag único y cumple la política `writePolicy: allow_once` de Nexus. No se reutiliza un tag `SNAPSHOT` flotante: cada build produce una nueva coordenada. Las imágenes incluyen además las etiquetas OCI `org.opencontainers.image.version` y `org.opencontainers.image.revision`.

### Flujo operativo

1. Los PR se integran en `dev` después de pasar la política de tamaño, pruebas y análisis de calidad.
2. Cada build de `dev` publica los componentes afectados con sufijo `-SNAPSHOT`.
3. Para preparar una versión, se crea `release/X.Y.Z` desde `dev`, donde `X.Y.Z` debe ser mayor que el último release estable y coincidir con el incremento calculado por Conventional Commits.
4. Cada build de esa rama publica el conjunto completo con sufijo `-rc`; el número de build y el SHA distinguen cada candidato.
5. Si se requieren correcciones, se integran en la rama `release/X.Y.Z` y se genera otro candidato.
6. Cuando el candidato está aprobado, se integra la rama de release en `main`. El pipeline existente calcula y publica `X.Y.Z` estable, crea `vX.Y.Z` y despliega según las aprobaciones actuales.

Los artefactos preliminares se publican en el mismo repositorio Docker `docker-hosted`, cuya política `allow_once` los mantiene inmutables. No se publica un tag genérico `SNAPSHOT`, `RC` ni `latest`.

**Nota:** CircleGuard publica imágenes Docker en Nexus; el flujo actual no publica JAR como artefactos Maven. Si posteriormente se requiere distribuir JAR, se debe crear un repositorio Maven `maven-snapshots` separado de `maven-releases`.

