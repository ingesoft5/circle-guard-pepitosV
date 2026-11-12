# Estrategia de branching

CircleGuard usa un **GitFlow simplificado**: `main` guarda las versiones estables, `dev` integra el trabajo y una rama temporal `release/X.Y.Z` estabiliza cada versión antes de promoverla a `main` mediante un pull request revisado.

```text
rama de trabajo  --PR-->  dev  --PR-->  release/X.Y.Z  --PR-->  main
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
| `release/X.Y.Z` | Rama temporal de estabilización; genera imágenes `-rc` y recibe solo correcciones para ese candidato. |

Las ramas de trabajo no tienen un formato de nombre obligatorio. Lo obligatorio es que los commits y el título del pull request sigan Conventional Commits.

## Flujo de trabajo

1. Crear la rama desde `dev` actualizada.
2. Hacer commits bajo Conventional Commits.
3. Abrir un pull request hacia `dev` y completar la plantilla.
4. Obtener la aprobación de otro integrante. El autor no puede aprobar su propio pull request.
5. Esperar a que pasen los checks obligatorios y fusionar con **Create a merge commit**.
6. Borrar la rama de trabajo.

Para preparar una versión estable, crear `release/X.Y.Z` desde `dev` (con una versión superior al último tag estable):

```bash
git switch dev
git pull --ff-only origin dev
git switch -c release/1.4.0
git push -u origin release/1.4.0
```

El ejemplo usa `1.4.0`; reemplazarlo por la siguiente versión que corresponda. Jenkins valida y publica las imágenes `-rc`; corregir allí cualquier defecto. Cuando el candidato esté aprobado, abrir un pull request de `release/X.Y.Z` hacia `main` con los mismos requisitos. El pipeline de `main` conserva el cálculo y publicación de la versión estable.

## Reglas de protección de `main` y `dev`

| Regla | Valor |
|:---|:---|
| Pull request obligatorio | Sí, se rechaza el push directo |
| Aprobaciones requeridas | 1 |
| Checks obligatorios | `commitlint`, `unit-tests` y `continuous-integration/jenkins/pr-merge` |
| Aplica a administradores | Sí |
| Force push y borrado de la rama | Bloqueados |

El check del pipeline de Jenkins se agregará como obligatorio cuando el pipeline esté funcionando.

Para commits, títulos de pull request y errores frecuentes, ver [commits-y-pull-requests.md](commits-y-pull-requests.md).
