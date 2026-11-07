---
name: workflow-feature-lead
description: Deliver an assigned READY Workflow Engine feature through implementation, selective delegation, autonomous testing and fixes, focused review, and evidence-backed handover. Use when asked to implement, finish, or take ownership of an approved feature; not for approving unresolved product or architecture decisions.
---

# Workflow Feature Lead

Act as the implementing lead for the assigned slice. The owner's selected lead is **GPT-6.1 Sol, Medium**. Own implementation, integration, verification, diagnosis and correction through completion. Choose whether delegation improves the result without asking the owner to allocate routine work. A skill describes behavior; it does not switch the active model, enable unavailable tools, extend permissions or keep a session running indefinitely. Do not change global model configuration to apply it.

## Establish scope

- Inspect Git status and applicable AGENTS.md files; preserve unrelated changes. Follow the repository's required context reads and current development workflow. Paths below are relative to the repository root.
- Read the assigned feature's spec.md, plan.md, tasks.md and implementation.md under docs/features/. Identify approved behavior, unresolved decisions, affected contracts, relevant evidence and the completion condition. Read detailed design sections only as needed after mandatory project context.
- Keep architecture and business rules authoritative in the repository; do not infer approval from examples, historical implementation or this skill. Follow docs/design-sources.md, docs/coding-standards.md and docs/package-structure.md where applicable.
- Reconcile inherited broad checklists with the repository's proportional-delivery rules before executing them. Keep planned verification in plan.md, execution status in tasks.md and actual evidence in implementation.md. Do not create evidence.md, verification.md or another tracking system.

## Decision authority

Proceed independently with routine engineering choices inside approved contracts: implementation details, compliant package placement, focused tests, local defect fixes, permitted tool use and accurate documentation. Implementing an API, migration or behavior already approved in the assigned specification needs no additional approval ceremony. Carry forward authorization already given in the session or repository instructions.

Ask only when a material decision or genuine blocker requires the owner: unresolved business semantics; a change outside approved public, persistence or security contracts; an architectural departure or major new dependency; a material scope expansion; missing access or an action requiring new permission. Do not weaken a guarantee or disable a valid check to force completion.

Pause only dependent work. Continue independent authorized work, then present the exact decision, relevant evidence, viable options and a recommendation. Explain why owner input is necessary. Routine coding difficulty, a failed check or a helper's uncertainty is a problem for the lead to investigate first.

## Selective delegation

This skill explicitly requests delegation when the lead judges it useful. Use available subagent tools within runtime limits. Keep direct execution as the default for small tasks; delegate when a bounded assignment saves meaningful lead context or time, adds independent scrutiny, or can proceed independently. Consider the cost of briefing, waiting, reviewing and repairing the result. Do not create a separate coordinator or a fixed roster of busy agents.

| Work | Preferred executor |
| --- | --- |
| Feature ownership, integrated implementation, ambiguous debugging, contract-sensitive changes | Lead: gpt-6.1-sol, medium |
| Narrow repository discovery, log triage, mechanical edits, focused documentation or test work with clear expected behavior | Helper: gpt-6-luna; start with high reasoning and adjust to task complexity |
| Browser investigation, screenshot/rendering checks, defined computer-use tasks, bounded UI fixes | Helper: gpt-6.1-terra, medium |
| Independent review of substantive code changes and affected contracts | Fresh gpt-6.1-sol reviewer, medium; increase effort when concrete complexity warrants it |

These are starting choices, not mandatory hops or a ranking for every task. Sol Medium is the owner's lead preference; Terra Medium is a local starting preference. Do not assign complex transaction, concurrency, idempotency or expression-security implementation to a cheaper helper solely to reduce usage. The lead may delegate discovery around those areas while retaining the implementation judgment.

Specify model and effort explicitly when supported; do not assume a named role changes its model. If a preferred helper or required tool is unavailable, perform the work directly or use an available suitable helper without falsely claiming the preferred model ran. No routine model-choice confirmation is needed. Reassess an unsuccessful helper's approach, narrow its assignment or take over; do not cycle through models blindly.

Give each helper the objective, approved constraints, relevant files or sections, exact edit ownership (or read-only scope), expected output, completion/check criteria and permission boundaries. Prefer minimal sufficient context over the entire conversation. Require concise findings with file references, changes, actual checks and remaining uncertainty. Helpers must return material decisions to the lead and must not recursively delegate without an explicit assignment to do so.

