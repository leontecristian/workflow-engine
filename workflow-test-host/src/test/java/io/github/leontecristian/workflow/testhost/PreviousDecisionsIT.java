package io.github.leontecristian.workflow.testhost;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.leontecristian.workflow.core.dto.definition.CheckWorkflowDefinition;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionManagementContext;
import io.github.leontecristian.workflow.core.dto.definition.ImportWorkflowDraft;
import io.github.leontecristian.workflow.core.dto.runtime.ActorKind;
import io.github.leontecristian.workflow.core.dto.runtime.AllAssignmentResult;
import io.github.leontecristian.workflow.core.dto.runtime.AssignmentContext;
import io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind;
import io.github.leontecristian.workflow.core.dto.runtime.CompleteWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.ResolvedTask;
import io.github.leontecristian.workflow.core.dto.runtime.SingleAssignmentResult;
import io.github.leontecristian.workflow.core.dto.runtime.TaskAssignmentTarget;
import io.github.leontecristian.workflow.core.dto.runtime.TaskStatus;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandContext;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskView;
import io.github.leontecristian.workflow.core.dto.tasktype.CreateTaskType;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.core.service.definition.WorkflowDefinitionService;
import io.github.leontecristian.workflow.core.service.runtime.AllTaskAssignmentResolver;
import io.github.leontecristian.workflow.core.service.runtime.SingleTaskAssignmentResolver;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowRuntimeQueryService;
import io.github.leontecristian.workflow.core.service.tasktype.TaskTypeCatalogService;
import io.github.leontecristian.workflow.testhost.configuration.integration.DatabaseIntegrationTest;
import io.github.leontecristian.workflow.testhost.configuration.console.ConsoleSettings;
import io.github.leontecristian.workflow.testhost.dto.runtime.SandboxStart;
import io.github.leontecristian.workflow.testhost.repository.sandbox.SandboxRequestStore;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleRuntimeTransaction;
import io.github.leontecristian.workflow.testhost.service.runtime.SandboxSharedAssignmentProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** Retained decision reads and receiving-task disclosure on the guarded SQL Server. */
@SpringBootTest(classes = WorkflowTestHostApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {"workflow.console.enabled=true", "workflow.console.capabilities=sample",
                "workflow.console.admin.username=f022-admin", "workflow.console.admin.password=f022-local-test-password",
                "workflow.console.reviewer.username=f022-reviewer", "workflow.console.reviewer.password=f022-local-test-password"})
class PreviousDecisionsIT extends DatabaseIntegrationTest {
    @Autowired WorkflowDefinitionService definitions;
    @Autowired TaskTypeCatalogService catalog;
    @Autowired WorkflowRuntimeQueryService reads;
    @Autowired ConsoleRuntimeTransaction runtime;
    @Autowired SandboxRequestStore requests;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired WebApplicationContext web;
    @Autowired SandboxSharedAssignmentProvider membership;
    @Autowired ConsoleSettings settings;
    @MockitoSpyBean(name = "sandboxResolver") SingleTaskAssignmentResolver singleResolver;
    @MockitoSpyBean(name = "sandboxAllResolver") AllTaskAssignmentResolver allResolver;
    private final ObjectMapper json = new ObjectMapper();
    private final String marker = "f022.it." + UUID.randomUUID();
    private final List<Long> instances = new ArrayList<>();
    private Long familyId, versionId, ownedTaskType;
    private MockMvc mvc;

    @BeforeEach void configureAssignments() {
        SingleTaskAssignmentResolver target = AopTestUtils.getUltimateTargetObject(singleResolver);
        doAnswer(call -> {
            AssignmentContext context = call.getArgument(0);
            return new SingleAssignmentResult(List.of(assigned("REVIEW",
                    "MANAGER".equals(context.humanNodeKey()) ? "f022-admin" : "f022-reviewer")), null);
        }).when(target).resolve(any());
        AllTaskAssignmentResolver allTarget = AopTestUtils.getUltimateTargetObject(allResolver);
        doReturn(new AllAssignmentResult(List.of(assigned("A", "f022-admin"), assigned("B", "f022-admin"),
                assigned("C", "f022-admin")), null)).when(allTarget).resolve(any());
        mvc = MockMvcBuilders.webAppContextSetup(web).addFilters(web.getBean(FilterChainProxy.class)).build();
    }

