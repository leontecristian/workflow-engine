package io.github.leontecristian.workflow.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.leontecristian.workflow.core.dto.runtime.ActorKind;
import io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind;
import io.github.leontecristian.workflow.core.dto.runtime.CompleteWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.TaskAssignmentTarget;
import io.github.leontecristian.workflow.core.dto.runtime.TaskStatus;
import io.github.leontecristian.workflow.core.dto.runtime.TaskSubmission;
import io.github.leontecristian.workflow.core.dto.runtime.TaskSubmissionPage;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandResult;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowJsonValue;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskPage;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskView;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.core.service.definition.WorkflowDefinitionService;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowRuntimeQueryService;
import io.github.leontecristian.workflow.core.service.tasktype.TaskTypeCatalogService;
import io.github.leontecristian.workflow.web.configuration.WorkflowAdministrationConfiguration;
import io.github.leontecristian.workflow.web.configuration.WorkflowAdministrationOptions;
import io.github.leontecristian.workflow.web.configuration.WorkflowRuntimeCommandConfiguration;
import io.github.leontecristian.workflow.web.configuration.WorkflowRuntimeOptions;
import io.github.leontecristian.workflow.web.configuration.WorkflowRuntimeReadConfiguration;
import io.github.leontecristian.workflow.web.configuration.WorkflowViewerConfiguration;
import io.github.leontecristian.workflow.web.configuration.WorkflowViewerOptions;
import io.github.leontecristian.workflow.web.controller.runtime.WorkflowRuntimeCommandController;
import io.github.leontecristian.workflow.web.controller.runtime.WorkflowRuntimeReadController;
import io.github.leontecristian.workflow.web.exception.runtime.WorkflowRuntimeException;
import io.github.leontecristian.workflow.web.integration.administration.WorkflowAdministrationHost;
import io.github.leontecristian.workflow.web.integration.instance.WorkflowViewerAccess;
import io.github.leontecristian.workflow.web.integration.runtime.WorkflowRuntimeCommandHost;
import io.github.leontecristian.workflow.web.integration.runtime.WorkflowRuntimeReadHost;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

