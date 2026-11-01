# Historical APOLO application reference and architecture discussion

> **ARCHIVED — extracted 2026-10-24.** This preserves the earlier application reference and module trade-offs. Use the [current technical summary](../reference/apolo-technical-summary.md) and [authoritative design](../architecture/workflow-engine-design.md) for current guidance.

The parent POM excerpt and dependency notes below are preserved as supplied context. Java 17 compilation settings remain correct; the confirmed runtime is JDK 21. Other dependency versions still require verification. The earlier backend-package recommendation is superseded by the independent-library direction. The separate supplied backend POM is available as [reference material](../reference/apolo-backend-pom.xml).

## Preserved reference and discussion

Summary

IntelliJ module structure summary

The apolo project contains three main modules:

backend: backend application and server-side logic
frontend: user interface application
configuration: configuration-related module

It also includes a config directory for supporting configuration files.

The root pom.xml manages the multi-module Maven project and coordinates the build of backend, frontend, and configuration.

Yes. A new workflow module can be added alongside the existing modules:

apolo/
├── backend/
├── configuration/
├── frontend/
├── workflow/
└── pom.xml


Then register it in the root pom.xml:

<modules>
    <module>frontend</module>
    <module>backend</module>
    <module>configuration</module>
    <module>workflow</module>
</modules>


The workflow directory should have its own pom.xml, typically inheriting from the root project. This is a good approach if workflow functionality is an independently buildable component with clear responsibilities and dependencies.

If workflow functionality is small, tightly coupled to the backend, and cannot reasonably operate independently, keeping it as a package inside backend may be simpler.

This is the parent/aggregator Maven POM for the Apolo project.

Project configuration
Group ID: com.kpmg
Artifact ID: apolo
Version: 6.1.8
Packaging: pom
Parent: Spring Boot Starter Parent 3.4.5

Because the packaging is pom, this module primarily coordinates the build and centralizes configuration rather than producing a runnable JAR or WAR.

Modules

The project contains three Maven modules:

frontend
backend
configuration

Building the parent project will include these modules in the Maven reactor build.

Java and build settings

The project is configured to use:

Java 17
UTF-8 source encoding
An empty jenkins.build.number property, likely intended to be populated by the Jenkins pipeline

Both maven.compiler.source and maven.compiler.target are explicitly set to Java 17, alongside java.version.

Dependency versions managed centrally

The POM defines shared versions for several important libraries:

Hibernate: 6.6.8.Final
Log4j: 2.24.3
Keycloak: 24.0.5
Infinispan: 14.0.27.Final
RESTEasy: 6.2.7.Final
Micrometer: 1.12.2
SnakeYAML Engine: 2.6
Bucket4j: 8.17.0
Privacy notice: v1

The comments indicate that Infinispan, RESTEasy, Micrometer, and SnakeYAML versions should be updated together with Keycloak, based on Keycloak’s effective dependency POM.

Additional Maven repositories

Two external repositories are configured:

JitPack: https://jitpack.io
WSO2 distribution repository: http://dist.wso2.org/maven2/

One notable detail is that the WSO2 repository uses plain HTTP, while JitPack uses HTTPS.

Overall purpose

In short, this POM:

Defines the root Apolo project and its version.
Aggregates the frontend, backend, and configuration modules.
Standardizes the project on Java 17 and Spring Boot 3.4.5.
Centralizes important dependency versions.
Provides additional repositories required by project dependencies.



