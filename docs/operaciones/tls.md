# TLS

> **Estado.** Este cambio está en `dev` y entra con la versión `0.8.0`. La `0.7.0`, que corre hoy en
> los tres ambientes, sirve la aplicación y el monitoreo por HTTP en el puerto 80, y ningún clúster
> tiene cert-manager todavía ([`../arquitectura/coherencia.md`](../arquitectura/coherencia.md)).

Los clústeres k3s 1.30 no tienen un nombre DNS propio. El Ingress público es la IP elástica.
`<ip>.sslip.io` resuelve a esa IP, pero es un dominio compartido y Let's Encrypt suele
rechazarlo por el límite de certificados. Por eso el certificado de cada ambiente lo firma
una CA de laboratorio creada por cert-manager, no una CA pública.

## Clúster

`deploy/k8s/scripts/install-cert-manager.sh` instala cert-manager 1.18.6 (la última versión
que todavía admite Kubernetes 1.30) y aplica `deploy/k8s/cert-manager/issuers.yaml`.

- `circleguard-ca` firma los certificados del chart. El navegador no lo confía hasta que se
  instala esa CA.
- `letsencrypt` queda creado para un nombre DNS propio. No es el emisor del chart. El correo
  `acme@example.com` hay que cambiarlo antes de usarlo.

El chart publica el puerto 443 con ese certificado y redirige el puerto 80 a HTTPS. Si el nodo
tiene ExternalIP, el despliegue agrega `<ip>.sslip.io` al certificado.

Grafana, Prometheus y Alertmanager usan el secreto `monitoring-tls` del mismo emisor. Ese
cambio entra en el próximo `helm upgrade` del stack de monitoreo. El manifiesto está en
`observability/tls/monitoring-certificate.yaml` y el despliegue de la aplicación lo aplica.

## Jenkins y SonarQube

Siguen en HTTP en el puerto que ya usa el pipeline (8080 y 9000). El proxy opcional
`infra/compose/ci/docker-compose.tls.yml` escucha en 8443 con el certificado interno de Caddy
y reenvía a un solo servicio:

```bash
# En el host de Jenkins
TLS_UPSTREAM=jenkins:8080 docker compose -f docker-compose.yml -f docker-compose.tls.yml up -d tls-proxy

# En el host de herramientas
TLS_UPSTREAM=sonarqube:9000 TLS_PORT=8443 docker compose -f docker-compose.yml -f docker-compose.tls.yml up -d tls-proxy
```

Ese proxy no se levantó desde esta máquina. El puerto 8443 queda declarado para cuando se active.
