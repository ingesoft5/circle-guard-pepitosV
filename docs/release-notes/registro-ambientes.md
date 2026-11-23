# Registro de despliegues por ambiente

Lo escribe el pipeline de Jenkins en cada despliegue y rollback de dev, stage y prod (HU-51):
no se edita a mano. Cómo se llena y quién aparece como aprobador:
[docs/release-notes/trazabilidad.md](https://github.com/ingesoft5/circle-guard-pepitosV/blob/main/docs/release-notes/trazabilidad.md).
El mismo registro está en GitHub, en [Deployments](https://github.com/ingesoft5/circle-guard-pepitosV/deployments).

| Fecha | Ambiente | Versión | Tipo | Aprobó | Resultado | Build |
|:---|:---|:---|:---|:---|:---|:---|
| 2026-11-23 02:15 UTC | stage | 0.7.0 | rollback | admin (admin) | fallo | [circleguard-rollback #11](http://184.196.87.200:8080/job/circleguard-rollback/11/) |
| 2026-11-23 02:26 UTC | stage | 0.7.0 | rollback | admin (admin) | fallo | [circleguard-rollback #12](http://184.196.87.200:8080/job/circleguard-rollback/12/) |
| 2026-11-23 02:38 UTC | stage | 0.6.0 | rollback | admin (admin) | éxito | [circleguard-rollback #13](http://184.196.87.200:8080/job/circleguard-rollback/13/) |
| 2026-11-23 02:41 UTC | stage | 0.7.0 | rollback | admin (admin) | éxito | [circleguard-rollback #14](http://184.196.87.200:8080/job/circleguard-rollback/14/) |
| 2026-11-23 02:59 UTC | stage | 0.8.0-rc.6f6b4bc | release candidate | automatic: PR #150 by DavidRamirez206 | éxito | [circleguard/PR-150 #1](http://184.196.87.200:8080/job/circleguard/job/PR-150/1/) |
| 2026-11-23 03:57 UTC | stage | 0.8.0-rc.cc63e6c | release candidate | automatic: PR #150 by DavidRamirez206 | éxito | [circleguard/PR-150 #2](http://184.196.87.200:8080/job/circleguard/job/PR-150/2/) |
| 2026-11-23 04:19 UTC | stage | 0.8.0-rc.e7e4b65 | release candidate | automatic: PR #150 by DavidRamirez206 | éxito | [circleguard/PR-150 #3](http://184.196.87.200:8080/job/circleguard/job/PR-150/3/) |
