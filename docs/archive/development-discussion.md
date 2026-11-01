# Historical development discussion

> **ARCHIVED — extracted 2026-10-24.** This preserves the earlier discussion from the development plan. Use the [current roadmap](../roadmap.md) and [authoritative design](../architecture/workflow-engine-design.md) for implementation.

Earlier suggestions about persistence inside workflow-core, optional durability, manual registry administration as a selected default and deferred host compatibility checks are superseded where they conflict with current decisions. Java 17 is the compilation target; JDK 21 is the runtime.

## Preserved discussion

That requirement makes a separate Maven module more reasonable. However, I would avoid creating a full dummy Apolo project because it could quickly diverge from the real application.

Recommended approach

Develop workflow as an independent Maven project or repository:

workflow/
├── pom.xml
├── README.md
└── src/
    ├── main/java/
    └── test/java/


Then integrate the resulting artifact into Apolo:

apolo/
├── backend/
├── configuration/
├── frontend/
├── workflow/       # checked out or included during integration
└── pom.xml


The backend module would depend on workflow, while workflow should ideally not depend on backend.

backend → workflow
workflow → shared contracts/libraries


Avoid this dependency direction:

workflow → backend


Otherwise, developing workflow separately becomes difficult because the workflow module needs much of Apolo to compile.

Instead of a dummy Apolo project

Create a small workflow test host or example application in the workflow repository:

workflow-project/
├── workflow-core/
├── workflow-test-host/
└── pom.xml

workflow-core

Contains the actual reusable functionality:

Workflow domain model
Workflow execution logic
Interfaces required from the host application
Events and commands
Validation
Unit tests
workflow-test-host

A minimal Spring Boot application used only for local development:

Test configuration
In-memory or containerized database
Dummy implementations of Apolo interfaces
Sample workflows and test data
Development endpoints, if needed
Integration tests

This gives you an executable environment without pretending to reproduce the full Apolo application.

Define integration contracts

If workflow needs information or operations from Apolo, expose those requirements as interfaces owned by the workflow module.

For example:

public interface WorkflowUserProvider {
    WorkflowUser findUser(String userId);
}


The test host provides a dummy implementation:

@Component
public class DummyWorkflowUserProvider implements WorkflowUserProvider {
    // Return development test data
}


The real Apolo backend provides the production implementation:

@Component
public class ApoloWorkflowUserProvider implements WorkflowUserProvider {
    // Retrieve the user through Apolo services
}


This follows a ports-and-adapters approach:

workflow-core
    └── declares required interfaces

workflow-test-host
    └── supplies dummy adapters

apolo-backend
    └── supplies production adapters

How to integrate the module

You have several options.

1. Same repository after development

Develop it separately initially, then place it under:

apolo/workflow


Register it in the root POM:

<modules>
    <module>frontend</module>
    <module>backend</module>
    <module>configuration</module>
    <module>workflow</module>
</modules>


This is straightforward but eventually brings development back into the Apolo repository.

2. Separate repository and published Maven artifact

The workflow pipeline publishes an artifact such as:

<dependency>
    <groupId>com.kpmg.apolo</groupId>
    <artifactId>workflow-core</artifactId>
    <version>${workflow.version}</version>
</dependency>


This provides the strongest separation and allows both machines to work independently. It requires an internal Maven repository, package registry, or another artifact publishing mechanism.

3. Separate repository included as a Git submodule

The workflow repository can be attached under Apolo:

apolo/
└── workflow/  # Git submodule


This preserves separate source control while allowing Apolo’s root Maven build to include the module. However, Git submodules add operational overhead around cloning, branch alignment, and updates.

My recommendation

Use:

workflow-project/
├── workflow-core/
└── workflow-test-host/


Then publish workflow-core as a Maven artifact and consume it from backend.

The test host replaces the need for a dummy Apolo project. It should simulate only the integration points required by workflow, not duplicate Apolo’s business logic.

The key architectural rule is:

workflow-core must define clear integration contracts and remain independent of Apolo backend implementation classes.

That gives you independent development, realistic local testing, and a much cleaner future integration path.

Yes, ideally use the same Spring Boot version as Apolo: 3.4.5, especially for the workflow test application.

Recommended setup
workflow-core

Keep the main workflow logic as independent from Spring Boot as practical:

Java 17
Plain Java domain and service classes
Minimal Spring dependencies
No embedded server or application-specific configuration

It can inherit version management from the project parent, but it does not necessarily need to be a Spring Boot application itself.

workflow-test-host

Use the same environment as Apolo:

Spring Boot 3.4.5
Java 17
Compatible Hibernate version
Same database driver
Same relevant security and integration libraries

This provides a realistic environment for testing workflow integration before adding it to Apolo.

Suggested structure
workflow-project/
├── pom.xml
├── workflow-core/
│   ├── pom.xml
│   └── src/
└── workflow-test-host/
    ├── pom.xml
    └── src/


The workflow project parent could use:

<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.4.5</version>
    <relativePath/>
</parent>

<properties>
    <java.version>17</java.version>
</properties>


Then both modules inherit the same dependency management.

Why matching versions matters

Matching Spring Boot versions reduces the risk of differences in:

Spring Framework APIs
Dependency versions managed by Spring Boot
Hibernate and Jakarta Persistence behavior
Jackson serialization
Spring Security configuration
Auto-configuration
Testing libraries
Logging and monitoring integrations

For example, developing the workflow host against a newer Spring Boot version might work independently but fail when loaded into Apolo due to incompatible transitive dependencies.

Important distinction

If workflow-core is eventually consumed as a library by backend, avoid packaging workflow-core as an executable Spring Boot JAR. It should produce a normal library JAR:

<packaging>jar</packaging>


The executable Spring Boot application should be workflow-test-host, not workflow-core.

The final relationship should be:

workflow-test-host → workflow-core
apolo-backend      → workflow-core


The two host applications use the same workflow library:

                 ┌─ workflow-test-host
workflow-core ───┤
                 └─ apolo-backend

Recommendation

Use Spring Boot 3.4.5 and Java 17 for the separate development environment. Keep the core workflow functionality as a regular library and create a small Spring Boot test host using the same versions as Apolo. This gives you compatibility without needing to duplicate the complete Apolo project.

Actually the runtime is jdk 21 version.

Yes. If the workflow component owns persistent data, it should also own the Liquibase changelogs for its tables. The important question is not whether to use Liquibase, but which application is responsible for executing those migrations.

