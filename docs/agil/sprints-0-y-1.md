# Sprints 0 y 1: planeación, review y retrospectiva

**Proyecto:** CircleGuard\
**Periodo:** 28 de octubre al 8 de noviembre de 2026\
**Marco de trabajo:** Scrum\
**Tablero:** Jira, proyecto `CGR` (<https://circleguard.atlassian.net>)

| Integrante | GitHub | En el equipo desde |
|:---|:---|:---|
| Ismael Barrionuevo | `DavidRamirez206` | Sprint 0 |
| Johan Suarez | `Jo-Ssua` | Sprint 0 |
| David Chicué | `Davinerto9` | Sprint 0 |
| Jean Carlo Ocampo | `jcot117` | Sprint 1 |

## 1. Marco de trabajo

El equipo trabaja con **Scrum**, con sprints cortos alineados con las dos entregas del curso:

| Sprint | Fechas | Objetivo |
|:---|:---|:---|
| Sprint 0 | 28 oct – 1 nov | Preparación: equipo, marco, branching, backlog, nube y código base |
| Sprint 1 | 3 nov – 8 nov | Pipeline CI/CD de punta a punta (Parte 1) |
| Sprint 2 | 8 nov – 22 nov | Terraform, Kubernetes, observabilidad, patrones, seguridad y documentación (Parte 2) |

**Por qué Scrum y no Kanban:**

- **Fechas fijas:** el proyecto tiene dos entregas con fecha cerrada, y cada una encaja en un sprint con un objetivo claro.
- **Requisito del curso:** el enunciado pide planeación, review y retrospectiva por sprint, que son eventos propios de Scrum.
- **Trabajo en paralelo:** con varios integrantes trabajando a la vez, la planeación del sprint reparte las HU por persona y deja visibles las dependencias entre ellas (por ejemplo, el despliegue depende de las imágenes y del registro).

**Prácticas:**

- Cada HU tiene el formato "Como… quiero… para…", criterios de aceptación en Gherkin y subtareas.
- El flujo del tablero es Por hacer → En curso → En revisión → Finalizado. Una HU pasa a "En revisión" con su PR abierto y a "Finalizado" cuando el PR se fusiona y el criterio se verifica.
- El seguimiento diario se hace por el chat del equipo y por el tablero.

## 2. Sprint 0

### 2.1. Planeación

**Objetivo (Jira):** *"Conformar el equipo, definir el marco ágil y la estrategia de branching. Backlog inicial con historias de usuario y criterios de aceptación, aprovisionar la nube y cargar el código base."*

| HU | Historia | Responsable |
|:---|:---|:---|
| HU-01 (CGR-24) | Tablero ágil con backlog y sprints documentados | David |
| HU-02 (CGR-25) | Estrategia de branching con `main` protegida y PR revisados | Ismael |
| HU-03 (CGR-26) | Aprovisionar Jenkins, SonarQube y Nexus con scripts Bash idempotentes | Johan |
| HU-04 (CGR-27) | Ambientes dev, stage y prod aislados en la nube | Ismael |

Además, en el Sprint 0 se redactó el backlog completo del proyecto: 56 HU repartidas entre los tres sprints.

### 2.2. Review

| HU | Resultado | Evidencia |
|:---|:---|:---|
| HU-01 | Tablero `CGR` con 56 HU, criterios Gherkin y subtareas; sprints 0, 1 y 2 creados | Tablero de Jira |
| HU-02 | `main` y `dev` protegidas con rulesets, plantillas de commits y validación con `commitlint` | PR #2, `docs/agil/branching.md` |
| HU-03 | Stack de CI (Jenkins, SonarQube y Nexus) montado con scripts idempotentes | PR #3, `infra/bootstrap/` |
| HU-04 | Cuatro máquinas en AWS (CI, dev, stage y prod) con Security Groups separados | PR #5, `infra/cloud/aws/` |

**Resultado:** las 4 HU se completaron. El sprint estaba planeado hasta el 1 de noviembre, pero los PR de HU-02, HU-03 y HU-04 se fusionaron entre el 2 y el 3 de noviembre, y el sprint se cerró en Jira el 4 de noviembre. Por eso el Sprint 1 empezó con un día de traslape.

### 2.3. Retrospectiva

**Qué salió bien:**

- El backlog completo quedó escrito desde el inicio, con cada HU enlazada a su entregable. Eso permitió repartir el trabajo de los tres sprints y, más adelante, reasignarlo con orden cuando Jean se unió al equipo.
- La estrategia de branching y `commitlint` se activaron antes de que empezara el trabajo en paralelo. Desde el primer PR, todos los cambios entraron revisados y con mensajes en el formato que luego usa el versionado automático.
- La infraestructura se montó con scripts y no a mano, así que se pudo reconstruir varias veces sin perder configuración.

**Qué salió mal y cómo se resolvió:**

- **El sprint se extendió tres días.** El aprovisionamiento de AWS y del stack de CI tomó más de lo previsto (permisos, Security Groups y conectividad entre máquinas). Se terminó en los primeros días del Sprint 1.
- **El bootstrap de CI fallaba en un host limpio:** pedía Docker antes de instalarlo. Se corrigió en el PR #5 y se agregó `verify-idempotency.sh` para comprobarlo.
- **La IP de los integrantes cambia:** las reglas de acceso quedaban viejas. Se resolvió con `hosts.sh allow`, que reemplaza la regla anterior del mismo integrante (PR #11).

**Acciones de mejora:**

| Acción | Resultado esperado |
|:---|:---|
| Estimar con margen las HU de infraestructura y ponerlas al principio del sprint | Que el sprint no se extienda por la nube |
| Dejar las dependencias entre HU explícitas en la planeación | Menos bloqueos entre integrantes |
| Documentar cada script en el mismo PR que lo crea | Entorno reproducible desde el primer día |

## 3. Sprint 1

### 3.1. Planeación

**Objetivo (Jira):** *"Pipeline CI/CD inicial funcionando de punta a punta."*

| HU | Historia | Responsable |
|:---|:---|:---|
| HU-05 | Imágenes multi-stage no root para los 8 microservicios | David |
| HU-06 | Imagen del cliente web (`expo export`) servida sin root | David |
| HU-07 | Imágenes versionadas con semver y publicadas en el registro | David |
| HU-08 | Compose por ambiente con healthchecks y sin credenciales en texto plano | Ismael |
| HU-09 | Pipeline multibranch activado por webhook de GitHub | Johan |
| HU-10 | Shared Library de Jenkins | Johan |
| HU-11 | Ejecución selectiva por servicio modificado | Johan |
| HU-12 | Cobertura JaCoCo y quality gate de SonarQube bloqueante | Johan |
| HU-13 | Despliegue automático a dev desde `main` | Ismael |
| HU-14 | Promoción a stage tras E2E y aprobación manual a prod | Johan |
| HU-15 | Versionado semántico automático con tag en Git | David |
| HU-16 | Notificaciones automáticas de fallo del pipeline | Johan |
| HU-17 | Rollback básico redesplegando una versión anterior | Ismael |
| HU-18 | Pruebas unitarias con cobertura ≥ 60 % en servicios núcleo | David |
| HU-19 | Pruebas de integración con Testcontainers | Jean |
| HU-20 | Pruebas E2E con Playwright sobre el cliente web | Ismael |
| HU-21 | Proceso formal de solicitud y aprobación de cambios | Ismael |
| HU-22 | Release notes automáticas y esquema de etiquetado | David |
| HU-23 | README operativo que permite reproducir el entorno | Jean |
| HU-24 | Diagramas de arquitectura y del pipeline | Johan |
| HU-25 | Bitácora de IAG e informe PDF de la Parte 1 | Ismael |

**Reasignación por la llegada de Jean.** Jean Carlo se unió al equipo al inicio del Sprint 1, porque no tenía grupo de trabajo. En el Sprint 0 el backlog se había repartido entre los tres integrantes iniciales, y al empezar el Sprint 1 varias de sus HU ya estaban adelantadas, así que no era práctico dividir ese trabajo para pasárselo. Se acordó entonces que en el Sprint 1 Jean tomara dos HU (HU-19 y HU-23) y que la compensación se hiciera en el Sprint 2, donde queda como responsable de más historias. Jira refleja esa misma división.

Que Jean sea el responsable de esas HU no significa que las haga solo. Él las lidera, y el resto del equipo revisa su avance en cada seguimiento, le ayuda cuando lo necesita y comparte el trabajo de las que resulten más pesadas. Así la carga del Sprint 2 queda repartida entre todos.

### 3.2. Review

| Área | Resultado | Evidencia |
|:---|:---|:---|
| Imágenes (HU-05 a HU-07) | 8 servicios y el cliente web en imágenes multi-stage sin root, publicadas en Nexus con su versión y nunca con `latest` | PR #10, #15 y #16 |
| Compose y despliegue a dev (HU-08 y HU-13) | Stack por ambiente con healthchecks; cada merge a `main` despliega en dev | PR #20 y #23 |
| Pipeline (HU-09 a HU-12) | Multibranch con webhook, Shared Library, ejecución selectiva, JaCoCo y quality gate de SonarQube bloqueante | PR #8, #12, #28 y #48 |
| Promoción (HU-14) | Promoción a stage tras las E2E y aprobación manual antes de prod | PR #48 |
| Versionado y release notes (HU-15 y HU-22) | Versión calculada desde los commits, tag en Git, GitHub Release y PR de `CHANGELOG.md`. Releases `v0.1.0` (5 nov), `v0.2.0` y `v0.3.0` (7 nov) | PR #35 y #36, Releases de GitHub |
| Notificaciones (HU-16) | Aviso a Discord cuando el pipeline falla | PR #29 |
| Rollback (HU-17) | Job `circleguard-rollback`; probado en stage de 0.2.0 a 0.1.0 y con una versión inexistente que falla sin tocar el ambiente | PR #43 |
| Pruebas (HU-18 a HU-20) | Unitarias con gate de 60 %, 3 pruebas de integración con Testcontainers y 5 pruebas E2E con Playwright contra dev | PR #41, #46 y #34 |
| Gestión de cambios (HU-21) | Plantillas de PR y de solicitud de cambio, política de aprobación, y solo el usuario `admin` aprueba prod | PR #19 y #49 |
| Documentación (HU-23 a HU-25) | README operativo; diagrama de arquitectura (vista de despliegue: CI y los tres ambientes) y diagrama del pipeline en `docs/arquitectura/`; bitácora de IAG (`IAG_REGISTRO.md`) e informe PDF | PR #47, `docs/arquitectura/`, `IAG_REGISTRO.md` |
| Release final (`v0.4.0`) | Primer release que recorre todo el flujo: dev, E2E, stage, aprobación de `admin` y prod | Build de `main`, Release `v0.4.0` en GitHub |

**Resultado:** las 21 HU del sprint se completaron y se cumplió el objetivo: un pipeline CI/CD que lleva cada cambio desde el PR hasta producción, con pruebas, análisis de calidad, versionado, aprobación manual y rollback.

### 3.3. Retrospectiva

**Qué salió bien:**

- **El pipeline funciona de punta a punta:** un merge a `main` construye, prueba, analiza, publica, etiqueta y despliega en dev y stage sin pasos manuales; prod solo requiere la aprobación de `admin`.
- **La Shared Library** permitió que cuatro personas agregaran etapas sin pisarse en el `Jenkinsfile`.
- **La ejecución selectiva** redujo el tiempo de los builds que tocan un solo servicio.
- **Revisión por pares en todos los PR:** ningún cambio llegó a `dev` ni a `main` sin la aprobación de otro integrante.
- **El rollback se probó de verdad,** incluido el caso de error.

**Qué salió mal y cómo se resolvió:**

- **Un solo ejecutor de Jenkins y builds de ~30 minutos.** Cada push hacía esperar al resto del equipo. Se limitó a un build a la vez (PR #26), se aceleraron los builds de CI (PR #34) y se acordó agrupar los cambios en pocos PR.
- **Checks en rojo por builds cancelados.** Cancelar el build de una rama deja el commit en rojo en GitHub. Se acordó abrir el PR en el mismo momento del push y no cancelar builds.
- **PR fusionados antes de que Jenkins terminara.** Se resolvió haciendo obligatorios en los rulesets los checks `continuous-integration/jenkins/pr-merge` y `unit-tests`, además de `commitlint`.
- **El release fallaba en "Release notes":** el build de `main` no tenía la rama `dev` que necesitaba el changelog. Se corrigió con un `fetch` explícito.
- **Las pruebas de integración fallaban con Docker 29:** la versión de Testcontainers no era compatible. Se fijó una versión que sí lo es.
- **La aprobación de prod no restringía nada:** Jenkins deja aprobar a cualquier administrador, y todos lo eran. Se agregó `matrix-auth`: solo `admin` es administrador, y el usuario `dev` no puede aprobar (PR #49).
- **Un plugin nuevo dejó a Jenkins sin arrancar:** se instalaba después de leer la configuración. Ahora los plugins van dentro de la imagen (PR #49).
- **Costos de AWS:** las cuatro máquinas encendidas todo el día consumen el presupuesto. Se apagan al terminar cada sesión con `hosts.sh stop`.

**Acciones de mejora para el Sprint 2:**

| Acción | Resultado esperado |
|:---|:---|
| Agrupar los cambios de un mismo tema en una rama y un PR | Menos builds en cola |
| Probar en Jenkins cada cambio de configuración antes de pedir revisión | Que un plugin o un paso nuevo no rompa el pipeline compartido |
| Actualizar Jira en el mismo momento en que se abre o se fusiona el PR | Tablero fiel al estado real |
| Dejar diagramas y documentación dentro de la HU que hace el cambio | Que la documentación no se acumule al final |
| Registrar el uso de IA en cada sesión y no al final | Bitácora de IAG completa sin reconstruirla |
| Trabajar las HU que lidera Jean en el Sprint 2 como trabajo de todo el equipo: revisar su avance en cada seguimiento, apoyarlo en lo que necesite y repartir las tareas más pesadas | Carga equilibrada entre los cuatro integrantes |

## 4. Balance de los dos sprints

| Dimensión | Sprint 0 | Sprint 1 |
|:---|:---|:---|
| Gestión ágil | Equipo, Scrum y backlog de 56 HU | Seguimiento en tablero y cierre de HU |
| Repositorio | Branching, rulesets y `commitlint` | Checks obligatorios de Jenkins y de pruebas |
| Infraestructura | Cuatro máquinas aisladas y stack de CI | Despliegue automático a dev, stage y prod |
| Calidad | Criterios de aceptación en Gherkin | Cobertura, SonarQube y pruebas unitarias, de integración y E2E |
| Operación | Scripts de aprovisionamiento | Notificaciones, rollback y README operativo |

## 5. Compromisos del equipo para el Sprint 2

1. Mantener el backlog priorizado y con criterios de aceptación verificables.
2. Definir las dependencias técnicas entre HU en la planeación.
3. Acompañar las HU que lidera Jean en el Sprint 2: el equipo está pendiente de su avance y le ayuda en lo que necesite, para que la reasignación no se convierta en una carga alta para él.
4. Mantener las reglas de branching y la revisión por pares en todos los PR.
5. Conservar las pruebas automatizadas como condición para fusionar.
6. Probar el rollback después de cada cambio importante en el despliegue.
7. No versionar secretos y revisar permisos en cada ambiente.
8. Actualizar la documentación en el mismo PR que cambia la arquitectura o el proceso.
9. Hacer la retrospectiva al cierre del sprint y convertir sus conclusiones en acciones con responsable.
