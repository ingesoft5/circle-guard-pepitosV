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

## Relación con los Tags de Imágenes Docker

Cada vez que el proceso genera un tag de release en GitHub (por ejemplo, `v1.3.4`), el pipeline publica las imágenes de los 8 servicios y del cliente web con **un único tag: la misma versión sin la `v`** (`1.3.4`).

```text
<registro>/circleguard/<nombre>:<MAJOR.MINOR.PATCH>
localhost:5000/circleguard/auth-service:1.3.4
localhost:5000/circleguard/web-client:1.3.4
```

Reglas (HU-07):

*   **Sin `latest` ni tags flotantes.** El paso `dockerPublish` de la Shared Library falla si recibe `latest` o cualquier tag que no sea `MAJOR.MINOR.PATCH`. Un tag flotante impide saber qué código corre en cada ambiente y hace que un rollback no sea exacto.
*   **Versiones inmutables.** Una versión publicada no se sobrescribe: `dockerPublish` falla si el tag ya existe con otro commit, y el repositorio `docker-hosted` de Nexus usa `writePolicy: allow_once`. Para corregir una imagen se publica una versión nueva.
*   **Trazabilidad.** Cada imagen lleva las etiquetas OCI `org.opencontainers.image.version` (la versión) y `org.opencontainers.image.revision` (el commit).
*   **Desarrollo local.** Para pruebas locales se construye la imagen con cualquier tag local (`docker build -t auth-service:dev ...`); esas imágenes no se publican en el registro.
