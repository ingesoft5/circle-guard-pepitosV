# Registro de despliegues por ambiente

Lo escribe el pipeline de Jenkins en cada despliegue y rollback de dev, stage y prod (HU-51):
no se edita a mano. Cómo se llena y quién aparece como aprobador:
[docs/release-notes/trazabilidad.md](https://github.com/ingesoft5/circle-guard-pepitosV/blob/main/docs/release-notes/trazabilidad.md).
El mismo registro está en GitHub, en [Deployments](https://github.com/ingesoft5/circle-guard-pepitosV/deployments).

| Fecha | Ambiente | Versión | Tipo | Aprobó | Resultado | Build |
|:---|:---|:---|:---|:---|:---|:---|
| 2026-11-23 02:15 UTC | stage | 0.7.0 | rollback | admin (admin) | fallo | [circleguard-rollback #11](http://184.196.87.200:8080/job/circleguard-rollback/11/) |
| 2026-11-23 02:26 UTC | stage | 0.7.0 | rollback | admin (admin) | fallo | [circleguard-rollback #12](http://184.196.87.200:8080/job/circleguard-rollback/12/) |
