# Costos de CircleGuard

Documentación de la estimación y del costo real de la infraestructura del proyecto
(HU-55 / CGR-165). Los datos de inventario y de Cost Explorer se consultaron el
**22 de noviembre de 2026** en la cuenta `028658486093`, región `us-east-1`.

## Resumen

| Concepto | Valor |
|:---|:---|
| Estimación on-demand (5 instancias encendidas 24/7, 260 GB EBS, IPv4, servicios) | **≈ $390.90 USD/mes** |
| Uso real en Cost Explorer, 1–22 de noviembre de 2026 | **$59.72 USD** |
| Créditos aplicados en el periodo | **−$59.72 USD** |
| Neto del periodo | **≈ $0 USD** |
| Instancias encendidas | 5 (`ci`, `tools`, `k3s-dev`, `k3s-stage`, `k3s-prod`) |
| Hosts legacy detenidos | 3 (`dev`, `stage`, `prod` de la Parte 1) |
| Volúmenes EBS | 8, 260 GB gp3 en total |

La estimación es una referencia de precio de lista para un mes completo. El costo real
depende del periodo parcial, de las instancias que estuvieron detenidas, de los descuentos y
de los créditos de la cuenta. La explicación completa está en
[`costos-reales.md`](costos-reales.md).

## Documentos

| Archivo | Contenido |
|:---|:---|
| [`estimacion.md`](estimacion.md) | Supuestos, inventario, precios y cálculo de la estimación mensual. |
| [`costos-reales.md`](costos-reales.md) | Costo del periodo con Cost Explorer, capturas de facturación y explicación de la diferencia. |
| [`evidencia-destroy.md`](evidencia-destroy.md) | Estado final de la infraestructura; la destrucción no se ejecutó. |
| [`capturas/README.md`](capturas/README.md) | Índice de la evidencia gráfica del informe. |

## Evidencia gráfica

Las capturas del informe se conservan en [`capturas/`](capturas/):

- `capturas/pricing-calculator.png` — estimación en AWS Pricing Calculator.
- `capturas/billing-nov-2026.png` — factura estimada de noviembre de 2026.
- `capturas/cost-explorer-servicios_1.png` — gráfico de Cost Explorer por servicio.
- `capturas/cost-explorer-servicios_2.png` — desglose de Cost Explorer por servicio.
- `capturas/cost-explorer-creditos_1.png` — gráfico de Cost Explorer por tipo de cargo.
- `capturas/cost-explorer-creditos_2.png` — desglose de Cost Explorer por tipo de cargo.

## Estado final y costos que permanecen

La destrucción de los tres ambientes **no se ejecutó**. La infraestructura permanece activa
para la evaluación y los recursos de `dev`, `stage` y `prod` siguen generando los costos
descritos en este informe. Permanecen también:

- El host de CI (`circleguard-ci`) y el host de herramientas (`circleguard-tools`).
- Los hosts legacy detenidos de la Parte 1 (`circleguard-dev`, `stage`, `prod`) y sus volúmenes EBS.
- El bucket del state (`circleguard-tfstate-*`) y la tabla DynamoDB
  (`circleguard-tf-locks`), que tienen `prevent_destroy`.

El detalle y la decisión de no destruir están en [`evidencia-destroy.md`](evidencia-destroy.md).