    @Test void persistedSingleDecisionsPageInHistoryOrderAndKeepTheirCreationCutoff() throws Exception {
        publish(fixture()); long instance = start();
        var manager = open(instance); complete(manager, "APPROVE", "  <b>exact</b>\nline  ", "f022-admin");
        var partner = open(instance);
        var partnerPage = reads.listPreviousDecisions(partner.id(), 0, 20);
        assertThat(partnerPage.items()).hasSize(1);
        var first = partnerPage.items().get(0);
        assertThat(first.taskId()).isEqualTo(manager.id());
        assertThat(first.activityId()).isEqualTo(manager.activityId());
        assertThat(first.nodeKey()).isEqualTo("MANAGER");
        assertThat(first.nodeName()).isEqualTo("Manager review");
        assertThat(first.outcomeKey()).isEqualTo("APPROVE");
        assertThat(first.outcomeLabel()).isEqualTo("Approve");
        assertThat(first.actorKind()).isEqualTo(ActorKind.USER);
        assertThat(first.actorRef()).isEqualTo("f022-admin");
        assertThat(first.comment()).isEqualTo("  <b>exact</b>\nline  ");
        assertThat(first.occurredAt()).isNotNull();
        complete(partner, "APPROVE", null, "f022-reviewer");
        var last = open(instance);
        var page = reads.listPreviousDecisions(last.id(), 0, 1);
        assertThat(page.items()).containsExactly(first);
        assertThat(page.nextAfterSequence()).isEqualTo(first.historySequence());
        var next = reads.listPreviousDecisions(last.id(), page.nextAfterSequence(), 1);
        assertThat(next.items()).hasSize(1);
        assertThat(next.items().get(0).taskId()).isEqualTo(partner.id());
        assertThat(next.items().get(0).comment()).isNull();
        assertThat(next.items().get(0).historySequence()).isGreaterThan(first.historySequence());
        assertThat(next.nextAfterSequence()).isNull();
        // A later completion cannot expand an older task's context, even after the instance ends.
        complete(last, "APPROVE", "later stage", "f022-reviewer");
        var before = reads.findInstance(instance).orElseThrow();
        int history = historyCount(instance);
        assertThat(reads.listPreviousDecisions(partner.id(), 0, 20)).isEqualTo(partnerPage);
        assertThat(reads.listPreviousDecisions(last.id(), Long.MAX_VALUE, 20).items()).isEmpty();
        assertThat(reads.findInstance(instance).orElseThrow()).isEqualTo(before);
        assertThat(historyCount(instance)).isEqualTo(history);
        // A separate instance of the same pinned definition contributes no decisions.
        var other = open(start());
        assertThat(reads.listPreviousDecisions(other.id(), 0, 20).items()).isEmpty();
    }

    @Test void resolvedAllIncludesIndividualContributionsAndOmitsCancelledPeersWithoutResults() throws Exception {
        var source = fixture();
        for (var node : source.path("nodes")) if ("MANAGER".equals(node.path("key").asText())) {
            ((ObjectNode) node.path("assignment")).put("resolver", "sandbox.allReviewers");
            ((ObjectNode) node.path("completion")).put("policy", "ALL").put("contributionOutcome", "APPROVE");
        }
        // Both resolutions reach the receiving stage; an early rejection still exposes individual outcomes.
        for (var edge : source.path("transitions")) if ("managerReject".equals(edge.path("key").asText()))
            ((ObjectNode) edge).put("to", "PARTNER");
        publish(source); long instance = start();
        var roster = reads.listTasks(instance, 0, 100).items();
        complete(roster.get(0), "APPROVE", null, "f022-admin");
        assertThat(reads.listPreviousDecisions(roster.get(1).id(), 0, 20).items()).isEmpty();
        complete(roster.get(1), "REJECT", "independent rejection", "f022-admin");
        assertThat(reads.findTask(roster.get(2).id()).orElseThrow().status()).isEqualTo(TaskStatus.CANCELLED);
        var page = reads.listPreviousDecisions(open(instance).id(), 0, 20);
        assertThat(page.items()).extracting(item -> item.taskId()).containsExactly(roster.get(0).id(), roster.get(1).id());
        assertThat(page.items()).extracting(item -> item.outcomeKey()).containsExactly("APPROVE", "REJECT");
        assertThat(page.items()).extracting(item -> item.comment()).containsExactly(null, "independent rejection");
        assertThat(page.nextAfterSequence()).isNull();
    }

