> **SUPERSEDED — historical design, marked 2026-10-24.**
>
> Use the [consolidated design](../architecture/workflow-engine-design.md) for current architecture and the [task description](../requirements/approval-capability.md) for delivery acceptance. The text below is preserved as design history, with Markdown line-break formatting normalized; it is not a second implementation specification.
>
> Current decisions include Java 17 compilation target / JDK 21 runtime, JPA persistence inside workflow-core, exactly two Maven modules (workflow-core and workflow-test-host), and eventual integration of core as an APOLO sibling module. Explicit START/HUMAN_TASK/SERVICE_TASK/DECISION/END nodes, human tasks, a persistent task-type registry and restricted expression routing belong to initial scope. Standalone business decisions and FIRST/UNIQUE table policies are optional later work. Production idempotency/durable handoff are mandatory; the designer follows the first APOLO integration.
>
> Older node models, transition-key decision outputs, delivery phases and optional reliability language below are superseded. Provisional and unresolved questions are tracked in the [current register](../architecture/workflow-engine-design.md#222-unresolved-questions-and-ownership).
>
> The [section-by-section reconciliation](original-design-reconciliation.md) explains what was retained, restored, superseded or deferred. A complete updated proposed example appears in [current design section 7.4](../architecture/workflow-engine-design.md#74-complete-proposed-workflow-example). The historical body below is preserved.

---

# APOLO Workflow Engine
## Technical Design Document

**Status:** Draft\
**Target application:** APOLO\
**Technology stack:** Java / Spring Boot, Angular, Microsoft SQL Server\
**Workflow definition format:** JSON\
**Expression language:** Spring Expression Language (SpEL)

---

## 1. Purpose

This document defines the architecture and initial design of a workflow engine integrated directly into APOLO.

The objective is not to implement a complete BPMN platform or reproduce products such as Camunda or Flowable. The objective is to provide APOLO with a lightweight, domain-oriented workflow capability covering the application's actual requirements:

- configurable business workflows;
- human tasks;
- approvals and rejections;
- conditional routing;
- decision gates;
- decision tables;
- workflow history;
- versioned workflow definitions;
- workflow-specific variables;
- integration with APOLO business entities;
- integration with the existing APOLO permission model;
- future graphical workflow editing.

The engine should remain small enough to be maintained as part of APOLO while providing a stable foundation for multiple business processes.

---

# 2. Design Principles

The following principles drive the architecture.

## 2.1 Workflow engine and business domain remain separated

The workflow engine understands concepts such as:

- state;
- transition;
- decision;
- task;
- workflow instance;
- variable;
- guard;
- action;
- history.

It must not understand APOLO-specific concepts such as:

- project;
- budget;
- planning;
- employee;
- resource;
- financial year.

Those remain responsibilities of the APOLO domain modules.

The boundary is:

> The workflow engine knows how workflows execute. APOLO modules know what the business means.

---

## 2.2 Workflow definitions are documents

Workflow definitions are represented using JSON.

A complete workflow definition is stored as one immutable JSON document for each published workflow version.

The definition is not normalized into relational state and transition tables.

---

## 2.3 Runtime data is relational

Only workflow **definitions** use JSON.

Runtime information is stored using conventional relational SQL Server tables.

Examples:

- workflow instances;
- current state;
- tasks;
- workflow variables;
- transition history;
- timers.

This gives APOLO a hybrid model:

```text
             DEFINITION

             JSON document
                  │
                  ▼
       WorkflowDefinition model


               RUNTIME

        relational SQL tables
                  │
        ┌─────────┼──────────┐
        ▼         ▼          ▼
    Instance    Tasks     History
        │
        ▼
    Variables
```

---

## 2.4 SQL Server is the source of truth

Workflow definitions are loaded from SQL Server when required.

The first implementation will not maintain a global in-memory definition cache.

Workflow definitions are expected to be small, and workflow activity in APOLO is primarily human-scale activity.

A typical execution therefore performs:

```text
WorkflowInstance
       │
       ▼
definitionVersionId
       │
       ▼
SQL Server
       │
       ▼
definition JSON
       │
       ▼
Jackson
       │
       ▼
WorkflowDefinition
       │
       ▼
WorkflowEngine
```

If performance measurements later justify caching, caching can be introduced transparently behind the `WorkflowDefinitionLoader` abstraction.

---

# 3. Workflow Definition Lifecycle

A workflow has a stable identity and multiple versions.

Example:

```text
planning-approval
    │
    ├── version 1
    ├── version 2
    └── version 3
```

Each version has a lifecycle:

```text
DRAFT
  │
  ▼
PUBLISHED
  │
  ▼
RETIRED
```

Published workflow versions are immutable.

A published definition is never edited in place.

Changes require:

```text
Published V3
     │
     ▼
clone
     │
     ▼
Draft V4
     │
     ▼
edit
     │
     ▼
validate
     │
     ▼
publish
```

Running workflow instances continue using the definition version against which they were started.

---

# 4. Definition Persistence

The initial persistence model is:

```text
workflow_definition
────────────────────────────────
id
workflow_key
name
description
created_at


workflow_definition_version
────────────────────────────────
id
workflow_definition_id
version
status
definition_json
checksum
created_at
created_by
published_at
published_by
```

For SQL Server versions without the native JSON datatype:

```sql
definition_json NVARCHAR(MAX) NOT NULL
```

with:

```sql
CHECK (ISJSON(definition_json) = 1)
```

The database stores the definition but does not execute workflow logic.

JSON interpretation belongs exclusively to the application.

---

# 5. Canonical Workflow Language

The canonical language is a strongly typed JSON DSL.

It is not treated as arbitrary JSON.

Java DTOs represent the language explicitly.

Conceptually:

```java
WorkflowDefinition
WorkflowStateDefinition
WorkflowTransitionDefinition
WorkflowTaskDefinition
WorkflowVariableDefinition
WorkflowDecisionDefinition
```

Jackson performs serialization and deserialization.

The engine itself should never navigate raw `JsonNode` structures during normal execution.

---

# 6. Core Workflow Graph Model

A workflow is modeled as a directed graph.

```text
Node       = workflow state
Edge       = workflow transition
```

Initial node types:

```text
STATE
DECISION
FINAL
```

Possible future node types may include:

```text
WAIT
TIMER
FORK
JOIN
SUBPROCESS
```

but these are deliberately outside V1.

---

# 7. STATE Node

A regular state represents a stable workflow position.

Example:

```json
{
  "key": "MANAGER_REVIEW",
  "type": "STATE",
  "name": "Manager Review"
}
```

A workflow instance may remain in such a state until a user or application action causes a transition.

A state can optionally create a human task.

Example:

```json
{
  "key": "MANAGER_REVIEW",
  "type": "STATE",
  "name": "Manager Review",
  "task": {
    "key": "reviewPlanning",
    "name": "Review planning",
    "assignment": {
      "type": "RESOLVER",
      "resolver": "projectManager"
    }
  }
}
```

---

# 8. FINAL Node

A final state terminates the workflow.

Example:

```json
{
  "key": "APPROVED",
  "type": "FINAL",
  "name": "Approved"
}
```

Final states:

- cannot have user transitions leaving them;
- mark the workflow instance as completed;
- record workflow completion time.

Typical examples:

```text
APPROVED
REJECTED
CANCELLED
COMPLETED
```

---

# 9. DECISION Node

Decision gates are first-class graph nodes.

They represent automatic routing decisions.

Example:

```text
MANAGER_REVIEW
      │
   APPROVE
      │
      ▼
<PARTNER_REQUIRED?>
      │
   ┌──┴──────────┐
   │             │
  yes          default
   │             │
   ▼             ▼
PARTNER       APPROVED
REVIEW
```

JSON:

```json
{
  "key": "PARTNER_REQUIRED",
  "type": "DECISION",
  "name": "Partner approval required?"
}
```

A decision node:

- does not wait for user interaction;
- should have one or more incoming transitions;
- must have at least two outgoing routes;
- automatically evaluates its routing logic;
- must immediately resolve to another node.

A successfully committed workflow instance should never remain permanently in a `DECISION` node.

---

# 10. Transitions

Transitions connect nodes.

Example:

```json
{
  "key": "SUBMIT",
  "name": "Submit",
  "from": "DRAFT",
  "to": "MANAGER_REVIEW"
}
```

A user transition may additionally define:

```json
{
  "key": "SUBMIT",
  "name": "Submit",
  "from": "DRAFT",
  "to": "MANAGER_REVIEW",
  "permission": "PLANNING_SUBMIT",
  "guards": [
    "planningComplete",
    "noBlockingConflicts"
  ],
  "actions": [
    "preparePlanningForReview"
  ],
  "events": [
    "planningSubmitted"
  ]
}
```

Possible properties include:

- key;
- presentation name;
- source node;
- target node;
- permission;
- guards;
- actions;
- events;
- comment requirement;
- decision condition;
- default decision branch.

---

# 11. Stable Keys vs Presentation Names

Technical keys are immutable identifiers.

Example:

```json
{
  "key": "MANAGER_REVIEW",
  "name": "Manager Review"
}
```

The key participates in workflow semantics.

The name is presentation information.

This allows names to be changed or localized without altering workflow identity.

A later localization implementation could use:

```json
{
  "key": "MANAGER_REVIEW",
  "nameKey": "workflow.planning.managerReview"
}
```

without affecting the engine.

---

# 12. Guards

A guard determines whether a requested user transition is currently permitted.

Example:

```json
"guards": [
  "planningComplete",
  "noBlockingConflicts"
]
```

The implementation is registered in Java.

Conceptually:

```java
@WorkflowGuard("planningComplete")
class PlanningCompleteGuard {
}
```

A guard should return more information than a boolean.

Conceptually:

```java
record GuardResult(
    boolean allowed,
    String code,
    String message
) {}
```

This permits the frontend to explain why an action cannot currently be performed.

Example response:

```json
{
  "action": "SUBMIT",
  "available": false,
  "reasons": [
    {
      "code": "PLANNING_INCOMPLETE",
      "message": "There are unallocated planning hours."
    }
  ]
}
```

---

# 13. Guards vs Decision Conditions

The two concepts have different semantics.

A guard answers:

> Is this transition allowed?

A condition answers:

> Which branch should the workflow follow?

A failed guard blocks an operation.

A false decision condition does not indicate an error; another route may be selected.

They should therefore remain separate engine concepts.

---

# 14. SpEL Expression Language

APOLO already uses Spring Expression Language for workflow expressions.

The new engine will reuse SpEL rather than introduce a new expression language.

Example:

```json
{
  "key": "PARTNER_REVIEW_REQUIRED",
  "from": "APPROVAL_ROUTE",
  "to": "PARTNER_REVIEW",
  "condition": "budgetAtSubmission >= approvalThreshold"
}
```

More complex example:

```text
budgetAtSubmission >= approvalThreshold &&
riskLevel == 'HIGH'
```

Expressions must execute using a controlled, read-only evaluation context.

They should have access only to explicitly exposed workflow information.

Arbitrary bean access, arbitrary Java type access and side-effecting method execution should not be available.

A decision expression must conceptually behave as:

```text
input data
   │
   ▼
SpEL expression
   │
   ▼
boolean result
```

and nothing else.

---

# 15. Decision Gate – Expression Branches

A simple decision gate can attach SpEL conditions to its outgoing transitions.

Example:

```json
{
  "key": "PARTNER_REQUIRED",
  "from": "APPROVAL_ROUTE",
  "to": "PARTNER_REVIEW",
  "condition": "budgetAtSubmission >= 500000"
}
```

with fallback:

```json
{
  "key": "PARTNER_NOT_REQUIRED",
  "from": "APPROVAL_ROUTE",
  "to": "APPROVED",
  "default": true
}
```

Decision semantics are:

```text
Evaluate all non-default routes.

If exactly one matches:
    take it.

If none matches:
    use the default route if present.

If none matches and no default exists:
    raise workflow routing error.

If multiple routes match:
    raise workflow routing error.
```

This gives strict and deterministic XOR decision semantics.

---

# 16. Decision Tables

APOLO already has decision-table expressions.

Decision tables should therefore become a first-class decision mechanism in the new workflow engine.

A decision gate may use either:

```text
EXPRESSION
```

or:

```text
DECISION_TABLE
```

For more complex routing rules, the table is preferable to placing many conditions directly on graph edges.

Example:

```json
{
  "key": "APPROVAL_ROUTE",
  "type": "DECISION",
  "name": "Determine approval route",
  "decision": {
    "type": "DECISION_TABLE",
    "hitPolicy": "FIRST",
    "rules": [
      {
        "when": "riskLevel == 'HIGH'",
        "result": "PARTNER_REQUIRED"
      },
      {
        "when": "budgetAtSubmission >= 500000",
        "result": "PARTNER_REQUIRED"
      },
      {
        "when": "budgetAtSubmission >= 100000",
        "result": "FINANCE_REQUIRED"
      },
      {
        "default": true,
        "result": "DIRECT_APPROVAL"
      }
    ]
  }
}
```

The decision table should return a **transition key**, not a destination state.

Therefore:

```text
Decision table
      │
      ▼
PARTNER_REQUIRED
      │
      ▼
transition definition
      │
      ▼
PARTNER_REVIEW
```

This separates business decision logic from graph topology.

For V1 the primary hit policy should be:

```text
FIRST
```

Rules are evaluated in order and the first matching rule wins.

Additional policies such as `UNIQUE` or `COLLECT` may be introduced later if a real use case requires them.

---

# 17. Automatic Decision Resolution

When execution reaches a decision node, the engine continues automatically.

Conceptually:

```java
while (currentNode.isAutomatic()) {
    transition = resolveAutomaticTransition(currentNode);
    executeAutomaticTransition(transition);
}
```

The workflow operation ends only when it reaches a stable node such as:

```text
STATE
FINAL
```

Decision gates can therefore be chained.

Example:

```text
MANAGER_REVIEW
      │
      ▼
AMOUNT_DECISION
      │
      ▼
RISK_DECISION
      │
      ▼
PARTNER_REVIEW
```

---

# 18. Protection Against Automatic Loops

The definition validator should detect obvious cycles consisting entirely of automatic nodes.

Runtime protection should also exist.

For example:

```text
maximum automatic transitions per command = 50
```

If the limit is exceeded, execution terminates with a workflow error.

This protects the system against conditions or future node types producing accidental infinite loops.

---

# 19. Workflow Instance Model

Runtime instances are stored relationally.

Initial model:

```text
workflow_instance
─────────────────────────────────
id
definition_version_id
business_type
business_id
current_state
status
started_at
started_by
completed_at
row_version
```

Example:

```text
id                    8127
definition_version    14
business_type         PLANNING
business_id           48521
current_state         MANAGER_REVIEW
status                ACTIVE
```

`definition_version_id` references the exact immutable workflow version used by the instance.

The current workflow definition is never looked up using only the workflow key.

---

# 20. Business Object Association

Each workflow instance refers to an APOLO domain object.

Example:

```text
WorkflowInstance
      │
      ├── businessType = PLANNING
      └── businessId   = 48521
                   │
                   ▼
             Planning entity
```

The workflow engine does not duplicate the full APOLO entity.

Domain information remains in the relevant APOLO tables and domain services.

---

# 21. Workflow Variables

Workflow variables contain values owned by the workflow execution itself.

They must not become a second copy of the APOLO domain model.

Initial table:

```text
workflow_variable
─────────────────────────────────
id
workflow_instance_id
variable_key
value_type

string_value
long_value
decimal_value
boolean_value
date_value
datetime_value
```

Constraint:

```text
UNIQUE(workflow_instance_id, variable_key)
```

Initial types:

```text
STRING
LONG
DECIMAL
BOOLEAN
DATE
DATETIME
```

Additional types can be introduced later where justified.

Arbitrary serialized objects should not initially be supported.

If a workflow needs to reference a domain object, it stores its ID rather than serializing the whole object.

---

# 22. Domain Data vs Workflow Variables

A strict distinction should be maintained.

Domain data includes:

```text
project name
project manager
budget
planning hours
employees
financial year
resource allocation
```

This belongs in APOLO.

Workflow-specific data may include:

```text
approval threshold snapshot
manual routing option
selected reviewer
escalation level
workflow deadline
decision-specific flags
```

The rule is:

> If the value belongs to APOLO's business domain, store it in the domain. If it belongs to execution of the process, store it as a workflow variable.

---

# 23. Live Data vs Snapshot Data

Decision conditions may use either current domain information or captured workflow values.

Example of live data:

```text
Does planning currently contain blocking conflicts?
```

Example of snapshot data:

```text
What was the budget when the workflow was submitted?
```

This distinction is important for auditability.

Consider:

```text
budget at submission = 520,000
```

A decision sends the workflow to partner approval.

If the budget changes the next day to:

```text
480,000
```

historical routing should remain explainable.

Values that must remain historically deterministic should therefore be captured as workflow variables.

Example:

```text
budgetAtSubmission = 520000
approvalThreshold  = 500000
```

The decision then evaluates:

```text
budgetAtSubmission > approvalThreshold
```

rather than reading the current budget again.

---

# 24. Variable Declarations in Workflow Definitions

Variables may be explicitly declared in workflow JSON.

Example:

```json
"variables": [
  {
    "key": "budgetAtSubmission",
    "type": "DECIMAL"
  },
  {
    "key": "approvalThreshold",
    "type": "DECIMAL"
  },
  {
    "key": "urgent",
    "type": "BOOLEAN",
    "default": false
  }
]
```

This provides:

- validation;
- documentation;
- expression autocomplete;
- graphical-editor support;
- predictable type conversion.

---

# 25. Workflow Context

Workflow execution exposes a controlled context.

Conceptually:

```java
interface WorkflowContext {

    long instanceId();

    String businessType();

    long businessId();

    String currentState();

    WorkflowVariables variables();

    UserContext user();
}
```

Domain-specific guards and actions may use the business reference:

```text
businessType + businessId
```

to obtain current domain information from APOLO services.

Expression evaluation receives a safe projection of the context.

---

# 26. Human Tasks

A workflow state may create a human task.

Example:

```json
{
  "key": "MANAGER_REVIEW",
  "type": "STATE",
  "task": {
    "key": "reviewPlanning",
    "name": "Review planning",
    "assignment": {
      "type": "RESOLVER",
      "resolver": "projectManager"
    }
  }
}
```

Runtime storage:

```text
workflow_task
─────────────────────────────────
id
workflow_instance_id
task_key
state_key
status
assigned_user_id
assignment_type
created_at
due_at
completed_at
completed_by
```

Possible statuses:

```text
OPEN
COMPLETED
CANCELLED
```

V1 should support zero or one human task per state.

Parallel human tasks can be introduced later if a real requirement emerges.

---

# 27. Assignment

Task assignment should support extensible strategies.

Possible forms include:

```text
USER
ROLE
RESOLVER
```

For APOLO, resolver-based assignments are particularly useful.

Example:

```json
{
  "type": "RESOLVER",
  "resolver": "projectManager"
}
```

Java:

```java
@WorkflowAssignmentResolver("projectManager")
class ProjectManagerResolver {
}
```

Other possible resolvers:

```text
planningOwner
projectPartner
budgetOwner
financialController
```

This avoids binding workflow definitions directly to implementation details such as Keycloak roles.

---

# 28. Actions

Transitions can invoke named synchronous business actions.

Example:

```json
"actions": [
  "approvePlanning"
]
```

Implementation:

```java
@WorkflowAction("approvePlanning")
class ApprovePlanningAction {
}
```

Actions execute inside the workflow/domain transaction where appropriate.

An action must be explicitly registered.

Workflow JSON cannot contain arbitrary Java code.

---

# 29. Events

Asynchronous or loosely coupled side effects should be represented using events.

Example:

```json
"events": [
  "planningApproved"
]
```

This permits:

```text
Workflow transition
       │
       ▼
PlanningApprovedEvent
       │
       ├── notifications
       ├── integration
       ├── audit
       └── analytics
```

The workflow engine should not directly contain email or integration-specific logic.

---

# 30. Execution Semantics

A normal user transition should conceptually execute in the following order:

```text
1. Load workflow instance
2. Load immutable definition version
3. Locate current node
4. Locate requested transition
5. Verify transition source matches current state
6. Check user authorization
7. Evaluate guards
8. Validate required input/comment
9. Execute synchronous workflow/domain actions
10. Close current human task
11. Move instance to target node
12. Record transition history
13. Resolve automatic decision nodes
14. Create task for resulting stable state
15. Mark workflow completed if FINAL
16. Record/publish resulting events
17. Commit transaction
```

The exact event publication implementation may use a transactional event mechanism or transactional outbox to ensure reliability.

---

# 31. Transaction Boundary

A workflow transition should behave atomically.

Conceptually:

```text
BEGIN TRANSACTION

business changes
workflow state change
workflow history
task completion
new task creation
outgoing event record

COMMIT
```

If a synchronous operation fails, the workflow transition should normally roll back.

A committed workflow transition must never disappear.

---

# 32. Concurrency

SQL Server optimistic locking should protect workflow instances from concurrent modifications.

The instance table contains:

```text
row_version
```

using SQL Server `ROWVERSION`.

Two simultaneous requests attempting to transition the same instance cannot both succeed against the same version.

This protects scenarios such as two users approving the same task concurrently.

---

# 33. Idempotency

Workflow commands should eventually support an idempotency identifier.

Example:

```text
commandId = UUID
```

A repeated request with the same command identifier should not cause the same transition to execute twice.

This protects against frontend retries and lost HTTP responses.

---

# 34. History and Audit

Workflow history is append-only.

Initial table:

```text
workflow_history
─────────────────────────────────
id
workflow_instance_id
sequence_number
from_state
to_state
transition_key
transition_type
performed_by
performed_at
comment
decision_expression
decision_result
```

History should record both manual and automatic routing.

Example:

```text
14:01  MANAGER_REVIEW
       APPROVE
       performed by user 124

14:01  PARTNER_REQUIRED
       automatic decision
       condition: budgetAtSubmission >= approvalThreshold
       result: true

14:01  PARTNER_REVIEW
```

The UI may hide technical automatic transitions by default, but audit data should preserve them.

---

# 35. Workflow Definition Loading

Workflow execution uses a loader abstraction:

```java
interface WorkflowDefinitionLoader {

    WorkflowDefinition load(long definitionVersionId);
}
```

Initial implementation:

```text
DatabaseWorkflowDefinitionLoader
```

Execution:

```text
workflow instance
      │
      ▼
definitionVersionId
      │
      ▼
SQL Server
      │
      ▼
definition JSON
      │
      ▼
Jackson
      │
      ▼
WorkflowDefinition
```

The definition should be loaded once for a workflow command and retained only for the duration of that command.

---

# 36. No Global Definition Cache in V1

V1 deliberately avoids a global application cache.

Reasons:

- workflow definitions are small;
- human workflow traffic is relatively low;
- SQL Server already provides database-level page caching;
- cache invalidation is unnecessary complexity at this stage;
- the architecture should first optimize for correctness and simplicity.

If profiling later identifies definition loading or JSON deserialization as significant, a bounded cache can be introduced transparently behind `WorkflowDefinitionLoader`.

Because published definitions are immutable, such a cache would be simple:

```text
cache key = definitionVersionId
```

No mutation-based invalidation is required.

---

# 37. Definition Validation

Validation happens before publishing, not on every execution.

Publishing pipeline:

```text
JSON syntax
    │
    ▼
JSON Schema validation
    │
    ▼
graph validation
    │
    ▼
semantic validation
    │
    ▼
SpEL parse validation
    │
    ▼
registered guard/action/resolver validation
    │
    ▼
PUBLISHED
```

Validation rules should include at least:

- workflow key exists;
- initial state exists;
- state keys are unique;
- transition keys are valid;
- source states exist;
- target states exist;
- final nodes have no outgoing user transitions;
- decision nodes have appropriate outgoing routes;
- at most one default route exists;
- SpEL syntax is valid;
- referenced variables exist where statically determinable;
- referenced guards exist;
- referenced actions exist;
- referenced assignment resolvers exist;
- obvious automatic cycles are rejected;
- unreachable nodes are detected.

---

# 38. Example Complete Workflow

A simplified APOLO planning approval definition could look like:

```json
{
  "key": "planning-approval",
  "name": "Planning Approval",
  "version": 1,

  "initialState": "DRAFT",

  "variables": [
    {
      "key": "budgetAtSubmission",
      "type": "DECIMAL"
    },
    {
      "key": "approvalThreshold",
      "type": "DECIMAL"
    },
    {
      "key": "riskLevel",
      "type": "STRING"
    }
  ],

  "states": [
    {
      "key": "DRAFT",
      "type": "STATE",
      "name": "Draft"
    },

    {
      "key": "MANAGER_REVIEW",
      "type": "STATE",
      "name": "Manager Review",
      "task": {
        "key": "managerReview",
        "name": "Review planning",
        "assignment": {
          "type": "RESOLVER",
          "resolver": "projectManager"
        }
      }
    },

    {
      "key": "APPROVAL_ROUTE",
      "type": "DECISION",
      "name": "Determine approval route",
      "decision": {
        "type": "DECISION_TABLE",
        "hitPolicy": "FIRST",
        "rules": [
          {
            "when": "riskLevel == 'HIGH'",
            "result": "PARTNER_REQUIRED"
          },
          {
            "when": "budgetAtSubmission >= approvalThreshold",
            "result": "PARTNER_REQUIRED"
          },
          {
            "default": true,
            "result": "DIRECT_APPROVAL"
          }
        ]
      }
    },

    {
      "key": "PARTNER_REVIEW",
      "type": "STATE",
      "name": "Partner Review",
      "task": {
        "key": "partnerReview",
        "name": "Approve planning",
        "assignment": {
          "type": "RESOLVER",
          "resolver": "projectPartner"
        }
      }
    },

    {
      "key": "CHANGES_REQUESTED",
      "type": "STATE",
      "name": "Changes Requested"
    },

    {
      "key": "APPROVED",
      "type": "FINAL",
      "name": "Approved"
    },

    {
      "key": "CANCELLED",
      "type": "FINAL",
      "name": "Cancelled"
    }
  ],

  "transitions": [
    {
      "key": "SUBMIT",
      "name": "Submit",
      "from": "DRAFT",
      "to": "MANAGER_REVIEW",
      "permission": "PLANNING_SUBMIT",
      "guards": [
        "planningComplete",
        "noBlockingConflicts"
      ],
      "actions": [
        "capturePlanningApprovalData"
      ],
      "events": [
        "planningSubmitted"
      ]
    },

    {
      "key": "APPROVE",
      "name": "Approve",
      "from": "MANAGER_REVIEW",
      "to": "APPROVAL_ROUTE",
      "permission": "PLANNING_APPROVE"
    },

    {
      "key": "REQUEST_CHANGES",
      "name": "Request changes",
      "from": "MANAGER_REVIEW",
      "to": "CHANGES_REQUESTED",
      "permission": "PLANNING_APPROVE",
      "requiresComment": true,
      "events": [
        "planningChangesRequested"
      ]
    },

    {
      "key": "RESUBMIT",
      "name": "Resubmit",
      "from": "CHANGES_REQUESTED",
      "to": "MANAGER_REVIEW",
      "permission": "PLANNING_SUBMIT",
      "guards": [
        "planningComplete",
        "noBlockingConflicts"
      ]
    },

    {
      "key": "PARTNER_REQUIRED",
      "from": "APPROVAL_ROUTE",
      "to": "PARTNER_REVIEW"
    },

    {
      "key": "DIRECT_APPROVAL",
      "from": "APPROVAL_ROUTE",
      "to": "APPROVED"
    },

    {
      "key": "APPROVE",
      "name": "Approve",
      "from": "PARTNER_REVIEW",
      "to": "APPROVED",
      "permission": "PLANNING_PARTNER_APPROVE",
      "actions": [
        "approvePlanning"
      ],
      "events": [
        "planningApproved"
      ]
    },

    {
      "key": "CANCEL",
      "name": "Cancel",
      "from": "DRAFT",
      "to": "CANCELLED",
      "permission": "PLANNING_CANCEL"
    }
  ]
}
```

---

# 39. Graphical Workflow Editor

The graph representation naturally supports a graphical Angular editor.

Mapping:

```text
Workflow JSON             Visual representation
────────────────────────────────────────────────
STATE                     rectangular node
DECISION                  decision/diamond node
FINAL                     terminal node
TRANSITION                directed edge
condition                 edge property
task                      node property
guard                     transition property
action                    transition property
```

Example:

```text
[DRAFT]
   │
 Submit
   ▼
[MANAGER REVIEW]
   │
 Approve
   ▼
<APPROVAL ROUTE>
   │
   ├──── Partner required ────► [PARTNER REVIEW]
   │                                  │
   │                               Approve
   │                                  │
   └──── Direct approval ──────────────┤
                                      ▼
                                  [APPROVED]
```

---

# 40. Single Canonical Model for Editor and Runtime

The graphical designer should edit the same JSON model consumed by the runtime.

Architecture:

```text
Angular Workflow Designer
          │
          ▼
WorkflowDefinition
TypeScript model
          │
          ▼
JSON
          │
          ▼
REST
          │
          ▼
SQL Server
          │
          ▼
Jackson
          │
          ▼
Java WorkflowDefinition
          │
          ▼
WorkflowEngine
```

There should not be separate "designer workflow" and "runtime workflow" formats.

---

# 41. Designer Layout Information

Visual layout must remain separate from workflow semantics.

The JSON may contain an optional editor section:

```json
"designer": {
  "nodes": {
    "DRAFT": {
      "x": 100,
      "y": 200
    },
    "MANAGER_REVIEW": {
      "x": 400,
      "y": 200
    }
  }
}
```

The runtime engine completely ignores the `designer` section.

Changing the visual layout therefore does not modify business semantics.

---

# 42. Visual and JSON Editing Modes

The editor should eventually support:

```text
[ Designer ] [ JSON ]
```

Both modes manipulate the same definition.

```text
Visual edit
    │
    ▼
JSON changes
    │
    ▼
graph redraw

JSON edit
    │
    ▼
model changes
    │
    ▼
graph redraw
```

This is particularly useful for developer workflows and AI-assisted implementation.

---

# 43. Editor Validation

The Angular editor should provide immediate validation, while backend validation remains authoritative.

For example, when configuring a decision node the editor can ensure:

- at least two outgoing routes;
- at most one default route;
- valid expressions;
- valid decision-table transition results;
- no human task directly on the decision node.

Selecting a node or edge should open a properties panel.

State properties:

```text
Name
Type
Task
Assignment
Due date
Description
```

Transition properties:

```text
Name
Permission
Guards
Actions
Events
Requires comment
```

Decision properties:

```text
Decision type
SpEL condition
Decision table
Hit policy
Default branch
```

---

# 44. REST API Direction

Typical runtime endpoints could include:

```text
GET  /api/workflows/instances/{id}
POST /api/workflows/instances/{id}/actions/{action}
GET  /api/workflows/instances/{id}/history
GET  /api/workflows/tasks
```

Definition management:

```text
GET  /api/workflow-definitions
POST /api/workflow-definitions

GET  /api/workflow-definitions/{key}/versions/{version}
POST /api/workflow-definitions/{key}/versions

POST /api/workflow-definitions/{key}/versions/{version}/validate
POST /api/workflow-definitions/{key}/versions/{version}/publish
```

The frontend should not reproduce workflow logic.

For example:

```text
GET /workflow-instance/8127
```

could return:

```json
{
  "state": {
    "key": "MANAGER_REVIEW",
    "name": "Manager Review"
  },

  "availableActions": [
    {
      "key": "APPROVE",
      "name": "Approve",
      "available": true
    },
    {
      "key": "REQUEST_CHANGES",
      "name": "Request changes",
      "available": true,
      "requiresComment": true
    }
  ]
}
```

Angular renders what the workflow service reports rather than embedding state/permission rules itself.

---

# 45. Security

Authentication remains outside the workflow engine.

Conceptually:

```text
Authentication
      │
      ▼
existing APOLO identity mechanism

Authorization
      │
      ▼
APOLO permission layer

Workflow assignment
      │
      ▼
Workflow engine
```

Workflow definitions reference business permissions:

```json
"permission": "PLANNING_APPROVE"
```

The engine delegates permission evaluation to the existing APOLO authorization mechanism.

Expressions must execute inside a restricted evaluation context.

They must not permit:

- arbitrary Spring bean access;
- arbitrary static type access;
- filesystem access;
- process execution;
- side effects;
- mutation of domain entities.

---

# 46. Suggested Java Module Structure

A possible structure:

```text
workflow
│
├── definition
│   ├── WorkflowDefinition
│   ├── WorkflowStateDefinition
│   ├── WorkflowTransitionDefinition
│   ├── WorkflowVariableDefinition
│   └── validation
│
├── runtime
│   ├── WorkflowEngine
│   ├── WorkflowContext
│   ├── WorkflowInstance
│   └── WorkflowCommand
│
├── decision
│   ├── WorkflowExpressionEvaluator
│   ├── DecisionTableEvaluator
│   └── DecisionResolver
│
├── guard
│   ├── WorkflowGuard
│   └── WorkflowGuardRegistry
│
├── action
│   ├── WorkflowAction
│   └── WorkflowActionRegistry
│
├── task
│   ├── WorkflowTaskService
│   └── WorkflowAssignmentResolver
│
├── persistence
│   ├── WorkflowInstanceRepository
│   ├── WorkflowDefinitionRepository
│   └── WorkflowDefinitionLoader
│
└── history
    └── WorkflowHistoryService
```

APOLO domain modules implement extensions such as:

```text
planning
├── PlanningCompleteGuard
├── NoBlockingConflictsGuard
├── ApprovePlanningAction
├── ProjectManagerAssignmentResolver
└── ProjectPartnerAssignmentResolver
```

---

# 47. Features Deliberately Excluded from V1

The first version should not attempt to implement:

- BPMN XML;
- BPMN compatibility;
- arbitrary scripting;
- parallel gateways;
- fork/join execution;
- multiple simultaneous human tasks;
- subprocesses;
- compensation;
- arbitrary external workers;
- distributed workflow execution;
- workflow-definition migration for running instances;
- arbitrary object-valued variables;
- embedded forms;
- signal/message correlation;
- event subprocesses.

These should be introduced only when a real APOLO requirement justifies the additional complexity.

---

# 48. Proposed Development Phases

## Phase 1 – Core workflow graph

Implement:

```text
JSON definitions
definition versioning
STATE
DECISION
FINAL
transitions
SpEL expressions
decision tables
workflow instances
history
workflow variables
```

Use one real APOLO process as the validation case.

Planning approval is a suitable candidate.

---

## Phase 2 – Human tasks

Add:

```text
workflow_task
assignment resolvers
My Tasks
task completion
task cancellation
due dates
```

---

## Phase 3 – Generic workflow UI

Build reusable Angular components for:

```text
current workflow status
available actions
workflow history
task information
decision history
```

---

## Phase 4 – Graphical workflow designer

Implement:

```text
drag/drop nodes
connections
state editor
decision editor
transition editor
decision-table editor
JSON view
validation
layout persistence
```

The designer works against the canonical JSON model.

---

## Phase 5 – Operational features

Possible additions:

```text
timers
reminders
escalations
workflow administration
overdue-task monitoring
metrics
workflow instance search
```

---

## Phase 6 – Advanced orchestration

Only if required:

```text
parallel branches
join nodes
wait states
external events
sub-workflows
workflow migration
advanced decision policies
```

---

# 49. Architecture Summary

The proposed APOLO workflow architecture can be summarized as:

```text
                         APOLO

                   Angular application
                          │
             ┌────────────┴────────────┐
             │                         │
       Workflow UI              Workflow Designer
             │                         │
             └────────────┬────────────┘
                          │ JSON / REST
                          ▼
                  Workflow Services
                          │
                          ▼
                  Workflow Engine
                          │
         ┌────────────────┼────────────────┐
         │                │                │
         ▼                ▼                ▼
      Guards          Decisions         Actions
                         │
                  SpEL / Decision
                      Tables
                          │
                          ▼
                    APOLO Domain
                          │
             ┌────────────┼────────────┐
             ▼            ▼            ▼
          Planning      Budget       Project

                          │
                          ▼

                     SQL Server

          ┌─────────────────────────────┐
          │ Workflow definitions        │
          │ immutable JSON              │
          ├─────────────────────────────┤
          │ Workflow instances          │
          │ relational                  │
          ├─────────────────────────────┤
          │ Variables / Tasks / History │
          │ relational                  │
          └─────────────────────────────┘
```

---

# 50. Key Architectural Decisions

The current design establishes the following decisions:

1. APOLO will implement a focused in-house workflow engine rather than a complete BPMN engine.

2. Workflow definitions use a custom, strongly typed JSON DSL.

3. Microsoft SQL Server remains the sole persistence platform.

4. Only workflow definitions are stored as JSON.

5. Workflow runtime information remains relational.

6. Published workflow definitions are immutable and versioned.

7. Each workflow instance references an exact definition version.

8. Workflow definitions are initially loaded from SQL Server on demand rather than globally cached.

9. States and transitions form a directed graph.

10. Initial node types are `STATE`, `DECISION`, and `FINAL`.

11. Decision gates are first-class automatic workflow nodes.

12. Decision routing supports SpEL expressions.

13. Existing APOLO decision-table concepts will be reused.

14. Decision tables return transition keys rather than destination states.

15. Workflow variables contain process-specific data and historical snapshots, not copies of APOLO domain objects.

16. Business data remains owned by APOLO domain modules.

17. Guards, actions and assignment resolvers are implemented as registered Java extensions.

18. The workflow engine does not contain APOLO business logic.

19. A future graphical designer operates directly on the same canonical JSON definition used by the runtime.

20. The first implementation prioritizes correctness, auditability and maintainability over advanced orchestration features.

---

# 51. Recommended Next Design Step

The architecture is sufficiently defined to begin refining individual components.

Before implementation, the next useful design exercise is to formally define **Workflow JSON DSL v1** as a schema.

This should specify:

```text
WorkflowDefinition
WorkflowVariableDefinition
WorkflowStateDefinition
WorkflowTaskDefinition
WorkflowTransitionDefinition
DecisionDefinition
DecisionTableDefinition
AssignmentDefinition
```

including:

- mandatory fields;
- optional fields;
- enumerations;
- naming constraints;
- semantic validation rules;
- JSON Schema;
- corresponding Java records/classes;
- corresponding TypeScript interfaces.

Once that contract is stable, the three major parts of the system can be implemented independently against it:

```text
Workflow Engine
Workflow Designer
Workflow Definition API
```

That JSON contract should therefore be considered the central interface of the APOLO workflow subsystem.
