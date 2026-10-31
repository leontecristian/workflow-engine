# Plan — F006 Test-host definition console

Implements [specification revision 1](spec.md). Execution is tracked in [tasks.md](tasks.md); actual results belong in [implementation.md](implementation.md). F005 runtime execution remains separate.

## 1. Establish baseline and delivery boundary (R01, R09–10)

Read repository guidance, F002/F004 contracts and source; preserve concurrent changes. Confirm the F004 review fixes are present. Retain exactly two Maven modules and core's ordinary JAR. Angular lives at `workflow-test-host/frontend/` with its own package.json/lockfile, not a third Maven module. No application build or database operation is needed while preparing these documents.

At implementation time inspect installed Node/npm, consult Angular's official compatibility matrix and select exact supported versions, recording source URLs and rationale. Use Angular CLI without test scaffolding and with standalone components, routing and ordinary CSS; no UI library is required. Pin dependencies/CLI and commit package-lock.json. No test runner or e2e scaffold is added.

## 2. Core family listing and HTTP DTOs (R03, R08)

Core additions use `io.github.leontecristian.workflow.core.dto.definition.DefinitionQuery` and `DefinitionPage`, `service.definition.WorkflowDefinitionService.listDefinitions`, and the existing `repository.definition.WorkflowDefinitionRepository` plus internal projection if needed. Preserve the existing core public boundary and F004 error translation. Use a bounded database query with limit+1; do not load all families or lazily load versions. No schema/index change is needed.

Host root remains `io.github.leontecristian.workflow.testhost`; use these layer-first assignments:

| Package | Responsibility |
| --- | --- |
| `configuration.console` | Feature gate, bind checks, credential/session/CSRF configuration and immutable console settings. |
| `controller.definition`, `controller.tasktype`, `controller.audit`, `controller.console` | Thin routes, transport status/media handling, session/capability information. |
| `dto.definition`, `dto.tasktype`, `dto.audit`, `dto.console` | HTTP records with decimal-string longs; explicit request/response mapping. |
| `service.definition`, `service.tasktype` | Concrete authenticated host transaction facades; core invocation and audit handoff. |
| `service.audit`, `repository.audit` | Durable host audit writer and bounded reads through transaction-participating JDBC. |
| `service.console` | Finite capability test double and trusted actor/clock context. |
| `exception.console` | Sanitized HTTP error mapping, including errors after transaction commit attempts. |

Create only classes needed by the feature. Use no mirrored service/impl interfaces. Host dependencies on core are restricted to documented services/DTOs/exceptions and WorkflowConfiguration; no core repository/entity imports.

## 3. Authentication, transport limits and transactions (R01–02, R07–08)

Add host-only Spring Security using the existing Boot dependency baseline. With console enabled, require environment-backed username/password, a local administrator role, server sessions/form login/logout and CSRF. Enforce loopback binding at startup. Disabled configuration must expose no console controller/provider beans and must block `/console` static resources even if assets are packaged; account for authentication library auto-configuration so the ordinary host startup remains documented. API entry/access-denied handlers return the specified envelope, while login uses the standard form. Permit only the login/bootstrap resources needed before authentication, with no management data. Set no wildcard CORS.

Serve Angular and API under `/console` on the same origin. Bootstrap CSRF through authenticated `/console/api/session`; configure Angular's same-origin XSRF handling to match the cookie/header names. Do not embed credentials or bearer secrets in static assets. On session expiry, retain editor state in memory and offer sign-in; do not persist credentials or workflow documents in browser local storage.

Use bounded raw UTF-8 body reading for import/replace so core sees original JSON text. Other command envelopes use strictly bound host DTOs, unknown-field rejection and the 16 KiB limit. Enforce actual streamed byte counts as well as Content-Length and reject content encoding. Validate numeric strings before conversion. Map definition JSON strings directly; never deserialize them to JavaScript numbers for editing/export.

Controllers call separate Spring-managed host facade beans with REQUIRED transactions. Inside the facade: authorize trusted principal, construct context, call core, write audit if changed, return detached response data. Transaction interception commits before the controller can emit 200. Advice handles commit failures outside the facade; do not catch and continue a rollback-only transaction. Capture actual returned tokens, never expected+1. Failed validation is a normal result eligible for commit/audit; infrastructure errors abort.

## 4. Host audit migration and fixture wiring (R06–07)

Append a host migration include after the unchanged core master in `workflow-test-host/src/main/resources/db/changelog/test-host/test-host-master.xml`. Use new resource `changes/001-console-management-audit.xml`, author `workflow-test-host`, with stable logical path. Use two ordered changesets `f006-001-create-wf-test-schema` and `f006-002-create-management-audit`; fail clearly on unexpected pre-existing objects rather than marking them run or deleting data.

Physical audit contract: schema `wf_test`; table `management_audit`; `id BIGINT IDENTITY(1,1)` primary key; `occurred_at DATETIME2(7) NOT NULL`; `actor_ref NVARCHAR(200) NOT NULL`; `operation VARCHAR(32) NOT NULL`; `entity_kind VARCHAR(32) NOT NULL` (DEFINITION, DEFINITION_VERSION, TASK_TYPE); `entity_id BIGINT NOT NULL` with positive CHECK; `correlation_id UNIQUEIDENTIFIER NOT NULL`; `evidence_json NVARCHAR(MAX) NOT NULL` with ISJSON=1 and DATALENGTH<=524288 CHECK. Actor and context validation use F004 limits. No foreign keys to core or extra runtime tables; identifiers in evidence are retained references.

