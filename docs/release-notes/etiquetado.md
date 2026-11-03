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

Cada vez que el proceso genera un tag exitoso en GitHub (por ejemplo, `v1.3.4`), el pipeline propaga exactamente esa misma versión al registro de imágenes distribuyendo dos tags simultáneamente:

1.  **Tag Inmutable (`vMAJOR.MINOR.PATCH`):** Garantiza la trazabilidad exacta de qué código se está ejecutando en los entornos (Ej: `mi-registro.io/app:v1.3.4`). **Nunca cambia**.
2.  **Tag Flotante (`latest`):** Apunta siempre a la versión más reciente construida en `main`. Es útil para entornos de desarrollo local o pipelines de pruebas rápidas.
