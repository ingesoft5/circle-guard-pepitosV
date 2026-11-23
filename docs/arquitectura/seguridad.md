# Seguridad

Los controles que tiene la solución, desde la red hasta el pipeline, y lo que todavía falta. Cada
control enlaza al documento o archivo que lo implementa. Estado del 22 de noviembre de 2026, con la
versión `0.7.0` desplegada.

## Red

| Superficie | Abierto a | Control |
|:---|:---|:---|
| Nodo k3s `80`, `443` | Internet | Security Group del nodo (Terraform). Solo Traefik escucha |
| Nodo k3s `22`, `6443` | IP privada de Jenkins y las IP que cada integrante autoriza con `hosts.sh allow` | Reglas sueltas en el Security Group; las de los integrantes quedan fuera del estado a propósito |
| Registro de Nexus `5000` (host `tools`) | VPC de cada ambiente, por el peering | Regla `module.network...ingress_rule.registry` en el Security Group de `tools` |
| Jenkins `8080` | `ADMIN_CIDR`, webhooks de GitHub y el webhook de SonarQube | [`infra/security/security-groups.md`](../../infra/security/security-groups.md) |
| SonarQube `9000`, Nexus `8081` | `ADMIN_CIDR` y el Security Group de CI | Ídem |
| Bases, Kafka, Redis, LDAP, Jaeger | Nadie fuera del clúster | Services `ClusterIP`; ningún `NodePort` |

Las VPC de los ambientes no tienen peering entre sí: dev no alcanza a prod. Todo el tráfico de
usuarios entra por el Ingress al nginx del cliente web, que solo reenvía `/svc/` a `auth`,
`identity`, `promotion`, `form` y `gateway`.

## Identidad y acceso

| Quién | Cómo se autentica | Qué puede hacer |
|:---|:---|:---|
| Usuario de la aplicación | LDAP de la universidad con respaldo local (`DualChainAuthenticationProvider`), JWT firmado | Lo que permitan sus permisos (RBAC declarativo en `auth`, `promotion` e `identity`) |
| Entrada al campus | QR firmado con vida de 30 s en stage y prod (120 s en dev) | `gateway` valida firma, vigencia y estado de salud |
| Equipo en Jenkins | Usuarios `admin` y `dev` declarados en `casc.yaml` | Solo `admin` aprueba prod, en el pipeline y en el rollback ([`../release-notes/politica-cambios.md`](../release-notes/politica-cambios.md)) |
| Prometheus y Alertmanager | Basic Auth de Traefik (`monitoring-basic-auth`, creado por `load-monitoring-auth.sh`) | Lectura y silencios |
| Grafana | Login propio; contraseña de admin por `TF_VAR_` | Paneles |
| Equipo en AWS | IAM; la política del equipo enciende, apaga y autoriza IP según la etiqueta `Project=CircleGuard` | [`infra/cloud/aws/README.md`](../../infra/cloud/aws/README.md) |
| Pods | Ninguno monta token de la API (`automountServiceAccountToken: false`) | Nada contra la API de Kubernetes |
| `file-service` ante S3 | Rol de IAM del nodo por IMDSv2 (hop limit 2), sin llaves | Listar, leer, escribir y borrar objetos del bucket de su ambiente |

Los permisos de la aplicación y el flujo del QR están documentados como patrones P9 a P11 en
[`../patrones/existentes.md`](../patrones/existentes.md).

## Secretos

- **Nada en Git.** Los `.env` reales, las llaves y el estado de Terraform están en `.gitignore`. Cada
  PR pasa por `secretScan` (gitleaks 8.21.2), y el 21 de noviembre se recorrió el historial completo
  sin hallazgos ([`../operaciones/secretos.md`](../operaciones/secretos.md)).
- **Clúster.** `apply-secrets.sh` cifra con `kubeseal` y aplica el SealedSecret
  `circleguard-secrets`; solo el controller de Sealed Secrets de cada clúster puede descifrarlo. La
  fuente local es `~/.circleguard/k8s-<env>.env`, con permisos 600.
- **Monitoreo.** El webhook de Discord (`alertmanager-discord`) y la Basic Auth se cargan por stdin
  con scripts de `infra/bootstrap/`; no pasan por argv ni por Git.
- **Jenkins.** Credenciales `github-pat`, `kubeconfig-<env>`, `nexus-ci`, `discord-webhook` y
  `e2e-user` en el almacén de Jenkins; las contraseñas de los hosts de CI viven en su `.env` (600).
