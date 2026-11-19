# Workflow Core

Workflow Core is the embedded Spring/JPA library. Hosts own command transactions and durable event storage. See the [workflow event catalog](src/main/java/io/github/leontecristian/workflow/core/dto/runtime/doc-files/workflow-events.html) for the event envelope, payloads and consumer examples.

## Writing workflow event batches

Register exactly one `io.github.leontecristian.workflow.core.service.runtime.WorkflowEventBatchWriter` bean. Zero or multiple writers cause `HOST_CONFIGURATION` when a command path requests the writer. In `write(List<WorkflowEvent> events)`, persist every event in order in the existing command transaction and let write failures escape. The host owns the transaction outcome. Ordering is within each batch.

An authorized command replay returns the stored receipt result without writing the original batch again. After commit, retry delivery from durable storage using the previously stored event and its stable `eventId`. Use that ID to make consumption idempotent.

## Migrating to F030

`WorkflowEventRecorder.record(List<WorkflowEvent>)` became `WorkflowEventBatchWriter.write(List<WorkflowEvent>)` in the same Java package. This breaks source and binary compatibility for the SPI. Update imports, implementation methods and bean wiring, then rebuild the host with the new core. Existing consumers can use the same event IDs, versions and payloads. Stored events and outbox rows require no data migration.
