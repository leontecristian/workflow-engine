# F037 — Compact task business context

Revision 1 · IMPLEMENTED · 2026-11-24 · Owner: workflow maintainer. [Constitution v6.6.1](../../constitution.md), [CON-03/08/10/13](../../constraints.md), [ASM-05/10](../../assumptions.md).

## Reviewer experience

A reviewer reading a long task should be able to scan related facts together, tell which label belongs to each value, and reach the decision controls on a small screen. Today each successive object adds indentation and each scalar consumes two lines. In the example below, the short booking reference follows a much taller object, and the duration displays fifteen fractional zeros.

### Required behavior

1. Show **Business context** once, before the decision. Place standalone scalar inputs together first, followed by structured inputs with their own readable headings. Preserve source order within each partition and preserve array order. Every supplied field remains visible on open and completed tasks.
2. Within a structured input, use a heading for each object group and compact label/value rows for its scalar fields. At a width that accommodates two groups comfortably, show sibling groups side by side. At narrow widths stack them, allow long labels and values to wrap, and keep the reading order consistent. Preserve deeper parent/child relationships and array item boundaries. Let the host page provide vertical scrolling.
3. Render numeric values from the exact source token. For plain decimal tokens, trim trailing fractional zeros and remove an empty decimal point for display. For example, `8.000000000000000` displays as **8** and `1.2300` as **1.23**. Preserve all other digits and leave exponent-form tokens intact. Keep the original token available in the existing data contract. Never round through a JavaScript number.
4. Keep strings literal, including identifiers, dates and local timestamps. Use the existing generic field labels. A key such as `durationHours` supplies its own unit in the label; a numeric value alone cannot establish a unit. Render text safely, preserve line breaks, and retain distinct presentations for false, zero, empty text, null, empty objects, empty arrays and an unreadable value. Omitted fields stay omitted.
5. Apply the improvement to the task input presentation in `@workflow-engine/ui/runtime`. Reuse the receiving task’s authorized, frozen inputs. Keep existing recorded-result and submission displays compatible where they share the same component. Use the library’s scoped styles and host theme, with semantic group headings and programmatically associated labels and values.

## Reference input

This is the supplied API example, wrapped as a JSON object. Preserve the numeric spelling when constructing the renderer fixture. The UI adapter supplies each input as exact `valueJson` text.

```json
{
  "inputs": {
    "bookingDetailsAtSubmission": {
      "type": "JSON",
      "value": {
        "booking": {
          "client": "ENEL SERVICII COMUNE",
          "priority": "D",
          "project": "Consolidation test Test KB Docs3 cc"
        },
        "personnel": {
          "employee": "Ori Efraim Ofir",
          "engagementManager": "Cezar Gabriel Furtuna",
          "phase": "Interim",
          "position": "Partner"
        },
        "schedule": {
          "durationHours": 8.000000000000000,
          "endDate": "2026-11-20",
          "lastUpdated": "2026-11-20 18:55:16",
          "startDate": "2026-11-20",
          "updatedBy": "atarassoff"
        }
      }
    },
    "bookingReference": {
      "type": "STRING",
      "value": "21685"
    }
  }
}
```

## Acceptance scenarios

- **Reference input.** Show **Booking reference** with `21685` once, then **Booking details at submission** with Booking, Personnel and Schedule. All twelve nested fields have their supplied values, subject only to the display formatting above. **Duration hours** reads **8**. **Last updated** reads **2026-11-20 18:55:16**.
- **Responsive reading.** On a wide task card, at least two sibling groups fit beside each other. At a 320 CSS-pixel viewport, all fields and the following decision or recorded decision are reachable with vertical scrolling, without clipped text or horizontal page scrolling.
- **Generic inputs.** Renaming every booking-specific key still produces the same grouping. An array containing two records retains both records in order. A plain scalar-only task remains readable.
- **Exact values.** The number `9007199254740993.01000` displays as `9007199254740993.01`. The string `"008.00"` displays as `008.00`. Rendering leaves the supplied input text unchanged.

## Contracts

**Revision 1 visual follow-up · 2026-11-24.** Input groups use flat, tiered headings and a fixed label column within each grid column. Narrow inputs stack labels above values. Placeholder values are visually distinguishable from literal strings with the same wording in Business context. Recorded-result and Submissions presentation is preserved.

**Acronym-aware label follow-up · owner approved 2026-11-24.** In Business context, preserve uppercase acronym tokens and split them from adjacent words in standalone and nested labels. For example, `customerVATId` reads “Customer VAT id”, `IBAN` reads “IBAN”, and `bookingID` reads “Booking ID”. Keep the existing empty-key handling and result labels.

[F020 frozen task inputs](../F020-human-task-input-bindings/spec.md) and [F034 reviewer experience](../F034-shared-task-page/spec.md) govern data access, placement and precision. [F036 style ownership](../F036-workflow-ui-style-ownership/spec.md) and the [frontend style guide](../../frontend-style-guide.md) govern presentation. The feature requires no new endpoint, definition version, APOLO component or metadata from hosts.