Recommended architecture
workflow-project/
├── workflow-core/
│   ├── src/main/java/
│   └── src/main/resources/
│       └── db/changelog/workflow/
│           ├── workflow-master.xml
│           ├── 001-create-workflow-instance.xml
│           ├── 002-create-workflow-task.xml
│           └── 003-add-workflow-indexes.xml
│
└── workflow-test-host/
    ├── src/main/java/
    └── src/main/resources/
        └── application.yml


The responsibilities would be:

workflow-core

Workflow entities and repositories
Persistence logic
Liquibase changelogs for workflow-owned tables
No production database credentials

workflow-test-host

Starts the workflow during independent development
Provides a local database configuration
Executes the workflow changelogs locally

Apolo backend

Uses the workflow library
Includes the workflow changelog from Apolo’s main changelog
Executes all production migrations through Apolo’s existing Liquibase process

Spring Boot can automatically execute embedded Liquibase changelogs during application startup, so keeping changelogs in a library JAR is compatible with this model.

Integrating with Apolo’s existing changelog

For example, the workflow module could contain:

<!-- workflow-core/src/main/resources/db/changelog/workflow/workflow-master.xml -->

<databaseChangeLog
        xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="
            http://www.liquibase.org/xml/ns/dbchangelog
            https://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">

    <include file="db/changelog/workflow/001-create-workflow-instance.xml"/>
    <include file="db/changelog/workflow/002-create-workflow-task.xml"/>
    <include file="db/changelog/workflow/003-add-workflow-indexes.xml"/>

</databaseChangeLog>


Then Apolo’s existing master changelog would include the workflow master:

<include file="db/changelog/workflow/workflow-master.xml"/>


A master changelog that includes component-specific changelogs is a common way to organize Liquibase migrations.

The resulting hierarchy becomes:

Apolo master changelog
├── existing Apolo changelogs
└── workflow-master.xml
    ├── workflow table creation
    ├── workflow indexes
    └── future workflow migrations

One Liquibase execution owner

In the integrated Apolo environment, I recommend having one Liquibase execution controlled by Apolo.

Avoid having both Apolo and an independently configured workflow component automatically execute migrations against the same database during startup. That can make migration order, locking, troubleshooting, and deployment ownership less clear.

The intended behavior should be:

Independent development:
workflow-test-host → runs workflow changelogs

Integrated Apolo:
Apolo backend → runs Apolo and workflow changelogs


The workflow-core library contains the migrations, but does not independently start Liquibase. The host application decides when Liquibase runs.

Persistence ownership

The workflow module should own its persistence-related code:

workflow-core/
└── src/main/java/com/kpmg/apolo/workflow/
    ├── domain/
    ├── persistence/
    │   ├── entity/
    │   └── repository/
    ├── service/
    └── api/


It should also own tables with distinctive names, for example:

workflow_definition
workflow_instance
workflow_task
workflow_transition
workflow_variable
workflow_event


Or, if Apolo uses table prefixes:

wf_definition
wf_instance
wf_task
wf_transition
wf_variable
wf_event


This makes table ownership obvious and reduces naming conflicts.

Local database for independent development

For simple development, the test host can use H2. However, if Apolo uses PostgreSQL, Oracle, SQL Server, or another production database, I recommend using the same database engine in a local container.

H2 is useful for:

Quick repository tests
Basic CRUD tests
Fast application startup

The real database engine is better for:

Column type compatibility
Sequences and identity behavior
Index definitions
Constraints
Native queries
JSON columns
Date and timestamp behavior
Locking and transaction behavior

A practical combination is:

Unit/repository tests      → H2 when compatible
Local integration testing → containerized Apolo database engine
Production integration    → actual Apolo database

Dependency setup

The workflow library will likely need:

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>

<dependency>
    <groupId>org.liquibase</groupId>
    <artifactId>liquibase-core</artifactId>
</dependency>


The database driver can usually be owned by the host application rather than workflow-core:

workflow-core       → JPA + Liquibase changelogs
workflow-test-host  → database driver + connection settings
apolo-backend       → production database driver + connection settings


Let Spring Boot 3.4.5 manage the Liquibase and JPA dependency versions unless Apolo explicitly overrides them. This lowers the risk of the separate project using a different dependency combination.

Important Liquibase rules
Use globally unique change-set identifiers

For example:

<changeSet id="workflow-001-create-instance" author="cleonte">


Do not use generic identifiers such as only 1, 2, or 3, because the changelogs will eventually run together with Apolo migrations.

Never modify an executed change set

Once integrated and executed in a shared environment, create a new change set instead of changing the original one. Liquibase records executed changesets and their checksums in its tracking tables.

Define migration order explicitly

Do not depend only on automatic filename discovery unless Apolo already has a well-defined convention. Explicit <include> entries make the order visible and predictable.

Treat JPA and Liquibase differently
JPA entities represent the application model.
Liquibase changelogs represent the database history.

In non-development environments, prefer:

spring:
  jpa:
    hibernate:
      ddl-auto: validate


Liquibase creates and changes the schema, while Hibernate validates that the entities match it.

Recommendation for Apolo

Create a separate workflow project with:

workflow-project/
├── workflow-core
└── workflow-test-host


Keep the following inside workflow-core:

Entities
Repositories
Workflow services
Workflow-specific changelogs
Integration interfaces

Use workflow-test-host to run those migrations against a disposable local database. When integrating with Apolo, include the workflow master changelog in Apolo’s existing Liquibase master and let Apolo remain the single production migration owner.

This gives the workflow module ownership of its schema without creating a second competing database migration process.


This uploaded pom.xml is the backend module POM, and it confirms that backend is a large host application with many infrastructure responsibilities. The main boundary risk is that a new workflow library could accidentally become coupled to this entire dependency stack.

Main boundary concerns
1. Workflow must not depend on backend

The dependency direction should be:

backend → workflow-core


Not:

workflow-core → backend


Because backend is a WAR containing security, Keycloak, messaging, email, batch, caching, metrics, web frameworks, database access, and frontend packaging, depending on it would make independent workflow development almost impossible.

If workflow needs something from Apolo, define a small interface in workflow-core, or introduce a neutral contracts module:

apolo-contracts
├── user references
├── tenant references
├── permission contracts
└── workflow integration DTOs

workflow-core → apolo-contracts
backend       → apolo-contracts
backend       → workflow-core


Avoid placing JPA entities in the contracts module.

2. Liquibase execution belongs to the host

The backend includes both liquibase-core and the Microsoft SQL Server JDBC driver, so the backend currently acts as the database migration host.

