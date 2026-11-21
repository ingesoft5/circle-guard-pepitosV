# Patrón de configuración externa y feature toggle

Un parámetro de negocio y una funcionalidad se cambian por ambiente sin reconstruir la imagen. Auth-service lee ambos al arrancar. En el clúster el ConfigMap `auth-config` inyecta `QR_EXPIRATION` y `CIRCLEGUARD_FEATURES_QR_DIAGNOSTICS`. El chart trae 60 s y el diagnóstico apagado; `values-dev` los deja en 120 s y encendido, y `values-stage` y `values-prod` en 30 s y apagado. Un cambio de esos valores entra al reiniciar el Deployment.

## TTL del QR

`qr.expiration` es el tiempo de vida del QR, en milisegundos. El token y el campo `expiresIn` salen de ese valor.

| Origen | TTL |
|---|---|
| `application.yml` (sin perfil) | 60 s |
| perfil `dev` | 120 s |
| perfil `prod` | 30 s |
| variable `QR_EXPIRATION` | el valor que traiga |

`QR_EXPIRATION=90000` y un reinicio hacen que la misma imagen emita QR de 90 s. No hace falta un build nuevo.

## Feature toggle

`circleguard.features.qr-diagnostics` abre `GET /api/v1/auth/qr/diagnostics` para un usuario autenticado. La respuesta dice cuántos milisegundos dura el QR que está en efecto.

| Origen | Toggle |
|---|---|
| sin perfil, y perfil `prod` | apagado: la ruta responde 404 |
| perfil `dev` | encendido |
| variable `CIRCLEGUARD_FEATURES_QR_DIAGNOSTICS` | `true` o `false` |

En producción el diagnóstico queda apagado para no publicar el parámetro. En desarrollo sirve para comprobar, sin mirar el JWT, que el ConfigMap ya entró en vigor.

## Trade-off

El proceso solo ve el valor nuevo al reiniciar. No es un cambio en caliente. A cambio, la imagen que ya pasó el pipeline no se vuelve a construir para mover un número o apagar una función.

## Prueba

`QrTokenServiceTest` emite dos tokens con el mismo código y vidas distintas, y comprueba los YAML de `dev` y `prod`. `QrAndUserControllerTest` comprueba que el diagnóstico responde 404 con el toggle apagado y publica el TTL configurado cuando está encendido.
