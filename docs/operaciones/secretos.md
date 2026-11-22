# Secretos

## Historial

El 21 de noviembre de 2026 se recorrió el historial completo con gitleaks 8.21.2
(`gitleaks detect`, 192 commits, `.gitleaks.toml`). No hubo hallazgos. Los YAML de
desarrollo siguen en la lista de permitidos porque son marcadores locales, no
credenciales del clúster. No se rotó ninguna credencial.

## Clúster

`deploy/k8s/scripts/apply-secrets.sh` no aplica un Secret en claro. Cifra los
valores con kubeseal y aplica un SealedSecret de alcance de namespace. El
controller `sealed-secrets-controller` en `kube-system` crea `circleguard-secrets`.
El archivo `~/.circleguard/k8s-<env>.env` sigue siendo la fuente local, con
permisos 600, y no se versiona.
