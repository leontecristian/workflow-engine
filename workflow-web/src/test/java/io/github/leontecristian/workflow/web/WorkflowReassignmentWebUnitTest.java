package io.github.leontecristian.workflow.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind;
import io.github.leontecristian.workflow.core.dto.runtime.InstanceStatus;
import io.github.leontecristian.workflow.core.dto.runtime.ReassignWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.TaskAssignmentTarget;
import io.github.leontecristian.workflow.core.dto.runtime.TaskStatus;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowActivityView;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandResult;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowInstanceView;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskAdministrationItem;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskAdministrationPage;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskAdministrationQuery;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskView;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowRuntimeQueryService;
import io.github.leontecristian.workflow.web.configuration.WorkflowRuntimeCommandConfiguration;
import io.github.leontecristian.workflow.web.configuration.WorkflowRuntimeReadConfiguration;
import io.github.leontecristian.workflow.web.dto.runtime.WorkflowReassignmentTarget;
import io.github.leontecristian.workflow.web.dto.runtime.WorkflowReassignmentTargetPage;
import io.github.leontecristian.workflow.web.exception.runtime.WorkflowRuntimeException;
import io.github.leontecristian.workflow.web.integration.runtime.WorkflowRuntimeCommandHost;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowAuthorizedRuntimeReadService;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowTaskEligibilityService;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowRuntimeReadAuthorization;
import io.github.leontecristian.workflow.web.integration.runtime.WorkflowRuntimeReadIdentity;
import io.github.leontecristian.workflow.web.integration.runtime.WorkflowAssignmentDirectory;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

/** Isolated controller/adapter boundaries; no persistence, server or migrations. */
class WorkflowReassignmentWebUnitTest {
    private static final String BODY = "{\"commandId\":\"12345678-1234-1234-1234-123456789abc\","
            + "\"assignment\":{\"kind\":\"GROUP\",\"ref\":\"Finance\"},\"reason\":\" exact \\n reason \"}";

    @Configuration(proxyBeanMethods=false) static class LabelOnlyDirectory {
        @Bean @Primary WorkflowAssignmentDirectory labels() {
            return new WorkflowAssignmentDirectory() {
                @Override public String assignmentLabel(WorkflowTaskView task) { return "Friendly " + task.assignment().ref(); }
            };
        }
    }

    @Test void labelOnlyDirectoryLabelsTasksAndDefaultsAuthorizedSearchToForbidden() throws Exception {
        try (var context = context(WorkflowRuntimeWebUnitTest.Host.class, WorkflowRuntimeWebUnitTest.Reads.class,
                LabelOnlyDirectory.class, WorkflowRuntimeReadConfiguration.class)) {
            var access = context.getBean(WorkflowAuthorizedRuntimeReadService.class);
            var task = task();
            when(access.task("actor", 2)).thenReturn(task);
            when(access.canReassignTask("actor", task)).thenReturn(true);
            when(access.listAdministrationTasks(eq("actor"), any())).thenReturn(new WorkflowTaskAdministrationPage(List.of(
                    new WorkflowTaskAdministrationItem(task, "REVIEW", "Review", "REVIEW", "Review")), null));
            mvc(context).perform(get("/custom/tasks/administration"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].assignmentLabel").value("Friendly Ana"));
            mvc(context).perform(get("/custom/tasks/2/reassignment-targets").param("kind", "USER"))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("REASSIGNMENT_FORBIDDEN"));
            verify(access).task("actor", 2);
            verify(access).canReassignTask("actor", task);
        }
    }

    @Test void malformedAndFailingLabelsFallBackToAssignmentReference() throws Exception {
        try (var context = reads()) {
            var access = context.getBean(WorkflowAuthorizedRuntimeReadService.class);
            var directory = context.getBean(WorkflowAssignmentDirectory.class);
            var task = task();
            when(access.listAdministrationTasks(eq("actor"), any())).thenReturn(new WorkflowTaskAdministrationPage(List.of(
                    new WorkflowTaskAdministrationItem(task, "REVIEW", "Review", "REVIEW", "Review")), null));
            for (String label : new String[]{null, " ", "\ud800", "x".repeat(501)}) {
                when(directory.assignmentLabel(task)).thenReturn(label);
                mvc(context).perform(get("/custom/tasks/administration"))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].assignmentLabel").value("Ana"));
            }
            when(directory.assignmentLabel(task)).thenThrow(new IllegalStateException("private directory failure"));
            mvc(context).perform(get("/custom/tasks/administration"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].assignmentLabel").value("Ana"));
        }
    }

