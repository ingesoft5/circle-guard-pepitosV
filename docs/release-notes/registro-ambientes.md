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
| 2026-11-23 04:27 UTC | stage | 0.8.0-rc.06154f5 | release candidate | automatic: PR #150 by DavidRamirez206 | éxito | [circleguard/PR-150 #4](http://184.196.87.200:8080/job/circleguard/job/PR-150/4/) |
| 2026-11-23 04:38 UTC | stage | 0.8.0-rc.06154f5 | release candidate | automatic: PR #150 by DavidRamirez206 | éxito | [circleguard/PR-150 #5](http://184.196.87.200:8080/job/circleguard/job/PR-150/5/) |
| 2026-11-23 05:42 UTC | stage | 0.8.0-rc.51b74a5 | release candidate | automatic: PR #160 by DavidRamirez206 | éxito | [circleguard/PR-160 #1](http://184.196.87.200:8080/job/circleguard/job/PR-160/1/) |
| 2026-11-23 05:49 UTC | dev | 0.8.0 | despliegue | automatic: merge by Ismael Barrionuevo | fallo | [circleguard/main #16](http://184.196.87.200:8080/job/circleguard/job/main/16/) |
| 2026-11-23 06:15 UTC | dev | 0.8.0 | despliegue | automatic: merge by Ismael Barrionuevo | fallo | [circleguard/main #17](http://184.196.87.200:8080/job/circleguard/job/main/17/) |
| 2026-11-23 07:00 UTC | stage | 0.9.0-rc.2f84550 | release candidate | automatic: PR #167 by DavidRamirez206 | fallo | [circleguard/PR-167 #1](http://184.196.87.200:8080/job/circleguard/job/PR-167/1/) |
| 2026-11-23 07:04 UTC | stage | 0.9.0-rc.8e35103 | release candidate | automatic: PR #167 by DavidRamirez206 | fallo | [circleguard/PR-167 #2](http://184.196.87.200:8080/job/circleguard/job/PR-167/2/) |
| 2026-11-23 07:07 UTC | stage | 0.9.0-rc.8e35103 | release candidate | automatic: PR #167 by DavidRamirez206 | fallo | [circleguard/PR-167 #3](http://184.196.87.200:8080/job/circleguard/job/PR-167/3/) |
| 2026-11-23 07:22 UTC | stage | 0.9.0-rc.8e35103 | release candidate | automatic: PR #167 by DavidRamirez206 | éxito | [circleguard/PR-167 #4](http://184.196.87.200:8080/job/circleguard/job/PR-167/4/) |
| 2026-11-23 07:34 UTC | stage | 0.9.0-rc.03911eb | release candidate | automatic: PR #167 by DavidRamirez206 | éxito | [circleguard/PR-167 #5](http://184.196.87.200:8080/job/circleguard/job/PR-167/5/) |
| 2026-11-23 07:47 UTC | stage | 0.9.0-rc.03911eb | release candidate | automatic: PR #167 by DavidRamirez206 | éxito | [circleguard/PR-167 #6](http://184.196.87.200:8080/job/circleguard/job/PR-167/6/) |
| 2026-11-23 07:56 UTC | dev | 0.9.0 | despliegue | automatic: merge by Ismael Barrionuevo | éxito | [circleguard/main #18](http://184.196.87.200:8080/job/circleguard/job/main/18/) |
| 2026-11-23 08:00 UTC | stage | 0.9.0 | despliegue | automatic: merge by Ismael Barrionuevo | éxito | [circleguard/main #18](http://184.196.87.200:8080/job/circleguard/job/main/18/) |
| 2026-11-23 08:04 UTC | prod | 0.9.0 | despliegue | admin | éxito | [circleguard/main #18](http://184.196.87.200:8080/job/circleguard/job/main/18/) |
| 2026-11-23 08:51 UTC | stage | 0.10.0-rc.ec01f2f | release candidate | automatic: PR #175 by jcot117 | éxito | [circleguard/PR-175 #1](http://184.196.87.200:8080/job/circleguard/job/PR-175/1/) |
| 2026-11-23 08:57 UTC | dev | 0.10.0 | despliegue | automatic: merge by Johan Stiven Suarez Perdomo | fallo | [circleguard/main #19](http://184.196.87.200:8080/job/circleguard/job/main/19/) |
| 2026-11-23 10:15 UTC | dev | 0.10.0 | despliegue | automatic: merge by Johan Stiven Suarez Perdomo | éxito | [circleguard/main #20](http://184.196.87.200:8080/job/circleguard/job/main/20/) |
| 2026-11-23 10:16 UTC | stage | 0.10.0 | despliegue | automatic: merge by Johan Stiven Suarez Perdomo | fallo | [circleguard/main #20](http://184.196.87.200:8080/job/circleguard/job/main/20/) |
| 2026-11-23 10:20 UTC | dev | 0.10.0 | despliegue | automatic: merge by Johan Stiven Suarez Perdomo | fallo | [circleguard/main #21](http://184.196.87.200:8080/job/circleguard/job/main/21/) |
| 2026-11-23 10:46 UTC | stage | 0.10.1-rc.9edc927 | release candidate | automatic: PR #178 by Jo-Ssua | fallo | [circleguard/PR-178 #1](http://184.196.87.200:8080/job/circleguard/job/PR-178/1/) |
| 2026-11-23 11:02 UTC | stage | 0.10.1-rc.46ed25b | release candidate | automatic: PR #178 by Jo-Ssua | éxito | [circleguard/PR-178 #2](http://184.196.87.200:8080/job/circleguard/job/PR-178/2/) |
| 2026-11-23 11:18 UTC | stage | 0.10.1-rc.46ed25b | release candidate | automatic: PR #178 by Jo-Ssua | éxito | [circleguard/PR-178 #3](http://184.196.87.200:8080/job/circleguard/job/PR-178/3/) |
| 2026-11-23 11:26 UTC | dev | 0.10.1 | despliegue | automatic: merge by Johan Stiven Suarez Perdomo | fallo | [circleguard/main #22](http://184.196.87.200:8080/job/circleguard/job/main/22/) |
| 2026-11-23 11:44 UTC | dev | 0.10.1 | despliegue | automatic: merge by Johan Stiven Suarez Perdomo | fallo | [circleguard/main #23](http://184.196.87.200:8080/job/circleguard/job/main/23/) |
| 2026-11-23 11:56 UTC | dev | 0.10.1 | despliegue | automatic: merge by Johan Stiven Suarez Perdomo | éxito | [circleguard/main #24](http://184.196.87.200:8080/job/circleguard/job/main/24/) |
| 2026-11-23 11:59 UTC | stage | 0.10.1 | despliegue | automatic: merge by Johan Stiven Suarez Perdomo | éxito | [circleguard/main #24](http://184.196.87.200:8080/job/circleguard/job/main/24/) |
| 2026-11-23 12:03 UTC | prod | 0.10.1 | despliegue | admin | éxito | [circleguard/main #24](http://184.196.87.200:8080/job/circleguard/job/main/24/) |