Recommended ownership:

workflow-core
└── owns workflow changelog files

workflow-test-host
└── executes workflow changelogs during separate development

apolo-backend
└── executes Apolo + workflow changelogs in integrated environments


workflow-core may contain changelog resources, but it should not automatically create a separate SpringLiquibase bean. Otherwise, Apolo could end up with multiple competing migration configurations.

Also, keep the SQL Server driver out of workflow-core. The hosts should supply the driver.

3. JPA creates a potentially strong coupling

The backend directly contains:

Spring Data JPA
Hibernate
Spring JDBC
SQL Server driver
Liquibase

The workflow module can use Spring Data JPA, but its entities should be self-contained.

Prefer:

@Entity
class WorkflowInstance {
    private String initiatedByUserId;
}


Avoid:

@Entity
class WorkflowInstance {
    @ManyToOne
    private ApoloUser initiatedBy;
}


The second version makes workflow-core depend on an Apolo-owned entity and persistence model.

For independently developed modules, store external references as IDs:

initiated_by_user_id
business_object_type
business_object_id
tenant_id


Let backend adapters resolve those IDs into Apolo domain objects.

4. Database foreign keys can cross the module boundary

Even without Java dependencies, workflow tables could become tightly coupled through database foreign keys.

Potentially problematic:

workflow_instance.created_by
    → apolo_user.id


This means the workflow schema cannot be created or tested without the Apolo user tables.

For genuine independence, consider keeping the user ID as a logical reference without a database-level foreign key. For references between workflow-owned tables, normal foreign keys are strongly recommended:

workflow_task.workflow_instance_id
    → workflow_instance.id


A good rule is:

Foreign keys within workflow-owned tables: yes
Foreign keys from workflow tables to Apolo-owned tables: evaluate carefully
Workflow JPA relationships to Apolo entities: avoid
5. Spring MVC and WebFlux are both present

The backend includes both:

spring-boot-starter-web
spring-boot-starter-webflux

This may be intentional if WebClient is needed, but it creates an architectural ambiguity:

Is Apolo fundamentally servlet-based?
Does workflow expose MVC endpoints?
Is workflow expected to be reactive?
Is WebFlux present only for its HTTP client?

The workflow library should not decide this. Keep web controllers and reactive infrastructure out of workflow-core unless they are genuinely part of its reusable API.

A cleaner division could be:

workflow-core
workflow-persistence-jpa
workflow-spring-adapter
workflow-test-host


For a smaller initial implementation, workflow-core can contain persistence, but it should still avoid both web starters.

6. Security and Keycloak are heavily embedded in backend

The backend contains:

Spring Security
OAuth2 client
OAuth2 resource server
Keycloak admin client
Keycloak authorization client
Keycloak server-related dependencies
Keycloak UI dependencies
RESTEasy dependencies

This is probably the largest potential workflow boundary trap.

workflow-core should not accept or return:

Keycloak-specific user classes
Spring Security Authentication
JWT implementation objects
Keycloak realm or session types
Keycloak admin client representations

Prefer a workflow-owned execution context:

public record WorkflowActor(
        String userId,
        String tenantId,
        Set<String> roles
) {}


The Apolo backend adapter translates the current Spring Security or Keycloak identity into WorkflowActor.

This keeps:

Keycloak/Spring Security → Apolo adapter → WorkflowActor


instead of:

workflow-core → Spring Security → Keycloak

7. Messaging should be behind a workflow port

The backend includes the Artemis Spring Boot starter and artemis-jms-server, with an Artemis BOM pinned to 2.41.0.

Workflow should not directly require an embedded Artemis server unless Artemis is formally part of the workflow product.

A better abstraction is:

public interface WorkflowEventPublisher {
    void publish(WorkflowEvent event);
}


Then:

workflow-core
└── declares WorkflowEventPublisher

workflow-test-host
└── uses in-memory publisher

apolo-backend
└── uses Artemis/JMS publisher


This allows independent development without installing or embedding Artemis.

8. Mail and Thymeleaf should remain host responsibilities

The backend has Spring Mail and Thymeleaf, likely for email notifications and templates.

Workflow should ideally produce a notification intent:

WorkflowNotificationRequested


It should not directly:

Build Apolo email templates
Resolve recipient email addresses
Use JavaMailSender
Depend on Apolo’s branding
Use backend-specific Thymeleaf templates

The backend should translate workflow notification events into actual emails.

9. Metrics must not tie workflow to Prometheus

The backend depends directly on micrometer-registry-prometheus.

If workflow produces metrics, it should depend at most on the Micrometer observation or core API, not the Prometheus registry:

workflow-core → Micrometer API, only if required
backend       → Prometheus registry


Prometheus is an operational choice made by the host application.

There is also a likely dependency-management concern: the parent pins micrometer.version to 1.12.2, while Spring Boot already manages its compatible Micrometer family. A manually pinned older Micrometer line can create mixed-version dependencies. This should be checked with:

mvn dependency:tree -Dincludes=io.micrometer

10. Caching should be abstracted or avoided in workflow-core

The backend has:

Spring Cache
Ehcache
JCache javax.cache:cache-api

Workflow should not expose Ehcache or JCache types in its public API. If workflow needs caching, either:

Use Spring Cache annotations internally and let the host configure the provider, or
Define a workflow-specific cache interface.

Be careful with javax.cache because the project otherwise uses the modern Jakarta-based Spring Boot 3 ecosystem. Even when technically functional, it introduces another legacy API boundary.

11. Hibernate is directly overridden

The backend uses Spring Data JPA but also explicitly declares hibernate-core at ${compatible.hibernate.version}, currently 6.6.8.Final.

That means the application is not fully relying on Spring Boot’s tested dependency set. The explicit version may be necessary, but it creates a compatibility responsibility for:

Spring Data JPA
Hibernate ORM
Jakarta Persistence
Liquibase
Entity mapping behavior

The workflow project should not independently choose another Hibernate version. Ideally:

workflow-core should not declare a direct Hibernate implementation dependency.
It should use the JPA and Spring Data dependencies managed by the parent.
The host application should provide the Hibernate implementation.
12. Keycloak server dependencies leak into the application host

Dependencies such as keycloak-dependencies-server-all, keycloak-admin-ui, keycloak-rest-admin-ui-ext, and the Spring Boot Maven plugin’s Keycloak unpacking configuration indicate that backend is doing more than consuming Keycloak as a normal OAuth2 identity provider.

