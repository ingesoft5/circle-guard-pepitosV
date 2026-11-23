# Chaos engineering con Chaos Mesh

Se inyectan fallas a propósito en `dev` (y en `stage` si hace falta) para comprobar que los
mecanismos de resiliencia que ya existen funcionan:
- el circuit breaker `redisHealth`;
- las sondas de readiness;
- la recreación de pods;
- las alertas a Discord.

Cada experimento es un manifiesto versionado en `deploy/k8s/chaos/` y tiene una hipótesis escrita
antes de correrlo.

## Decisión

| Criterio | Elección |
|:---|:---|
| Herramienta | Chaos Mesh: los experimentos son CRDs (`PodChaos`, `NetworkChaos`) que se versionan junto al chart. Litmus necesita su propio portal y más componentes |
| Versión | **2.7.3**. Corrige las CVE "Chaotic Deputy" (CVE-2025-59358 a 59361). La 2.8 solo declara soporte para Kubernetes 1.35 o más, y los nodos usan k3s 1.30 |
| Runtime | containerd de k3s: `socketPath: /run/k3s/containerd/containerd.sock`. Con el socket por defecto, los experimentos de red no hacen nada |
| Alcance | Solo los namespaces con la anotación `chaos-mesh.org/inject=enabled` (`enableFilterNamespace`). Nunca prod: el daemon corre privilegiado en el nodo |
| Dashboard | No se instala. Los resultados se leen en Grafana, el panel de Linkerd y Discord |
| Memoria y CPU | Controlador y daemon sin dashboard ni `dnsServer` (solo sirve para DNSChaos y reservaba 100m de CPU): unos 100–200 MiB y 20m de CPU |

## Experimentos

| Experimento | Falla | Hipótesis | Cómo se comprueba |
|:---|:---|:---|:---|
| `gateway-pod-kill` | Mata un pod de `gateway` (o `gateway-primary` con canary) | Kubernetes lo recrea y queda Ready en menos de 90 s | `chaos-run.sh`: el pod nuevo pasa a `true` |
| `gateway-redis-partition` | Corta la red de `gateway` hacia Redis durante 4 minutos | El circuito `redisHealth` se abre, `gateway` niega las entradas (fail closed) sin reiniciarse, `CircuitBreakerOpen` llega a Discord, y el circuito se cierra solo al terminar | Panel "CircleGuard redisHealth circuit" en Grafana, Discord, `RESTARTS` sin cambios |
| `postgres-pod-kill` | Mata el pod de PostgreSQL | Mientras PostgreSQL se recupera, `identity`, `auth`, `form`, `promotion` y `dashboard` salen de readiness sin reiniciarse; todos vuelven a Ready sin perder datos | `chaos-run.sh`: `READY false` y luego `true`, `RESTARTS` igual |

## Cómo correrlos

Se instala una vez por clúster y se corre en el nodo. Desde Windows, el script entra por la entrada
estándar de `ssh`, para que PowerShell no altere las comillas:

```powershell
$key = "<llave .pem>"
function Run-Node([string]$ip, [string]$s) { $s | ssh -i $key ubuntu@$ip "tr -d '\r' | sudo bash -s" }
ssh -i $key ubuntu@52.204.64.188 "rm -rf ~/cg && mkdir -p ~/cg/deploy"
scp -i $key -r deploy/k8s ubuntu@52.204.64.188:~/cg/deploy/

# Instalación (una vez)
Run-Node 52.204.64.188 @'
export KUBECONFIG=/etc/rancher/k3s/k3s.yaml; cd /home/ubuntu/cg
bash deploy/k8s/scripts/install-chaos-mesh.sh circleguard-dev
'@

# Un experimento: namespace, nombre y segundos que se observa
Run-Node 52.204.64.188 @'
export KUBECONFIG=/etc/rancher/k3s/k3s.yaml; cd /home/ubuntu/cg
bash deploy/k8s/scripts/chaos-run.sh circleguard-dev gateway-redis-partition 300
'@
```

`chaos-run.sh` aplica el experimento, muestra cada 15 s el estado de los pods de la aplicación y,
al terminar o si se interrumpe, borra el experimento. Para detener una falla a mano:
`kubectl -n circleguard-dev delete networkchaos,podchaos --all`.

Durante cada experimento conviene tener abiertos Grafana (paneles de servicios y de `redisHealth`),
el panel de Linkerd y el canal de Discord.

**No correr experimentos durante un análisis del canary de `gateway`.** Flagger lo revertiría por la
caída de la tasa de éxito.

## Resultados

| Fecha | Ambiente | Experimento | ¿Se cumplió la hipótesis? | Observaciones |
|:---|:---|:---|:---|:---|
| 2026-11-23 | `dev` | `postgres-pod-kill` | Sí | `postgres-0` volvió en menos de 16 s. `auth`, `form`, `dashboard` e `identity` fallaron la readiness unos segundos y se recuperaron con 0 reinicios |
| 2026-11-23 | `dev` | `gateway-pod-kill` (1.ª corrida) | No | El pod no se pudo recrear: un despliegue a medias había dejado los Deployments apuntando a ServiceAccounts que no existían. Los pods viejos ocultaban el fallo; dev quedó sin `gateway` |
| 2026-11-23 | `dev` | `gateway-pod-kill` (2.ª corrida) | Sí | Mató `gateway-primary` (el que sirve con el canary). El pod nuevo estuvo Ready en unos 30 s, con el proxy de Linkerd inyectado y su certificado mTLS emitido al arrancar. Los demás servicios, sin reinicios |
| 2026-11-23 | `dev` | `gateway-redis-partition` | Sí | Partición de `gateway-primary` hacia Redis durante 4 min, aplicada y recuperada por Chaos Mesh. `gateway-primary` siguió Ready y sin reinicios (Redis no está en su grupo de readiness, a propósito). Apertura del circuito y alerta `CircuitBreakerOpen`: ver capturas de Grafana y Discord |

### Hallazgos

Ninguno de estos problemas se veía mientras todos los pods seguían vivos:

| Hallazgo | Causa | Corrección |
|:---|:---|:---|
| El controlador de Chaos Mesh moría con `too many open files` | Ubuntu limita inotify a 128 instancias por usuario, y Linkerd, Flagger y la aplicación ya usan casi todas | `install-chaos-mesh.sh` sube `fs.inotify.max_user_instances` a 1024 y lo persiste en `/etc/sysctl.d/90-inotify.conf` |
| Los despliegues de dev quedaron en `Pending` (`Insufficient cpu`), y Helm falló tres veces por `context deadline exceeded` | Con la malla y el canary (`gateway-primary`), el nodo de 2 vCPU llegó al 95 % de CPU reservada; en una actualización gradual, el pod nuevo no cabía junto al viejo | Los servicios Spring reservan `cpu: 50m` en vez de 100m (sin límite de CPU); la reserva quedó en 77 % |
| El API de Kubernetes dejó de responder (`TLS handshake timeout`) | Varias JVM arrancando a la vez en 2 vCPU dejan sin CPU al plano de control de k3s | Reiniciar los servicios de a uno, nunca un `rollout restart` de todo el namespace |

Si una hipótesis no se cumple, se registra aquí qué pasó y se abre una HU para corregirlo.
