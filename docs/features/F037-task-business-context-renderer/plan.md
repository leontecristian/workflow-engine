# F037 implementation plan

Use [revision 1](spec.md) as the acceptance contract. In `@workflow-engine/ui/runtime`, update `workflow-ui/projects/workflow-ui/runtime/src/lib/task-values.ts` and the Business context section of `task.html`. Use the exact-token parser for display and dedicated markup and styles for input groups and rows. Preserve the result presentation used by `task.html` and `submissions.html`. The F020 input contract and the F034 task read and decision boundaries still apply.

1. Build the library and use the existing NgModule fixture host to render the full task with the specification input captured as raw JSON text. Use fixture reads for open and completed tasks.
2. In a browser, check paired labels and values, scalar ordering, group layout on a wide card and at 320 CSS pixels, and page scrolling to the decision. Check scalar-only and unrelated nested inputs, a two-record array, exceptional values and exact token formatting. Exercise a recorded result to cover the shared renderer's other mode.
3. Finish when the build and these targeted checks pass. F020 frozen-input behavior and F034 authorization and command evidence cover the unchanged contracts.

For the flat-group follow-up, scope the new container and placeholder styles to input mode. Confirm the parent H2, run the booking and generic fixtures at desktop and narrow sizes, and compare recorded-result and Submissions renders with the previous build. Verify placeholder kinds with the focused unit suite and class styling in Chromium.

For the approved label follow-up, add acronym handling to the compact input formatter. Check the six approved examples at both input levels and exercise the existing label conversion in result mode.
