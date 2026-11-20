# F036 — Workflow UI style ownership

Revision 1 · IMPLEMENTED · 2026-11-20 · Owner: workflow maintainer. [Constitution v6.6.1](../../constitution.md), [CON-02/03](../../constraints.md), [ASM-01](../../assumptions.md), [design sources](../../design-sources.md) and [frontend style guide](../../frontend-style-guide.md).

## Outcome

A consumer installs the compiled Angular library, provides its supported Material theme and documented icon setup, and gets complete workflow component presentation. No sandbox stylesheet, APOLO-specific patch or new mandatory global stylesheet is required. A host keeps control of its brand, global typography, density, shell, tabs and navigation.

## Why this is needed

In APOLO’s Definitions screenshot, Angular layout and Material controls are present. The count “0 on this page” inherits the heading styling. The library emits `.count-badge`, but the badge’s spacing, border and typography are defined in the [sandbox stylesheet](../../../workflow-test-host/frontend/src/styles.scss). Other shared lists emit the same class. The [Definitions template](../../../workflow-ui/projects/workflow-ui/administration/src/lib/definition/definitions.html) also puts the single Quick lookup card in a grid whose [page stylesheet](../../../workflow-ui/projects/workflow-ui/administration/src/lib/definition/definitions.scss) selects column two. This leaves the first column empty even if every stylesheet is loaded.

## Required behavior

- **R1 — Ownership by rendered element.** Inspect the root, administration and runtime entry points against sandbox globals. Move demonstrated dependencies for shared components into their owning library styles. Include affected headings, context labels, badges, card content, hints, empty states, feedback, tables/pagination and dialogs. Existing self-contained components need no restyling. A rule used by both a sandbox page and a library component needs a scoped solution for each consumer; deleting the global rule must not leave the sandbox-only consumer unstyled.
- **R2 — Containment and host theme.** Normal component imports deliver the necessary styles, including lazy entry points. Scope styles to workflow-owned elements, including projected actions and workflow-owned content displayed in Material overlays. Preserve native Material focus, hover, disabled and error states. Prefer inherited host typography and semantic theme colors with readable fallbacks when Material 3 system variables are absent. No library-level theme reset, global heading/card/button selectors, global icon change, or styling of unrelated host dialogs. Preserve existing icon and graph asset entry points.
- **R3 — Definitions.** The count reads as a secondary badge visually separated from the title for both zero and populated lists. Heading, supporting copy, hint and actions have clear hierarchy. Keep Quick lookup below the list, aligned to its leading content edge with a readable width on wide screens; allow it to use the available width on narrow screens. Remove the empty leading column. This explicitly revises the earlier desktop placement recorded in F014. Preserve footer pagination, title-row action alignment, outlined fields, and the existing import dialog behavior.
- **R4 — Scope of change.** Remove or narrow sandbox selectors that supplied the migrated appearance. Keep its shell, branding, global theme and sandbox-specific screens under host ownership. No workflow logic, content rewrite, new API, host application change, package release, or general visual redesign is required.

## Acceptance

1. **A1 — Installed package in isolation.** In the existing independent Angular consumer, show empty and populated Definitions with no sandbox CSS. The count has distinct type, padding and visible bounds; Quick lookup starts at the list’s leading edge with no empty track. At a narrow viewport the lookup, headings and pagination wrap without overlap or page-wide horizontal scrolling.
2. **A2 — Shared ownership.** Show another affected consumer of the same migrated treatment, such as the count on a runtime list, and representative examples of any other materially changed style families. A hint or empty state remains readable when a host supplies its own typography and the optional Material 3 system variables are unavailable.
3. **A3 — Host isolation.** In the same consumer, a neighboring host heading, button and card retain their host styling. Opening the workflow import dialog preserves its local spacing, readable fields, visible actions, focus and scroll handling. Shared overlay styling has no effect on a host-owned overlay.
4. **A4 — Sandbox after cleanup.** The sandbox uses the library’s presentation for those same shared elements. A representative sandbox-only screen that used a touched selector retains its intended appearance. Record checked pages, states, widths and limitations in the handover.

Verification is confined to the engine repository. The reported APOLO symptom motivates the work; modifying APOLO or running APOLO integration tests is outside scope.