This leads to extensive version alignment across:

Infinispan
RESTEasy
Micrometer
SnakeYAML
Bouncy Castle
Jandex
Logging

None of those Keycloak-driven version constraints should propagate into workflow unless workflow directly embeds Keycloak functionality, which I would avoid.

Treat Keycloak as entirely behind an Apolo adapter.

13. Backend currently packages frontend

The backend has a runtime dependency on the frontend artifact and produces a WAR named apolo.

This confirms that backend is the composition/deployment module:

frontend ──runtime──→ backend WAR
workflow ──library──→ backend WAR


That is another reason workflow should be a normal library JAR, not another WAR and not a second Spring Boot application in production.

The independent workflow-test-host can be a Spring Boot executable JAR, but it should not be included in Apolo.

Recommended dependency allocation
workflow-core

Start with only what is genuinely required:

<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>

    <dependency>
        <groupId>org.liquibase</groupId>
        <artifactId>liquibase-core</artifactId>
    </dependency>

    <dependency>
        <groupId>org.mapstruct</groupId>
        <artifactId>mapstruct</artifactId>
    </dependency>
</dependencies>


Even liquibase-core may be unnecessary as a compile dependency if the module only packages XML/YAML changelogs. The host can supply Liquibase.

Do not initially add:

SQL Server driver
Keycloak
Spring Security
OAuth2
Prometheus registry
Artemis server
Spring Mail
Thymeleaf
Ehcache
Jasypt
RESTEasy
frontend
Spring Batch
Spring MVC or WebFlux
workflow-test-host

This host can contain:

Spring Boot application starter
Spring Web, only if test endpoints are required
SQL Server driver
Liquibase
Testcontainers or local database configuration
Test security adapter
In-memory event publisher
Dummy user and tenant providers

apolo-backend

Keep application-level integrations here:

Keycloak and Spring Security adapters
Artemis publisher
Prometheus registry
Email and Thymeleaf
SQL Server driver
Liquibase execution
Apolo-specific controllers
Workflow-to-Apolo entity mapping

Suggested final module relationship
                         ┌──────────────────────┐
                         │   apolo-contracts    │
                         │ optional shared DTOs │
                         └──────────┬───────────┘
                                    │
                    ┌───────────────┴───────────────┐
                    │                               │
          ┌─────────▼─────────┐           ┌─────────▼─────────┐
          │   workflow-core   │           │   apolo-backend   │
          │ domain/persistence│◄──────────│ integration host  │
          │ ports/changelogs  │           │ WAR deployment    │
          └─────────▲─────────┘           └───────────────────┘
                    │
          ┌─────────┴─────────┐
          │workflow-test-host │
          │ independent dev   │
          └───────────────────┘

Highest-priority rules
Never reference backend classes from workflow.
Do not reference Apolo JPA entities from workflow entities.
Keep Keycloak, Artemis, Prometheus, mail, and caching implementations in backend adapters.
Let the host execute Liquibase, while workflow owns its changelog files.
Keep SQL Server credentials and the JDBC driver in the host.
Use IDs or workflow-owned value objects when referring to Apolo data.
Package workflow as a normal JAR, not a WAR or production executable application.

Based on this POM, a separate workflow module is justified. The backend is already broad enough that placing workflow directly inside it would make independent development and clean dependency ownership significantly harder.

Human tasks should belong primarily to the workflow module, because a human task is part of the workflow’s execution state. However, authentication, employee data, permissions, and the actual user interface should remain outside the workflow core.

Recommended ownership
Workflow module owns the human-task lifecycle

The workflow component should own:

Creation of a task when a workflow reaches a human step
Task status and lifecycle
Assignment rules
Candidate users or groups
Claiming and releasing tasks
Completion and rejection
Due dates and priorities
Task input and output data
Task history and audit trail
Relationship between a task and its workflow instance
Validation of permitted task transitions

For example:

Workflow instance
    ↓ reaches human-task step
Human task created
    ↓
Candidate user claims task
    ↓
Assigned user completes task
    ↓
Workflow execution continues


Typical workflow-owned tables could be:

wf_task
wf_task_candidate
wf_task_variable
wf_task_action
wf_task_history


These belong with the workflow Liquibase changelogs.

What should remain in Apolo

Apolo should own the meaning of users, groups, organizational structures, and application permissions.

Apolo responsibilities would include:

Retrieving the current authenticated user
Keycloak and Spring Security integration
Looking up users and groups
Determining organization or tenant membership
Mapping Apolo roles to workflow candidate groups
Rendering the task inbox and task forms
Sending email or UI notifications
Applying Apolo-specific visibility restrictions
Enriching task data with user display names and business information

The boundary would be:

Apolo
├── knows users, roles and organizational structures
├── provides the authenticated actor
├── displays task forms and task lists
└── calls workflow operations

Workflow
├── knows task IDs and assignment references
├── controls task lifecycle
├── records task actions
└── advances workflow execution

Avoid Keycloak dependencies in workflow

The workflow module should not store or expose Keycloak-specific objects.

Avoid:

KeycloakPrincipal
AccessToken
Authentication
Jwt
UserRepresentation


Instead, define workflow-owned identity values:

public record WorkflowActor(
        String userId,
        String tenantId,
        Set<String> groupIds
) {}


Apolo converts its authenticated identity into this representation before calling workflow:

Keycloak token
    ↓ Apolo security adapter
WorkflowActor
    ↓
workflow-core


This allows the workflow test host to create a simple dummy actor without running Keycloak.

Assignment model

I recommend supporting three assignment concepts.

1. Assignee

The specific user currently responsible for the task:

assignee_id = "user-123"

2. Candidate users

Specific users allowed to claim the task:

candidate_user_id = "user-456"
candidate_user_id = "user-789"

3. Candidate groups

Logical groups whose members may claim the task:

candidate_group_id = "FINANCE_APPROVERS"


The workflow records the identifiers, but Apolo resolves their meaning.

For example, workflow knows:

candidate group: FINANCE_APPROVERS


Apolo knows:

Which Keycloak role or Apolo group corresponds to FINANCE_APPROVERS?
Is the current user a member?
What display name should be shown?

Two possible authorization models
Model A: Apolo resolves candidates

Apolo asks workflow for tasks matching already resolved user and group IDs:

workflowTaskService.findAvailableTasks(
    actor.userId(),
    actor.groupIds()
);


This keeps workflow independent and is relatively simple.

The workflow query checks:

task.assignee = current user
OR task candidate user = current user
OR task candidate group is in current user's groups


