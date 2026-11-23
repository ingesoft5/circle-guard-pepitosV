# Informe de pruebas

Revisión `338b5a3` de `dev`, 22 de noviembre de 2026. SonarQube 9.9.8 analizó esa revisión
a las 19:05 (hora de Colombia). El build 87 de la rama `dev` en Jenkins terminó en SUCCESS
y archivó los reportes de Trivy de las imágenes `0.8.0-SNAPSHOT`. Locust y ZAP no se
repitieron: las cifras de abajo son las del 21 de noviembre contra el Ingress de k3s-dev.
k3s-stage no tenía el release.

## Cobertura

`build.gradle.kts` exige 60 % de líneas en auth, identity, promotion, form y gateway.
El quality gate de SonarQube, `CircleGuard Gate`, mira el código nuevo y falla si la
cobertura nueva baja de 80 %, si la duplicación nueva pasa de 3 %, o si la fiabilidad,
la seguridad o la mantenibilidad del código nuevo dejan de ser A. También exige que los
security hotspots nuevos estén revisados. El scanner espera el gate hasta 10 minutos.

La cobertura de la tabla es la que SonarQube guardó a partir del XML de JaCoCo de ese build.
Los cinco servicios con umbral de Gradle están por encima del 60 % y del 80 % de cobertura
nueva. Los otros tres también tienen el gate en OK.

| Servicio | Líneas | Cobertura | Cobertura nueva | Bugs | Vulnerabilidades | Olores | Duplicación | Gate |
|---|---:|---:|---:|---:|---:|---:|---:|---|
| auth | 551 | 97,1 % | 100 % | 1 | 0 | 22 | 0 % | OK |
| identity | 332 | 97,5 % | 100 % | 0 | 0 | 6 | 0 % | OK |
| promotion | 2197 | 98,0 % | 94,3 % | 3 | 0 | 35 | 2,7 % | OK |
| form | 809 | 87,8 % | 80,9 % | 0 | 0 | 14 | 0 % | OK |
| gateway | 139 | 97,7 % | 100 % | 0 | 0 | 6 | 0 % | OK |
| dashboard | 210 | 98,1 % | | 0 | 0 | 10 | | OK |
| file | 76 | 86,7 % | | 0 | 0 | 5 | | OK |
| notification | 623 | 92,8 % | | 0 | 0 | 18 | | OK |

La fiabilidad global no es A en dos servicios: auth está en C y promotion en E. El gate no
los frena porque solo evalúa el código nuevo desde el 15 de noviembre de 2026. Los bugs
abiertos son estos:

- auth, `IdentityClient` línea 20, `java:S2259`: `response` puede ser nulo.
- promotion, `CircleService` línea 91, `java:S2119`: se crea un `Random` y no se reutiliza.
- promotion, `HealthStatusService` líneas 37 y 189, `java:S2229`: `updateStatus` y
  `resolveStatus` se invocan desde la misma clase y su `@Transactional` no aplica.

## Locust

Objetivos y corrida en `tests/performance-locust/OBJETIVOS.md`. Locust 2.32.4, headless,
contra `http://web:8080` dentro de `circleguard-dev`.

Acceso (`POST /api/v1/gate/validate`): 50 usuarios, 30 segundos, 2739 peticiones, 0 % de
error, p95 13 ms, 92 req/s. El objetivo es 50 usuarios, p95 de 500 ms, 30 req/s y 1 % de
error. Cumple los cuatro.

Cuestionarios en rampa (`POST /api/v1/surveys`): 10, 30, 60 y 120 usuarios. 12748
peticiones, 0 % de error, p95 97 ms, máximo 961 ms. El punto de quiebre sería el primer
tramo con p95 mayor de 2 s o más de 5 % de error. No hubo quiebre. El HPA de `form` llegó
a 3 réplicas, su máximo, con la CPU al 160 % del objetivo. `gateway` se quedó en 1 réplica.
El objetivo estable de cuestionarios (30 usuarios, p95 de 800 ms, 15 req/s, 1 % de error)
también se cumple con esa corrida.