/** Isolated MVC contexts and doubles; no persistence, migrations or listener. */
class WorkflowRuntimeWebUnitTest {
    @Configuration(proxyBeanMethods=false) @EnableWebMvc
    static class Host extends HostBeans {}
    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration({JacksonAutoConfiguration.class, HttpMessageConvertersAutoConfiguration.class, WebMvcAutoConfiguration.class})
    static class BootHost extends HostBeans {}
    static class HostBeans {
        @Bean WorkflowRuntimeOptions runtimeOptions() { return new WorkflowRuntimeOptions("/custom", "trusted"); }
        @Bean WorkflowRuntimeQueryService reads() { return mock(WorkflowRuntimeQueryService.class); }
        @Bean WorkflowDefinitionService definitions() { return mock(WorkflowDefinitionService.class); }
        @Bean TaskTypeCatalogService catalog() { return mock(TaskTypeCatalogService.class); }
        @Bean WorkflowViewerOptions viewerOptions() { return new WorkflowViewerOptions("/custom", "trusted"); }
        @Bean WorkflowViewerAccess viewerAccess() { return mock(WorkflowViewerAccess.class); }
        @Bean WorkflowAdministrationOptions administrationOptions() { return new WorkflowAdministrationOptions("/custom", "trusted"); }
        @Bean WorkflowAdministrationHost administrationHost() { return mock(WorkflowAdministrationHost.class); }
    }
    @Configuration(proxyBeanMethods=false) static class Reads {
        @Bean WorkflowRuntimeReadHost readHost() { return mock(WorkflowRuntimeReadHost.class); }
    }
    @Configuration(proxyBeanMethods=false) static class Commands {
        @Bean WorkflowRuntimeCommandHost commandHost() { return mock(WorkflowRuntimeCommandHost.class); }
    }
    private AnnotationConfigWebApplicationContext context(Class<?>... types) {
        var c = new AnnotationConfigWebApplicationContext(); c.setServletContext(new MockServletContext());
        c.register(types); c.refresh(); return c;
    }
    private MockMvc mvc(AnnotationConfigWebApplicationContext c) { return MockMvcBuilders.webAppContextSetup(c).build(); }
    @Test void scanAloneDoesNotActivateAndAdaptersAreRequired() throws Exception {
        try(var c = new AnnotationConfigWebApplicationContext()) {
            c.setServletContext(new MockServletContext()); c.register(Host.class);
            c.scan("io.github.leontecristian.workflow.web.configuration", "io.github.leontecristian.workflow.web.controller"); c.refresh();
            assertThat(c.getBeansOfType(WorkflowRuntimeReadController.class)).isEmpty();
            assertThat(c.getBeansOfType(WorkflowRuntimeCommandController.class)).isEmpty();
            mvc(c).perform(get("/custom/instances")).andExpect(status().isNotFound());
        }
        assertThatThrownBy(()->context(Host.class, WorkflowRuntimeReadConfiguration.class)).hasMessageContaining("WorkflowRuntimeReadHost");
        assertThatThrownBy(()->context(Host.class, WorkflowRuntimeCommandConfiguration.class)).hasMessageContaining("WorkflowRuntimeCommandHost");
    }
    @Test void readsRemainIndependentAndRetainAuthorizedEmptyContinuation() throws Exception {
        try(var c=context(Host.class, Reads.class, WorkflowRuntimeReadConfiguration.class)) {
            var host=c.getBean(WorkflowRuntimeReadHost.class); var mvc=mvc(c);
            when(host.listTasks(1,0,50)).thenReturn(new WorkflowTaskPage(List.of(),42L));
            mvc.perform(get("/custom/instances/1/tasks")).andExpect(status().isOk())
                .andExpect(content().json("{\"items\":[],\"nextAfterId\":\"42\"}"));
            mvc.perform(post("/custom/commands/complete/replay")).andExpect(status().isNotFound());
            for(String q:List.of("unknown=1","limit=0","limit=2&limit=3","afterId=01"))
                mvc.perform(get("/custom/instances/1/tasks?"+q)).andExpect(status().isBadRequest());
            when(host.task(5)).thenThrow(new WorkflowRuntimeException(404,"NOT_FOUND"));
            mvc.perform(get("/custom/tasks/5?unchanged=1")).andExpect(status().isNotFound());
            verify(host).task(5);
            verifyNoInteractions(c.getBean(WorkflowRuntimeQueryService.class));
        }
    }
    @Test void sharedAssignmentProjectionAndExactSubmissionFilterPreserveSourceContinuation() throws Exception {
        try(var c=context(Host.class,Reads.class,WorkflowRuntimeReadConfiguration.class)) {
            var reads=c.getBean(WorkflowRuntimeQueryService.class); var host=c.getBean(WorkflowRuntimeReadHost.class); var mvc=mvc(c);
            var task=new io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskView(5,2,1,3,"REVIEW",
                    new io.github.leontecristian.workflow.core.dto.runtime.TaskAssignmentTarget(
                            io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind.GROUP,"finance"),
                    io.github.leontecristian.workflow.core.dto.runtime.TaskStatus.OPEN,null,java.time.Instant.EPOCH,null,null,null,null,null);
            when(host.task(5)).thenReturn(task);
            mvc.perform(get("/custom/tasks/5")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.assignment.kind").value("GROUP"))
                    .andExpect(jsonPath("$.assignment.ref").value("finance"))
                    .andExpect(jsonPath("$.assignedUserRef").doesNotExist());
            var submission=new io.github.leontecristian.workflow.core.dto.runtime.TaskSubmission(5,2,1,9,
                    java.util.UUID.randomUUID(),io.github.leontecristian.workflow.core.dto.runtime.ActorKind.USER,
                    "other",java.time.Instant.EPOCH,"APPROVE","private",java.util.Map.of());
            when(reads.findTask(5)).thenReturn(Optional.of(task));
            when(reads.listActivitySubmissions(2,0,50)).thenReturn(new io.github.leontecristian.workflow.core.dto.runtime.TaskSubmissionPage(List.of(submission),null));
            when(host.canReadTask(task)).thenReturn(false);
            mvc.perform(get("/custom/activities/2/submissions")).andExpect(status().isOk())
                    .andExpect(content().json("{\"items\":[],\"nextAfterTaskId\":null}"));
        }
    }
    @Test void readOnlyContractIncludesPinnedPolicyWithoutOwnershipActions() throws Exception {
        try (var c = context(Host.class, Reads.class, WorkflowRuntimeReadConfiguration.class)) {
            var host = c.getBean(WorkflowRuntimeReadHost.class);
            var reads = c.getBean(WorkflowRuntimeQueryService.class);
            var definitions = c.getBean(WorkflowDefinitionService.class);
            var task = new io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskView(5, 2, 1, 3, "REVIEW",
                    new io.github.leontecristian.workflow.core.dto.runtime.TaskAssignmentTarget(
                            io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind.GROUP, "finance"),
                    io.github.leontecristian.workflow.core.dto.runtime.TaskStatus.OPEN,
                    null, java.time.Instant.EPOCH, null, null, null, null, "reviewer");
            var instance = mock(io.github.leontecristian.workflow.core.dto.runtime.WorkflowInstanceView.class);
            var activity = mock(io.github.leontecristian.workflow.core.dto.runtime.WorkflowActivityView.class);
            var version = mock(io.github.leontecristian.workflow.core.dto.definition.DefinitionVersionView.class);
            when(host.task(5)).thenReturn(task);
            when(host.canCompleteTask(task)).thenReturn(true);
            when(host.canReleaseTask(task)).thenReturn(true);
            when(host.canAdministrativelyReleaseTask(task)).thenReturn(true);
            when(reads.findInstance(1)).thenReturn(Optional.of(instance));
            when(instance.definitionVersionId()).thenReturn(4L);
            when(instance.lockVersion()).thenReturn(9L);
            when(reads.findActivity(2)).thenReturn(Optional.of(activity));
            when(activity.nodeKey()).thenReturn("REVIEW");
            when(reads.taskClaimPolicy(1, 5))
                    .thenReturn(io.github.leontecristian.workflow.core.dto.runtime.TaskClaimPolicy.REQUIRED);
            when(definitions.findVersion(4)).thenReturn(Optional.of(version));
            when(version.documentJson()).thenReturn("""
                    {"nodes":[{"key":"REVIEW","taskType":"REVIEW","outcomes":[]}]}
                    """);

            mvc(c).perform(get("/custom/tasks/5/contract")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.claimPolicy").value("REQUIRED"))
                    .andExpect(jsonPath("$.resultContract.mode").value("FORBIDDEN"))
                    .andExpect(jsonPath("$.canComplete").value(true))
                    .andExpect(jsonPath("$.canClaim").value(false))
                    .andExpect(jsonPath("$.canRelease").value(false))
                    .andExpect(jsonPath("$.canAdminRelease").value(false));
            when(version.documentJson()).thenReturn("""
                    {"nodes":[{"key":"REVIEW","taskType":"REVIEW","outcomes":[],"resultContract":
                      {"mode":"REQUIRED","schema":{"type":"object","properties":{"amount":{"minimum":9007199254740993.00}}}}}]}
                    """);
            mvc(c).perform(get("/custom/tasks/5/contract")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.resultContract.mode").value("REQUIRED"))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("9007199254740993.00")));
            verify(host, org.mockito.Mockito.never()).instance(1);
            verify(host, org.mockito.Mockito.never()).activity(2);
        }
    }

    @Test void completionAndReplayPreserveRawResultNumbersAndEnforceActualUtf8Boundary() throws Exception {
        try (var c = context(BootHost.class, Commands.class, Reads.class, WorkflowRuntimeCommandConfiguration.class,
                WorkflowRuntimeReadConfiguration.class)) {
            var host = c.getBean(WorkflowRuntimeCommandHost.class);
            var readHost = c.getBean(WorkflowRuntimeReadHost.class);
            var reads = c.getBean(WorkflowRuntimeQueryService.class);
            var mvc = mvc(c);
            var mapper = c.getBean(ObjectMapper.class);
            assertThat(c.getBean(RequestMappingHandlerAdapter.class).getMessageConverters())
                    .filteredOn(MappingJackson2HttpMessageConverter.class::isInstance)
                    .anyMatch(converter -> ((MappingJackson2HttpMessageConverter) converter).getObjectMapper() == mapper);
            assertThat(mapper.version().toString()).isEqualTo("2.22.3");
            assertThat(mapper.writeValueAsString(Instant.EPOCH)).isEqualTo("\"1970-01-01T00:00:00Z\"");

            String base = "{\"commandId\":\"12345678-1234-1234-1234-123456789abc\",\"outcomeKey\":\"APPROVE\",\"comment\":\"漢\",\"result\":";
            String result = "{\"amount\":9007199254740993.00,\"zero\":0e+2147483647}";
            String body = base + result + "}";
            String complete = "/custom/instances/1/tasks/5/complete";
            String replay = "/custom/commands/complete/replay";
            String replayBody = base + result + ",\"instanceId\":\"1\",\"taskId\":\"5\"}";
            String requestAtLimit = body + " ".repeat(256 * 1024 - body.getBytes(StandardCharsets.UTF_8).length);
            when(host.complete(any(), any())).thenReturn(new WorkflowCommandResult(false, null));
            when(host.replayComplete(any(), any())).thenReturn(Optional.empty());
            mvc.perform(post(complete).contentType("application/json").header("X-Expected-Lock-Version", "0")
                    .content(requestAtLimit.getBytes(StandardCharsets.UTF_8))).andExpect(status().isOk());
            mvc.perform(post(replay).contentType("application/json").header("X-Expected-Lock-Version", "0")
                    .content(replayBody.getBytes(StandardCharsets.UTF_8))).andExpect(status().isOk());
            var command = new CompleteWorkflowTask(UUID.fromString("12345678-1234-1234-1234-123456789abc"),
                    1, 5, 0, "APPROVE", "漢", WorkflowJsonValue.parse(result));
            verify(host).complete(command, null);
            verify(host).replayComplete(command, null);
            mvc.perform(post(complete).contentType("application/json").header("X-Expected-Lock-Version", "0")
                    .content((requestAtLimit + " ").getBytes(StandardCharsets.UTF_8))).andExpect(status().is(413));
            for (String invalid : List.of("null", "[]", "1", "{\"nested\":{\"x\":1,\"x\":2}}")) {
                mvc.perform(post(complete).contentType("application/json").header("X-Expected-Lock-Version", "0")
                        .content(base + invalid + "}")).andExpect(status().isBadRequest());
                mvc.perform(post(replay).contentType("application/json").header("X-Expected-Lock-Version", "0")
                        .content(base + invalid + ",\"instanceId\":\"1\",\"taskId\":\"5\"}"))
                        .andExpect(status().isBadRequest());
            }
            org.mockito.Mockito.verifyNoMoreInteractions(host);

            when(readHost.task(5)).thenReturn(task(5));
            mvc.perform(get("/custom/tasks/5/submission")).andExpect(content().string("null"));
            when(reads.findTaskSubmission(5)).thenReturn(Optional.of(submission(5, null)));
            mvc.perform(get("/custom/tasks/5/submission")).andExpect(jsonPath("$.result").doesNotHaveJsonPath());
            when(reads.findTaskSubmission(5)).thenReturn(Optional.of(submission(5, command.result())));
            mvc.perform(get("/custom/tasks/5/submission")).andExpect(jsonPath("$.occurredAt").value("1970-01-01T00:00:00Z"))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString(command.result().toJson())));
            when(readHost.task(5)).thenThrow(new WorkflowRuntimeException(404, "NOT_FOUND"));
            mvc.perform(get("/custom/tasks/5/submission")).andExpect(status().isNotFound());
            verify(reads, times(3)).findTaskSubmission(5);

            var open = mock(WorkflowTaskView.class);
            when(open.status()).thenReturn(TaskStatus.OPEN);
            org.mockito.Mockito.doReturn(open).doThrow(new WorkflowRuntimeException(404, "NOT_FOUND"))
                    .when(readHost).task(5);
            mvc.perform(get("/custom/tasks/5/submission")).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.result").doesNotExist());
            verify(reads, times(4)).findTaskSubmission(5);
        }
    }

    @Test void resultFailuresExposeBoundedDiagnosticsAndSanitizedCategories() throws Exception {
        try (var c = context(Host.class, Commands.class, WorkflowRuntimeCommandConfiguration.class)) {
            var host = c.getBean(WorkflowRuntimeCommandHost.class);
            var mvc = mvc(c);
            var request = post("/custom/instances/1/tasks/5/complete").contentType("application/json")
                    .header("X-Expected-Lock-Version", "0").requestAttr("trusted", "safe")
                    .content("{\"commandId\":\"12345678-1234-1234-1234-123456789abc\",\"outcomeKey\":\"APPROVE\",\"result\":{}}");
            doThrow(WorkflowExecutionException.invalidResultSchema("/amount", "/properties/amount/minimum"))
                    .when(host).complete(any(), any());
            mvc.perform(request).andExpect(status().is(422))
                    .andExpect(jsonPath("$.code").value("RESULT_SCHEMA_INVALID"))
                    .andExpect(jsonPath("$.diagnostics[0].path").value("/amount"))
                    .andExpect(jsonPath("$.diagnostics[0].schemaPath").value("/properties/amount/minimum"));

            doThrow(WorkflowExecutionException.rejectResult("/" + "x".repeat(4200))).when(host).complete(any(), any());
            mvc.perform(request).andExpect(status().is(422)).andExpect(jsonPath("$.code").value("RESULT_REJECTED"))
                    .andExpect(jsonPath("$.diagnostics[0].path").doesNotExist())
                    .andExpect(jsonPath("$.diagnostics[0].locationOmitted").value(true));

            doThrow(WorkflowExecutionException.resultLimit("The result exceeds the validation work limit."))
                    .when(host).complete(any(), any());
            mvc.perform(request).andExpect(status().is(422)).andExpect(jsonPath("$.code").value("LIMIT_EXCEEDED"))
                    .andExpect(jsonPath("$.message").value("The result exceeds the validation work limit."));

            doThrow(new WorkflowExecutionException(WorkflowExecutionException.Category.SUBMISSION_UNAVAILABLE))
                    .when(host).complete(any(), any());
            mvc.perform(request).andExpect(status().is(422))
                    .andExpect(jsonPath("$.code").value("SUBMISSION_UNAVAILABLE"));

            doThrow(new WorkflowExecutionException(WorkflowExecutionException.Category.CONFLICT))
                    .when(host).complete(any(), any());
            mvc.perform(request).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONFLICT"));

            doThrow(new WorkflowExecutionException(WorkflowExecutionException.Category.RESULT_VALIDATION_FAILED,
                    new IllegalStateException("private provider error"))).when(host).complete(any(), any());
            mvc.perform(request).andExpect(status().isServiceUnavailable())
                    .andExpect(content().json("""
                            {"code":"RESULT_VALIDATION_FAILED","message":"RESULT_VALIDATION_FAILED","diagnostics":[],"correlationId":"safe"}
                            """, org.springframework.test.json.JsonCompareMode.STRICT));
        }
    }

    @Test void filteredSubmissionResponseCapsCombinedUtf8AndRetainsUnreturnedVisibleRow() throws Exception {
        try (var c = context(Host.class, Reads.class, WorkflowRuntimeReadConfiguration.class)) {
            var reads = c.getBean(WorkflowRuntimeQueryService.class);
            var host = c.getBean(WorkflowRuntimeReadHost.class);
            var result = WorkflowJsonValue.parse("{\"note\":\"" + "漢".repeat(15000) + "\"}");
            var rows = new ArrayList<TaskSubmission>();
            for (long id = 1; id <= 26; id++) {
                var task = task(id);
                rows.add(submission(id, result));
                when(reads.findTask(id)).thenReturn(Optional.of(task));
                when(host.canReadTask(task)).thenReturn(id != 13 && id != 26);
            }
            when(reads.listActivitySubmissions(2, 0, 30)).thenReturn(new TaskSubmissionPage(rows.subList(0, 13), 13L));
            when(reads.listActivitySubmissions(2, 13, 30)).thenReturn(new TaskSubmissionPage(rows.subList(13, 26), null));
            when(reads.listActivitySubmissions(2, 24, 30)).thenReturn(new TaskSubmissionPage(rows.subList(24, 26), null));
            byte[] body = mvc(c).perform(get("/custom/activities/2/submissions?limit=30"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(23))
                    .andExpect(jsonPath("$.nextAfterTaskId").value("24"))
                    .andReturn().getResponse().getContentAsByteArray();
            assertThat(body.length).isBetween(1024 * 1024 - 45000, 1024 * 1024);
            mvc(c).perform(get("/custom/activities/2/submissions?limit=30&afterTaskId=24"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                    .andExpect(jsonPath("$.items[0].taskId").value("25"))
                    .andExpect(jsonPath("$.items[0].result.note").value("漢".repeat(15000)))
                    .andExpect(jsonPath("$.nextAfterTaskId").doesNotExist());
        }
    }

    private WorkflowTaskView task(long id) {
        return new WorkflowTaskView(id, 2, 1, 3, "REVIEW", new TaskAssignmentTarget(AssignmentTargetKind.GROUP, "finance"),
                TaskStatus.COMPLETED, null, Instant.EPOCH, null, null, null, null, null);
    }

    private TaskSubmission submission(long id, WorkflowJsonValue result) {
        return new TaskSubmission(id, 2, 1, id + 10, new UUID(0, id), ActorKind.USER,
                "reviewer", Instant.EPOCH, "APPROVE", null, Map.of(), result);
    }
    @Test void namedOutputUsesInstanceAccessWhileTheSubmissionRequiresExactTaskAccess() throws Exception {
        try(var c=context(Host.class,Reads.class,WorkflowRuntimeReadConfiguration.class)) {
            var reads=c.getBean(WorkflowRuntimeQueryService.class);var host=c.getBean(WorkflowRuntimeReadHost.class);var mvc=mvc(c);
            var value=new io.github.leontecristian.workflow.core.dto.runtime.WorkflowValue(
                io.github.leontecristian.workflow.core.dto.runtime.VariableValueType.JSON,
                io.github.leontecristian.workflow.core.dto.runtime.WorkflowJsonValue.parse("{\"amount\":9007199254740993.00}"));
            when(reads.listVariables(1,null,50,null)).thenReturn(new io.github.leontecristian.workflow.core.dto.runtime.WorkflowVariablePage(1,7,
                List.of(new io.github.leontecristian.workflow.core.dto.runtime.WorkflowVariableView("reviewResult",value.type(),true,value)),"reviewResult"));
            mvc.perform(get("/custom/instances/1/variables")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("9007199254740993.00")))
                .andExpect(jsonPath("$.items[0].key").value("reviewResult"))
                .andExpect(jsonPath("$.items[0].initialized").value(true))
                .andExpect(jsonPath("$.lockVersion").value("7")).andExpect(jsonPath("$.nextAfterKey").value("reviewResult"));
            verify(host).instance(1);
            when(reads.listVariables(1,"reviewResult",50,7L)).thenThrow(new WorkflowExecutionException(WorkflowExecutionException.Category.STALE_VERSION));
            mvc.perform(get("/custom/instances/1/variables?afterKey=reviewResult&expectedLockVersion=7"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_VERSION"));

            when(host.task(5)).thenThrow(new WorkflowRuntimeException(404, "NOT_FOUND"));
            mvc.perform(get("/custom/tasks/5/submission")).andExpect(status().isNotFound());
            verify(reads, times(0)).findTaskSubmission(5);
        }
    }
    @Test void allGroupsCoexistAndCompletionPreservesExactFrozenInputAndLookupOnlyMiss() throws Exception {
        try(var c=context(Host.class,Reads.class,Commands.class,WorkflowRuntimeReadConfiguration.class,
                WorkflowRuntimeCommandConfiguration.class,WorkflowViewerConfiguration.class,WorkflowAdministrationConfiguration.class)) {
            var host=c.getBean(WorkflowRuntimeCommandHost.class);var mvc=mvc(c);
            when(host.complete(any(),eq("corr"))).thenReturn(new WorkflowCommandResult(false,null));
            String body="{\"commandId\":\"12345678-1234-1234-1234-123456789abc\",\"outcomeKey\":\"APPROVE\",\"comment\":\" exact \"}";
            mvc.perform(post("/custom/instances/9007199254740993/tasks/5/complete").contentType("application/json")
                .header("X-Expected-Lock-Version","9007199254740994").requestAttr("trusted","corr").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.replayed").value(false));
            var captured=org.mockito.ArgumentCaptor.forClass(CompleteWorkflowTask.class);
            verify(host).complete(captured.capture(),eq("corr"));
            assertThat(captured.getValue()).isEqualTo(new CompleteWorkflowTask(java.util.UUID.fromString("12345678-1234-1234-1234-123456789abc"),
                9007199254740993L,5,9007199254740994L,"APPROVE"," exact "));
            when(host.replayComplete(any(),any())).thenReturn(Optional.empty());
            mvc.perform(post("/custom/commands/complete/replay").contentType("application/json").header("X-Expected-Lock-Version","0")
                .content(body.substring(0,body.length()-1)+",\"instanceId\":\"1\",\"taskId\":\"5\"}"))
                .andExpect(status().isOk()).andExpect(content().json("{\"found\":false,\"result\":null}"));
            verify(host,times(1)).complete(any(),any());
            verifyNoInteractions(c.getBean(WorkflowRuntimeReadHost.class));
        }
    }
    @Test void ownershipRoutesPreserveExactInputAndReplayNeverMutates() throws Exception {
        try (var c = context(Host.class, Commands.class, WorkflowRuntimeCommandConfiguration.class)) {
            var host = c.getBean(WorkflowRuntimeCommandHost.class);
            var mvc = mvc(c);
            String uuid = "12345678-1234-1234-1234-123456789abc";
            String body = "{\"commandId\":\"" + uuid + "\",\"reason\":\" exact \"}";
            String token = "9007199254740994";
            String instance = "9007199254740993";
            String release = "/custom/instances/" + instance + "/tasks/5/admin-release";
            when(host.administrativelyRelease(any(), eq("corr")))
                    .thenReturn(new WorkflowCommandResult(false, null));
            mvc.perform(post(release).contentType("application/json")
                    .header("X-Expected-Lock-Version", token).requestAttr("trusted", "corr").content(body))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.replayed").value(false));
            var command = new io.github.leontecristian.workflow.core.dto.runtime.AdministrativelyReleaseWorkflowTask(
                    java.util.UUID.fromString(uuid), Long.parseLong(instance), 5, Long.parseLong(token), " exact ");
            verify(host).administrativelyRelease(command, "corr");
            when(host.replayAdministrativeRelease(command, "corr")).thenReturn(Optional.empty());
            mvc.perform(post("/custom/commands/admin-release/replay").contentType("application/json")
                    .header("X-Expected-Lock-Version", token).requestAttr("trusted", "corr")
                    .content(body.substring(0, body.length() - 1) + ",\"instanceId\":\"" + instance + "\",\"taskId\":\"5\"}"))
                    .andExpect(status().isOk()).andExpect(content().json("{\"found\":false,\"result\":null}"));
            verify(host).replayAdministrativeRelease(command, "corr");

            for (String bad : List.of(body.replace(" exact ", " "), body.replace(" exact ", "x".repeat(1001)),
                    body.replace(" exact ", "x\",\"reason\":\"y"),
                    body.replace(" exact ", "x\",\"assignee\":\"fake"))) {
                mvc.perform(post(release).contentType("application/json")
                        .header("X-Expected-Lock-Version", token).content(bad))
                        .andExpect(status().isBadRequest());
            }
            org.mockito.Mockito.verifyNoMoreInteractions(host);
        }
    }
    @Test void commandParsingAndSafeErrorCategoriesArePreserved() throws Exception {
        try(var c=context(Host.class,Commands.class,WorkflowRuntimeCommandConfiguration.class)) {
            var host=c.getBean(WorkflowRuntimeCommandHost.class);var mvc=mvc(c);
            String body="{\"commandId\":\"12345678-1234-1234-1234-123456789abc\",\"outcomeKey\":\"APPROVE\",\"comment\":null}";
            for(String bad:List.of(body.replace("null","3"),body.replace("null","null,\"actor\":\"fake\""),body.replace("APPROVE","APPROVE\",\"outcomeKey\":\"REJECT"),body.replace("12345678-1234-1234-1234-123456789abc","1-1-1-1-1")))
                mvc.perform(post("/custom/instances/1/tasks/5/complete").contentType("application/json").header("X-Expected-Lock-Version","0").content(bad))
                    .andExpect(status().isBadRequest());
            verifyNoInteractions(host);
            for(var category:List.of(WorkflowExecutionException.Category.STALE_VERSION,WorkflowExecutionException.Category.LIMIT_EXCEEDED,
                    WorkflowExecutionException.Category.NOT_ASSIGNED,WorkflowExecutionException.Category.ASSIGNMENT_LOOKUP_FAILED,WorkflowExecutionException.Category.HOST_CONFIGURATION)) {
                doThrow(new WorkflowExecutionException(category)).when(host).complete(any(),any());
                int status=category==WorkflowExecutionException.Category.STALE_VERSION?409:category==WorkflowExecutionException.Category.LIMIT_EXCEEDED?422:category==WorkflowExecutionException.Category.NOT_ASSIGNED?403:category==WorkflowExecutionException.Category.ASSIGNMENT_LOOKUP_FAILED?503:500;
                mvc.perform(post("/custom/instances/1/tasks/5/complete").contentType("application/json").header("X-Expected-Lock-Version","0")
                    .requestAttr("trusted","safe").content(body)).andExpect(status().is(status)).andExpect(jsonPath("$.correlationId").value("safe"));
            }
        }
    }
}
