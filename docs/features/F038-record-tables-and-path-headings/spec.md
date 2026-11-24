# F038 — Record tables and path headings for task inputs

Revision 1 · IMPLEMENTED · 2026-11-24 · Owner: workflow maintainer. [Constitution v6.6.1](../../constitution.md), [CON-03/08/10/13](../../constraints.md), [ASM-05/10](../../assumptions.md). Builds on [F037](../F037-task-business-context-renderer/spec.md).

## Reviewer experience

Two shapes of JSON input are hard to read after F037.

An array of similar records renders as one block per record, each with its own heading ("1", "2") and a full set of label/value rows. A reviewer comparing quantities or prices across records has to read the same labels again for every record.

Every nesting level below a group places another responsive grid inside its parent's column. At depth three the columns become narrow, labels wrap, and the heading hierarchy relies on small differences in type. A reviewer loses track of where a value sits.

This feature presents uniform record arrays as a table and replaces nested layout with headings that state the path. It adds no knowledge of any specific payload.

### Required behavior

1. **Record tables.** Present an array of objects as a table when all of the following are true:
    - It has at least two items, and every item is an object.
    - Every item has exactly the same set of keys. Key order may differ between items.
    - Every value in every item is a scalar: string, number, boolean or null. An empty object or empty array counts as non-scalar.
    - No item has duplicate keys.
    - Each item has one through six keys, inclusive.

    Otherwise keep the per-item layout. A table never shows a cell for a field that an item omits, so arrays with mismatched keys keep the item layout.
2. **Table layout.**
    - The array's heading stays as it is.
    - The first column is the 1-based item number, as a row header, in array order.
    - The remaining columns follow the key order of the first item, labeled with the existing generic field labels.
    - Each cell uses the F037 display rules: exact numeric tokens with trimmed fractional zeros, literal strings, preserved line breaks, and distinct presentations for false, zero, empty text and null.
    - End-align a data column with tabular figures when it contains at least one number and every value is a number or null. Start-align all other data columns, including all-null columns. Cell text wraps.
    - A qualifying root array spans the structured input width. A qualifying array in the sibling grid directly under a structured input spans all grid columns. A deeper table spans its containing column and scrolls within it when needed. Preserve scalar-first order and the relative order of sibling groups.
3. **Narrow widths.** When the container is too narrow to give each column about 6rem, the table scrolls horizontally inside its own region. That region is named after the array's heading and can be reached with the keyboard. The page itself never scrolls horizontally, and no text is clipped.
4. **Path headings.**
    - The groups shown in the sibling grid under a structured input keep their F037 heading.
    - Every group below them renders inside the same column, in depth-first source order, as a flat sequence of sections without further nested grids or indentation.
    - Each of these sections has a heading that shows its path from the column's group, with the segments joined by `›`. For example, inside **Routing**: *Escalation*, then *Escalation › Window*.
    - A structured array item contributes the segment *Item N*, for example *Item 2 › Dimensions*. Structured array items that are not in a table also use *Item N* as their own heading instead of the bare number.
5. **Semantics.**
    - Tables use native table markup with column headers and row headers.
    - Path headings keep a heading level that reflects their true depth, capped at 6. Their accessible name lists the segments in order without the separator glyph.
    - Reading order, visual order and DOM order are the same.
6. **Scope.**
    - The change applies to task input presentation in `@workflow-engine/ui/runtime`.
    - Recorded results and the Submissions screen keep their current presentation.
    - Inputs remain the receiving task's authorized, frozen inputs, and the supplied input text is unchanged.

## Reference input

One `JSON` task input named `orderAtSubmission`. Preserve the numeric spelling when constructing the fixture.

```json
{
  "lineItems": [
    { "sku": "A-100", "quantity": 2, "unitPrice": 300.250, "description": "Annual licence" },
    { "description": "", "sku": "B-220", "unitPrice": 649.00, "quantity": 1 }
  ],
  "routing": {
    "queue": "finance-l2",
    "escalation": {
      "level": 0,
      "window": { "start": "08:00", "end": "18:00" }
    }
  },
  "contacts": [
    { "name": "Team A", "role": "Primary" },
    { "name": "Team B" }
  ],
  "shipments": [
    { "carrier": "DHL", "dimensions": { "weightKg": 1.50 } },
    { "carrier": "UPS", "dimensions": { "weightKg": 2 } }
  ]
}
```

## Acceptance scenarios

- **Record table.** *Line items* shows a table across the full width:
    - The columns are #, Sku, Quantity, Unit price, Description.
    - Row 2 reads B-220, 1, 649 and the empty-text presentation, each under its own column even though the source key order differs.
    - Unit price reads 300.25 and 649.
    - Quantity and Unit price are end-aligned; Sku and Description are start-aligned.
- **Null alignment.** Replace one quantity with null and the column stays end-aligned. Replace both quantities with null and the column becomes start-aligned. Each null uses the F037 placeholder.
- **Table width by depth.** The reference *Line items* table spans the sibling grid. Supplying the array as the root input value gives the table the full input width. Moving it inside `routing` confines the table and any horizontal scrolling to the *Routing* column. Queue stays first and sibling groups retain their source order.
- **Path headings.** The *Routing* column shows Queue, then a section *Escalation* with Level 0, then a section *Escalation › Window* with Start and End.
    - No nested grid or indentation appears.
    - The accessibility tree exposes the two headings at levels 5 and 6, named "Escalation" and "Escalation Window".
- **Mismatched keys.** *Contacts* keeps the item layout with headings *Item 1* and *Item 2*. *Item 2* shows Name only, with no Role row.
- **Nested records.** *Shipments* keeps the item layout. Each item shows Carrier, followed by a section *Item 1 › Dimensions* (or *Item 2 › Dimensions*) with Weight kg reading 1.5 and 2.
- **Fallbacks.** In a copy of the reference input:
    - Extending both four-field line items to seven matching scalar keys produces the item layout.
    - Setting `lineItems` to `[{}, {}]` shows *Item 1* and *Item 2*, each displaying *Empty group*.
    - Reducing `lineItems` to one record produces the item layout.
    - Replacing one line item's `description` with `{}` produces the item layout.
- **Generic keys.** Renaming every key still produces the same table, path headings and fallbacks.
- **Narrow width.** At a 320 CSS-pixel viewport:
    - The table scrolls inside its own focusable region named *Line items*.
    - The page has no horizontal overflow.
    - Path headings wrap.
    - All values and the following decision or recorded decision can be reached by vertical page scrolling and the table's own scrolling.
- **Unchanged paths.** The F037 booking reference input renders as it does after F037, since it has no arrays and no groups deeper than the column groups. Recorded results and Submissions render as before.

## Contracts

- [F020 frozen task inputs](../F020-human-task-input-bindings/spec.md) and [F034 reviewer experience](../F034-shared-task-page/spec.md) govern data access, placement and precision.
- [F037](../F037-task-business-context-renderer/spec.md) governs value display, scalar-first order and the group grid.
- [F036 style ownership](../F036-workflow-ui-style-ownership/spec.md) and the [frontend style guide](../../frontend-style-guide.md) govern presentation.
- The [implemented F037 flat-group follow-up](../F037-task-business-context-renderer/implementation.md#flat-group-visual-follow-up) distinguishes placeholders from identical literal text. Table cells reuse that presentation.
- The feature requires no new endpoint, definition version, APOLO component or metadata from hosts.
