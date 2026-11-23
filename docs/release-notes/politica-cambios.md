# Política de cambios

Define cómo se clasifica un cambio, quién lo aprueba y hasta qué ambiente llega. Aplica a todo cambio que pueda llegar a producción.

## Solicitud de cambio

Cada cambio se registra en un pull request con la plantilla `.github/pull_request_template.md`. Si hay que discutirlo antes de escribir el código, se abre primero un issue con la plantilla **Change request**.

Ambas piden descripción, categoría, riesgo, plan de pruebas y plan de rollback. Un pull request sin esos campos no se aprueba.

## Tamaño de Pull Requests

Todo PR hacia `dev` o `main` debe cumplir la validación automática de tamaño de `PR size policy`: máximo 700 líneas agregadas, 300 eliminadas y 1000 modificadas en total. Se excluyen Markdown, `docs/`, lockfiles, artefactos generados y archivos binarios comunes. Los límites se ajustan en el bloque `environment` del `Jenkinsfile`. Si el cambio supera los límites, debe dividirse en PRs más pequeños. La única excepción automática es el PR de promoción `release/X.Y.Z` hacia `main`: el pipeline de ese PR ejecuta la validación completa del candidato (incluidos despliegue a stage y E2E) y sus checks obligatorios bloquean la fusión. El PR de solicitud `dev` -> `main` no se fusiona; al recibir la etiqueta `release`, la automatización lo cierra y crea la promoción desde `release/X.Y.Z`. La promoción se fusiona con **Create a merge commit**, no squash ni rebase, para conservar el historial que consume SemVer.

## Tipos de cambio

| Tipo | Cuándo aplica | Ejemplos |
|:---|:---|:---|
| **Estándar** | Riesgo bajo, procedimiento conocido y sin efecto en el comportamiento de los servicios | Documentación, pruebas, comentarios |
| **Normal** | Cambia el comportamiento, la configuración o la infraestructura | Funcionalidades, correcciones, migraciones, cambios de pipeline o de Compose |
| **Emergencia** | Hay una falla activa en producción y no se puede esperar al flujo normal | Servicio caído, vulnerabilidad en explotación |

Si hay duda entre dos tipos, se usa el más estricto. Una emergencia se limita a restablecer el servicio; lo demás va en un cambio normal.

## Quién aprueba

| Tipo | Pull request hacia `dev` y `main` | Promoción a stage | Aprobación manual a prod |
|:---|:---|:---|:---|
| **Estándar** | Otro integrante | Automática si pasan las pruebas E2E | Aprobador de producción |
| **Normal** | Otro integrante | Automática si pasan las pruebas E2E | Aprobador de producción, revisando el plan de rollback |
| **Emergencia** | Otro integrante, con prioridad | Automática si pasan las pruebas E2E | Aprobador de producción disponible |

### Aprobadores de producción

| Rol | Persona | GitHub |
|:---|:---|:---|
| Responsable de releases | Ismael Barrionuevo | `DavidRamirez206` |
| Suplente | Johan Suarez | `Jo-Ssua` |

- Nadie aprueba en producción un cambio del que es autor. En ese caso aprueba el otro aprobador.
- Si cambia un aprobador, se actualiza esta tabla, `.github/CODEOWNERS` y el `submitter` del paso `input` del `Jenkinsfile` en el mismo pull request.

### Responsables de código

`.github/CODEOWNERS` asigna a los aprobadores de producción las rutas que deciden qué llega a producción. GitHub les pide revisión automáticamente en cada pull request que las toca:

| Ruta | Qué contiene |
|:---|:---|
| `Jenkinsfile` y `jenkins/` | Pipeline y Shared Library |
| `deploy/` | Compose y script de despliegue de cada ambiente |
| `infra/` | Scripts de aprovisionamiento, stack de CI y Security Groups |
| `.github/` | Plantillas, workflows y este mismo reparto |
| `docs/release-notes/politica-cambios.md` | Esta política |

El resto del repositorio sigue la regla general: lo aprueba otro integrante.

## Ambientes

| Ambiente | Cómo llega un cambio | Control |
|:---|:---|:---|
| `dev` | Despliegue automático desde `main` | Revisión del pull request y checks obligatorios |
| `stage` | Primero RC desde el PR `release/X.Y.Z` -> `main`; después versión estable desde `main` | Jenkins publica el RC, despliega en stage y exige Playwright E2E aprobado |
| `prod` | Versión estable desde `main` | Paso `input` del pipeline; solo los aprobadores de producción |

## Aplicación en el pipeline

El paso `input` de la etapa de producción, y el del job de rollback en `prod`, restringe la aprobación con `submitter: 'admin'`. Jenkins registra quién aprobó y en qué ejecución, lo que queda como evidencia junto con el pull request.

| Usuario de Jenkins | Permisos | Aprueba `prod` |
|:---|:---|:---|
| `admin` | Administrador | Sí. Lo usan los aprobadores de producción de la tabla anterior |
| `dev` | Lanza, cancela y revisa builds, incluido el rollback | No. Jenkins rechaza su respuesta al `input` |

- Jenkins deja que cualquier administrador responda un `input`, sin importar el `submitter`. Por eso `admin` es la única cuenta con permisos de administrador.
- Los usuarios se declaran en `infra/compose/ci/jenkins/casc.yaml` y sus contraseñas en el `.env` del host de CI, nunca en el repositorio.

## Emergencias

Una emergencia usa el mismo flujo de ramas y las mismas reglas de protección: `main` y `dev` no tienen excepciones para fusionar.

1. Si redesplegar la versión anterior resuelve la falla, se hace el rollback antes que un cambio nuevo.
2. Si no basta, se abre un pull request con el tipo **Emergency** y se pide revisión directamente a otro integrante.
3. Después de restablecer el servicio, se registra en el pull request la causa, el impacto y lo que evitará que se repita.

## Registro

Un cambio queda trazado por su historia en Jira, su pull request, la ejecución del pipeline y el tag de la versión. Las release notes y el esquema de versiones se generan según [`etiquetado.md`](etiquetado.md).

Cada despliegue a dev, stage o prod, y cada rollback, queda registrado con fecha, versión, ambiente, quién lo aprobó y resultado, en GitHub Deployments y en `registro-ambientes.md` ([`trazabilidad.md`](trazabilidad.md)).
