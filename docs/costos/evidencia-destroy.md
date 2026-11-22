# Estado final de la infraestructura

La destrucción de los tres ambientes Terraform **no se ejecutó**. Los entornos `dev`, `stage`
y `prod`, sus nodos k3s y el stack de observabilidad permanecen activos para la evaluación.

## Decisión

Se conserva la infraestructura para:

- Mantener disponibles la aplicación, Grafana, Prometheus y Alertmanager durante la revisión.
- No perder el estado en ejecución de observability ni los datos local-path de las bases.
- Evitar una reconstrucción completa antes de la sustentación.

El escenario de destrucción de HU-55 queda **fuera de esta entrega**. El procedimiento de
destrucción se conserva abajo como referencia para un cierre posterior.

## Estado actual

- `dev`, `stage` y `prod` tienen sus recursos Terraform aplicados.
- `terraform state list` no está vacío: cada ambiente conserva nodos, red, registro, storage y
  observability.
- El bucket del state (`circleguard-tfstate-*`) y la tabla DynamoDB del lock
  (`circleguard-tf-locks`) siguen existiendo y tienen `prevent_destroy`.
- `circleguard-ci`, `circleguard-tools` y los hosts legacy detenidos siguen en la cuenta.
- La configuración versionada de Prometheus/Grafana/Alertmanager vive en
  `observability/` y se puede volver a desplegar; el historial de métricas, la base local de
  Grafana y los cambios manuales no versionados solo viven en los nodos.

## Costos

Los recursos de los tres ambientes siguen generando los costos descritos en
[`costos-reales.md`](costos-reales.md). No hay un periodo de “después del destroy” porque la
infraestructura no se destruyó.

## Procedimiento de referencia para un cierre posterior

Si en el futuro se decide destruir, se hace al final y con respaldo previo de las bases y de
cualquier cambio manual de Grafana/Prometheus. Por ambiente:

```bash
cd infra/terraform/envs/<env>
terraform init -reconfigure -backend-config=backend.hcl
terraform state list > /tmp/state-antes-<env>.txt
terraform plan -destroy -out=destroy-<env>.tfplan
terraform apply destroy-<env>.tfplan
terraform state list > /tmp/state-despues-<env>.txt
```

El `state list` posterior debe quedar vacío. Los buckets de archivos y los repositorios ECR
están vacíos hoy, así que no bloquean la destrucción. El bucket del state y la tabla del lock
no se destruyen; para retirarlos hay que quitar `prevent_destroy` y vaciar el bucket en un
cambio revisado.
