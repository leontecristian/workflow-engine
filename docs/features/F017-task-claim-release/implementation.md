# Implementation — F017

## Planning history

2026-11-09, specification revision 3, READY. Documentation-only preparation against baseline commit 981c175. T01–T02 record the owner's decisions and closed P01–P05 plan. Runtime implementation remains F016; F017 code and migration work have not started.

The four feature records capture policy-controlled claiming, exclusive claimant completion, owner/administrator release and a separate assignee_ref column. Project navigation and the constitutional amendment identify these as agreed future behavior with detailed contracts closed in revision 3.

Revision 2 adopts assignee_ref as the current responsible individual. New and existing OPEN USER tasks obtain that reference from assignment_ref and complete directly under either policy. Claim/release is restricted to GROUP/ROLE tasks, including administrative release. O02 is closed for USER-only and mixed-kind resolvers. Creation evidence distinguishes initial USER ownership from an explicit shared-task claim. AC08 and the plan cover these behaviors; already closed-task storage remains O06.

Revision 3 records accepted peer visibility, former-member assignee discovery/release, own receipt recovery after membership/admin-privilege loss, and mandatory administrator reasons. It closes definition encoding, commands/HTTP/errors, host authorization, preserving migration, immutable evidence and historical receipt contracts. Existing closed assignees remain null; future closure retains the existing assignee. Engineering selections include claimPolicy, three separate command operations, a 1000 UTF-16-unit reason bound, ownership authorization SPI, migration 005 and versioned evidence/results as specified in O01–O06.

## Planning verification

PASS — reviewed requirements, decisions, open-contract dependencies and task/plan consistency. A GPT-6.1 Sol worker at medium reasoning mapped affected source and tests read-only; the lead checked decision-bearing execution, task storage and access/receipt references. PASS — git diff --check and explicit new-feature Markdown whitespace/local-link checks. The initial Python-based documentation update could not start because the local Python alias was unavailable; the update completed through native PowerShell file operations.

Application builds, unit tests, database integration and browser checks are NOT RUN because this request is specification/planning only. Earlier implementation evidence is referenced as candidate reuse in plan.md, not fresh F017 acceptance. No host/database processes were started.

Revision 3 review used GPT-6.1 Sol at medium reasoning for bounded source/contract checking. It confirmed all three schemas, strict receipt decoding, string event payloads and SQL identity/null semantics. Its read-only-provider ambiguity was corrected by defining unsupported ownership action flags as false while retaining independently authorized reads. Original v3/v4 receipts are validated before projection to the new task shape. Documentation whitespace/link/status checks and final diff review passed; these establish document consistency only.

## Planning handover

O01–O06 are closed and T02 is complete. T03–T06 await a separate implementation request. Runtime feasibility and the focused verification plan remain unexecuted; APOLO acceptance stays separate. No implementation acceptance is claimed. Changes are uncommitted.

## Implementation run — 2026-11-09

The checkout started clean at c549188, which already contained the approved revision 3 records. The owner authorized implementation and verification in this run. No material contract change was required.

- P02 adds OPTIONAL/REQUIRED to all three human-node schema paths without rewriting published bytes. Core migration 005 backfills OPEN USER tasks and adds null-aware, exact-target constraints. New USER tasks record their initial assignee; shared tasks start unassigned.
- P03 adds trusted claim, release and administrative release commands with an early checked aggregate write, current membership for claim/completion, and exact business authorization before ownership receipt lookup. A release leaves the same task and target available for a new claim. New creation and ownership facts and result-v5 receipts retain the specified history/event and replay behavior.
- P04 extends shared HTTP and Angular task actions and frozen recovery. The sandbox uses its existing request-instance link, roles and membership. The bounded inbox includes eligible peers and the current assignee. Ownership-only access does not grant broader instance or submission access.

## Implementation verification

