# Plan — F030

The owner approved this plan for [specification revision 1](spec.md) on 2026-11-19 and separately authorized implementation of T02–T04.

1. Rename the interface and method in `io.github.leontecristian.workflow.core.service.runtime`. Update the required adapter lookup, calls and diagnostics in `RuntimeHostSupport` and `WorkflowExecutionService`. Keep the functional signature, exception handling, ordering and transaction participation otherwise intact.
2. Migrate the sandbox implementation in `io.github.leontecristian.workflow.testhost.service.event` and registration in `io.github.leontecristian.workflow.testhost.configuration.console`, plus tests and fixtures referencing the old SPI. Preserve mandatory transaction participation, JSON serialization, outbox SQL and delivery behavior. Rename adapter-facing terminology where it identifies this SPI; avoid unrelated repository method renames.
3. Publish one catalog in `workflow-core/src/main/java/io/github/leontecristian/workflow/core/dto/runtime/doc-files/workflow-events.html`. Use the specification's 18-event inventory and its booking, GROUP assignment and ALL review examples. Link to it from the rendered `WorkflowEvent` Javadoc and a short new `workflow-core/README.md`. Update current integration guidance, including the root README, with the rename, lookup requirement and replay semantics.
4. Verify the catalog against the event producers, envelope construction, host booking association contract and authorized runtime read APIs. Check payload keys, conditional fields, schema versions and the ALL completion sequence. Follow the rendered Javadoc and README links.

## Planned integration verification

After implementation authorization, compile the affected modules and their test sources on JDK 21 and check active integration code for stale SPI references. Exercise the real handoff method in `RuntimeHostSupport` with a recording writer and a deliberately failing writer. Verify the writer receives all supplied events once in order and that failure propagates through the existing error translation. `RuntimeCommandsUnitTest` mocks host support, so it cannot establish those handoff properties. Inspect lookup and receipt-replay call paths for the behavior specified in A01.

For database rollback, reuse the [F021 guarded integration evidence](../F021-all-collective-review/implementation.md) if the transaction and outbox write paths are unchanged. Record its revision and applicability as REUSED. Run a focused database check only if a change or failure affects that evidence. Record actual results in the handover.