    @Test void nodeOverridesReplaceTheRootAndSelectedSkippedSourcesRemainEmpty() throws Exception {
        var definition = fixture();
        for (var node : definition.path("nodes")) {
            if ("PARTNER".equals(node.path("key").asText()))
                ((ObjectNode) node).putObject("previousDecisions").put("mode", "NONE");
            if ("FINAL".equals(node.path("key").asText()))
                ((ObjectNode) node).putObject("previousDecisions").put("mode", "SELECTED").putArray("nodeKeys").add("PARTNER");
        }
        for (var edge : definition.path("transitions")) if ("managerReject".equals(edge.path("key").asText()))
            ((ObjectNode) edge).put("to", "FINAL");
        publish(definition);
        long instance = start();
        complete(open(instance), "APPROVE", "root-visible source", "f022-admin");
        var partner = open(instance);
        assertThat(reads.previousDecisionsPolicy(partner.id()).mode().name()).isEqualTo("NONE");
        assertThat(reads.listPreviousDecisions(partner.id(), 0, 20).items()).isEmpty();
        complete(partner, "APPROVE", "selected source", "f022-reviewer");
        var receiving = open(instance);
        assertThat(reads.previousDecisionsPolicy(receiving.id()).mode().name()).isEqualTo("SELECTED");
        assertThat(reads.listPreviousDecisions(receiving.id(), 0, 20).items())
                .extracting(item -> item.taskId()).containsExactly(partner.id());
        long skippedInstance = start();
        complete(open(skippedInstance), "REJECT", "skip the selected partner stage", "f022-admin");
        var skippedPage = reads.listPreviousDecisions(open(skippedInstance).id(), 0, 20);
        assertThat(skippedPage.items()).isEmpty();
        assertThat(skippedPage.nextAfterSequence()).isNull();
    }

    @Test void sharedReceivingPeersLoseMetadataAndContinuationAccessWhenMembershipChanges() throws Exception {
        var definition = fixture();
        for (var node : definition.path("nodes")) if ("PARTNER".equals(node.path("key").asText()))
            ((ObjectNode) node.path("assignment")).put("resolver", "sandbox.sharedGroupReviewer");
        var target = SandboxSharedAssignmentProvider.FINANCE;
        membership.replaceMembers(target, Set.of("f022-reviewer", "f022-peer"));
        try {
            publish(definition); long instance = start(); var source = open(instance);
            complete(source, "APPROVE", "shared with current receiving peers", "f022-admin");
            var receiving = open(instance); var peer = session("f022-peer");
            var response = mvc.perform(get("/console/api/tasks/" + receiving.id() + "/previous-decisions").session(peer))
                    .andReturn().getResponse();
            assertThat(response.getStatus()).isEqualTo(200);
            String cursor = json.readTree(response.getContentAsString()).path("items").get(0).path("historySequence").asText();
            assertThat(mvc.perform(get("/console/api/tasks/" + source.id() + "/submission").session(peer))
                    .andReturn().getResponse().getStatus()).isEqualTo(404);
            membership.replaceMembers(target, Set.of("f022-reviewer"));
            assertThat(mvc.perform(get("/console/api/tasks/" + receiving.id() + "/contract").session(peer))
                    .andReturn().getResponse().getStatus()).isEqualTo(404);
            assertThat(mvc.perform(get("/console/api/tasks/" + receiving.id() + "/previous-decisions")
                    .param("afterSequence", cursor).session(peer)).andReturn().getResponse().getStatus()).isEqualTo(404);
        } finally {
            membership.replaceMembers(target, Set.of(settings.reviewer, settings.reviewer2));
        }
    }

