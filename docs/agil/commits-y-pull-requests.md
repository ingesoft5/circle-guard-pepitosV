# Commits y pull requests

Guía para que el check `commitlint` pase a la primera. Los mensajes de commit y los títulos de pull request van **en inglés**.

## Formato del commit

```text
<tipo>(<scope>): <descripción>

<cuerpo opcional>

<footers opcionales>
```

Ejemplo:

```text
fix(gateway): correct signed QR expiration

The token expired before the client could read it.

Refs: CGR-77
```

### Primera línea

- **Tipo:** obligatorio, en minúscula y de la lista de abajo.
- **Scope:** opcional, en minúscula y entre paréntesis.
- **`: `:** dos puntos seguidos de un espacio.
- **Descripción:** en imperativo (`add`, no `added`), empieza en minúscula y no termina en punto.
- **Longitud:** máximo 72 caracteres en toda la línea.

### Tipos

| Tipo | Cuándo usarlo | Versión |
|:---|:---|:---|
| `feat` | Funcionalidad nueva | MINOR |
| `fix` | Corrección de un error | PATCH |
| `docs` | Solo documentación | Ninguna |
| `style` | Formato, sin cambiar el comportamiento | Ninguna |
| `refactor` | Cambio interno, sin funcionalidad ni corrección | Ninguna |
| `perf` | Mejora de rendimiento | Ninguna |
| `test` | Pruebas | Ninguna |
| `build` | Gradle, dependencias o Docker | Ninguna |
| `ci` | Pipeline y checks | Ninguna |
| `chore` | Mantenimiento que no encaja en los anteriores | Ninguna |
| `revert` | Revertir un commit anterior | Ninguna |

Un `!` después del tipo o del scope marca un cambio incompatible y sube la versión MAJOR. Debe usarse solo si el cambio de verdad rompe la compatibilidad:

```text
feat(auth)!: replace role schema

BREAKING CHANGE: existing role records must be migrated.
```

### Scopes

`auth`, `identity`, `promotion`, `notification`, `form`, `file`, `gateway`, `dashboard`, `mobile`, `infra`, `deploy`, `jenkins`, `tests`, `docs`, `repo`.

### Cuerpo y footers

- Dejar una línea en blanco después de la primera línea.
- Cada línea del cuerpo debe tener **máximo 100 caracteres**. Hay que partir los párrafos largos en varias líneas.
- Explicar qué cambió y por qué.
- Referenciar la incidencia al final con `Refs: CGR-XX`.

## Pull requests

| | Hacia `dev` | De `dev` hacia `main` |
|:---|:---|:---|
| **Rama de origen** | Rama de trabajo | `dev` |
| **Título** | Igual que un commit: `feat(auth): add login` | `chore(repo): release dev to main` |
| **Descripción** | Plantilla del repositorio | Plantilla, con la lista de cambios incluidos |
| **Aprobaciones** | 1 de otro integrante | 1 de otro integrante |
| **Check `commitlint`** | Valida el título y los commits del PR | Valida el título y todos los commits de `dev` que no están en `main` |
| **Forma de fusionar** | Create a merge commit | Create a merge commit |

### Título

El título sigue las mismas reglas de la primera línea del commit y también lo valida el check. Si se corrige en GitHub, el check vuelve a ejecutarse.

### Forma de fusionar

Usar siempre **Create a merge commit**.

- **No usar Squash ni Rebase de `dev` hacia `main`.** Crean commits distintos en cada rama. Entonces `dev` y `main` divergen y el siguiente PR muestra conflictos y commits repetidos.
- **Los mensajes de merge no fallan.** commitlint ignora los que genera Git o GitHub, como `Merge pull request #12 from ...` o `Merge branch 'dev' into ...`.

## Por qué suele fallar

1. **El check revisa todos los commits del PR, no solo el último.** Un mensaje incorrecto al inicio de la rama hace fallar el PR, aunque los demás estén bien.
2. **Corregir con un commit nuevo no arregla el anterior.** El mensaje incorrecto sigue en la rama, así que hay que reescribirlo.
3. **El error aparece más tarde en `main`.** El PR de `dev` hacia `main` vuelve a validar los commits de `dev`. Como `dev` también exige el check, un mensaje incorrecto debería detenerse antes de llegar ahí.
4. **El cuerpo también cuenta.** Una línea muy larga en el cuerpo hace fallar el check, aunque la primera línea esté bien.

## Errores frecuentes

| Mensaje | Error de commitlint | Corrección |
|:---|:---|:---|
| `arreglo cosas` | `subject may not be empty`, `type may not be empty` | `fix(auth): correct login redirect` |
| `feat(auth) add login` | `subject may not be empty`, `type may not be empty` | Faltan los dos puntos: `feat(auth): add login` |
| `feat(auth):add login` | `subject may not be empty`, `type may not be empty` | Falta el espacio: `feat(auth): add login` |
| `Feat(auth): add login` | `type must be lower-case` | `feat(auth): add login` |
| `feature(auth): add login` | `type must be one of [...]` | Usar un tipo de la lista: `feat` |
| `feat(auth): Add login` | `subject must not be sentence-case` | `feat(auth): add login` |
| `feat(auth): add login.` | `subject may not end with full stop` | Quitar el punto final |
| Primera línea muy larga | `header must not be longer than 72 characters` | Acortarla y pasar el detalle al cuerpo |
| Línea de cuerpo muy larga | `body's lines must not be longer than 100 characters` | Partir la línea |

## Cómo corregir un mensaje

**Último commit:**

```bash
git commit --amend -m "fix(auth): correct login redirect"
```

**Un commit anterior** (`3` es la cantidad de commits hacia atrás; cambiar `pick` por `reword` en el commit a corregir):

```bash
git rebase -i HEAD~3
```

**Si los commits ya estaban en GitHub**, subirlos así:

```bash
git push --force-with-lease
```

Reescribir solo en tu propia rama de trabajo. `dev` y `main` bloquean el force push.

## Validar antes de subir

Con Node.js 22.12 o superior, ejecutar una vez desde la raíz de cada clon:

```bash
npm install
```

Esto instala commitlint y activa el hook `commit-msg`, que rechaza un mensaje incorrecto antes de crear el commit. El hook es opcional y local: el check `commitlint` del PR es el que no se puede omitir.

Para revisar los commits de la rama sin subirlos:

```bash
npx commitlint --from origin/dev --to HEAD --verbose
```