- **Rotación.** Procedimiento por tipo de secreto en el
  [manual de operaciones](../operaciones/manual.md#5-rotación-de-secretos).

## Cargas de trabajo

| Control | Dónde se aplica |
|:---|:---|
| Usuario no root (`10001`) y `capabilities: drop [ALL]` | Los ocho servicios Spring |
| `seccompProfile: RuntimeDefault` y `allowPrivilegeEscalation: false` | Todos los pods del chart |
| Sistema de archivos raíz de solo lectura, con `emptyDir` solo en las rutas que escriben | Todos, salvo `neo4j`, `zookeeper`, `kafka` y `openldap`, cuyas imágenes escriben en la raíz (excepción registrada en `.trivyignore.rego`) |
| Requests y límites de memoria | Todos los componentes ([`kubernetes.md`](kubernetes.md)) |
| Redis con contraseña | `--requirepass` desde el Secret |

## Cadena de suministro

| Control | Efecto |
|:---|:---|
| Imágenes con versión inmutable (`X.Y.Z`, `-SNAPSHOT.N.sha`, `-rc.N.sha`), sin `latest` | `dockerPublish` rechaza tags flotantes y Nexus usa `allow_once` ([`../release-notes/etiquetado.md`](../release-notes/etiquetado.md)) |
| La imagen estable es el mismo digest del RC probado en stage | `promoteRelease` no reconstruye |
| Trivy de imágenes, HIGH y CRITICAL | Rompe el build |
| Trivy de IaC (Terraform y chart) | Rompe el build |
| Trivy del sistema de archivos | Informativo (`--exit-code 0`) |
| Quality gate de SonarQube sobre código nuevo | Rompe el carril del servicio |
| ZAP baseline | Corrida manual contra dev; 0 alertas altas |
| Etiquetas OCI `version` y `revision` en cada imagen | Trazabilidad imagen → commit |

Resultados de la última corrida en [`../operaciones/informe-pruebas.md`](../operaciones/informe-pruebas.md).

## Datos

- **Identidad.** Los nombres reales solo están en la bóveda de `identity`, cifrados y con sal. El
  grafo de Neo4j guarda seudónimos (patrón P12). `dashboard` aplica k-anonimato (P13).
- **En reposo.** Disco gp3 cifrado en cada nodo; el bucket de archivos cifrado con AES256, privado,
  con bloqueo de acceso público y versionado; el estado de Terraform cifrado y versionado en S3.
- **Archivos subidos.** El nombre que se guarda es `<uuid>_<nombre>` con solo `[A-Za-z0-9._-]`; la
  descarga solo acepta nombres con esa forma, así que no hay rutas ni `..`.
- **Instancia.** IMDSv2 obligatorio en los nodos k3s.

## Brechas conocidas

| Brecha | Riesgo | Estado |
|:---|:---|:---|
| El Ingress sirve HTTP en `0.7.0` | Credenciales y JWT viajan sin cifrar | TLS con cert-manager está en `dev` y entra con `0.8.0` ([`../operaciones/tls.md`](../operaciones/tls.md)) |
| Sin RBAC propio del namespace en `0.7.0` | El acceso al clúster es con el kubeconfig de administrador | Roles `circleguard-deployer` y `circleguard-viewer` en `dev`, entran con `0.8.0` |
| Sin NetworkPolicy | Un pod comprometido alcanza a cualquier otro del namespace | Pendiente |
| Cualquier pod del nodo puede usar el rol de IAM | Un pod comprometido lee o borra los archivos del bucket | La política se limita al bucket del ambiente; el bucket es versionado |
| `file-service` no exige autenticación | Cualquiera que llegue a `/svc/file/` sube o descarga si conoce el nombre | Pendiente: validar el JWT como los demás servicios |
| Tráfico interno sin cifrar (Kafka `PLAINTEXT`, `LDAP_TLS=false`) | Lectura del tráfico dentro del nodo | Entre servicios, mTLS con Linkerd en `dev` ([`service-mesh.md`](service-mesh.md)); el middleware sigue sin cifrar, aceptado: un solo nodo |
| Sin políticas de autorización en la malla | Cualquier pod de la malla llama a cualquier servicio | Pendiente: `AuthorizationPolicy` por ServiceAccount |
| Jenkins y SonarQube en HTTP | Credenciales en claro hacia CI | Proxy TLS opcional en `infra/compose/ci/docker-compose.tls.yml`, no activado |
| Lockfile de `mobile/` con 1 CRITICAL y 74 HIGH | Dependencias de build vulnerables | No están en las imágenes publicadas; pendiente de actualizar |
| El cliente web no envía CSP, Permissions-Policy ni COEP | Avisos `WARN` de ZAP | Pendiente en el nginx del cliente |