    @Test void mutationAndLookupPreserveCompleteFrozenEnvelope() throws Exception {
        try (var context = commands()) {
            var host = context.getBean(WorkflowRuntimeCommandHost.class);
            var command = new ReassignWorkflowTask(UUID.fromString("12345678-1234-1234-1234-123456789abc"),
                    9007199254740993L, 2, 9007199254740994L,
                    new TaskAssignmentTarget(AssignmentTargetKind.GROUP, "Finance"), " exact \n reason ");
            when(host.reassign(command, "corr")).thenReturn(new WorkflowCommandResult(false, null));
            mvc(context).perform(post("/custom/instances/9007199254740993/tasks/2/reassign")
                    .header("X-Expected-Lock-Version", "9007199254740994").requestAttr("trusted", "corr")
                    .contentType("application/json").content(BODY)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.replayed").value(false));
            verify(host).reassign(command, "corr");
            when(host.replayReassignment(command, "corr")).thenReturn(Optional.empty());
            mvc(context).perform(post("/custom/commands/reassign/replay")
                    .header("X-Expected-Lock-Version", "9007199254740994").requestAttr("trusted", "corr")
                    .contentType("application/json").content(BODY.substring(0, BODY.length() - 1)
                            + ",\"instanceId\":\"9007199254740993\",\"taskId\":\"2\"}"))
                    .andExpect(status().isOk()).andExpect(content().json("{\"found\":false,\"result\":null}"));
            verify(host).replayReassignment(command, "corr");
        }
    }

    @Test void strictReassignmentShapeRejectsInvalidAndUnknownFieldsBeforeHost() throws Exception {
        try (var context = commands()) {
            for (String body : List.of(BODY.replace("GROUP", "OTHER"), BODY.replace("Finance", ""),
                    BODY.replace("Finance\"", "Finance\",\"assignee\":\"fake\""),
                    BODY.replace("Finance\"", "Finance\",\"ref\":\"other\""),
                    BODY.replace(" exact \\n reason ", " "), BODY.replace(" exact \\n reason ", "x".repeat(1001)),
                    BODY.replace("Finance", "\\ud800"), BODY.replace("\"assignment\":{\"kind\":\"GROUP\",\"ref\":\"Finance\"}", "\"assignment\":null"),
                    BODY.substring(0, BODY.length() - 1) + ",\"actor\":\"fake\"}")) {
                mvc(context).perform(post("/custom/instances/1/tasks/2/reassign").header("X-Expected-Lock-Version", "0")
                        .contentType("application/json").content(body)).andExpect(status().isBadRequest());
            }
            mvc(context).perform(post("/custom/instances/1/tasks/2/reassign").header("X-Expected-Lock-Version", "0")
                    .contentType("application/json").content(BODY + " ".repeat(16 * 1024))).andExpect(status().is(413));
            verify(context.getBean(WorkflowRuntimeCommandHost.class), never()).reassign(any(), any());
        }
    }

    @Test void administrationDefaultsOpenAndRetainsFilteredEmptyContinuation() throws Exception {
        try (var context = reads()) {
            var host = context.getBean(WorkflowAuthorizedRuntimeReadService.class);
            when(host.listAdministrationTasks(eq("actor"), any())).thenReturn(new WorkflowTaskAdministrationPage(List.of(), 12L));
            mvc(context).perform(get("/custom/tasks/administration")).andExpect(status().isOk())
                    .andExpect(content().json("{\"items\":[],\"nextAfterId\":\"12\"}"));
            verify(host).requireTaskAdministrationAccess("actor");
            verify(host).listAdministrationTasks("actor", new WorkflowTaskAdministrationQuery(TaskStatus.OPEN, null, null, null, null, null, 0, 50));
            String taskName = "  Réview %_[x].  ";
            mvc(context).perform(get("/custom/tasks/administration").param("status", "ALL").param("taskType", "REVIEW")
                    .param("assignmentKind", "ROLE").param("assignmentRef", "reviewer").param("assigneeRef", "Ana")
                    .param("instanceId", "9").param("afterId", "12").param("limit", "100").param("taskName", taskName))
                    .andExpect(status().isOk());
            verify(host).listAdministrationTasks("actor", new WorkflowTaskAdministrationQuery(null, "REVIEW", AssignmentTargetKind.ROLE,
                    "reviewer", "Ana", 9L, 12, 100, taskName));
            for (String[] field : List.of(new String[]{"assignmentRef", "Finance"}, new String[]{"status", "BAD"},
                    new String[]{"assigneeRef", ""}, new String[]{"limit", "101"}, new String[]{"unsupported", "x"})) {
                mvc(context).perform(get("/custom/tasks/administration").param(field[0], field[1])).andExpect(status().isBadRequest());
            }
            mvc(context).perform(get("/custom/tasks/administration").param("status", "OPEN", "ALL")).andExpect(status().isBadRequest());
        }
    }

    @Test void administrationTaskNameAcceptsBoundAndRejectsInvalidTextBeforeHost() throws Exception {
        try (var context = reads()) {
            var host = context.getBean(WorkflowAuthorizedRuntimeReadService.class);
            when(host.listAdministrationTasks(eq("actor"), any())).thenReturn(new WorkflowTaskAdministrationPage(List.of(), null));
            String taskName = "x".repeat(200);
            mvc(context).perform(get("/custom/tasks/administration").param("taskName", taskName))
                    .andExpect(status().isOk());
            verify(host).listAdministrationTasks("actor", new WorkflowTaskAdministrationQuery(TaskStatus.OPEN, null, null,
                    null, null, null, 0, 50, taskName));
        }
        try (var context = reads()) {
            var host = context.getBean(WorkflowAuthorizedRuntimeReadService.class);
            for (String taskName : List.of("", " \t", "x".repeat(201), "\ud800")) {
                mvc(context).perform(get("/custom/tasks/administration").param("taskName", taskName))
                        .andExpect(status().isBadRequest());
            }
            mvc(context).perform(get("/custom/tasks/administration").param("taskName", "Review", "Other"))
                    .andExpect(status().isBadRequest());
            verify(host, never()).listAdministrationTasks(eq("actor"), any());
        }
    }

    @Test void administrationGrantPrecedesQueryParsing() throws Exception {
        try (var context = reads()) {
            var host = context.getBean(WorkflowAuthorizedRuntimeReadService.class);
            org.mockito.Mockito.doThrow(new WorkflowRuntimeException(403, "ADMINISTRATION_FORBIDDEN"))
                    .when(host).requireTaskAdministrationAccess("actor");
            mvc(context).perform(get("/custom/tasks/administration").param("unsupported", "x")).andExpect(status().isForbidden());
            verify(host, never()).listAdministrationTasks(eq("actor"), any());
        }
    }

    @Test void administrationProjectsOnlyJoinedAuthorizedMetadata() throws Exception {
        try (var context = reads()) {
            var host = context.getBean(WorkflowAuthorizedRuntimeReadService.class);
            var task = new WorkflowTaskView(2, 3, 1, 4, "REVIEW", new TaskAssignmentTarget(AssignmentTargetKind.USER, "Ana"),
                    TaskStatus.COMPLETED, "APPROVE", Instant.parse("2026-11-14T00:00:00Z"), null,
                    Instant.parse("2026-11-14T01:00:00Z"), "Ana", null, "Ana");
            when(context.getBean(WorkflowAssignmentDirectory.class).assignmentLabel(task)).thenReturn("Ana Reviewer");
            when(host.listAdministrationTasks(eq("actor"), any())).thenReturn(new WorkflowTaskAdministrationPage(List.of(
                    new WorkflowTaskAdministrationItem(task, "REVIEW", "Pinned review", "REVIEW", "Review type")), null));
            mvc(context).perform(get("/custom/tasks/administration").param("status", "COMPLETED"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value("2"))
                    .andExpect(jsonPath("$.items[0].nodeName").value("Pinned review"))
                    .andExpect(jsonPath("$.items[0].taskType").value("REVIEW"))
                    .andExpect(jsonPath("$.items[0].assignmentLabel").value("Ana Reviewer"))
                    .andExpect(jsonPath("$.items[0].outcomeKey").doesNotExist())
                    .andExpect(jsonPath("$.items[0].closedBy").doesNotExist());
            org.mockito.Mockito.verifyNoInteractions(context.getBean(WorkflowRuntimeQueryService.class));
        }
    }

    @Test void targetDirectoryRequiresExactTaskAndReassignmentGrant() throws Exception {
        try (var context = reads()) {
            var host = context.getBean(WorkflowAuthorizedRuntimeReadService.class);
            var task = task();
            when(host.task("actor", 2)).thenReturn(task);
            mvc(context).perform(get("/custom/tasks/2/reassignment-targets").param("kind", "USER"))
                    .andExpect(status().isForbidden());
            verify(context.getBean(WorkflowAssignmentDirectory.class), never()).reassignmentTargets(any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyInt());
            when(host.canReassignTask("actor", task)).thenReturn(true);
            when(context.getBean(WorkflowAssignmentDirectory.class).reassignmentTargets(new io.github.leontecristian.workflow.core.dto.runtime.TaskEligibilityContext(task, "actor"), AssignmentTargetKind.USER, "An", null, 20)).thenReturn(
                    new WorkflowReassignmentTargetPage(List.of(new WorkflowReassignmentTarget("Ana", "Ana Reviewer")), "next"));
            mvc(context).perform(get("/custom/tasks/2/reassignment-targets").param("kind", "USER").param("query", "An"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].ref").value("Ana"))
                    .andExpect(jsonPath("$.nextCursor").value("next"));
            for (String[] field : List.of(new String[]{"query", "x".repeat(201)}, new String[]{"cursor", "x".repeat(1001)},
                    new String[]{"limit", "51"}, new String[]{"kind", "INVALID"})) {
                var request = get("/custom/tasks/2/reassignment-targets");
                if (!"kind".equals(field[0])) request.param("kind", "USER");
                mvc(context).perform(request.param(field[0], field[1])).andExpect(status().isBadRequest());
            }
            when(host.task("actor", 2)).thenThrow(new WorkflowRuntimeException(404, "NOT_FOUND"));
            mvc(context).perform(get("/custom/tasks/2/reassignment-targets").param("kind", "USER")).andExpect(status().isNotFound());
        }
    }

    @Test void availabilityIsOptionalAndClosedTasksAreInspectionOnly() throws Exception {
        try (var context = reads()) {
            var host = context.getBean(WorkflowAuthorizedRuntimeReadService.class);
            var query = context.getBean(WorkflowRuntimeQueryService.class);
            var task = task();
            var instance = mock(WorkflowInstanceView.class);
            var activity = mock(WorkflowActivityView.class);
            when(host.task("actor", 2)).thenReturn(task);
            when(query.findInstance(1)).thenReturn(Optional.of(instance));
            when(query.findActivity(3)).thenReturn(Optional.of(activity));
            when(instance.status()).thenReturn(InstanceStatus.ACTIVE);
            when(instance.currentNodeKey()).thenReturn("REVIEW");
            when(instance.lockVersion()).thenReturn(9007199254740994L);
            when(activity.nodeKey()).thenReturn("REVIEW");
            when(host.canReassignTask("actor", task)).thenReturn(false);
            mvc(context).perform(get("/custom/tasks/2/reassignment-availability")).andExpect(status().isOk())
                    .andExpect(content().json("{\"allowed\":false,\"instanceLockVersion\":\"9007199254740994\"}"));
            when(host.canReassignTask("actor", task)).thenReturn(true);
            mvc(context).perform(get("/custom/tasks/2/reassignment-availability")).andExpect(jsonPath("$.allowed").value(true));
            when(instance.status()).thenReturn(InstanceStatus.CANCELLED);
            mvc(context).perform(get("/custom/tasks/2/reassignment-availability")).andExpect(jsonPath("$.allowed").value(false));
        }
    }

    @Test void reassignmentErrorsKeepSpecifiedStatuses() throws Exception {
        try (var context = commands()) {
            var host = context.getBean(WorkflowRuntimeCommandHost.class);
            for (var category : List.of(WorkflowExecutionException.Category.REASSIGNMENT_FORBIDDEN,
                    WorkflowExecutionException.Category.INVALID_ASSIGNMENT)) {
                org.mockito.Mockito.doThrow(new WorkflowExecutionException(category)).when(host).reassign(any(), any());
                mvc(context).perform(post("/custom/instances/1/tasks/2/reassign").header("X-Expected-Lock-Version", "0")
                        .contentType("application/json").content(BODY))
                        .andExpect(status().is(category == WorkflowExecutionException.Category.REASSIGNMENT_FORBIDDEN ? 403 : 422));
            }
        }
    }

    private WorkflowTaskView task() {
        return new WorkflowTaskView(2, 3, 1, 4, "REVIEW", new TaskAssignmentTarget(AssignmentTargetKind.USER, "Ana"),
                TaskStatus.OPEN, null, Instant.parse("2026-11-14T00:00:00Z"), null, null, null, null, "Ana");
    }
    private AnnotationConfigWebApplicationContext commands() {
        return context(WorkflowRuntimeWebUnitTest.Host.class, WorkflowRuntimeWebUnitTest.Commands.class, WorkflowRuntimeCommandConfiguration.class);
    }
    private AnnotationConfigWebApplicationContext reads() {
        return context(WorkflowRuntimeWebUnitTest.Host.class, WorkflowRuntimeWebUnitTest.Reads.class, WorkflowRuntimeReadConfiguration.class);
    }
    private AnnotationConfigWebApplicationContext context(Class<?>... types) {
        var context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext()); context.register(types); context.refresh(); return context;
    }
    private MockMvc mvc(AnnotationConfigWebApplicationContext context) { return MockMvcBuilders.webAppContextSetup(context).build(); }
}
