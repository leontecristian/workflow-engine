# F037 implementation plan

## Revision 2 — Assignee instructions

1. In `io.github.leontecristian.workflow.web.controller.runtime.WorkflowRuntimeReadController`, include the pinned node’s nonblank description as an optional string in the existing task-detail contract. Use the node already resolved by `io.github.leontecristian.workflow.web.service.runtime.WorkflowTaskProjection`. Keep this addition local to the detail response because task lists share the projection. The existing definition schema and stored version document supply the text.
2. Carry the optional text through the shared Angular runtime model and `@workflow-engine/ui/http` task-contract adapter. In the shared runtime task page and the sandbox task route (`workflow-test-host/frontend/src/app/pages/tasks`), let the component that owns the title render the instructions. Use the existing authorized read in both header modes.
3. Verify the detail projection for distinct pinned wording and absence, and that older responses still load through the adapter. Exercise both header owners with a multiline description containing literal markup and with omitted or blank text. Include a closed-task view. Confirm placement, wrapping and single rendering. Revision 1 renderer checks concern the input section and need no repeat for this header change.

## Revision 1 — Business context

Use [revision 1](spec.md) as the acceptance contract. In `@workflow-engine/ui/runtime`, update `workflow-ui/projects/workflow-ui/runtime/src/lib/task-values.ts` and the Business context section of `task.html`. Use the exact-token parser for display and dedicated markup and styles for input groups and rows. Preserve the result presentation used by `task.html` and `submissions.html`. The F020 input contract and the F034 task read and decision boundaries still apply.

1. Build the library and use the existing NgModule fixture host to render the full task with the specification input captured as raw JSON text. Use fixture reads for open and completed tasks.
2. In a browser, check paired labels and values, scalar ordering, group layout on a wide card and at 320 CSS pixels, and page scrolling to the decision. Check scalar-only and unrelated nested inputs, a two-record array, exceptional values and exact token formatting. Exercise a recorded result to cover the shared renderer's other mode.
3. Finish when the build and these targeted checks pass. F020 frozen-input behavior and F034 authorization and command evidence cover the unchanged contracts.

For the flat-group follow-up, scope the new container and placeholder styles to input mode. Confirm the parent H2, run the booking and generic fixtures at desktop and narrow sizes, and compare recorded-result and Submissions renders with the previous build. Verify placeholder kinds with the focused unit suite and class styling in Chromium.

For the approved label follow-up, add acronym handling to the compact input formatter. Check the six approved examples at both input levels and exercise the existing label conversion in result mode.
