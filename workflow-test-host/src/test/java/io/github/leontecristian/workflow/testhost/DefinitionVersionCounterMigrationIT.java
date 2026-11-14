package io.github.leontecristian.workflow.testhost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.leontecristian.workflow.core.dto.definition.CreateWorkflowDraftFromVersion;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionManagementContext;
import io.github.leontecristian.workflow.core.dto.definition.DeleteWorkflowDraft;
import io.github.leontecristian.workflow.core.dto.definition.ImportWorkflowDraft;
import io.github.leontecristian.workflow.core.dto.definition.UpdateWorkflowDefinition;
import io.github.leontecristian.workflow.core.exception.definition.WorkflowDefinitionException;
import io.github.leontecristian.workflow.core.service.definition.WorkflowDefinitionService;
import io.github.leontecristian.workflow.testhost.configuration.integration.DatabaseIntegrationTest;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Executes shipped counter SQL against isolated, transactionally rolled-back old-schema fixtures. */
class DefinitionVersionCounterMigrationIT extends DatabaseIntegrationTest {
    @Autowired WorkflowDefinitionService definitions;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;

    private final String marker = "f023.counter." + UUID.randomUUID();
    private final DefinitionManagementContext context = new DefinitionManagementContext(marker,
            Instant.parse("2026-11-14T10:00:00Z"));

    private <T> T tx(Supplier<T> work) {
        return new TransactionTemplate(transactions).execute(status -> work.get());
    }

    @AfterEach void cleanup() {
        tx(() -> {
            jdbc.update("DELETE FROM wf.workflow_definition_version WHERE definition_id IN "
                    + "(SELECT id FROM wf.workflow_definition WHERE workflow_key=?)", marker);
            jdbc.update("DELETE FROM wf.workflow_definition WHERE workflow_key=?", marker);
            return null;
        });
    }

    @Test void populatedUpgradeBackfillsEveryFamilyWithoutChangingExistingEvidence() throws Exception {
        migrateFixture(true);
    }

    @Test void freshUpgradeHasTheSameDefaultNonNullAndNonnegativeContract() throws Exception {
        migrateFixture(false);
    }

