# Workflow Core

Workflow Core is the embedded Spring/JPA library. Hosts own command transactions and durable event storage. See the [workflow event catalog](src/main/java/io/github/leontecristian/workflow/core/dto/runtime/doc-files/workflow-events.html) for the event envelope, payloads and consumer examples.

## Writing workflow event batches

Register exactly one `io.github.leontecristian.workflow.core.service.runtime.WorkflowEventBatchWriter` bean. Zero or multiple writers cause `HOST_CONFIGURATION` when a command path requests the writer. In `write(List<WorkflowEvent> events)`, persist every event in order in the existing command transaction and let write failures escape. The host owns the transaction outcome. Ordering is within each batch.

An authorized command replay returns the stored receipt result without writing the original batch again. After commit, retry delivery from durable storage using the previously stored event and its stable `eventId`. Use that ID to make consumption idempotent.

## Migrating to F030

`WorkflowEventRecorder.record(List<WorkflowEvent>)` became `WorkflowEventBatchWriter.write(List<WorkflowEvent>)` in the same Java package. This breaks source and binary compatibility for the SPI. Update imports, implementation methods and bean wiring, then rebuild the host with the new core. Existing consumers can use the same event IDs, versions and payloads. Stored events and outbox rows require no data migration.

## Migrating to F032

Replace `DefinitionCapabilityProvider` with one `core.dto.definition.TaskTypeCapability` bean per task-type code. Register the executable assignment resolvers using the existing `core.service.runtime.SingleTaskAssignmentResolver` or `AllTaskAssignmentResolver` interface. A SINGLE-only review host can register the following beans. Here `ReviewerResolver` implements `SingleTaskAssignmentResolver` and declares its identifier and supported target kinds.

```java
@Bean
TaskTypeCapability reviewCapability() {
    return new TaskTypeCapability("SANDBOX_REVIEW", Set.of("COMPLETE", "APPROVE", "REJECT"));
}

@Bean
SingleTaskAssignmentResolver reviewerResolver() {
    return new ReviewerResolver();
}
```

To support an ALL roster, add a bean whose implementation supplies its identifier, target kinds and fixed roster.

```java
@Bean
AllTaskAssignmentResolver reviewRosterResolver() {
    return new ReviewRosterResolver();
}
```

Reuse the task-type bean when both policies use the same outcomes. Resolver identifiers are unique within their policy. Shared GROUP or ROLE destinations also require `WorkflowSharedAssignmentProvider`. Maintain selectable task types in the persisted catalog.

Core accepts empty registries. Duplicate or malformed descriptors fail with `HOST_CONFIGURATION` when the relevant registry is used. Publication reads metadata without resolving assignments. Fresh start checks task capabilities and resolver presence across the whole pinned definition. Fresh completion checks task capabilities again, and stage entry checks and invokes that stage's resolver. Service binding checks and assignment target validation still apply.

Replace the completion, ownership, reassignment and cancellation authorization adapters with one `core.service.runtime.WorkflowAuthorization` bean. Import the operation, scope and typed request DTOs from `core.dto.runtime`. The following completion-only example delegates to the host's existing booking policy.

```java
@Bean
WorkflowAuthorization workflowAuthorization(BookingPolicy policy) {
    return new WorkflowAuthorization() {
        @Override
        public Set<WorkflowOperation> supportedOperations() {
            return Set.of(WorkflowOperation.COMPLETE_TASK);
        }

        @Override
        public boolean isAllowed(WorkflowAuthorizationRequest request) {
            if (request instanceof WorkflowAuthorizationRequest.Task access
                    && access.permission().scope() == WorkflowScope.ACCESS_COMPLETION) {
                return policy.canView(access.task());
            }
            if (request instanceof WorkflowAuthorizationRequest.Completion completion) {
                return policy.canDecide(completion.task());
            }
            return false;
        }
    };
}
```

Core captures operation support at wiring. Omit the service or return an empty set to support zero covered operations. An ambiguous service or invalid declaration fails wiring with `HOST_CONFIGURATION`. Declare claim, release and administrative release separately even though they share `ACCESS_OWNERSHIP`. Business starts remain host-owned. See the [F032 permission map](../docs/features/F032-host-contract-review/spec.md#command-authorization).

Current business access precedes receipt lookup. A matching original-actor receipt skips fresh permission and validation. Completion hosts also supply `WorkflowTaskCompletionValidator` for fresh decisions. Reassignment hosts supply `WorkflowTaskReassignmentTargetValidator` to check the proposed destination after actor authorization on fresh execution. Advisory read hints remain separate from authoritative command permission.

Rebuild the host and optional workflow-web together with core. Published definitions, receipts and execution evidence keep their existing formats.
