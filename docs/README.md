# Project constitution and specification guide

This folder governs development of the workflow project through specifications that define observable behavior before implementation.

Start with the [roadmap](roadmap.md) for delivery priorities, dependencies and milestone exit conditions, and the [feature index](features/README.md) for implementation status. Detailed acceptance evidence stays in each feature's implementation record.

Read these documents in order:

1. [Project constitution](constitution.md): enduring architectural principles, required guarantees, scope and amendment rules.
2. [Constraints](constraints.md): confirmed architectural, delivery and current working boundaries, with stable CON identifiers.
3. [Assumptions](assumptions.md): unverified expectations, responsible roles, required evidence and consequences if an expectation fails, with stable ASM identifiers.
4. [Specification-driven development](specification-driven-development.md): how to turn a requirement into a specification, implementation plan, tasks and recorded evidence.
5. [Design sources and decision status](design-sources.md): provenance, unresolved decisions and the distinction between agreed design and implemented behavior.
6. [Coding standards](coding-standards.md): practical clean-code guidance for Java, Spring, JPA and Lombok, applied during implementation and review.
7. [Package structure](package-structure.md): agreed technical-layer-first Java hierarchy, dependency boundaries and exact F002 package assignments.

The constitution is version **6.0.0**, established on **2026-10-24** and last amended on **2026-11-07** to adopt reusable workflow-web and Angular 19 workflow-ui libraries. The agreed target is core/web/test-host Maven modules plus the Angular library; implementation has not begun. Earlier F009 compatibility decisions and runtime guarantees remain unchanged. Earlier feature records retain their historical constitution baselines and acceptance evidence.

## Document responsibilities

The [workflow UI and REST integration record](architecture/workflow-ui-web-integration.md) records the agreed Angular 19 target and adopted `workflow-ui`/`workflow-web` boundaries, consolidated in main design revision 0.11. This is documentation only; implemented modules/dependencies and F011 remain unchanged.

The constitution defines constraints that all feature specifications must respect. The [consolidated design](architecture/workflow-engine-design.md) remains the detailed architectural baseline, with scoped superseding decisions from [database design v1.4](architecture/Workflow_Engine_Database_Design_v1.md) documented in [design sources](design-sources.md). Active engine designs live in `docs/architecture/`, including the proposed [workflow definition language](architecture/workflow-definition-language-design.md). Feature specifications will define exact behavior within those constraints. Plans describe the intended approach; tasks track execution; implementation records what was built and actually checked.

The constraints register makes confirmed boundaries easy to reference. The assumptions register identifies expectations that need verification and keeps provisional business choices distinct from mandatory guarantees. Both derive from the existing design; they do not finalize its open contracts or change the constitution version.

New explicit owner decisions take precedence over older documentation. Record their effect in the relevant documents. Where documents conflict and no superseding decision exists, expose the conflict and resolve it with the owner rather than silently choosing a new architecture.

## Documentation and feature structure

Project-wide guidance lives in `docs/`. Individual feature specifications live in its [features subfolder](features/README.md). Create a feature folder when its content is being written:

```text
workflow-engine/
  docs/
    README.md
    roadmap.md
    constitution.md
    constraints.md
    assumptions.md
    specification-driven-development.md
    design-sources.md
    coding-standards.md
    architecture/
      workflow-engine-design.md
      Workflow_Engine_Database_Design_v1.md
      workflow-definition-language-design.md
    integrations/
      apolo/         # business requirements and host references
    archive/         # historical discussions and design provenance
    features/
      README.md
      <feature-id>-<name>/
        spec.md
        plan.md
        tasks.md
        implementation.md
        schemas/       # when machine-readable contracts are needed
        examples/      # when concrete contract examples are needed
```

F001–F006 are implemented within their documented scopes, including minimal SINGLE execution and the revision 5 console. Implementation does not imply complete acceptance: F005 T11 and F006 browser/live-check gaps remain open. Use the [feature index](features/README.md) and linked handovers for current details, rather than treating architecture examples as delivered capabilities.

Build and local startup instructions remain in the [project README](../README.md). Isolated unit tests and integration tests on the dedicated local `apolo_workflow_test` database are authorized under [AGENTS.md](../AGENTS.md); see [database testing](testing/database-integration-tests.md). Other targets require task-specific authorization. See the [verification policy](specification-driven-development.md#verification-and-current-no-tests-policy).