    @Test void receivingReaderGetsOnlyTheProjectionAndCannotReadSourceSubmission() throws Exception {
        publish(fixture()); long instance = start(); var source = open(instance);
        complete(source, "APPROVE", "shared accepted comment", "f022-admin");
        var receiving = open(instance); var reviewer = session("f022-reviewer");
        var contract = mvc.perform(get("/console/api/tasks/" + receiving.id() + "/contract").session(reviewer)).andReturn().getResponse();
        assertThat(contract.getStatus()).isEqualTo(200);
        assertThat(json.readTree(contract.getContentAsString()).path("previousDecisions").path("mode").asText()).isEqualTo("ALL_PREVIOUS");
        var response = mvc.perform(get("/console/api/tasks/" + receiving.id() + "/previous-decisions").session(reviewer)).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        var item = json.readTree(response.getContentAsString()).path("items").get(0);
        assertThat(item.path("taskId").asText()).isEqualTo(Long.toString(source.id()));
        assertThat(item.path("comment").asText()).isEqualTo("shared accepted comment");
        var fields = new ArrayList<String>(); item.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("taskId", "activityId", "nodeKey", "nodeName", "outcomeKey", "outcomeLabel",
                "actorKind", "actorRef", "occurredAt", "historySequence", "comment");
        assertThat(mvc.perform(get("/console/api/tasks/" + source.id() + "/submission").session(reviewer))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
        // Assignment read authority is rechecked for both metadata and cursor requests.
        var stranger = session("unassigned-reviewer");
        assertThat(mvc.perform(get("/console/api/tasks/" + receiving.id() + "/contract").session(stranger))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
        assertThat(mvc.perform(get("/console/api/tasks/" + receiving.id() + "/previous-decisions")
                .param("afterSequence", item.path("historySequence").asText()).session(stranger))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
    }

    @ParameterizedTest @ValueSource(strings = {"cutoff", "activityCompletion", "taskCompletion", "outcome"})
    void unavailableOrInconsistentRequiredFactsFailIntegrityHandling(String corruption) throws Exception {
        publish(fixture()); long instance = start(); var source = open(instance);
        complete(source, "APPROVE", "retained", "f022-admin"); var receiving = open(instance);
        tx().executeWithoutResult(t -> {
            if ("outcome".equals(corruption)) jdbc.update("UPDATE wf.workflow_history SET details_json=JSON_MODIFY(details_json, '$.outcomeKey', 'UNKNOWN') WHERE instance_id=? AND task_id=? AND event_type='TASK_COMPLETED'", instance, source.id());
            else {
                String event = switch (corruption) { case "cutoff" -> "ACTIVITY_ENTERED"; case "activityCompletion" -> "ACTIVITY_COMPLETED"; default -> "TASK_COMPLETED"; };
                long activity = "cutoff".equals(corruption) ? receiving.activityId() : source.activityId();
                jdbc.update("DELETE FROM wf.workflow_history WHERE instance_id=? AND activity_id=? AND event_type=?", instance, activity, event);
            }
        });
        assertThatThrownBy(() -> reads.listPreviousDecisions(receiving.id(), 0, 20))
                .isInstanceOfSatisfying(WorkflowExecutionException.class,
                        failure -> assertThat(failure.category()).isEqualTo(WorkflowExecutionException.Category.HOST_CONFIGURATION));
    }

    private ObjectNode fixture() throws Exception {
        Path path = Path.of("docs/features/F011-sequential-human-stages/three-stage-review.json");
        if (!Files.exists(path)) path = Path.of("..").resolve(path);
        var source = (ObjectNode) json.readTree(Files.readString(path)); source.put("key", marker);
        source.putObject("previousDecisions").put("mode", "ALL_PREVIOUS"); return source;
    }
    private void publish(ObjectNode source) {
        if (catalog.findByCode("SANDBOX_REVIEW").isEmpty())
            ownedTaskType = tx().execute(t -> catalog.create(new CreateTaskType("SANDBOX_REVIEW", marker, null, true)).after().id());
        tx().executeWithoutResult(t -> {
            var context = new DefinitionManagementContext(marker, Instant.now());
            var draft = definitions.importDraft(new ImportWorkflowDraft(source.toString()), context).after();
            versionId = draft.id(); familyId = draft.definitionId();
            var checked = definitions.publish(new CheckWorkflowDefinition(versionId, draft.lockVersion()), context);
            assertThat(checked.published()).withFailMessage(() -> checked.report().toString()).isTrue();
        });
    }
    private long start() {
        var request = tx().execute(t -> requests.create(marker, null, marker, Instant.now()));
        long instance = runtime.start(new SandboxStart(UUID.randomUUID(), versionId, request.id(), 0), actor("f022-admin")).execution().instance().id();
        instances.add(instance); return instance;
    }
    private void complete(WorkflowTaskView task, String outcome, String comment, String actor) {
        runtime.complete(new CompleteWorkflowTask(UUID.randomUUID(), task.instanceId(), task.id(),
                reads.findInstance(task.instanceId()).orElseThrow().lockVersion(), outcome, comment), actor(actor));
    }
    private WorkflowTaskView open(long instance) {
        return reads.listTasks(instance, 0, 100).items().stream().filter(task -> task.status() == TaskStatus.OPEN).findFirst().orElseThrow();
    }
    private static ResolvedTask assigned(String key, String actor) {
        return new ResolvedTask(key, new TaskAssignmentTarget(AssignmentTargetKind.USER, actor));
    }
    private WorkflowCommandContext actor(String actor) { return new WorkflowCommandContext(ActorKind.USER, actor, Instant.now(), marker); }
    private TransactionTemplate tx() { var tx = new TransactionTemplate(transactions); tx.setTimeout(30); return tx; }
    private int historyCount(long instance) { return jdbc.queryForObject("SELECT COUNT(*) FROM wf.workflow_history WHERE instance_id=?", Integer.class, instance); }
    private MockHttpSession session(String actor) {
        var session = new MockHttpSession();
        session.setAttribute("SPRING_SECURITY_CONTEXT", new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated(
                actor, "unused", List.of(new SimpleGrantedAuthority("ROLE_CONSOLE_REVIEWER"))))); return session;
    }
    @AfterEach void cleanupOwnedFixtures() {
        reset(AopTestUtils.getUltimateTargetObject(singleResolver), AopTestUtils.getUltimateTargetObject(allResolver));
        tx().executeWithoutResult(t -> {
            for (long instance : instances) {
                jdbc.update("DELETE FROM wf_test.workflow_event_inbox WHERE event_id IN (SELECT event_id FROM wf_test.workflow_event_outbox WHERE instance_id=?)", instance);
                jdbc.update("DELETE FROM wf_test.workflow_event_outbox WHERE instance_id=?", instance);
                jdbc.update("DELETE FROM wf.workflow_command_receipt WHERE instance_id=?", instance);
                jdbc.update("DELETE FROM wf.workflow_history WHERE instance_id=?", instance);
                jdbc.update("DELETE FROM wf.workflow_variable WHERE instance_id=?", instance);
                jdbc.update("DELETE FROM wf.workflow_task WHERE activity_id IN (SELECT id FROM wf.workflow_activity WHERE instance_id=?)", instance);
                jdbc.update("DELETE FROM wf.workflow_activity WHERE instance_id=?", instance);
                jdbc.update("DELETE FROM wf.workflow_instance WHERE id=?", instance);
            }
            jdbc.update("DELETE FROM wf_test.sandbox_request WHERE name=? AND created_by=?", marker, marker);
            if (versionId != null) jdbc.update("DELETE FROM wf.workflow_definition_version WHERE id=?", versionId);
            if (familyId != null) jdbc.update("DELETE FROM wf.workflow_definition WHERE id=? AND workflow_key=?", familyId, marker);
            if (ownedTaskType != null) jdbc.update("DELETE FROM wf.workflow_task_type WHERE id=? AND code='SANDBOX_REVIEW'", ownedTaskType);
        });
    }
}
