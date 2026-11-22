# Transactional Outbox en form-service (HU-38)

## Problema

`HealthSurveyService` guardaba el cuestionario en PostgreSQL y publicaba el evento en Kafka dentro
del mismo `@Transactional`:

```java
repository.save(survey);
kafkaTemplate.send("survey.submitted", ...);
```

Son dos sistemas distintos. Si Kafka falla después del `commit`, el cuestionario queda guardado
pero promotion-service y notification-service nunca lo reciben. Si el `send` ocurre antes del
`commit` y el commit falla, se publica un evento de un cuestionario que no existe. Es el clásico
**dual write**.

## Patrón

El **Transactional Outbox** separa la escritura del negocio de la publicación:

1. En la misma transacción se guarda el cuestionario y una fila en `outbox_event`.
2. El `OutboxRelay`, un `@Scheduled`, lee las filas pendientes con
   `SELECT ... FOR UPDATE SKIP LOCKED`.
3. Publica cada evento en Kafka y espera el ack del broker.
4. Solo entonces marca `published_at`. Si Kafka falla, deja la fila pendiente, incrementa
   `attempts` y guarda `last_error` para el siguiente intento.

La tabla `outbox_event` (`V6__create_outbox_event.sql`) guarda el payload JSON, el topic, la key,
el `traceparent` del request y los metadatos de reintento. La limpieza diaria borra los publicados
más viejos que `outbox.retention`.

## Alternativas

| Alternativa | Por qué no |
|:---|:---|
| Publicar directo (código anterior) | Pierde eventos si Kafka falla entre el `save` y el `send`. |
| Transacciones de Kafka | Solo cubren topics de Kafka; no la transacción de PostgreSQL. |
| CDC/Debezium | Agrega infraestructura (connect, réplica lógica) para un solo productor. |
| **Outbox** | Usa la misma base y un relay pequeño; el efecto es medible con una prueba. |

## Trade-offs

- **Entrega al-menos-una-vez.** Si el ack se pierde después del envío, el relay puede publicar dos
  veces. `SurveyListener.updateStatus` es idempotente para el mismo estado; si se agregan efectos
  no idempotentes, hay que deduplicar por `id` del evento.
- **Latencia.** El evento no sale en el mismo instante que el request; el relay lo publica en el
  siguiente ciclo (1 s por defecto).
- **Crecimiento de la tabla.** La limpieza diaria y el índice parcial controlan el costo.
- **Reintentos.** Un evento veneno se reintenta siempre y queda visible en `last_error`; un DLQ es
  una mejora posterior.
- **Trazas.** El `traceparent` del request se guarda en la fila y el relay lo pone como header.
  `TraceparentProducerInterceptor` no lo sobrescribe. Sin eso, la traza de HU-42 empezaría en el
  relay.

## Evidencia

- `OutboxServiceTest`: serializa el payload y guarda el `traceparent` del span activo.
- `OutboxRelayTest`: con Kafka caído, la fila queda pendiente, `attempts=1` y `last_error`; con
  Kafka disponible, se marca `published_at` y el `ProducerRecord` conserva el `traceparent`.
- `HealthSurveyServiceTest`: `submitSurvey` y `validateSurvey` encolan en el outbox en vez de
  llamar a `KafkaTemplate`.

La prueba del relay es la que demuestra el beneficio: **un fallo de Kafka no pierde el evento**.

## Límites

Solo form-service (`survey.submitted` y `certificate.validated`). Llevar el patrón a
`StatusLifecycleService` de promotion-service es una mejora posterior.