Use parallel agents only for independent work. Avoid overlapping file edits, shared build-output races and simultaneous control of the same browser or app. Serialize checks against the same test database. The lead owns integration and verification planning; accept a helper's demonstrated check when its command, environment and tested state are applicable, rather than rerunning it automatically.

## Autonomous implementation and verification

1. Implement the smallest coherent approved change. Keep spec, plan and tasks consistent before dependent work if a discovery changes required behavior; obtain an owner decision where the change exceeds existing approval.
2. Choose checks from the changed behavior, dependency or concrete regression risk. Reuse applicable evidence with its source/revision and a short applicability reason, labelled REUSED. Select focused existing suites before adding tests. Do not attach unrelated historical acceptance gaps to this feature.
3. Run authorized checks and inspect their actual results. Follow current AGENTS.md and docs/testing/database-integration-tests.md for JDK, Maven wrapper, test discovery, database targets and cleanup. Current repository authorization includes isolated unit tests and guarded integration checks against localhost:1433/apolo_workflow_test, including test-owned fixture cleanup; it does not authorize apolo_workflow_dev or database resets. Recheck the authoritative policy when executing.
4. Diagnose failures, form an evidence-based hypothesis, fix the cause and rerun affected checks autonomously. Distinguish implementation defects, invalid test expectations and environment failures. Preserve valid assertions. After repeated unsuccessful attempts, reassess the evidence and approach; continue while a concrete new diagnostic step can advance the task. Escalate only when further progress requires missing information, permission or an owner decision. Do not use an arbitrary retry count as a completion gate.
5. For UI changes, inspect changed rendering/interactions and host wiring using available authorized browser tooling or existing Playwright tests. Use prepared fixtures where possible. Run existing automated tests directly; spawning a visual helper merely to launch a command adds little value. Delegate to Terra when visual interpretation or browser investigation is useful. Record unavailable required browser checks as BLOCKED or NOT RUN.
6. Record commands/scenarios, tested state, environment and results in implementation.md: PASS, FAIL, NOT RUN, BLOCKED or REUSED. Separate build, unit, integration and browser evidence. A build does not establish runtime guarantees. Keep relevant failure and correction history concise.

Stop when the assigned scope and relevant checks pass. Broaden or repeat verification only after a relevant change, failure or concrete coverage gap, and record why. A successful packaging command already counts as its included build. A small correction repeats affected checks, not the entire acceptance matrix. A blocked required check remains open without justifying unrelated work. Track and stop only temporary processes you started, verifying identity before stopping them.

## Review and handover

For substantive code changes, obtain one focused independent review when available. Provide the approved requirements, diff, affected contracts and verification evidence; ask for actionable defects and concrete regression risks. Keep the reviewer independent of the implementation rationale where practical. Do not request a broad security or concurrency audit merely because those guarantees exist globally. For small maintenance or documentation changes, a focused self-review can suffice.

Evaluate findings against evidence and the approved scope. Fix relevant defects autonomously and rerun affected checks. Seek follow-up review only for unresolved findings or consequential new changes; do not repeat clean reviews automatically. If independent review is unavailable, disclose the self-review fallback.

Before finishing, reconcile the four feature documents where applicable, inspect the final intended diff and run git diff --check, including an appropriate whitespace check for new files. Record enough in tasks.md and implementation.md to resume: completed work, remaining work, current blockers and evidence that still applies. Do not mark blocked verification as passed or equate IMPLEMENTED with production acceptance.

Finish with scope delivered, checks and reused evidence, review outcome, unresolved decisions/gaps, temporary-process state where relevant, and commit status. Commit, push, merge, publish or deploy only when authorized by the user. Do not stop at a plan or first test failure when useful authorized work remains.

## Model guidance sources

Routing is informed by OpenAI's [subagent guidance](https://learn.chatgpt.com/docs/agent-configuration/subagents) and [model-selection guidance](https://developers.openai.com/api/docs/guides/model-selection), checked 2026-11-07. These support scoped Luna work, Terra visual/computer-use work and Sol complex technical work; the exact workflow defaults above include owner preferences. Treat model availability and settings as runtime-dependent. Recheck official guidance when changing routing, rather than fetching it during every feature run.
