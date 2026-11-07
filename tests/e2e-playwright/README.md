# Pruebas E2E con Playwright

Pruebas de extremo a extremo del cliente web (HU-20). Corren en un navegador Chromium contra un ambiente desplegado y usan los servicios reales a través del nginx del cliente (`/svc/<servicio>/`).

## Escenarios

| Archivo | Escenario |
|:---|:---|
| `tests/login.spec.ts` | Un usuario con credenciales válidas llega a la pantalla principal. Con credenciales inválidas se queda en el login. |
| `tests/questionnaire.spec.ts` | Un usuario autenticado envía el cuestionario de salud y ve la confirmación. |
| `tests/access.spec.ts` | El QR de un usuario sin restricciones se aprueba en el gateway. Un QR alterado se rechaza. |

Notas:

- **Cuestionario activo:** el servicio de formularios no trae datos iniciales. Si el ambiente no tiene un cuestionario activo, la prueba crea uno y lo activa por la API.
- **Validación del QR:** el escáner de la puerta necesita una cámara. Por eso la prueba toma el QR que muestra la pantalla principal y lo envía al gateway igual que el escáner (`POST /api/v1/gate/validate`).

## Variables

| Variable | Uso |
|:---|:---|
| `E2E_BASE_URL` | URL del cliente web del ambiente, por ejemplo `http://<ip-de-dev>`. Obligatoria. |
| `E2E_USERNAME` | Usuario de prueba. Los usuarios sembrados están en `services/circleguard-auth-service/src/main/resources/db/migration/V2__seed_test_users.sql`. |
| `E2E_PASSWORD` | Contraseña del usuario de prueba. No se versiona. |

## Ejecutar

En un contenedor, como lo hace Jenkins. Solo necesita Docker:

```bash
E2E_BASE_URL=http://<ip-de-dev> E2E_USERNAME=health_user E2E_PASSWORD=<contraseña> \
  tests/e2e-playwright/run-e2e.sh
```

En local, con Node.js 20:

```bash
cd tests/e2e-playwright
npm ci
npx playwright install chromium
E2E_BASE_URL=http://<ip-de-dev> E2E_USERNAME=health_user E2E_PASSWORD=<contraseña> npx playwright test
```

## Resultados

Quedan en `tests/e2e-playwright/results/`, que no se versiona:

| Ruta | Contenido |
|:---|:---|
| `results/html/` | Reporte HTML. Se abre con `npx playwright show-report results/html`. |
| `results/junit.xml` | Resultados que Jenkins muestra en la pestaña **Tests** del build. |
| `results/artifacts/` | Traza, captura y video de cada prueba que falla. La traza se abre con `npx playwright show-trace <trace.zip>`. |

## En el pipeline

La etapa **E2E tests on dev** del `Jenkinsfile` corre después de **Deploy to dev**, solo en `main` y con un tag de release:

- Ejecuta `run-e2e.sh` contra la IP privada de dev (`DEPLOY_HOST_DEV`), con la credencial `e2e-user` de Jenkins.
- Publica `junit.xml` en el build.
- Archiva `results/` (reporte HTML, trazas, capturas y videos) como artefactos del build.
- Si una prueba falla, el build falla y Discord lo notifica.

La imagen usa solo Chromium headless (unos 1,2 GB) para no llenar el disco del host de CI. La imagen oficial de Playwright trae tres navegadores y pesa unos 3,5 GB.