This is my preferred starting approach.

Model B: Workflow calls an identity port

Workflow declares an interface:

public interface WorkflowIdentityProvider {

    boolean isMemberOfGroup(
            String userId,
            String groupId
    );
}


Apolo supplies the production implementation, while the test host supplies a dummy implementation.

This is useful when workflow itself must evaluate assignment rules, but it introduces runtime calls from workflow logic into an external identity provider. It can also make task-list queries less efficient.

Use this only when assignment logic cannot be handled by passing the actor’s group IDs into workflow.

Human-task UI

The UI should normally remain in Apolo frontend, not in workflow-core.

frontend
└── workflow/
    ├── task-inbox
    ├── task-details
    ├── task-history
    └── task-form


The backend exposes Apolo-facing endpoints:

GET  /api/workflow/tasks
GET  /api/workflow/tasks/{taskId}
POST /api/workflow/tasks/{taskId}/claim
POST /api/workflow/tasks/{taskId}/complete
POST /api/workflow/tasks/{taskId}/reject


The controllers can belong in an Apolo workflow adapter package:

backend/
└── workflow/
    ├── api/
    ├── security/
    ├── identity/
    ├── notification/
    └── mapping/


The underlying service and task state remain in the separate workflow library.

Task forms need a deliberate boundary

Human tasks usually require user-entered data, so you need to decide how forms are defined.

Apolo-owned forms

The workflow stores only a form reference:

form_key = "invoice-approval"
form_version = "2"


Apolo frontend knows how to render that form.

Benefits:

Best fit for an existing frontend
Full control over UI and validation
Workflow core stays independent of frontend technology

Tradeoff:

Workflow definitions and frontend forms must be versioned together
Schema-driven forms

Workflow stores or references a neutral form schema:

{
  "type": "object",
  "required": ["decision", "comment"],
  "properties": {
    "decision": {
      "type": "string",
      "enum": ["APPROVE", "REJECT"]
    },
    "comment": {
      "type": "string"
    }
  }
}


Apolo frontend renders the schema dynamically.

Benefits:

More reusable and configurable
New workflows may require fewer frontend releases

Tradeoff:

Dynamic form rendering becomes a substantial product by itself
Complex business interfaces may not fit generic schemas

For the first implementation, I recommend Apolo-owned forms referenced by a stable formKey.

Persistence boundary

A human task should reference Apolo concepts by stable IDs, not through JPA relationships.

Recommended:

@Entity
class HumanTask {

    private String assigneeId;

    private String createdById;

    private String tenantId;

    private String businessObjectType;

    private String businessObjectId;
}


Avoid:

@ManyToOne
private ApoloUser assignee;

@ManyToOne
private ApoloBusinessEntity businessObject;


The recommended model avoids:

Workflow dependency on backend entities
Cross-module JPA scanning
Requirement for Apolo tables in the workflow test host
Accidental cascading between workflow and Apolo records
Difficulty publishing workflow as a separate library
Notifications

Workflow should decide that a notification is required, but Apolo should decide how it is delivered.

public interface WorkflowNotificationPublisher {

    void taskAssigned(TaskAssignedEvent event);

    void taskDueSoon(TaskDueSoonEvent event);
}


Possible implementations:

workflow-test-host → log or in-memory notification
Apolo backend      → email, Artemis message or application notification


Workflow should not directly depend on:

Spring Mail
Thymeleaf
Apolo email templates
Artemis
User email addresses
Audit ownership

Workflow should maintain the authoritative task audit history:

CREATED
CANDIDATES_ASSIGNED
CLAIMED
RELEASED
DELEGATED
COMPLETED
REJECTED
CANCELLED
EXPIRED


Each entry should capture at least:

task ID
workflow instance ID
action
actor ID
timestamp
previous state
new state
optional comment
correlation ID


Apolo may forward these events to its central logging or audit infrastructure, but the workflow database should retain enough information to explain why a workflow advanced.

Recommended structure
workflow-project/
├── workflow-core/
│   ├── domain/
│   │   ├── workflow/
│   │   └── task/
│   ├── application/
│   ├── persistence/
│   ├── ports/
│   │   ├── identity/
│   │   └── notification/
│   └── db/changelog/workflow/
│
└── workflow-test-host/
    ├── api/
    ├── dummyidentity/
    ├── dummynotification/
    └── configuration/


Inside Apolo:

backend/
└── workflow/
    ├── api/
    ├── security/
    ├── identity/
    ├── notification/
    └── configuration/

frontend/
└── workflow/
    ├── inbox/
    ├── task-details/
    └── forms/

Recommendation

Put the human-task domain, lifecycle, persistence, assignment references, and audit trail in workflow-core.

Keep these in Apolo:

Keycloak and Spring Security integration
User and group resolution
Human-task controllers
UI and forms
Email and application notifications
Apolo-specific authorization and data enrichment

The most important boundary is:

Workflow owns the task, but Apolo owns the person and the user experience.

That allows the test host to simulate users and human-task actions easily, while the production integration continues to use Apolo’s real users, authorization model, frontend, and notification infrastructure.

Exactly. A booking approval form is a booking-domain concern, not a workflow concern. The workflow should know that a human decision is required, but it should not understand booking fields such as destination, dates, cost, or project code.

Recommended separation
Workflow owns
The human task lifecycle
The task identifier
The workflow instance
Who may claim or complete the task
The available outcomes, such as APPROVE and REJECT
A reference to the business object
A reference to the form that the host should display
Generic completion data
Booking domain owns
The booking entity
Booking details
Booking-specific validation
Approval business rules
Changes to booking status
The booking approval UI
Authorization rules specific to bookings

The relationship would look like this:

Booking domain creates approval request
              ↓
Workflow creates generic human task
              ↓
Apolo displays booking approval form
              ↓
Approver submits decision
              ↓
Booking domain validates the decision
              ↓
Workflow task is completed
              ↓
Workflow continues

What the workflow task should contain

The task can hold generic references:

{
  "taskId": "task-8742",
  "taskType": "HUMAN_APPROVAL",
  "title": "Approve booking request",
  "formKey": "booking-approval",
  "formVersion": "1",
  "businessObjectType": "BOOKING",
  "businessObjectId": "booking-123",
  "allowedOutcomes": [
    "APPROVE",
    "REJECT"
  ]
}


The workflow understands:

There is a task.
The task concerns business object booking-123.
Apolo should display booking-approval.
The user can approve or reject it.

The workflow does not understand what a booking contains.

