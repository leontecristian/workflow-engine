# Implementation — F017

## Current result

2026-11-09, specification revision 3, READY. Documentation-only preparation against baseline commit 981c175. T01–T02 record the owner's decisions and closed P01–P05 plan. Runtime implementation remains F016; F017 code and migration work have not started.

The four feature records capture policy-controlled claiming, exclusive claimant completion, owner/administrator release and a separate assignee_ref column. Project navigation and the constitutional amendment identify these as agreed future behavior with detailed contracts closed in revision 3.

Revision 2 adopts assignee_ref as the current responsible individual. New and existing OPEN USER tasks obtain that reference from assignment_ref and complete directly under either policy. Claim/release is restricted to GROUP/ROLE tasks, including administrative release. O02 is closed for USER-only and mixed-kind resolvers. Creation evidence distinguishes initial USER ownership from an explicit shared-task claim. AC08 and the plan cover these behaviors; already closed-task storage remains O06.

Revision 3 records accepted peer visibility, former-member assignee discovery/release, own receipt recovery after membership/admin-privilege loss, and mandatory administrator reasons. It closes definition encoding, commands/HTTP/errors, host authorization, preserving migration, immutable evidence and historical receipt contracts. Existing closed assignees remain null; future closure retains the existing assignee. Engineering selections include claimPolicy, three separate command operations, a 1000 UTF-16-unit reason bound, ownership authorization SPI, migration 005 and versioned evidence/results as specified in O01–O06.

## Verification

PASS — reviewed requirements, decisions, open-contract dependencies and task/plan consistency. A GPT-6.1 Sol worker at medium reasoning mapped affected source and tests read-only; the lead checked decision-bearing execution, task storage and access/receipt references. PASS — git diff --check and explicit new-feature Markdown whitespace/local-link checks. The initial Python-based documentation update could not start because the local Python alias was unavailable; the update completed through native PowerShell file operations.

Application builds, unit tests, database integration and browser checks are NOT RUN because this request is specification/planning only. Earlier implementation evidence is referenced as candidate reuse in plan.md, not fresh F017 acceptance. No host/database processes were started.

Revision 3 review used GPT-6.1 Sol at medium reasoning for bounded source/contract checking. It confirmed all three schemas, strict receipt decoding, string event payloads and SQL identity/null semantics. Its read-only-provider ambiguity was corrected by defining unsupported ownership action flags as false while retaining independently authorized reads. Original v3/v4 receipts are validated before projection to the new task shape. Documentation whitespace/link/status checks and final diff review passed; these establish document consistency only.

## Remaining work

O01–O06 are closed and T02 is complete. T03–T06 await a separate implementation request. Runtime feasibility and the focused verification plan remain unexecuted; APOLO acceptance stays separate. No implementation acceptance is claimed. Changes are uncommitted.
