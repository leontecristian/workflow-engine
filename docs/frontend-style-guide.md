# Workflow UI frontend style guide

**Agreed baseline — 2026-11-14.** Applies to shared `workflow-ui` components and their sandbox presentation. This records the owner's Material 3, icon and layout decisions; it does not authorize a redesign of unchanged pages or new business behavior. Feature specifications still govern interactions. Use the [coding standards](coding-standards.md) and [verification policy](../AGENTS.md#build-and-verification) alongside this guide.

## Material 3 and host ownership

- Use native Angular Material components for buttons, fields, selects, tables, dialogs and feedback where applicable. Preserve their hover, focus, disabled and pressed states.
- Hosts own the global theme, typography, density, application shell and icon-font setup. The library supplies component layouts and opt-in assets; it must not silently replace host-wide defaults.
- Fix alignment and spacing at their actual layout boundary. Do not restyle an individual button's colors, radius or hover layer to compensate for an alignment problem. Avoid pixel translations that conceal baseline or container-size errors.
- Reuse the surrounding spacing and theme conventions. This guide introduces no new universal spacing scale, palette or density setting. The sandbox's current theme choices are not requirements for APOLO.

## Icons

Use direct `mat-icon` elements with Google's Material Symbols Outlined names. The selected font settings are weight 400, fill 0, grade 0 and optical size 24. Bundle the font locally; do not add runtime Google Fonts requests, custom SVG path catalogs, per-page icon registration or another icon family for ordinary UI controls. Workflow graph SVGs are visualizations and are outside this rule.

Each host includes the packaged font stylesheet and configures the default font once before views render. Follow the [library setup](../workflow-ui/README.md); do not repeat `fontSet` on every icon or configure a global default from inside the shared library. APOLO must account for its other icon consumers when adopting this host-level setting.

```html
<button mat-icon-button type="button"
        aria-label="Reload first page" matTooltip="Reload first page">
  <mat-icon>refresh</mat-icon>
</button>

<button mat-flat-button type="button">
  <mat-icon>upload</mat-icon>
  Import definition
</button>
```

Import `MatIconModule` in the component or owning module. Let Material place icons within its buttons; use its trailing-icon support when needed. Keep decorative icons separate from the accessible action name: an icon-only button needs an explicit label, and a tooltip does not replace that label.

The local font is a subset, not the complete Google catalog. Before using a new name, check the [font notice](../workflow-ui/projects/workflow-ui/material-symbols.NOTICE.txt). Additions require regenerating the subset with the updated sorted names, retaining the license, updating provenance/hash and checking rendered glyphs. Dynamic metadata must use supported Google names too; there is no former `ConsoleIcon` alias/fallback layer. Keep the [font checks](../workflow-ui/projects/workflow-ui/src/lib/font-icons.test.mjs) consistent with the subset.

## Hierarchy, spacing and alignment

- Align a page's primary action with its title row, not the center of a combined eyebrow/title/description block. Keep supporting description below. Allow the row to wrap or stack at narrow widths without overlap.
- Maintain consistent content edges across headings, cards, tables and forms. Align labels, input edges and action rows deliberately; check both component boxes and visible icon/text placement.
- Keep summaries compact enough that the main content is prominent. Use responsive grids for related metadata and equal-width step cards; an orphan card should not stretch merely because it is alone on a row.
- Preserve readable labels and sensible wrapping for long names and identifiers. Do not hide meaningful text or create page-wide horizontal overflow to force a single line.
- Prefer a clear page scroll path. Avoid duplicate vertical scrollbars and accidental overflow caused by nested containers or negative margins. A wide table or graph may have its own horizontal scrolling when the viewport genuinely requires it.

## Actions, lists and forms

- Keep the primary create/import entry point discoverable near the heading, rather than after a long list. Choose action emphasis through native Material variants; utility actions such as reload should remain visually secondary.
- Place list pagination after the rows it controls. Describe only what the API knows: cursor-based lists can show the current page and its row count, but must not invent a total or unsupported last-page navigation. Keep refresh accessible near the list heading.
- Keep fields, labels, validation feedback and dialog actions within the available viewport. Use Material dialog title/content/actions regions and retain native focus handling and background-scroll blocking.
- Visual changes must preserve the existing handling of pending operations, failed submissions, unsaved input and session changes. Do not infer successful persistence from closing a dialog or alter business-action behavior as styling cleanup.

## Current page-specific decisions

These are local decisions, not automatic changes to every page:

| Surface | Agreed choice |
| --- | --- |
| Instance viewer | Fixed **50% default and reset graph zoom**; manual zoom/scroll remain. Improve typography and bounds without replacing this with automatic fit. |
| Definitions | Header import action opens the existing import form in a Material dialog. Bottom cursor pagination offers page size, current-page information and Previous/Next; refresh stays at the top. |
| Definitions lookup | Exact-version lookup remains below the list. Its right-hand placement was left unchanged, not selected as a universal layout pattern. |
| Sign out | Native Material text-button appearance with balanced horizontal padding. |

Implementation and historical verification are recorded in [F013](features/F013-shared-instance-viewer/implementation.md) and [F014](features/F014-definition-catalog-administration/implementation.md). Those records include superseded SVG approaches; the direct-font approach above is the current decision. Declared Angular 19.2 compatibility is not proof of live APOLO integration.

## Focused visual review

Select checks for the changed components and affected shared consumers; this is not a mandatory full-page acceptance matrix for every edit.

1. Confirm the browser has the rebuilt library and locally loaded font. Check for missing glyphs, literal icon names, clipping and relevant console errors.
2. Inspect title/action alignment, shared content edges, table columns, form controls and footer actions. For icon buttons, compare the visible glyph with its button and hover area, not just an element's bounding box.
3. Exercise actual hover and keyboard focus separately, plus relevant disabled states. Do not report focus as hover coverage. Do not activate logout or a business mutation merely to inspect styling.
4. Check the affected scroll regions and relevant viewport sizes, including dialog content/actions when changed. Use representative long text or existing populated fixtures where useful.
5. Report page/state, viewport, checked controls and limitations. Separate confirmed defects from design preferences; distinguish representative repeated controls from exhaustive coverage. Keep screenshots or measurements when they help explain a finding.

Record actual outcomes in the owning implementation record or concise maintenance handoff. Reuse applicable evidence for unchanged behavior, and stop once the requested scope and relevant checks pass. Documentation-only edits need content, link and whitespace checks, not builds or browser reruns.