How the form is displayed

The Apolo frontend receives the task and interprets:

formKey = booking-approval
businessObjectId = booking-123


It then loads the appropriate booking form component:

booking-approval
    → BookingApprovalComponent


The component retrieves booking details from the booking API:

GET /api/bookings/booking-123


The workflow API does not need to provide the complete booking.

Conceptually:

GET /api/workflow/tasks/task-8742
    → task metadata and business reference

GET /api/bookings/booking-123
    → booking-specific data


The frontend combines them into one screen.

Completing the task

The UI could submit a booking-specific command to an Apolo endpoint:

{
  "decision": "APPROVE",
  "comment": "Budget and travel dates verified"
}


For example:

POST /api/bookings/booking-123/approval


The booking service should:

Load the booking.
Verify that the current user may approve it.
Validate the booking state.
Apply the approval decision.
Complete the corresponding workflow task.
Commit the operation transactionally where possible.

A simplified orchestration service might look like:

public void approveBooking(
        String bookingId,
        String taskId,
        BookingApprovalCommand command,
        WorkflowActor actor
) {
    Booking booking = bookingRepository.getRequired(bookingId);

    bookingApprovalPolicy.validate(booking, command, actor);

    booking.applyDecision(
            command.decision(),
            command.comment(),
            actor.userId()
    );

    workflowTaskService.complete(
            taskId,
            actor,
            Map.of(
                    "outcome", command.decision().name(),
                    "comment", command.comment()
            )
    );
}


This orchestration belongs in the Apolo booking integration layer, not in workflow-core.

Why not submit directly to a generic workflow endpoint?

You could expose something like:

POST /api/workflow/tasks/{taskId}/complete


But allowing the frontend to complete a booking task directly could bypass booking-specific rules.

For example, the booking domain may need to verify:

The booking still exists.
The booking is still awaiting approval.
The approver has the appropriate business authority.
The total amount has not changed.
The booking has not already been cancelled.
Required compliance information is present.

Therefore, for domain-specific tasks, prefer:

Frontend
   → Booking approval endpoint
       → Booking validation and update
       → Workflow task completion


Instead of:

Frontend
   → Generic workflow completion endpoint


A generic completion endpoint is more suitable for genuinely generic tasks without additional domain behavior.

Where to store form data

There are three different categories.

Task metadata

Stored by workflow:

task title
task description
form key
form version
business object type
business object ID
allowed outcomes
assignee and candidates
due date

Business data

Stored by booking:

destination
booking dates
traveler
cost
currency
project
booking status
approval details


Workflow should reference this data, not duplicate it.

Task-completion snapshot

Workflow may store a small immutable completion record:

{
  "outcome": "APPROVE",
  "comment": "Budget verified",
  "completedBy": "user-123",
  "completedAt": "2026-10-24T10:30:00Z"
}


This is useful for workflow history, but should not become a copy of the complete booking.

Form registry in the frontend

A simple registry can map form keys to components:

const workflowFormRegistry = {
  "booking-approval": BookingApprovalComponent,
  "invoice-approval": InvoiceApprovalComponent,
  "access-request-approval": AccessRequestApprovalComponent
};


The task inbox remains generic:

Task inbox
    ↓ select task
Read formKey
    ↓
Load registered business component


This lets the generic task inbox host multiple domain-specific forms while keeping each form with its domain.

Versioning

Include a form version in the task:

formKey: booking-approval
formVersion: 1


This becomes important when a workflow instance remains active while the booking form changes.

The frontend registry can resolve both values:

booking-approval:1 → original form
booking-approval:2 → updated form


You do not necessarily need separate UI components for every version, but the version gives you a controlled compatibility mechanism.

Recommended ownership structure
workflow-core/
├── task/
│   ├── HumanTask
│   ├── TaskAssignment
│   ├── TaskOutcome
│   └── TaskService
└── persistence/

backend/
├── booking/
│   ├── Booking
│   ├── BookingService
│   ├── BookingApprovalService
│   └── BookingApprovalController
└── workflow/
    ├── WorkflowTaskAdapter
    └── WorkflowConfiguration

frontend/
├── workflow/
│   ├── task-inbox/
│   ├── task-shell/
│   └── form-registry/
└── booking/
    └── booking-approval/

Recommendation

Use a generic task shell with domain-owned forms:

Workflow identifies which form is needed and which business object it concerns. The booking domain supplies the data, UI, validation, and approval behavior.

For booking approval, the clean boundary is:

Workflow owns:
"Booking booking-123 requires an approval task."

Booking owns:
"What booking-123 contains and whether it may be approved."


This preserves a reusable workflow engine without forcing it to understand every Apolo business domain.

Yes, events are useful for the workflow module, especially for human tasks. But JMS itself should normally stay outsideworkflow-core.

The clean boundary is:

Workflow owns the meaning of workflow events. Apolo owns how those events are transported.

Two categories of events
1. Events consumed by workflow

These originate in an Apolo business domain and may start or advance a workflow:

BookingSubmitted
BookingUpdated
BookingCancelled
PaymentConfirmed
DocumentUploaded


For example:

Booking module
    → BookingSubmitted event
        → Workflow starts booking approval


The booking domain owns BookingSubmitted. The workflow module consumes a neutral representation of that event, but should not depend directly on Apolo booking entities.

2. Events produced by workflow

These describe something that happened inside the workflow:

WorkflowStarted
HumanTaskCreated
HumanTaskAssigned
HumanTaskClaimed
HumanTaskCompleted
HumanTaskOverdue
WorkflowCompleted
WorkflowFailed


For example:

Workflow creates HumanTaskCreated
    → Notification adapter sends an email
    → Apolo UI updates the task inbox
    → Audit integration records the event


These events belong conceptually to the workflow module.

Where JMS belongs

JMS and Artemis are transport/infrastructure technologies. They should belong in an adapter, usually in the Apolo backend or in a dedicated workflow messaging adapter.

workflow-core
├── event definitions
├── event publisher port
└── event handlers/use cases

apolo-backend
└── JMS/Artemis implementation

workflow-test-host
└── in-memory implementation


The workflow core could declare a port:

public interface WorkflowEventPublisher {

    void publish(WorkflowEvent event);
}


And a workflow-owned event:

public record HumanTaskCreatedEvent(
        String eventId,
        String taskId,
        String workflowInstanceId,
        String taskType,
        String businessObjectType,
        String businessObjectId,
        Instant occurredAt
) implements WorkflowEvent {
}


Apolo provides the JMS adapter:

@Component
public class JmsWorkflowEventPublisher
        implements WorkflowEventPublisher {

    private final JmsTemplate jmsTemplate;

    public JmsWorkflowEventPublisher(JmsTemplate jmsTemplate) {
        this.jmsTemplate = jmsTemplate;
    }

    @Override
    public void publish(WorkflowEvent event) {
        jmsTemplate.convertAndSend(
                "apolo.workflow.events",
                event
        );
    }
}


The independent test host can provide a simpler implementation:

@Component
public class InMemoryWorkflowEventPublisher
        implements WorkflowEventPublisher {

    private final List<WorkflowEvent> events = new ArrayList<>();

    @Override
    public void publish(WorkflowEvent event) {
        events.add(event);
    }
}


This keeps Artemis, JmsTemplate, queue names, retries, serialization, and broker credentials out of the workflow core.

Recommended boundary
┌───────────────────────────────────────┐
│             workflow-core             │
│                                       │
│  HumanTaskCreatedEvent                │
│  WorkflowCompletedEvent               │
│  WorkflowEventPublisher interface     │
│  Workflow event-handling logic        │
└──────────────────┬────────────────────┘
                   │ port
        ┌──────────┴──────────┐
        │                     │
┌───────▼────────┐   ┌────────▼───────────┐
│ Apolo backend  │   │ Workflow test host │
│ JMS/Artemis    │   │ In-memory adapter  │
└────────────────┘   └────────────────────┘

Domain event versus integration event

This distinction is important.

Internal domain event

Used within workflow processing:

HumanTaskCompleted


It may trigger:

Updating workflow state
Creating the next task
Completing the workflow
Recording history

It can remain inside the same JVM and transaction. It does not necessarily need JMS.

Integration event

Published outside the workflow boundary:

BookingApprovalWorkflowCompleted


It may trigger:

Updating the booking status
Sending notifications
Informing another system
Refreshing projections
Starting another business process

This is where JMS becomes useful.

Do not automatically put every internal event on JMS. That adds latency, asynchronous failure modes, serialization requirements, duplicate handling, and operational overhead without necessarily improving the architecture.

A useful rule is:

Use in-process behavior inside one transactional boundary. Use JMS when crossing a meaningful application or reliability boundary.

Example: booking approval

A reasonable flow is:

1. Apolo booking module submits booking

2. BookingSubmitted integration event is sent

3. Workflow event consumer starts booking workflow

4. Workflow creates human task

5. Workflow publishes HumanTaskCreated

6. Notification consumer informs approvers

7. Approver uses booking-owned approval form

8. Booking approval service validates booking

9. Workflow completes human task

10. Workflow publishes BookingApprovalWorkflowCompleted

11. Booking consumer updates booking status


However, you may not need JMS for all those steps.

If booking and workflow are libraries inside the same Apolo deployment, the simpler initial flow is:

Booking service
    → calls workflow service directly
    → workflow creates task
    → workflow records outbox event
    → Artemis publisher sends external notification later


Direct calls are often preferable for commands that require an immediate response:

Start workflow
Claim task
Complete task
Cancel workflow


JMS is more suitable for facts that have already occurred:

WorkflowStarted
TaskCreated
TaskCompleted
WorkflowCompleted

Commands and events should not be mixed
Command

A request for something to happen:

StartBookingApproval
CompleteHumanTask
CancelWorkflow


A command can fail and normally has one intended handler.

Event

A fact that already happened:

BookingApprovalStarted
HumanTaskCompleted
WorkflowCancelled


An event may have multiple consumers.

For the initial integration, I recommend:

Synchronous service calls for workflow commands
Events for notifications and downstream reactions

This avoids turning basic workflow operations into an unnecessarily asynchronous API.

Avoid business-sensitive events

Do not publish complete booking or human-task entities by default.

Avoid:

{
  "eventType": "HumanTaskCreated",
  "booking": {
    "traveler": "...",
    "destination": "...",
    "cost": "...",
    "comments": "..."
  }
}


Prefer references:

{
  "eventId": "event-123",
  "eventType": "HumanTaskCreated",
  "eventVersion": 1,
  "taskId": "task-456",
  "workflowInstanceId": "workflow-789",
  "businessObjectType": "BOOKING",
  "businessObjectId": "booking-321",
  "occurredAt": "2026-10-24T07:30:00Z"
}


The authorized consumer retrieves booking information from the booking service when needed. This reduces duplication, stale snapshots, security exposure, and coupling to booking schema changes.

Reliability: database update plus JMS publish

The dangerous flow is:

1. Save human task to database
2. Publish JMS event
3. Application crashes between the two operations


Possible outcomes include:

Task saved, but event never published
Message published, but database transaction rolled back
Message delivered more than once after retries

For important workflow events, use a transactional outbox:

Single database transaction:
├── insert/update workflow task
└── insert event into wf_event_outbox

After commit:
└── publisher sends pending outbox events to Artemis


Persisting the state change and event record in one database transaction avoids the unreliable database-plus-broker dual write. A separate publisher then delivers the recorded event.

The workflow module could own:

wf_event_outbox


The Apolo infrastructure adapter could own:

Polling unpublished events
JMS serialization
Publishing to Artemis
Retry policy
Dead-letter handling
Broker monitoring
Idempotency is mandatory

Outbox-based delivery is commonly at least once, which means a consumer can receive the same event more than once.

Every event should have a unique identifier:

eventId
eventType
eventVersion
aggregateId
occurredAt
correlationId
causationId


Consumers should store processed IDs or perform naturally idempotent updates:

If eventId was already processed:
    acknowledge and do nothing


For task completion, protect the workflow state too:

ACTIVE → COMPLETED


Receiving the same completion request again must not advance the workflow twice.

Event ownership

A helpful ownership rule is:

Event	OwnerBookingSubmitted	Booking domain
BookingCancelled	Booking domain
HumanTaskCreated	Workflow
HumanTaskClaimed	Workflow
HumanTaskCompleted	Workflow
WorkflowCompleted	Workflow
JMS destination naming	Apolo infrastructure
Artemis configuration	Apolo infrastructure
Serialization format	Integration adapter
Retry and dead-letter policy	Apolo infrastructure

The producer owns the event contract because the producer controls when the fact occurs and what the fact means.

Suggested module structure
workflow-core/
├── domain/
│   ├── workflow/
│   └── task/
├── event/
│   ├── WorkflowEvent.java
│   ├── HumanTaskCreatedEvent.java
│   ├── HumanTaskCompletedEvent.java
│   └── WorkflowCompletedEvent.java
├── port/
│   └── WorkflowEventPublisher.java
└── persistence/
    └── outbox/