    private void migrateFixture(boolean populated) throws Exception {
        String schema = "f023_" + UUID.randomUUID().toString().replace("-", "");
        List<String> initial = sql("001-initial-schema.xml");
        List<String> migration = sql("007-definition-version-counter.xml");
        new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
            // DDL and fixtures are rolled back even if an assertion fails. Never alter the live wf tables.
            transaction.setRollbackOnly();
            jdbc.execute("CREATE SCHEMA " + schema);
            for (String table : List.of("workflow_definition", "workflow_definition_version")) {
                var matcher = Pattern.compile("CREATE TABLE wf\\." + table + " \\(.*?\\n\\);", Pattern.DOTALL)
                        .matcher(String.join("\n", initial));
                assertThat(matcher.find()).as("shipped initial table %s", table).isTrue();
                jdbc.execute(matcher.group().replace("wf.", schema + "."));
            }
            // Reproduce the already-applied F004 column removal before counter adoption.
            jdbc.execute("ALTER TABLE " + schema + ".workflow_definition_version DROP COLUMN expression_policy");
            if (populated) {
                insertFamily(schema, "first", 31);
                insertFamily(schema, "second", 47);
                insertFamily(schema, "empty", 59);
                insertVersion(schema, 1, 2, "DRAFT", 71);
                insertVersion(schema, 1, 8, "PUBLISHED", 73);
                insertVersion(schema, 1, 13, "RETIRED", 79);
                insertVersion(schema, 2, 3, "RETIRED", 83);
            }
            var familiesBefore = jdbc.queryForList("SELECT * FROM " + schema + ".workflow_definition ORDER BY id");
            var versionsBefore = versionEvidence(schema);
            for (String statement : migration) jdbc.execute(statement.replace("wf.", schema + "."));
            if (populated) {
                assertThat(jdbc.queryForList("SELECT highest_allocated_version FROM " + schema
                        + ".workflow_definition ORDER BY id", Integer.class)).containsExactly(13, 3, 0);
                var familiesAfter = jdbc.queryForList("SELECT * FROM " + schema + ".workflow_definition ORDER BY id");
                familiesAfter.forEach(row -> row.remove("highest_allocated_version"));
                assertThat(familiesAfter).isEqualTo(familiesBefore);
                assertThat(versionEvidence(schema)).isEqualTo(versionsBefore);
            }
            insertFamily(schema, "after", 97);
            assertThat(jdbc.queryForObject("SELECT highest_allocated_version FROM " + schema
                    + ".workflow_definition WHERE workflow_key='after'", Integer.class)).isZero();
            assertThatThrownBy(() -> jdbc.update("UPDATE " + schema
                    + ".workflow_definition SET highest_allocated_version=-1 WHERE workflow_key='after'"))
                    .isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> jdbc.update("UPDATE " + schema
                    + ".workflow_definition SET highest_allocated_version=NULL WHERE workflow_key='after'"))
                    .isInstanceOf(DataAccessException.class);
        });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys.schemas WHERE name=?", Integer.class, schema)).isZero();
    }

    private List<?> versionEvidence(String schema) {
        // Convert binary evidence to text so equality compares values rather than Java byte-array identities.
        return jdbc.queryForList("SELECT id, definition_id, version, schema_version, definition_json, status, "
                + "content_revision, CONVERT(VARCHAR(64), content_hash, 2) AS content_hash, canonicalization_version, "
                + "created_at, created_by, updated_at, updated_by, validation_status, validated_content_revision, "
                + "CONVERT(VARCHAR(64), validated_content_hash, 2) AS validated_content_hash, "
                + "CONVERT(VARCHAR(64), validation_context_hash, 2) AS validation_context_hash, validation_details_json, "
                + "validated_at, validated_by, published_at, published_by, retired_at, retired_by, lock_version FROM "
                + schema + ".workflow_definition_version ORDER BY id");
    }

    private void insertFamily(String schema, String key, long token) {
        jdbc.update("INSERT INTO " + schema + ".workflow_definition "
                + "(workflow_key,catalog_name,catalog_description,created_at,created_by,lock_version) "
                + "VALUES (?,?,'preserved description','2026-11-14','fixture',?)", key, key, token);
    }

    private void insertVersion(String schema, long family, int version, String state, long token) {
        String published = state.equals("DRAFT") ? "NULL,NULL" : "'2026-11-14','publisher'";
        String retired = state.equals("RETIRED") ? "'2026-11-14','retirer'" : "NULL,NULL";
        jdbc.update("INSERT INTO " + schema + ".workflow_definition_version "
                + "(definition_id,version,schema_version,definition_json,status,content_revision,content_hash,"
                + "canonicalization_version,created_at,created_by,updated_at,updated_by,published_at,published_by,"
                + "retired_at,retired_by,lock_version) VALUES (?, ?,1,?, ?,5,0x1234,'fixture',"
                + "'2026-11-14','creator','2026-11-14','editor'," + published + "," + retired + ",?)",
                family, version, "{\"version\":" + version + ",\"precise\":1.2300}", state, token);
    }

    private static List<String> sql(String file) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        try (InputStream input = DefinitionVersionCounterMigrationIT.class.getClassLoader()
                .getResourceAsStream("db/changelog/workflow/changes/" + file)) {
            assertThat(input).isNotNull();
            var nodes = factory.newDocumentBuilder().parse(input).getElementsByTagName("sql");
            List<String> result = new ArrayList<>();
            for (int i = 0; i < nodes.getLength(); i++) result.add(nodes.item(i).getTextContent());
            return result;
        }
    }

    @Test void allocationInvalidatesFamilyMetadataTokenWhileEquivalentImportAndDeletionPreserveIt() {
        var source = tx(() -> definitions.importDraft(new ImportWorkflowDraft(document(2)), context)).after();
        var before = definitions.findDefinition(source.definitionId()).orElseThrow();
        var copied = tx(() -> definitions.createDraftFromVersion(
                new CreateWorkflowDraftFromVersion(source.id(), source.lockVersion()), context)).after();
        var allocated = definitions.findDefinition(source.definitionId()).orElseThrow();
        assertThat(allocated.lockVersion()).isGreaterThan(before.lockVersion());
        assertThat(allocated.name()).isEqualTo(before.name());
        assertThatThrownBy(() -> tx(() -> definitions.updateMetadata(new UpdateWorkflowDefinition(before.id(),
                before.lockVersion(), "stale editor", null), context)))
                .isInstanceOfSatisfying(WorkflowDefinitionException.class,
                        failure -> assertThat(failure.category()).isEqualTo(WorkflowDefinitionException.Category.CONFLICT));
        assertThat(tx(() -> definitions.importDraft(new ImportWorkflowDraft(document(2)), context)).changed()).isFalse();
        assertThat(definitions.findDefinition(before.id()).orElseThrow()).isEqualTo(allocated);
        tx(() -> definitions.deleteDraft(new DeleteWorkflowDraft(copied.id(), copied.lockVersion()), context));
        assertThat(definitions.findDefinition(before.id()).orElseThrow()).isEqualTo(allocated);
        assertThat(counter(before.id())).isEqualTo(3);

        assertThatThrownBy(() -> tx(() -> {
            definitions.createDraftFromVersion(new CreateWorkflowDraftFromVersion(source.id(), source.lockVersion()), context);
            throw new IllegalStateException("rollback after flushed allocation");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(counter(before.id())).isEqualTo(3);
        assertThat(definitions.findDefinition(before.id()).orElseThrow()).isEqualTo(allocated);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf.workflow_definition_version WHERE definition_id=?",
                Integer.class, before.id())).isEqualTo(1);
    }

    private int counter(long id) {
        return jdbc.queryForObject("SELECT highest_allocated_version FROM wf.workflow_definition WHERE id=?", Integer.class, id);
    }

    private String document(int version) {
        return "{\"schemaVersion\":1,\"key\":\"" + marker + "\",\"version\":" + version + ",\"name\":\"Counter fixture\"}";
    }
}
