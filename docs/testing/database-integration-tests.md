# Database integration testing

## Scope and standing authorization

Owner decision, 2026-11-01: agents may create and run isolated unit tests and database integration tests against **localhost:1433 / apolo_workflow_test** as part of implementation and verification without asking again. Applying project migrations and creating/removing test-owned fixtures there is authorized. Other database targets, including apolo_workflow_dev, require task-specific authorization. This does not authorize dropping/resetting databases, clearing unrelated records, or interrupting another agent's processes.

This policy in [AGENTS.md](../../AGENTS.md) supersedes older blanket no-tests and per-run authorization wording in feature documents. Historical verification results, NOT RUN entries and feature acceptance remain unchanged until their particular scenarios are executed and evidenced.

## Environment and commands

The database is on the same local SQL Server instance as the development database, with Windows integrated authentication. It was created independently; no development rows were copied. The default application configuration still targets apolo_workflow_dev. The legacy WORKFLOW_TEST_DB_URL variable remains the normal host's override; the integration profile does not use it to select its database.

Use an x64 JDK 21 and run from the repository root:

```powershell
# Unit tests only; no host or database startup.
./mvnw.cmd -B -ntp test

# Unit tests followed by SQL Server integration tests.
./mvnw.cmd -B -ntp -Pintegration-tests verify
```

The existing windows-native-auth profile copies the matching SQL Server JDBC authentication DLL into the host's target/native directory. Failsafe passes that directory as java.library.path to its forked JVM. SQL Server must already be available; the build does not provision databases or start Docker. Unsupported platforms/authentication methods need a separately designed configuration.

Surefire selects *UnitTest.java in both modules. The integration-tests Maven profile enables the Boot-managed Failsafe plugin in the host, selects *IT.java and the preserved WorkflowTestHostApplicationTests, and fails if no integration tests are discovered. Use verify so failed integration tests fail the build. Default test/verify does not execute the database suite. Test sources may still compile during ordinary builds.

Results are under workflow-core/target/surefire-reports, workflow-test-host/target/surefire-reports and workflow-test-host/target/failsafe-reports. Run integration suites serially against this shared test database. During another agent's verification, use a separate source/build directory; never rebuild, clean or stop its active host.

## Harness and fixtures

Host database tests extend [DatabaseIntegrationTest](../../workflow-test-host/src/test/java/io/github/leontecristian/workflow/testhost/configuration/integration/DatabaseIntegrationTest.java). Its test-only Spring profile loads the real host/core migrations and uses Hibernate schema validation. A mock web environment supplies the application context without opening an HTTP port. This is database integration testing, not browser acceptance.

[IntegrationDatabaseInitializer](../../workflow-test-host/src/test/java/io/github/leontecristian/workflow/testhost/configuration/integration/IntegrationDatabaseInitializer.java) runs before bean creation. It requires the exact approved JDBC URL, project changelog, drop-first=false and ddl-auto=validate, and rejects alternate Liquibase/JNDI/Hikari/JPA connection routes. A fixed test-only datasource checks the database catalog on every connection. These checks prevent accidental redirection of this harness; they do not change SQL Server user permissions. Keep new tests on this base and never introduce an unguarded datasource or database-backed *UnitTest.

Use unique fixture identifiers and cleanup scoped to those identifiers, including on assertion failure. Do not rely on development records or clear entire tables. Tests that verify commits, rollback or races must observe results outside the transaction under test. A test-level transaction automatically rolled back by the framework is not sufficient evidence for those guarantees.

The initial [DatabaseInfrastructureIT](../../workflow-test-host/src/test/java/io/github/leontecristian/workflow/testhost/DatabaseInfrastructureIT.java) checks the target catalog, applied migrations, released migration lock, a committed audit fixture visible outside its transaction, and rollback of a deliberately failed write. Cleanup removes only that test's correlation UUID and actor marker. The retained context-load test uses the same guarded base.

## Recorded setup evidence — 2026-11-01

Verification used a temporary source snapshot containing the then-current F007 work, with independent target directories. No branch switch, active-host restart, browser action, development-database write or application-source change was performed for this setup.

- PASS: apolo_workflow_test created and online on localhost:1433.
- PASS: isolated guard suite, 10 cases covering the approved configuration, development/remote/H2/duplicate targets, alternate connection routes, missing profile and destructive migration setting.
- PASS: JDK 21.0.13, Java release 17; `./mvnw.cmd -B -ntp -Pintegration-tests verify`: 50 core unit tests, 41 host unit tests (including the 10 guard cases), and 4 database integration tests; no failures or skipped cases. Liquibase applied all 9 changesets from the source snapshot and Hibernate validated the mappings.
- PASS: an explicit spring.datasource.url override naming apolo_workflow_dev caused the integration run to fail in the initializer before migration/JPA startup. This expected failure is a guard check, not a successful feature test.
- PASS: repeat `-Pintegration-tests verify` run: the same 91 unit and 4 integration tests passed; Liquibase reported 0 new and 9 previously applied changesets. Independent SQL inspection found 9 tracking rows, no held migration lock, no remaining test fixtures and no other sessions in the test database. Both test JVMs exited; no HTTP listener was started.

The owner also ran the documented command from the working checkout on 2026-11-01 at 20:25; the supplied log recorded BUILD SUCCESS with the same 91 unit and 4 integration tests, zero failures/errors/skips and no pending migrations.

This establishes the database test infrastructure and the listed infrastructure behavior. It does not close F007 service-specific rollback, replay, concurrency or browser acceptance. Those scenarios need their own tests/evidence. No new core or host migration was authored, no feature acceptance status was changed, and the testing setup is committed separately from F007. No push was requested.
