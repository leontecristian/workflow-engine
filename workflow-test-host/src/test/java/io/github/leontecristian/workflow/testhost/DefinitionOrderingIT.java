package io.github.leontecristian.workflow.testhost;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.leontecristian.workflow.core.dto.definition.DefinitionOrder;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionQuery;
import io.github.leontecristian.workflow.core.entity.definition.WorkflowDefinitionEntity;
import io.github.leontecristian.workflow.core.service.definition.WorkflowDefinitionService;
import io.github.leontecristian.workflow.testhost.configuration.integration.DatabaseIntegrationTest;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Only the changed SQL ordering/cursor path; no runtime commands or HTTP listener. */
class DefinitionOrderingIT extends DatabaseIntegrationTest {
    @Autowired WorkflowDefinitionService definitions;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    @Test void newestFirstPagingUsesStrictDescendingDatabaseCursor() {
        var transaction = new TransactionTemplate(transactionManager);
        String marker = "definition.order." + UUID.randomUUID();
        List<Long> ids = new ArrayList<>();
        try {
            transaction.executeWithoutResult(status -> {
                for (int index = 0; index < 3; index++) {
                    var family = new WorkflowDefinitionEntity(marker + "." + index, marker, null, LocalDateTime.now(), marker);
                    entityManager.persist(family);
                    entityManager.flush();
                    ids.add(family.getId());
                }
            });
            var first = definitions.listDefinitions(new DefinitionQuery(0, 2, DefinitionOrder.DESC));
            assertThat(first.items()).extracting(item -> item.id()).containsExactly(ids.get(2), ids.get(1));
            assertThat(first.nextAfterId()).isEqualTo(ids.get(1));
            var second = definitions.listDefinitions(new DefinitionQuery(first.nextAfterId(), 2, DefinitionOrder.DESC));
            assertThat(second.items().get(0).id()).isEqualTo(ids.get(0));
            assertThat(second.items()).allSatisfy(item -> assertThat(item.id()).isLessThan(first.nextAfterId()));
            var ascending = definitions.listDefinitions(new DefinitionQuery(0, 100));
            assertThat(ascending.items().stream().map(item -> item.id()).toList()).isSorted();
        } finally {
            transaction.executeWithoutResult(status -> jdbc.update("DELETE FROM wf.workflow_definition WHERE created_by=? AND workflow_key LIKE ?", marker, marker + ".%"));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf.workflow_definition WHERE created_by=?", Integer.class, marker)).isZero();
        }
    }
}