## ZAP

`tests/security-zap/rules.tsv` registra la corrida del 21 de noviembre contra el Ingress de
dev: 21 URLs, `FAIL-NEW` 0, `WARN-NEW` 6. Los avisos son CSP, caché, Permissions-Policy,
una marca de tiempo, JavaScript y COEP. No hay líneas `IGNORE`. El objetivo del pipeline es
fallar solo ante alertas altas nuevas, y ese objetivo se cumple. Esa prueba no salió del job
de Jenkins: fue una corrida manual, porque stage no tenía release.

## Trivy

En el build 87 el escaneo de las nueve imágenes (`auth`, `identity`, `promotion`, `form`,
`gateway`, `dashboard`, `file`, `notification` y `web`) y el escaneo de IaC no dejaron
hallazgos HIGH ni CRITICAL. Esos dos escaneos usan Trivy 0.57.1 y salen con código 1, así
que un hallazgo de ese nivel rompe el build. El build terminó en SUCCESS.

`.trivyignore` sigue aceptando tres CVE de Spring Framework (`CVE-2026-47884`,
`CVE-2026-47890`, `CVE-2026-47892`) hasta el 17 de febrero de 2027, porque el arreglo
publicado está en Spring Framework 7.0.9. No aparecen en el JSON de las imágenes de este
build. `.trivyignore.rego` acepta el sistema de archivos escribible de neo4j, zookeeper,
kafka y openldap, y el acceso del rol `circleguard-deployer` a roles e Ingress. La revisión
de esas excepciones es la misma fecha.

El escaneo del sistema de archivos es informativo (`--exit-code 0`). En
`mobile/package-lock.json` quedaron 1 CRITICAL y 74 HIGH, 61 CVE distintos. El CRITICAL es
`CVE-2026-9277` en `shell-quote` 1.8.3, con arreglo en 1.8.4. Los HIGH se concentran en
`brace-expansion` (15), `@xmldom/xmldom` (13), `axios` (12), `node-forge` (8) y `js-yaml`
(6). No están en las imágenes publicadas.

## Análisis

La cobertura, el quality gate, el acceso, el objetivo estable de cuestionarios, las alertas
altas de ZAP y el escaneo de imágenes cumplen su objetivo.

El estrés de cuestionarios no encontró el punto de quiebre. La rampa se detiene en 120
usuarios y, con el HPA de `form` ya en el máximo, el p95 siguió en 97 ms y no hubo errores.
La mejora es alargar la rampa por encima de 120 usuarios hasta pasar 2 s de p95 o 5 % de
error, subir el máximo de réplicas de `form` antes de una carga mayor, y repetir la corrida
en stage cuando ese ambiente tenga release.

Los seis avisos de ZAP no rompen el build. La causa es que las respuestas del cliente web no
envían Content-Security-Policy, Permissions-Policy, Cross-Origin-Embedder-Policy ni una
política de caché restrictiva, y ZAP marca una marca de tiempo y un patrón de JavaScript.
La mejora es agregar esas cabeceras en el nginx del cliente web y repetir el baseline.

El lockfile de la app móvil no cumple un resultado limpio de HIGH y CRITICAL. El build no
lo muestra porque ese escaneo no falla. La mejora es subir `shell-quote` a 1.8.4 y actualizar
`brace-expansion`, `@xmldom/xmldom`, `axios` y `node-forge`, y cambiar el escaneo del
sistema de archivos para que un CRITICAL falle el build igual que el escaneo de imágenes.

Los cuatro bugs de auth y promotion no incumplen el gate: quedaron fuera de la ventana de
código nuevo. La mejora es comprobar el nulo en `IdentityClient`, reutilizar un solo
`Random` en `CircleService` y dejar de invocar `updateStatus` y `resolveStatus` desde la
misma clase, para que el proxy aplique `@Transactional`.

`form` es el margen más corto: 80,9 % de cobertura nueva frente a un umbral de 80. No
incumple. La mejora, antes del próximo cambio de ese servicio, es cubrir las líneas nuevas
que todavía quedan fuera.
