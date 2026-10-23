-- O evento da outbox é publicado depois, por um job: guardar o traceparent (W3C) do pedido que o criou
-- deixa o mesmo traceId seguir da requisição HTTP até o worker e de volta, apesar do intervalo.
ALTER TABLE outbox_event ADD COLUMN trace_parent TEXT;