backend/
└── workflow/
    ├── messaging/
    │   ├── JmsWorkflowEventPublisher.java
    │   ├── JmsBookingEventListener.java
    │   └── WorkflowEventSerializer.java
    ├── configuration/
    └── integration/


If the JMS adapter becomes substantial, it could later become:

workflow-jms-adapter/


But initially, keeping it inside the Apolo backend is simpler.

Practical recommendation

For your first version:

Put workflow and human-task events in workflow-core.
Put only a transport-neutral publisher interface in workflow-core.
Keep JMS, Artemis, destinations, serialization, and broker configuration in Apolo.
Use synchronous calls for starting workflows and completing tasks.
Use events for notifications and reactions after workflow state changes.
Use a transactional outbox for events that must not be lost.
Make consumers idempotent.
Do not put complete booking objects into workflow events.

The concise boundary is:

Workflow decides what happened. Apolo decides whether and how that fact is published through JMS.

Yes, that separation makes sense. Apolo does not need to use the task registry directly at runtime if Apolo already maps task operation keys to its own screens and domain services.

The registry’s primary consumer is the workflow designer.

Registry purpose

The task registry would improve the design experience by providing:

Available human-task types
Friendly display names and descriptions
Supported outcomes
Optional configuration metadata
Version and status
Categories for easier selection
Validation when publishing a workflow

For example:

{
  "key": "booking.approval",
  "version": 1,
  "name": "Booking approval",
  "description": "Requires an authorized user to approve or reject a booking",
  "category": "Booking",
  "outcomes": [
    "APPROVE",
    "REJECT"
  ],
  "status": "ACTIVE"
}


The designer can present this as a selection instead of requiring someone to manually enter booking.approval.

Who uses it?
Workflow designer

The main consumer:

Task Registry
      ↓
Workflow Designer
      ↓
User selects “Booking approval”
      ↓
Workflow definition stores:
taskType = booking.approval
taskTypeVersion = 1

Workflow engine

The engine may use the registry for definition validation, for example:

Does booking.approval exist?
Is version 1 active?
Does the task support APPROVE and REJECT?
Is the task type permitted in a published definition?

But the engine should not use the registry to execute booking-specific behavior. The registry contains metadata, not executable domain logic.

Apolo

Apolo does not need to query the registry for normal runtime behavior.

Apolo already owns its local mapping:

booking.approval
├── BookingApprovalComponent
├── BookingApprovalController
├── BookingApprovalService
└── BookingApprovalPolicy


When Apolo receives a task with:

{
  "taskType": "booking.approval",
  "taskTypeVersion": 1
}


Apolo resolves this through its own code or frontend mapping.

One unavoidable Apolo responsibility

Although Apolo does not need to consume the registry, something must initially put booking.approval into it.

There are three possible models.

1. Manual administration

A workflow administrator creates registry entries through the designer:

Key: booking.approval
Name: Booking approval
Outcomes: APPROVE, REJECT


Apolo has no technical interaction with the registry.

This provides maximum decoupling, but configuration can drift:

The registry says booking.approval exists.
Apolo does not actually support it.
A workflow is published successfully but cannot render the task at runtime.
2. Deployment migration

The workflow module or an Apolo deployment script inserts the task metadata through a database migration:

Liquibase:
Insert booking.approval task-type metadata


This keeps registration repeatable, but creates a database-level deployment relationship. I would avoid using Liquibase for frequently changing catalog data unless Apolo already follows this convention for controlled reference data.

3. Registration API

During deployment or application startup, an Apolo integration component calls a workflow administration API:

PUT /api/workflow/task-types/booking.approval/versions/1


This is more automated, but it gives Apolo an integration with the registry.

The dependency direction remains acceptable:

Apolo contributes capability metadata
        ↓
Workflow registry


The designer still has no dependency on Apolo.

Recommended initial solution

For the first version, I would use manual registry administration through the workflow designer.

This keeps all workflow design metadata inside the workflow feature:

workflow platform
├── task registry persistence
├── task registry administration API
├── designer task selection
└── workflow publication validation


Apolo only implements the agreed task keys:

booking.approval
invoice.approval
document.review


Later, if manual synchronization becomes error-prone, add an optional registration API.

Important distinction: catalog versus runtime resolution

You effectively have two separate things:

Persistent design-time registry

Owned by the workflow feature:

booking.approval
├── display name
├── description
├── version
├── supported outcomes
└── active status


Used for selection and validation.

Runtime implementation mapping

Owned by Apolo:

booking.approval
├── frontend component
├── backend endpoint
├── authorization rules
└── booking validation


Used to actually display and process a task.

The registry must not contain:

Java class names
Spring bean names
Angular component names
Internal Apolo API routes
JPA entity names
Keycloak-specific roles

Otherwise, the workflow registry becomes coupled to Apolo implementation details.

Runtime failure handling

Because the registry does not guarantee that a host implements a task type, Apolo should handle unsupported tasks explicitly.

For example:

Task type booking.approval.v2 is not supported by this Apolo deployment.


It should not silently open a generic completion form, because that could bypass booking validation.

The workflow deployment process should ideally include a compatibility check:

Workflow requires:
- booking.approval v1

Apolo supports:
- booking.approval v1

Result:
- compatible


This check can be added later without making Apolo a regular consumer of the registry.

Suggested registry data model
wf_task_type
├── id
├── task_key
├── version
├── display_name
├── description
├── category
├── status
├── created_at
└── updated_at

wf_task_type_outcome
├── task_type_id
├── outcome_key
├── display_name
└── sort_order


A unique constraint should cover:

task_key + version


The workflow definition should store both:

{
  "taskType": "booking.approval",
  "taskTypeVersion": 1
}


Published registry versions should be immutable. Changes should create version 2, rather than modifying version 1 while active workflows still reference it.

Final boundary
Workflow feature owns:
├── Registry database
├── Registry administration
├── Designer selection
├── Definition validation
└── Task type references

Apolo owns:
├── Actual task screen
├── Business data
├── Domain validation
├── Authorization
└── Task completion orchestration


So your conclusion is essentially correct:

Apolo does not need the registry for normal runtime processing. The registry is workflow design metadata used by the designer and by workflow-definition validation.

The only connection is an operational agreement that a task key registered in the workflow platform must have a corresponding implementation in the host application.
