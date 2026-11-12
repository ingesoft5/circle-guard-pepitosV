# Estrategia de branching

CircleGuard usa un **GitFlow simplificado**: `main` guarda las versiones estables, `dev` integra el trabajo y una rama temporal `release/X.Y.Z` estabiliza cada versión antes de promoverla a `main` mediante un pull request revisado. La rama release la crea automáticamente GitHub Actions cuando se agrega la etiqueta `release` a un PR de solicitud de release desde `dev` hacia `main`.

```text
rama de trabajo --PR--> dev --PR de solicitud + label release--> release/X.Y.Z --PR validado--> main
```

## Por qué esta estrategia

- **`main` siempre es estable.** El trabajo se integra y se prueba en `dev` antes de llegar a `main`.
- **Es más simple que GitFlow completo.** Un equipo pequeño no necesita ramas `release/*` permanentes ni `hotfix/*`; solo crea una rama temporal `release/X.Y.Z` cuando va a estabilizar una versión. Las correcciones de producto siguen entrando por PR hacia `dev`.
- **Por qué no GitHub Flow.** Todo llegaría directo a `main`, sin una rama donde integrar el trabajo del equipo antes de darlo por estable.
- **Por qué no trunk-based.** Depende de feature flags y de un conjunto de pruebas maduro, que el proyecto todavía no tiene.

## Ramas

| Rama | Uso |
|:---|:---|
| `main` | Protegida y estable. Los tags de versión semántica se crean desde esta rama. |
| `dev` | Protegida. Integra el trabajo terminado. |
| Rama de trabajo | De corta duración, creada desde `dev`. |
| `release/X.Y.Z` | Rama temporal creada por automatización; genera imágenes `-rc`, se valida en stage y recibe solo correcciones para ese candidato. |

Las ramas de trabajo no tienen un formato de nombre obligatorio. Lo obligatorio es que los commits y el título del pull request sigan Conventional Commits.

## Flujo de trabajo

1. Crear la rama desde `dev` actualizada.
2. Hacer commits bajo Conventional Commits.
3. Abrir un pull request hacia `dev` y completar la plantilla.
4. Obtener la aprobación de otro integrante. El autor no puede aprobar su propio pull request.
5. Esperar a que pasen los checks obligatorios y fusionar con **Create a merge commit**.
6. Borrar la rama de trabajo.

## Preparación automática de releases

No se crean ramas release manualmente. Para iniciar una release:

1. Abrir un PR de solicitud desde `dev` hacia `main` y completar la plantilla. Este PR no se fusiona; solo representa la solicitud de release.
2. Agregar la etiqueta exacta `release` al PR.
3. GitHub Actions valida que `main` sea ancestro del commit solicitado en `dev`, calcula la siguiente versión con las mismas reglas de Conventional Commits de Jenkins y crea `release/X.Y.Z` en el SHA exacto del PR.
4. La automatización abre un PR de promoción `release/X.Y.Z` hacia `main` y cierra el PR de solicitud para evitar que `dev` se fusione directamente en `main`.
5. Jenkins construye y prueba el PR de promoción, publica las imágenes `X.Y.Z-rc.BUILD.SHA`, despliega el candidato en `stage` y ejecuta Playwright E2E contra stage. El check obligatorio de Jenkins impide fusionar antes de que termine correctamente.
6. Si hay defectos, abrir PRs de corrección hacia `release/X.Y.Z`. Al actualizarse el PR de promoción, Jenkins vuelve a construir y validar el candidato.
7. Después de la aprobación y de todos los checks, fusionar el PR `release/X.Y.Z` hacia `main` usando **Create a merge commit** (no squash ni rebase). El pipeline existente de `main` calcula y publica la versión estable, crea el tag `vX.Y.Z` y conserva los despliegues y la aprobación de producción actuales. El merge commit conserva los commits Conventional Commits que usa SemVer para calcular la versión.
8. Al fusionarse el PR de promoción, GitHub Actions elimina la rama temporal `release/X.Y.Z`.

El PR de solicitud `dev` -> `main` no debe fusionarse. La promoción real es exclusivamente el PR `release/X.Y.Z` -> `main`.

### Etiqueta y permisos de automatización

**Configuración inicial (una sola vez):** GitHub debe tener el workflow en la rama base `main` antes de poder reaccionar a la etiqueta en un PR `dev` -> `main`. Desde una rama basada en `main`, copiar únicamente los dos archivos de automatización desde la rama que contiene la implementación:

```bash
git switch main
git pull --ff-only origin main
git switch -c ci/bootstrap-release-label
git checkout <rama-de-implementacion> -- .github/workflows/release-request.yml .github/scripts/resolve-release-version.mjs
git add .github/workflows/release-request.yml .github/scripts/resolve-release-version.mjs
git commit -m "ci(github): add label-triggered release automation"
git push -u origin ci/bootstrap-release-label
```

Abrir y fusionar ese PR pequeño hacia `main`. Es un cambio `ci`, por lo que no genera una versión estable según las reglas actuales. Después se integra el resto de la implementación en `dev`.

- Crear en GitHub la etiqueta de PR `release`.
- Configurar el secreto de Actions `RELEASE_AUTOMATION_TOKEN` con un fine-grained token que tenga `Contents: Read and write`, `Pull requests: Read and write` e `Issues: Read and write` en este repositorio.
- Jenkins conserva su credencial existente `github-pat`; no se reemplaza ni se comparte con Actions.
- Solo se permite una rama `release/*` activa a la vez. La automatización rechaza una segunda solicitud mientras exista otra release sin fusionar. Si se cancela una release cerrando su PR sin fusionarlo, eliminar manualmente su rama temporal antes de iniciar otra.

## Reglas de protección de `main` y `dev`

| Regla | Valor |
|:---|:---|
| Pull request obligatorio | Sí, se rechaza el push directo |
| Aprobaciones requeridas | 1 |
| Checks obligatorios | `commitlint`, `unit-tests` y `continuous-integration/jenkins/pr-merge` |
| Método de merge para promociones release | Create a merge commit (obligatorio para conservar el historial SemVer) |
| Aplica a administradores | Sí |
| Force push y borrado de la rama | Bloqueados |

En **Settings → General → Pull Requests**, permitir merge commits y deshabilitar squash y rebase para que una promoción no oculte los Conventional Commits que consume `calculateVersion`. El check del pipeline de Jenkins se agregará como obligatorio cuando el pipeline esté funcionando.

Para commits, títulos de pull request y errores frecuentes, ver [commits-y-pull-requests.md](commits-y-pull-requests.md).
