# Registro de uso de IA generativa (IAG): Parte 1

## 1. Declaración

| Dato | Valor |
|:---|:---|
| Nivel de uso | **Nivel 3: colaboración con IAG**, con registro de prompts |
| Herramientas | Asistentes de IA generativa: Claude, Gemini y Google AI Mode |
| Alcance | Entregables E1.1 a E1.8 de la Parte 1 (Sprints 0 y 1) |
| Responsables | Todo el equipo CircleGuard |

El equipo usó la IAG como apoyo para analizar, redactar, diagnosticar y revisar. Ningún resultado entró al repositorio sin pasar por estos cuatro controles:

1. Lo revisó la persona que hizo el prompt.
2. Pasó por un pull request con la aprobación de otro integrante.
3. Pasó los checks obligatorios: `commitlint`, `unit-tests` y el pipeline de Jenkins.
4. Cuando aplicaba, se probó en AWS (Jenkins, SonarQube, Nexus y los ambientes).

Todos los integrantes pueden explicar y modificar lo que se entregó.

## 2. Para qué se usó

| Uso | Ejemplos |
|:---|:---|
| Análisis | Lectura del enunciado y la rúbrica, alcance de cada HU, qué cubre un criterio de aceptación |
| Redacción | Historias de usuario con Gherkin, descripciones de PR con la plantilla del repositorio, documentación en `docs/` |
| Implementación | Scripts Bash de aprovisionamiento, configuración de Jenkins (JCasC y Shared Library), Docker Compose, pruebas |
| Diagnóstico | Lectura de logs de Jenkins y GitHub Actions para encontrar por qué fallaba un build |
| Revisión | Revisión de código contra los criterios de aceptación, riesgos de seguridad y rendimiento |

## 3. Registro de prompts (resumen por tema)

En la columna "Contenido propio entregado" está lo que el equipo le dio a la IAG. Nunca se entregaron contraseñas, tokens ni llaves.

| Tema | HU | Contenido propio entregado | Prompt (resumen) | Resultado y verificación |
|:---|:---|:---|:---|:---|
| Análisis inicial | HU-02 a HU-04 | Enunciado, tablero de Jira y código base | Explicar qué pide la Parte 1 y revisar las primeras HU contra la rúbrica | Lista de ajustes a las HU, que el equipo aplicó en Jira |
| Infraestructura en AWS | HU-03, HU-04 | Scripts existentes y errores del bootstrap en un host limpio | Corregir el bootstrap y separar los ambientes con un Security Group cada uno | Scripts idempotentes; una segunda ejecución sin cambios lo comprueba |
| Compose por ambiente | HU-08 | `stack.yml`, logs de contenedores que no arrancaban | Agregar healthchecks y dependencias entre servicios sin credenciales en texto plano | 12 contenedores `healthy` en local y en dev |
| Pruebas en rojo | HU-18 | Salida de Gradle con pruebas fallidas | Explicar por qué fallaban y cómo corregirlas sin bajar la cobertura | Pipeline en verde; el umbral del 60 % se mantiene |
| Gestión de cambios | HU-21 | Plantilla de PR, enunciado (E1.7) | Proponer la política de cambios, CODEOWNERS y cómo restringir la aprobación de prod | Política, plantillas y aprobación solo por `admin`, probada con el usuario `dev` |
| Pruebas E2E | HU-20 | Cliente web y flujos de la aplicación | Escribir tres flujos con Playwright contra dev | 5 pruebas en verde. Destaparon dos errores reales (QR y aviso del cuestionario), que se corrigieron |
| Rollback | HU-17 | `deploy.sh` y la configuración de Jenkins | Diseñar un job parametrizado que valide la versión antes de desplegar | Probado en stage: versión inexistente rechazada y vuelta de 0.2.0 a 0.1.0 |
| SonarQube y promoción | HU-12, HU-14 | Cambios de la rama y criterios de aceptación | Revisar el código contra los criterios y redactar los PR con la plantilla | PR revisados y fusionados; quality gate bloqueante en el pipeline |
| Ejecución selectiva | HU-11 | `Jenkinsfile` y logs de builds | Encontrar por qué el pipeline construía siempre los 8 servicios | La ejecución selectiva quedó funcionando: un build que toca un servicio procesa solo ese |
| Pruebas de integración | HU-19 | Historia CGR-42 y la estructura del repositorio | Validar si tres pruebas con Testcontainers (Kafka, LDAP y Redis) cubren la historia | Tres pruebas en una tarea `integrationTest` y una etapa propia en Jenkins |
| README operativo | HU-23 | Historia CGR-46 | Qué no puede faltar en una guía de Jenkins, SonarQube, Nexus, ambientes, deploy y rollback | `docs/operaciones/README.md`, con los secretos solo por nombre |
| Documentación y cierre | HU-01, HU-24, HU-25 | Retrospectiva, diagramas y estado del sprint | Revisar la coherencia de la documentación con el repositorio y la rúbrica | Se corrigieron afirmaciones que no coincidían con el repositorio |

