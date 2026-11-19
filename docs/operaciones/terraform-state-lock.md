# Estado remoto y bloqueo de Terraform

Guía de operación de la HU-28 (`CGR-138`): cómo comprobar que el estado de CircleGuard vive en
S3, que nadie lo versiona en Git y que dos `apply` simultáneos no se pisan. Cubre las subtareas
`CGR-174` (bootstrap), `CGR-175` (backend por ambiente) y `CGR-176` (prueba de bloqueo y
`.gitignore`).

## Dónde vive el estado

| Root | Bucket | `key` | Tabla de lock |
|:---|:---|:---|:---|
| `bootstrap` | `circleguard-tfstate-028658486093` | `bootstrap/terraform.tfstate` | `circleguard-tf-locks` |
| `envs/dev` | `circleguard-tfstate-028658486093` | `environments/dev/terraform.tfstate` | `circleguard-tf-locks` |
| `envs/stage` | `circleguard-tfstate-028658486093` | `environments/stage/terraform.tfstate` | `circleguard-tf-locks` |
| `envs/prod` | `circleguard-tfstate-028658486093` | `environments/prod/terraform.tfstate` | `circleguard-tf-locks` |

El bucket y la tabla se crean con el stack [`infra/terraform/bootstrap`](../../infra/terraform/bootstrap/README.md).
Cada ambiente tiene su propia `key`, así que un estado no se mezcla con otro aunque compartan
bucket y tabla.

## Verificar que el estado está en S3

```bash
cd infra/terraform/envs/dev
terraform init -reconfigure -backend-config=backend.hcl
terraform state list
```

`terraform state list` debe devolver los recursos del ambiente. Ese mismo listado se obtiene desde
el estado remoto; no hay ningún `terraform.tfstate` local.

## Verificar que no hay estado en el repositorio

```bash
# Nada versionado
git ls-files | grep -E '\.tfstate($|\.)' || echo "OK: ningún terraform.tfstate en el repo"

# Nada en el árbol de trabajo
find infra/terraform -name '*.tfstate*' -o -name '*.tfplan'
```

El `.gitignore` de la raíz excluye `**/.terraform/`, `*.tfstate`, `*.tfstate.*`, `crash.log` y
`*.tfplan`. El `.terraform.lock.hcl` **sí** se versiona: fija la versión del provider.

## Probar el bloqueo concurrente (CGR-176)

El backend usa DynamoDB porque Terraform 1.9.8 (la versión de Jenkins) no soporta el bloqueo
nativo de S3. La prueba necesita dos terminales sobre **el mismo ambiente** (`dev`).

> No la ejecutes en `prod`. Usa `dev` y coordina con el equipo para que nadie más aplique en ese
> ambiente mientras dura la prueba.

### Opción A: apply lento controlado (recomendada)

1. En `envs/dev`, crea un recurso temporal que mantiene el lock unos 90 segundos:

   ```bash
   cd infra/terraform/envs/dev
   cat > lock-test.tf <<'EOF'
   resource "terraform_data" "lock_test" {
     input = "lock-test"

     provisioner "local-exec" {
       command = "sleep 90"
     }
   }
   EOF

   terraform plan -out=lock-test.tfplan
   ```

2. **Terminal A** - toma el lock y lo retiene durante el `sleep`:

   ```bash
   terraform apply lock-test.tfplan
   ```

3. **Terminal B** - durante esos 90 segundos, intenta leer el estado:

   ```bash
   terraform plan
   ```

   Debe fallar de inmediato con un mensaje como:

   ```
   Error: Error acquiring the state lock
   Error message: ConditionalCheckFailedException: The conditional request failed
   Lock Info:
     ID:        <LOCK_ID>
     Path:      circleguard-tfstate-028658486093/environments/dev/terraform.tfstate
     Operation: OperationTypeApply
     Who:       <usuario>@<host>
     Version:   1.9.8
   ```

   Guarda esa salida completa: es la evidencia de `CGR-176`.

4. Limpieza (espera a que la terminal A termine):

   ```bash
   rm lock-test.tf
   terraform apply -auto-approve   # destruye terraform_data.lock_test
   ```

### Opción B: insertar el lock a mano (sin tocar el estado)

1. Lee el `LockID` real de la tabla:

   ```bash
   aws dynamodb scan --table-name circleguard-tf-locks \
     --query 'Items[].LockID.S' --output text
   ```

   El `LockID` tiene la forma `<bucket>/<key>`; por ejemplo
   `circleguard-tfstate-028658486093/environments/dev/terraform.tfstate`.

2. Inserta el item:

   ```bash
   aws dynamodb put-item --table-name circleguard-tf-locks \
     --item '{"LockID":{"S":"circleguard-tfstate-028658486093/environments/dev/terraform.tfstate"}}'
   ```

3. Ejecuta `terraform plan` en `envs/dev`: debe fallar con `Error acquiring the state lock`.

4. Borra el item:

   ```bash
   aws dynamodb delete-item --table-name circleguard-tf-locks \
     --key '{"LockID":{"S":"circleguard-tfstate-028658486093/environments/dev/terraform.tfstate"}}'
   ```

### Si un lock queda huérfano

`terraform force-unlock <LOCK_ID>` **solo** cuando tengas certeza de que no hay otro `apply` en
curso. Forzar el desbloqueo mientras alguien aplica corrompe el estado. Para saberlo, revisa en
CloudWatch o en la tabla quién tiene el lock y confirma que ese proceso ya no corre.