- PASS — Corretto JDK 21.0.13, Java release 17. RoutingDefinitionUnitTest exercised all three schema paths, omitted/explicit policy hashes, invalid values and placement. SharedAssignmentUnitTest and TaskAssignmentEvidenceUnitTest covered existing membership, candidate scan and retained evidence paths. RuntimeReceiptUnitTest exercised v3/v4/v5 shape and ownership invariants. WorkflowRuntimeWebUnitTest exercised independent read activation, ownership routes and lookup-only replay.
- PASS — the root Maven wrapper completed offline console-ui verify with the prebuilt Angular assets, the Mockito 5.14.2 Java agent and focused test selection. The final run passed both RuntimeCommandsUnitTest variants and 31 focused sandbox unit tests, compiled SharedAssignmentIT and packaged all three modules including production console assets. The previous affected run passed 6 receipt and 8 shared web tests. Initial attempts exposed a locked Maven cache, stalled sandboxed forks, a test fixture using the wrong human-node index, the obsolete receipt entity version guard and an integration-test compilation error. The successful scoped runs used the cached dependencies and corrected code/fixtures.
- PASS — the shared Angular library and sandbox production bundle built. The final task and command-buffer rerun passed 19 isolated tests, including the review fix for token-before-task reads. Existing Sass, bundle-budget and duplicate component-ID diagnostics appeared.
- PASS — the production Angular bundle was exercised in a browser with a test-owned mocked HTTP fixture. Verified REQUIRED unassigned state, Claim and exclusive completion controls, conflict refresh and reasoned administrator release. After the read-order fix, repeated the affected observation with reviewer-A/token 1, a simulated 409, reviewer-B/token 2 and release using token 2. This evidence covers UI interactions against the fixture.
- Initially BLOCKED — the guarded localhost:1433/apolo_workflow_test run and real sandbox browser check. SQL Server Express was stopped and both IPv4 and IPv6 listener probes failed outside the sandbox. Windows denied the service start attempt. The implementation run above ended before database access. The resumed verification below records the results after the owner started SQL Server Express.
- REUSED — F016 revision 2 at 981c175 for unchanged target identity, membership provider and pre-F017 creation evidence. F015 revision 2 at the F016 baseline for retained recovery mechanics and independent read activation. F011 revision 1 at the same baseline for unchanged sequential routing and pinned-stage semantics. F017 checks cover only the changed boundaries above.

- PASS — intended diff and tracked/untracked whitespace reviewed.

## Independent review and handover

A fresh GPT-6.1 Sol Medium reviewer found two issues. The task page could show an old assignee alongside a newer version token. It now reads the contract token before the displayed task snapshot. Viewer ownership authorization outages now map to the specified 503 instead of 500. Focused checks were selected for the affected paths. The reviewer found no further actionable defect in the inspected command ordering, receipt handling or migration predicates.

## Resumed guarded verification — 2026-11-09

- PASS — the guarded harness connected to localhost:1433/apolo_workflow_test under Corretto 21.0.13. Liquibase applied 005 with 13 existing changesets. Hibernate validated the schema. The second startup found all 14 changesets applied, and a read-only query confirmed the test catalog, the migration tracking row and no held Liquibase lock.
- The first run passed the claim/claim and claim/OPTIONAL completion races, shared ROLE completion and the migration/legacy evidence scenario. The injected recorder failure initially errored during Mockito setup because stubbing went through the mandatory Spring transaction proxy. The next method inherited the unfinished Mockito stub. The fixture now stubs the target behind the proxy and still calls the recorder through the real transactional path. No production code changed for this correction.

- PASS — the focused rerun of the two affected methods passed with zero failures, errors or skips. Across the two runs, all five guarded scenarios passed. The rerun verified current membership, former-member release, exclusive completion, owner and administrative release, stale release after reclaim, own receipt recovery after privilege loss, exact task access, reason retention and omission from delivery, and rollback of task ownership, version, history, receipt and outbox after recorder failure.
- PASS — the live production console used a fresh REQUIRED Finance task held by the guarded harness. The reviewer claimed, released and reclaimed the same task. The eligible peer saw it once, with claim and completion unavailable while the other reviewer held it. The administrator released it with a reason, and the peer claimed it. An older administrator tab submitted a stale release and refreshed to the peer's newer claim, which remained intact.
- PASS — the scoped Maven reactor finished with BUILD SUCCESS. The browser tabs closed, both test host JVMs exited, and a read-only query confirmed that the browser fixture's instance, task, version, history, receipts, outbox and linked request were removed. SQL Server Express remains running.

To reproduce the guarded suite with JDK 21 from the repository root:

```powershell
./mvnw.cmd -B -ntp -Pintegration-tests '-Dtest=NoRepeatedF017UnitTests' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dit.test=SharedAssignmentIT' verify
```

For a live browser fixture, add the console-ui profile and -Df017.browserHold=true. The harness writes .local/f017-runtime.json and resumes test cleanup when .local/f017-browser.done appears. APOLO acceptance remains separate. Stop old writers before applying migration 005 as described in O06. All F017 changes are unstaged and uncommitted.

## Viewer review follow-up — 2026-11-09

The shared viewer now checks the exact-task grant before returning ownership event details, even when a host supplies its own detail authorization hook. The history component accepts source cursors that advance beyond hidden rows, including pages with no visible events, and rejects stalled or backward cursors.

- PASS — WorkflowViewerUnitTest, 11 cases on JDK 21. Direct reads conceal administrative release details when the exact-task grant is denied, honor the host's additional detail hook, and preserve continuation when filtering leaves an empty page.
- PASS — viewer lifecycle suite, 8 cases. The history component navigates partially and fully filtered pages with exact decimal cursors and rejects invalid continuation.
- REUSED — the guarded SQL and live sandbox evidence above applies to the existing storage and command paths. The viewer corrections were exercised by the focused HTTP and Angular regressions.