<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.4.5</version>
        <relativePath/>
    </parent>

    <groupId>com.kpmg</groupId>
    <artifactId>apolo</artifactId>
    <version>6.1.8</version>

    <packaging>pom</packaging>
    <name>apolo</name>

    <repositories>
        <repository>
            <id>jitpack.io</id>
            <url>https://jitpack.io</url>
        </repository>
        <repository>
            <id>dist.wso2</id>
            <url>http://dist.wso2.org/maven2/</url>
        </repository>
    </repositories>

    <modules>
        <module>frontend</module>
        <module>backend</module>
        <module>configuration</module>
    </modules>

    <properties>
        <jenkins.build.number></jenkins.build.number>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <java.version>17</java.version>

        <compatible.hibernate.version>6.6.8.Final</compatible.hibernate.version>

        <log4j.version>2.24.3</log4j.version>

        <!--        Keycloak        -->
        <keycloak.version>24.0.5</keycloak.version>
        <!-- these should be updated together with Keycloak -->
        <!-- check keycloak-dependencies-server-all effective pom -->
        <infinispan.version>14.0.27.Final</infinispan.version>
        <resteasy.version>6.2.7.Final</resteasy.version>
        <micrometer.version>1.12.2</micrometer.version>
        <org.snakeyaml.snakeyaml-engine.version>2.6</org.snakeyaml.snakeyaml-engine.version>
        <privacy.notice.version>v1</privacy.notice.version>

        <bucket4j.version>8.17.0</bucket4j.version>

    </properties>





Separate workflow module vs package inside backend
Option 1: Separate Maven module
apolo/
├── backend/
├── workflow/
│   ├── pom.xml
│   └── src/
├── configuration/
├── frontend/
└── pom.xml


The backend module would depend on workflow:

<dependency>
    <groupId>com.kpmg</groupId>
    <artifactId>workflow</artifactId>
    <version>${project.version}</version>
</dependency>


Advantages

Enforces a clear architectural boundary.
Workflow code has its own dependencies and tests.
Prevents unrelated backend code from directly accessing workflow internals.
Can be reused by another module or application.
Can potentially be built, versioned, or deployed independently later.
Makes ownership clearer if workflow becomes a substantial subsystem.

Disadvantages

Adds another pom.xml and more Maven configuration.
Requires dependencies between modules to be managed carefully.
Changes across backend and workflow may require edits in multiple modules.
Can be unnecessary complexity if workflow is small and backend-specific.
A separate module does not automatically mean a separate deployable service. It may still be packaged inside the backend application.
Option 2: Package inside backend
backend/
└── src/main/java/com/kpmg/apolo/
    ├── workflow/
    │   ├── controller/
    │   ├── service/
    │   ├── domain/
    │   └── repository/
    └── ...


Advantages

Simpler project and build structure.
No additional Maven module or dependency declaration.
Easier refactoring when workflow interacts closely with existing backend services.
All backend code uses the same dependency set and application configuration.
A good starting point when the feature is still small or evolving.

Disadvantages

The boundary is based mostly on package conventions rather than build-level enforcement.
Workflow code can gradually become coupled to unrelated backend components.
Dependencies required only by workflow are added to the entire backend module.
Reusing or extracting the workflow functionality later can require more work.
The backend module may become increasingly large.
Direct comparison
Consideration	Separate module	Backend packageBuild unit	Independent Maven module	Part of backend
Own pom.xml	Yes	No
Dependency isolation	Strong	Limited
Architectural boundary	Enforced by Maven	Enforced by conventions
Initial complexity	Higher	Lower
Refactoring across boundaries	More deliberate	Easier
Reusability	Better	Lower
Independent tests	Clear module test suite	Part of backend tests
Independent deployment	Possible, but not automatic	No
Best fit	Large or reusable subsystem	Backend-specific feature
Recommendation for Apolo

Use a package inside backend if the workflow functionality:

Is used only by the backend application.
Shares the backend database and services.
Is relatively small.
Is expected to change together with backend functionality.
Does not require significant workflow-specific dependencies.

Use a separate workflow module if the workflow functionality:

Represents a substantial domain or subsystem.
Needs clear dependency isolation.
Has its own persistence, APIs, or integration layer.
May be reused by multiple modules.
Requires libraries that the rest of the backend should not depend on.
Is expected to grow significantly.

A practical rule is:

Start with a well-structured workflow package inside backend. Extract it into a Maven module when there is a concrete need for dependency isolation, reuse, separate ownership, or independent build behavior.

For the structure currently shown, I would keep workflow under backend unless workflow is already intended to be a major platform component rather than a backend feature.
