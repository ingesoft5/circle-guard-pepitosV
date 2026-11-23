# Trazabilidad de releases (HU-51)

Dos registros permiten auditar qué cambió en cada versión y qué versión corre en cada ambiente:

| Pregunta | Dónde se responde | Quién lo escribe |
|:---|:---|:---|
| ¿Qué trae la versión `vX.Y.Z`? | [GitHub Releases](https://github.com/ingesoft5/circle-guard-pepitosV/releases) y `CHANGELOG.md` | `Release notes` del pipeline de `main` (HU-22) |
| ¿Tiene cada tag sus notas? | Job `circleguard-release-notes` | Jenkins, cuando se ejecuta el job |
| ¿Qué se desplegó, dónde, cuándo y quién lo aprobó? | [GitHub Deployments](https://github.com/ingesoft5/circle-guard-pepitosV/deployments) y [`registro-ambientes.md`](https://github.com/ingesoft5/circle-guard-pepitosV/blob/registro-despliegues/docs/release-notes/registro-ambientes.md) | `deployK8s` y el job de rollback, en cada despliegue |

## Release notes por versión

Cada build de `main` que publica una versión crea su GitHub Release con las notas de
`releaseNotes`: los commits desde el tag anterior agrupados en *Breaking changes*, *Features*,
*Fixes* y *Performance*, con enlace al commit y al PR ([`etiquetado.md`](etiquetado.md)).

El job `circleguard-release-notes` comprueba que todos los tags `vX.Y.Z` de `main` tengan su
release con esas notas. Para cada versión compara las notas publicadas con las que genera
`releaseNotes` para ese tag:

| Estado | Significado |
|:---|:---|
| `OK` | El release existe y sus notas son las generadas. |
| `MISSING` | El tag no tiene GitHub Release. |
| `DIFFERENT` | El release existe, pero con otras notas (por ejemplo, uno anterior a HU-22). |
| `CREATED` / `UPDATED` | Con `APPLY`, el job lo creó o reemplazó sus notas. |

1. En Jenkins, abrir `circleguard-release-notes` → **Build with Parameters** con `APPLY` sin
   marcar. El build queda `UNSTABLE` si alguna versión está en `MISSING` o `DIFFERENT`.
2. Revisar el reporte `release-notes-audit.md` en los artefactos del build.
3. Si hay pendientes, ejecutarlo de nuevo con `APPLY` marcado. La versión más alta queda como
   *Latest*; las anteriores se publican sin esa marca.
4. Volver a ejecutarlo sin `APPLY`: todas las versiones deben quedar en `OK`. Ese reporte es la
   evidencia del primer escenario.

## Registro por ambiente

Cada despliegue y cada rollback de dev, stage y prod queda registrado, salga bien o mal, en tres
lugares:

- **GitHub Deployments**: un deployment del ambiente con su estado (`success` o `failure`), el
  enlace al build y la URL del ambiente. GitHub marca como inactivo el anterior, así que la
  pestaña muestra qué versión corre ahora en cada ambiente.
- **`docs/release-notes/registro-ambientes.md`** en la rama `registro-despliegues`: una fila por
  despliegue con fecha, ambiente, versión, tipo, quién aprobó, resultado y build. `main` y `dev`
  están protegidas, así que el registro vive en su propia rama y el pipeline la publica sin PR.
  La rama la crea el primer despliegue; no se edita a mano.
- **`helm history`** del namespace: la descripción de cada revisión lleva el tipo, la versión, el
  aprobador y el build.

### Quién aparece como aprobador

| Despliegue | Aprobó |
|:---|:---|
| dev y stage desde `main` | `automatic: merge by <autor>`, quien fusionó el PR de promoción |
| Release candidate en stage | `automatic: PR #N by <autor>`, el PR de promoción |
| prod | Quien respondió `Approve production` (`PROD_APPROVER`) |
| Rollback en prod | Quien aprobó `Approve production` del job de rollback |
| Rollback en dev o stage | Quien lanzó el job |

Un rollback que se detiene antes de la etapa `Roll back` (versión inexistente, aprobación
rechazada) no toca el ambiente y no se registra. Un prod que no se aprueba tampoco.

Si GitHub no responde, el despliegue no se deshace: el build queda `UNSTABLE` con el motivo en
el log, y el registro de ese despliegue se completa a mano en `registro-ambientes.md`.

### Comprobarlo

1. Desplegar una versión (o ejecutar `circleguard-rollback` en stage).
2. En GitHub → **Deployments**, el ambiente muestra la versión, el estado y el enlace al build.
3. En la rama `registro-despliegues`, `docs/release-notes/registro-ambientes.md` tiene la fila nueva.
4. `helm -n circleguard-<ambiente> history circleguard` muestra la misma versión y aprobador.

La credencial `github-pat` necesita, además de los permisos de HU-15 y HU-22, **Deployments:
Read and write** si es un token fine-grained (un token classic con `repo` ya lo incluye).
