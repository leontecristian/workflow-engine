# Implementation — F008 Restricted expressions and exclusive routing

## 1. Implementation Summary

**2026-11-01: documentation only, revision 1 DRAFT.** User requested a branch and next-feature specifications. Created and switched to `codex/f008-restricted-expressions-routing` from main, preserving existing work. T00 is complete; T01–T09 remain open. No code or expression-safety claim.

## 2. Components and Files Changed

Created spec.md, plan.md, tasks.md and this handover under F008-restricted-expressions-routing. Navigation identifies the draft and F007 dependency. No application source, POM, migration or test-source changes.

## 3. Technical Decisions and Deviations

Roadmap increment B is next: restricted expressions and exclusive routing. The draft proposes scalar #vars and an acyclic graph whose every completion path retains one SINGLE human node, preserving F007's result shape. Facts, JSON navigation, guards, loops, no-human paths and additional human stages remain outside this slice. These proposed bounds require readiness reconciliation and do not remove broader agreed capabilities.

First-match/default/strict-error and SpEL semantics are inherited. Exact AST/numeric behavior, operation enforcement/dependency support and integration codecs remain G01–G03. No counter is claimed merely because spring-expression is present. The inspected root POM uses Boot 3.4.5 management; effective dependency compatibility/runtime safety were not tested.

F007 revision 2 was READY at the initial baseline. Its implementation source files appeared concurrently during this task; they were not edited or verified here, and completion is not asserted. Its untracked documents and the existing modified F006 handover/index/roadmap were preserved. Planned APIs/providers are not claimed as executable support.

## 4. Verification

- **PASS — baseline/branch:** inspected main and created codex/f008-restricted-expressions-routing without discarding or staging existing work.
- **PASS — contract inspection:** read routing/graph/expression rules, F007 status, governing guidance and POM declarations.
- **PASS — documentation checks:** exactly four feature files, 58 local file links across six edited documents, valid inline JSON, six nodes/six transitions with valid references and intended conditional/default ordering, all 15 acceptance IDs mapped in the plan, whitespace scan including untracked files and git diff --check. These checks do not execute SpEL or establish runtime graph validation.
- **NOT RUN:** builds, unit/integration tests, dependency resolution/download, expression execution/probes, host/browser startup, database access, migrations or runtime acceptance. No process was started.

The initial combined file-write command exceeded Windows process command-length limits before execution; split it into smaller writes. No partial files or failed application changes resulted from that attempt.

## 5. Known Limitations

G01–G03 remain open; F008 is DRAFT. F007 implementation is a prerequisite. Safe expression execution needs actual structural/access/counter enforcement and compatibility evidence during implementation. Historical F005/F006 checks are not fresh F008 acceptance.

## 6. Remaining Work

Resolve T01, then implement in dependency order once F007 components are available. Preserve historical results and record actual checks here. The initial branch was created in the shared checkout, so concurrent F007 work followed that checkout onto the F008 branch. After the user reported that Codex had finished, both branches were verified at the same commit and the checkout was switched back to main without discarding any work. This documentation is prepared for a separate F008-only commit, including only its navigation changes; F007 implementation and documentation changes remain uncommitted. No push was performed.
