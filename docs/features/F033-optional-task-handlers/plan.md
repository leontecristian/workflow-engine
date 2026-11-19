# Plan — F033 revision 1

## P1 · Angular integration contract (R1–R2)

In `workflow-ui/projects/workflow-ui/runtime/src/lib/`, define the decision, handler inputs and typed component registration. Export them and `provideWorkflowTaskEditors({ BOOKING_REVIEW: BookingReviewComponent })` through `runtime/src/public-api.ts` of the library. Use the existing public `TaskContract` and Angular 19 standalone components. An optional injection token holds a host-local exact-key map. The library imports no host types. Document the public imports and a short working example in [the UI integration guide](../../../workflow-ui/README.md).

## P2 · Migrate the form and coordinator (R1–R4)

Keep `WorkflowTaskComponent` at its existing selector and entry point. Extract the completion panel's generic fields/buttons from `runtime/src/lib/task.html` into a library-owned default component. Move field state with the form. Keep contextual messages, diagnostics and read-only views accessible at their current page locations. Mount the selected component with `NgComponentOutlet`, binding `contract`, `editable` and `submitDecision`; the default uses this outlet and callback too. Follow the [frontend style guide](../../frontend-style-guide.md).

Refactor the existing `complete` in `runtime/src/lib/task.ts` to consume the supplied snapshot and reuse the existing command buffer, execution and feedback paths. Preserve the current Java-compatible blank-comment check and exact result text. Bind the callback to the displayed session/task/contract load, verify it is still current at invocation and refresh editability as command state changes. Avoid remounting the form on keystrokes. Inspect the existing clear-on-refresh and uncertain-command paths during extraction.

## P3 · Deliver the two-task walkthrough (R5)

Implement the standalone booking component in `workflow-test-host/frontend/src/app/pages/tasks/` and register only its type in `workflow-test-host/frontend/src/main.ts`. The owner authorized adjusting the sandbox's initial bundle budget to account for loading the editor and runtime entry point at bootstrap. Add the sample definition JSON beside this spec. Use existing sandbox assignment and request-start flows. Supply a small captured booking reference using supported bindings and the sample's own data. Add the sample-mode booking capability in `io.github.leontecristian.workflow.testhost.configuration.console` and align the existing session capability display in `io.github.leontecristian.workflow.testhost.controller.console`. Ensure both task types are selectable through existing catalog administration. Link the importable file and walkthrough from the root README. Keep sample-only behavior in the host; core, shared web, migrations and the workflow language require no extension.

## P4 · Focused verification

- Check handler selection with and without the provider, the default's exact comment/result submission and the custom callback. Cover invalid input and a retained callback after task/session replacement at the changed Angular boundary. Exercise disabled/read-only behavior using existing fixture facilities. Adapt affected assertions while preserving their original guarantees.
- Compile the library and sandbox consumer through their normal packaging path once. If the sample capability changes Java discovery or session metadata, run the affected unit checks. Check the two-stage definition through the actual sandbox import/publication flow.
- Observe the custom controls, accepted decision and generic follow-up in one focused sandbox walkthrough (AC3). Use an authorized test-owned fixture/environment under [database testing guidance](../../testing/database-integration-tests.md). Document the reproducible user path; if an environment prevents a required observation, record that specific gap.
- **REUSED baseline:** [F015 revision 2 focused acceptance](../F015-runtime-task-instance-extraction/implementation.md#final-fresh-results-and-commands) for retained envelope freezing, dispatch and recovery operations. Check the new handler-to-page boundary and the observable pending-state change with focused Angular tests. Finish when AC1–AC3 have scoped evidence and the walkthrough is usable.