### Prompts tal como se usaron

Prompts generales que el equipo repitió en varias HU:

```text
Haz un resumen estructurado del trabajo realizado hasta ahora. Incluye los archivos
modificados, las funcionalidades o fixes implementados y lo que queda pendiente.

Realiza una revisión de código de los cambios que acabo de implementar. Ten en cuenta
los criterios de aceptación. Si encuentras un fallo explica detalladamente por qué está
mal y cómo corregirlo.

Redacta las descripciones para los Pull Requests de las historias de usuario HU-12 y
HU-14. Sigue la plantilla predefinida de PR.

¿Existe algún riesgo de seguridad o rendimiento al mantener la solución como está?
Explícame en detalle los pros y contras antes de decidir si aplicamos un refactor o lo
dejamos así.

Por algún motivo el caching que implementé en el pipeline no funciona. ¿Puedes revisar
qué sentencia está mal para que siga ejecutando siempre los 8 servicios, aunque en el
siguiente push no haya cambiado nada de ellos?

Escribe más bonito este PR que tengo acá para hacer la solicitud a un compañero. Sigue
la plantilla predefinida y ten en cuenta mis cambios.

Dime qué archivos de documentación debo actualizar. Muestra los cambios a realizar y
en dónde.
```

Prompts de inicio de una historia:

```text
Estoy con CGR-46. Misma regla: rama desde dev, nomenclatura del repo, local hasta que
yo diga. Quería una guía en docs/operaciones/README.md, enlazada desde el README, de
Jenkins, SonarQube, Nexus, los ambientes, el deploy y el rollback. Secretos solo por
nombre. ¿Qué no puede faltar y qué dejarías por fuera?

Estoy viendo CGR-42. La rama sale de dev, el nombre sigue docs/agil/branching.md y se
queda en local hasta que yo la suba. Estaba pensando en Testcontainers aparte de las
unitarias: form a Kafka y promotion, login LDAP, y el QR en el gateway con Redis, con
./gradlew integrationTest y una etapa en Jenkins. ¿Eso cubre la historia o me estoy
pasando?
```

### Conversaciones compartidas

- <https://share.gemini.google/ZebNpOAlTpzr>
- <https://share.google/aimode/qSGi74aHoRxJgZl0G>
- <https://share.google/aimode/Th5zl96HrwuDXDhGU>
- <https://share.google/aimode/nvGYXaX81Z72VvanL>

## 4. Reglas que siguió el equipo

- **Nada de secretos en los prompts:** ni contraseñas, ni tokens, ni llaves. Los secretos se mencionan solo por su nombre (por ejemplo, `JENKINS_ADMIN_PASSWORD`).
- **Primero local:** cada historia sale de una rama desde `dev` y se queda en local hasta que su responsable la revisa.
- **Sin atajos:** todo cambio pasa por pull request, revisión de otro integrante y pipeline, igual que el código escrito sin IAG.
- **Contexto propio:** se le daba a la IAG el enunciado, la historia y los logs reales, en lugar de preguntas genéricas, para que las respuestas se ajustaran al proyecto.

## 5. Reflexión crítica

**Dónde ayudó:**

- **Velocidad:** redujo el tiempo de tareas repetitivas, como plantillas, descripciones de PR y documentación, y dejó más tiempo para probar.
- **Diagnóstico:** leer logs largos de Jenkins y Gradle y proponer la causa probable aceleró la corrección de builds en rojo.
- **Revisión:** pedir una revisión contra los criterios de aceptación encontró detalles antes de que los viera un compañero.

**Dónde se equivocó o hubo que corregirla:**

- **Propuso más de lo necesario:** por ejemplo, una cuenta de Jenkins por integrante. El equipo lo redujo a dos cuentas (`admin` y `dev`), que bastan para demostrar la restricción de aprobación.
- **Un cambio sin probar rompió Jenkins:** un plugin agregado a la configuración se instalaba después de que Jenkins la leía, y Jenkins no arrancó. Se corrigió instalando los plugins dentro de la imagen, y desde entonces cada cambio de configuración se prueba antes de pedir revisión.
- **Textos genéricos o inexactos:** la primera versión de la retrospectiva servía para cualquier proyecto y afirmaba entregables que todavía no existían. Se reescribió con las HU, los PR y los problemas reales.
- **Datos que no coincidían con el código:** algunas descripciones (etapas del pipeline, alcance de una prueba, tipos de cambio) se contrastaron con el repositorio y se corrigieron antes de entregarlas.

**Qué aprendimos:**

- La IAG acelera, pero no reemplaza la verificación. Lo que no se prueba en el pipeline o en AWS no se da por hecho.
- Las mejores respuestas salieron de prompts con contexto real (logs, archivos y criterios), no de preguntas abiertas.
- La revisión por pares sigue siendo necesaria: un compañero detecta lo que ni el autor ni la herramienta vieron.
- Hay que poder explicar cada línea. Si alguien del equipo no puede explicar un resultado, ese resultado se rehace o se simplifica.
