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
8. [Frontend style guide](frontend-style-guide.md): Material 3, local icons, host-owned styling and focused visual review for shared UI and sandbox frontend work.

The constitution is version **6.6.1**, established on **2026-10-24** and last amended on **2026-11-14** to reconcile its body with adopted decisions and delivery through F022, without adding scope. The core/web/test-host Maven modules and Angular library are implemented; APOLO integration remains separate. Earlier feature records retain their historical constitution baselines and acceptance evidence; later scoped decisions supersede earlier contracts where explicitly documented.

## Document responsibilities

The [workflow UI and REST integration record](architecture/workflow-ui-web-integration.md) preserves the Angular 19 target and adopted `workflow-ui`/`workflow-web` boundaries, now reflected in the [current consolidated design](architecture/workflow-engine-design.md). The original adoption authorized documentation only. F012 subsequently delivered Angular alignment, and F013–F015 delivered the shared libraries and viewer, administration and runtime extraction. APOLO integration remains separate; F011 retains its historical scope and acceptance evidence.

The constitution defines constraints that all feature specifications must respect. The [consolidated design](architecture/workflow-engine-design.md) remains the detailed architectural baseline, with scoped superseding decisions from [database design v1.5](architecture/workflow-engine-database-design.md) documented in [design sources](design-sources.md). Active engine designs live in `docs/architecture/`, including the [workflow definition language](architecture/workflow-definition-language-design.md), which distinguishes agreed and implemented contracts from remaining proposals and deferred scope. Feature specifications define exact behavior within those constraints. Plans describe the intended approach; tasks track execution; implementation records what was built and actually checked.

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
    package-structure.md
    frontend-style-guide.md
    architecture/
      workflow-engine-design.md
      workflow-engine-database-design.md
      workflow-definition-language-design.md
      workflow-ui-web-integration.md
    testing/
      database-integration-tests.md
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

Delivery extends through F022 previous-decision comments, including shared web/UI libraries and ALL collective review. Implementation and scoped verification do not imply production acceptance. Historical gaps remain owned by their feature records; this summary closes no acceptance gate. APOLO integration and the later designer remain separate work. Use the [feature index](features/README.md) and linked handovers for current details, rather than treating architecture examples as delivered capabilities.

Build and local startup instructions remain in the [project README](../README.md). Isolated unit tests and integration tests on the dedicated local `apolo_workflow_test` database are authorized under [AGENTS.md](../AGENTS.md); see [database testing](testing/database-integration-tests.md). Other targets require task-specific authorization. See the [verification policy](specification-driven-development.md#verification-and-current-no-tests-policy).