Evidence version 1 contains `{schemaVersion:1, operation, before, after}` using the specified transport snapshot encoding. Core summaries already carry lifecycle/revisions/hashes and validation context/outcome; catalog snapshots include all metadata/selectability. Trusted actor/time/correlation are row columns. Bound serialized evidence before insertion; overflow fails the transaction. The table primary key supports ascending pages. List summaries omit evidence_json; exact reads return it as a string. Use JdbcTemplate on the same DataSource/JpaTransactionManager connection, declaring its dependency directly if imported. Verify this transaction participation through AC09; do not introduce a second manager or independently committing connection.

This migration creates host infrastructure on ordinary host startup through its master, even when console endpoints are disabled; it inserts no application/audit rows. Document that scope. No F001/F004 changeset or core master modification is permitted. Preserve core entity/repository scanning; JDBC audit needs no broadened JPA scan.

Register the finite sample/none DefinitionCapabilityProvider only with console enabled. Its descriptors certify only the synthetic validation environment, per the explicit exception in the spec. Label them in `/capabilities`, the UI and evidence. Do not implement an execution engine or claim a functioning resolver. Startup-only fail-audit mode throws after a changed core result before insert; enable it only for the manual rollback walkthrough and remove it from the normal launch environment afterward.

## 5. Angular pages and packaged delivery (R03–06, R09)

Implement definitions, version detail/editor, catalog and audit pages, with shared API/error display and exact-string ID types. Use hash routing below `/console/` to allow direct reloads without a catch-all that swallows API/asset errors. Keep API paths relative to the same origin and encode lookup query values. No client workflow evaluator, implicit save or optimistic successful-state display.

Use a textarea and raw file-to-text import; export via the HTTP response Blob. Display server canonical JSON as text and render diagnostics safely. Track unsaved changes and loaded tokens separately. Disable duplicate actions during requests, update tokens from committed responses and preserve edits on 409. Parse stored diagnostic evidence only for presentation; never rebuild the original workflow document from a parsed object. Treat large snapshot token values as strings throughout.

Add an opt-in Maven profile `console-ui` in the test host: run `npm ci` and `npm run build` from frontend, then copy generated Angular browser assets to the host's target classes under `static/console/` before JAR packaging. Pin any new build plugin, use installed documented Node/npm, and fail explicitly on missing tools or frontend build failure. The default Maven build skips Node work; it must not accidentally package stale console assets from a prior profile build. In the profile, recreate only generated console output. Never place generated assets under tracked src/main/resources.

Update ignore rules for node_modules, .angular, dist and generated host outputs; keep lockfiles tracked. README commands cover the default backend build, the `-Pconsole-ui` build, required environment/profile settings and host-native JDBC launch. A development Angular server/proxy is optional later work; the required delivery is one packaged host origin with no additional server to operate.

## 6. Verification procedure (R10)

After implementation, verify JDK 21 explicitly and use `./mvnw.cmd '-Dmaven.test.skip=true' package` and `./mvnw.cmd '-Dmaven.test.skip=true' -Pconsole-ui package`. Frontend compilation is required; do not generate/run automated tests, npm test, ng test, Maven test or verify. Inspect the produced host assets and core JAR boundary. Build both profiles in succession to check stale-asset handling.

For manual acceptance, identify `localhost:1433/apolo_workflow_dev` and the actual configured target before launching. Host startup runs Liquibase. Use only explicitly created development catalog/definition records, record their IDs/keys and retained audit rows, and do not reset/delete unrelated data. Existing SANDBOX_REVIEW must be inspected rather than silently overwritten. This implementation's manual walkthrough intentionally persists data; the documentation-preparation turn does not execute it.

Execute AC01–AC12 from the spec through the browser and individual HTTP requests as needed. Preserve relevant action/result excerpts with sensitive values omitted. For rollback, compare independent post-request reads under normal mode with before state and audit IDs; a 500 alone is insufficient. Distinguish two-tab stale writes from true parallel races. For initialization, use separate fresh drafts so repeated lifecycle/validation state cannot hide results. For exact numbers, keep raw document/export bytes and expected values without parsing via JavaScript Number.

Where oversized payloads, credential failure or fail-audit settings require a separate launch, record that configuration and restore normal settings. The null-constraint-name branch remains a separately reported manual reproduction gap if it cannot be exercised without schema corruption or an automated harness. Do not label the finite capability fixture as runtime support or claim that all F004 acceptance is covered.

Record temporary PID/executable/start time, stop only owned processes after verification unless the user asks to keep the console open, and report their final state. Keep logs/screenshots under ignored target, without credentials. Update implementation.md with per-scenario PASS/FAIL/NOT RUN/BLOCKED, actual toolchains, migrations/checksums, retained data and outstanding gaps. Reconcile navigation and run link/whitespace checks; commit/push only when requested.
