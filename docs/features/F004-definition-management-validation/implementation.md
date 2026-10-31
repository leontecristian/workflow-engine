# Implementation — F004 Definition management and validation

## 1. Implementation Summary

**NOT STARTED.** Specification revision 1 is READY for a later Codex implementation session. The owner requested documentation only and selected minimal SINGLE publication validation and explicitly supplied definition versions on 2026-10-31.

The inspected baseline is commit `4c67943` on main, with a clean working tree before this feature's documentation preparation. F003's definition entities/repositories exist, including the read-only legacy expression-policy mapping. No definition administration service, validator, schema resource, capability provider or F004 migration is implemented by this session.

## 2. Components and Files Changed

Prepared spec.md, plan.md, tasks.md and this record under F004-definition-management-validation, plus project navigation/status links. No separate evidence/verification process file was created. Planned Java/resource assignments are in the plan; no implementation inventory is claimed yet.

## 3. Technical Decisions and Deviations

The spec formalizes the selected subset, explicit portable version identity, draft ingestion versus publication checks, canonical content identity, validation evidence and lifecycle behavior. The planned migration halts on populated legacy definition-version data. Provider/dependency choices remain implementation work within that contract; no dependency version was installed or claimed verified. No implementation deviation exists.

## 4. Verification

Documentation preparation checks — **PASS** on 2026-10-31:

- Reviewed the scope against language design sections 12–14/16.3, database lifecycle/legacy-column rules, F001 storage and the existing F002/F003 service/mapping boundaries. Preserved the distinction between draft authoring and publication, including the catalog's new-selection restriction.
- Checked all 74 relative file links and anchors across the four feature files and four updated navigation/source files; destinations exist.
- Inspected the inventory of 11 requirement IDs, 10 acceptance scenarios and 10 ordered implementation tasks, and their requirement references.
- Parsed the inline normative JSON as document content and inspected its three-node/two-transition SINGLE shape. This is documentation validation, not execution of a workflow or automated application tests.
- New-file whitespace inspection and `git diff --check` passed. The change set contains documentation only.

- **AC01–AC10: NOT RUN.** No F004 implementation exists. Parsing/hashing, lifecycle CRUD, capability checks, publication races, migration application/restart and audit rollback need their later recorded checks.
- **Build, application startup, database access and automated tests: NOT RUN.** Outside this documentation-only task; the no-tests instruction is unchanged.
- F001–F003 historical build/schema observations are prerequisites and context, not F004 evidence.

## 5. Known Limitations

Only the specified minimal language subset may publish after implementation. Broader v1 validation and runtime execution are later work. The migration cannot proceed where legacy version rows exist; it must not delete or reinterpret them. Host audit, identity, capability declarations and production compatibility remain host integration responsibilities.

## 6. Remaining Work

Implement T01–T10 in order of dependencies. Update this handover with actual code revision, completed components, decisions/deviations and verification results; track task progress in tasks.md. If an implementation discovery changes required behavior, update spec and reconcile plan/tasks before dependent work. No processes were started and no files were staged or committed in this preparation session.
