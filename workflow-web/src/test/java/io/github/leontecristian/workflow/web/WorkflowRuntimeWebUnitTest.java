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
import io.github.leontecristian.workflow.core.dto.definition.DefinitionVersionView;
import io.github.leontecristian.workflow.core.dto.definition.document.PreviousDecisionsPolicy;
import io.github.leontecristian.workflow.core.dto.runtime.ActorKind;
import io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind;
import io.github.leontecristian.workflow.core.dto.runtime.BooleanLiteral;
import io.github.leontecristian.workflow.core.dto.runtime.CompleteWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.InstanceStatus;
import io.github.leontecristian.workflow.core.dto.runtime.NullLiteral;
import io.github.leontecristian.workflow.core.dto.runtime.TaskAssignmentTarget;
import io.github.leontecristian.workflow.core.dto.runtime.TaskStatus;
import io.github.leontecristian.workflow.core.dto.runtime.TaskSubmission;
import io.github.leontecristian.workflow.core.dto.runtime.TaskSubmissionPage;
import io.github.leontecristian.workflow.core.dto.runtime.TextLiteral;
import io.github.leontecristian.workflow.core.dto.runtime.VariableValueType;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowActivityView;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandResult;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowInstanceView;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowJsonValue;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskPage;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskView;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowValue;
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
import io.github.leontecristian.workflow.web.integration.WorkflowRequestIdentity;
import io.github.leontecristian.workflow.web.integration.administration.WorkflowAdministrationAuthorization;
import io.github.leontecristian.workflow.web.integration.administration.WorkflowAdministrationAuditRecorder;
import io.github.leontecristian.workflow.web.integration.instance.WorkflowViewerAccess;
import io.github.leontecristian.workflow.web.service.runtime.WorkflowRuntimeCommandCoordinator;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowAuthorization;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowAuthorizationSupport;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowAuthorizedRuntimeReadService;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowTaskEligibilityService;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowRuntimeReadAuthorization;
import io.github.leontecristian.workflow.web.integration.runtime.WorkflowAssignmentDirectory;
import io.github.leontecristian.workflow.web.service.runtime.RuntimeWire;
import io.github.leontecristian.workflow.web.service.runtime.WorkflowTaskTokens;
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
    @Test void cancellationTransportPreservesExactReasonAndUsesLookupOnlyRecovery() throws Exception {
        try (var c = context(Host.class, Commands.class, WorkflowRuntimeCommandConfiguration.class)) {
            var host = c.getBean(WorkflowRuntimeCommandCoordinator.class);
            var mvc = mvc(c);
            String base = "{\"commandId\":\"12345678-1234-1234-1234-123456789abc\",\"reason\":\" exact \"}";
            when(host.cancel(any(), eq("corr"))).thenReturn(
                    new io.github.leontecristian.workflow.core.dto.runtime.WorkflowCancellationCommandResult(false, null));
            mvc.perform(post("/custom/instances/9007199254740993/cancel").contentType("application/json")
                    .header("X-Expected-Lock-Version", "9007199254740994").requestAttr("trusted", "corr").content(base))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.replayed").value(false));
            var command = new io.github.leontecristian.workflow.core.dto.runtime.CancelWorkflow(
                    UUID.fromString("12345678-1234-1234-1234-123456789abc"), 9007199254740993L, 9007199254740994L, " exact ");
            verify(host).cancel(command, "corr");
            when(host.replayCancellation(command, "corr")).thenReturn(Optional.empty());
            mvc.perform(post("/custom/commands/cancel/replay").contentType("application/json")
                    .header("X-Expected-Lock-Version", "9007199254740994").requestAttr("trusted", "corr")
                    .content(base.substring(0, base.length() - 1) + ",\"instanceId\":\"9007199254740993\"}"))
                    .andExpect(status().isOk()).andExpect(content().json("{\"found\":false,\"result\":null}"));
            verify(host).replayCancellation(command, "corr");
            for (String bad : List.of(base.replace(" exact ", " "), base.replace(" exact ", "x".repeat(2001)),
                    base.replace(" exact ", "x\",\"reason\":\"y"), base.replace(" exact ", "x\",\"actor\":\"fake"),
                    base.replace(" exact ", "\\ud800"))) {
                mvc.perform(post("/custom/instances/1/cancel").contentType("application/json")
                        .header("X-Expected-Lock-Version", "0").content(bad)).andExpect(status().isBadRequest());
            }
            mvc.perform(post("/custom/instances/1/cancel").contentType("application/json")
                    .header("X-Expected-Lock-Version", "0").content(base + " ".repeat(16 * 1024)))
                    .andExpect(status().is(413));
            org.mockito.Mockito.verifyNoMoreInteractions(host);
        }
    }

    @Configuration(proxyBeanMethods=false) @EnableWebMvc
    static class Host extends HostBeans {}
    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration({JacksonAutoConfiguration.class, HttpMessageConvertersAutoConfiguration.class, WebMvcAutoConfiguration.class})
    static class BootHost extends HostBeans {}
    static class HostBeans {
        @Bean WorkflowRuntimeOptions runtimeOptions() { return new WorkflowRuntimeOptions("/custom", "trusted"); }
        @Bean WorkflowAuthorizationSupport authorizationSupport(org.springframework.beans.factory.ObjectProvider<WorkflowAuthorization> policy) {
            return new WorkflowAuthorizationSupport(policy);
        }
        @Bean WorkflowTaskEligibilityService eligibility() { return mock(WorkflowTaskEligibilityService.class); }
        @Bean WorkflowRuntimeQueryService reads() { return mock(WorkflowRuntimeQueryService.class); }
        @Bean WorkflowDefinitionService definitions() { return mock(WorkflowDefinitionService.class); }
        @Bean TaskTypeCatalogService catalog() { return mock(TaskTypeCatalogService.class); }
        @Bean WorkflowViewerOptions viewerOptions() { return new WorkflowViewerOptions("/custom", "trusted"); }
        @Bean WorkflowViewerAccess viewerAccess() { return mock(WorkflowViewerAccess.class); }
        @Bean WorkflowAdministrationOptions administrationOptions() { return new WorkflowAdministrationOptions("/custom", "trusted"); }
        @Bean WorkflowRequestIdentity identity() {
            var identity = mock(WorkflowRequestIdentity.class);
            when(identity.requireActorRef()).thenReturn("actor");
            return identity;
        }
        @Bean WorkflowAdministrationAuthorization administrationAuthorization() { return mock(WorkflowAdministrationAuthorization.class); }
        @Bean WorkflowAdministrationAuditRecorder administrationRecorder() { return mock(WorkflowAdministrationAuditRecorder.class); }
        @Bean java.time.Clock administrationClock() { return java.time.Clock.systemUTC(); }
        @Bean org.springframework.transaction.PlatformTransactionManager administrationTransactions() {
            return mock(org.springframework.transaction.PlatformTransactionManager.class);
        }
    }
    @Configuration(proxyBeanMethods=false) static class Reads {
        @Bean @org.springframework.context.annotation.Primary
        WorkflowAuthorizedRuntimeReadService readHost() { return mock(WorkflowAuthorizedRuntimeReadService.class); }
        @Bean WorkflowRuntimeReadAuthorization readAuthorization() { return mock(WorkflowRuntimeReadAuthorization.class); }
        @Bean WorkflowAssignmentDirectory directory() { return mock(WorkflowAssignmentDirectory.class); }
    }
    @Configuration(proxyBeanMethods=false) static class Commands {
        @Bean io.github.leontecristian.workflow.core.service.runtime.WorkflowExecutionService execution() {
            return mock(io.github.leontecristian.workflow.core.service.runtime.WorkflowExecutionService.class);
        }
        @Bean @org.springframework.context.annotation.Primary WorkflowRuntimeCommandCoordinator commandHost() { return mock(WorkflowRuntimeCommandCoordinator.class); }
    }
    @Configuration(proxyBeanMethods=false) static class ReadPolicies {
        @Bean WorkflowRuntimeReadAuthorization readAuthorization() { return mock(WorkflowRuntimeReadAuthorization.class); }
    }
    @Test void explicitReadActivationWiresRealFacadeWithoutCommandsOrCancellationPolicy() throws Exception {
        try (var c = context(Host.class, ReadPolicies.class, WorkflowRuntimeReadConfiguration.class)) {
            assertThat(org.mockito.Mockito.mockingDetails(c.getBean(WorkflowAuthorizedRuntimeReadService.class)).isMock()).isFalse();
            assertThat(c.getBeansOfType(WorkflowRuntimeCommandController.class)).isEmpty();
            var policy = c.getBean(WorkflowRuntimeReadAuthorization.class);
            var reads = c.getBean(WorkflowRuntimeQueryService.class);
            mvc(c).perform(get("/custom/instances").param("unsupported", "x"))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
            mvc(c).perform(get("/custom/tasks/administration").param("unsupported", "x"))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ADMINISTRATION_FORBIDDEN"));
            verifyNoInteractions(reads);
            var instance = mock(io.github.leontecristian.workflow.core.dto.runtime.WorkflowInstanceView.class);
            when(instance.status()).thenReturn(io.github.leontecristian.workflow.core.dto.runtime.InstanceStatus.ACTIVE);
            when(instance.lockVersion()).thenReturn(8L);
            when(reads.findInstance(1)).thenReturn(Optional.of(instance));
            when(policy.canReadInstance("actor", instance)).thenReturn(true);
            mvc(c).perform(get("/custom/instances/1/cancellation-availability"))
                    .andExpect(status().isOk()).andExpect(content().json("{\"allowed\":false,\"lockVersion\":\"8\"}"));
            verify(policy).canReadInstance("actor", instance);
            when(policy.canReadInstance("actor", instance)).thenReturn(false);
            mvc(c).perform(get("/custom/instances/1/cancellation-availability"))
                    .andExpect(status().isNotFound());
        }
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
        assertThatThrownBy(()->context(Host.class, WorkflowRuntimeReadConfiguration.class)).hasMessageContaining("WorkflowRuntimeReadAuthorization");
        assertThatThrownBy(()->context(Host.class, WorkflowRuntimeCommandConfiguration.class)).hasMessageContaining("WorkflowExecutionService");
    }
    @Test void readsRemainIndependentAndRetainAuthorizedEmptyContinuation() throws Exception {
        try(var c=context(Host.class, Reads.class, WorkflowRuntimeReadConfiguration.class)) {
            var host=c.getBean(WorkflowAuthorizedRuntimeReadService.class); var mvc=mvc(c);
            when(host.listInstanceTasks("actor", 1,0,50)).thenReturn(new WorkflowTaskPage(List.of(),42L));
            String response = mvc.perform(get("/custom/instances/1/tasks")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.nextCursor").isString())
                    .andExpect(jsonPath("$.nextAfterId").doesNotExist()).andReturn().getResponse().getContentAsString();
            String cursor = new RuntimeWire().tree(response).path("nextCursor").textValue();
            assertThat(cursor).isNotEqualTo("42");
            when(host.listInstanceTasks("actor", 1, 42, 50)).thenReturn(new WorkflowTaskPage(List.of(), null));
            mvc.perform(get("/custom/instances/1/tasks").param("cursor", cursor))
                    .andExpect(status().isOk()).andExpect(content().json("{\"items\":[],\"nextCursor\":null}"));
            mvc.perform(get("/custom/instances/2/tasks").param("cursor", cursor)).andExpect(status().isBadRequest());
            mvc.perform(get("/custom/tasks/mine").param("cursor", cursor)).andExpect(status().isBadRequest());
            var identity = c.getBean(WorkflowRequestIdentity.class);
            when(identity.requireActorRef()).thenReturn("other");
            mvc.perform(get("/custom/instances/1/tasks").param("cursor", cursor)).andExpect(status().isBadRequest());
            when(identity.requireActorRef()).thenReturn("actor");
            when(host.listMyTasks("actor", 0, 50)).thenReturn(new WorkflowTaskPage(List.of(), 17L));
            String mineBody = mvc.perform(get("/custom/tasks/mine")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.nextCursor").isString()).andReturn().getResponse().getContentAsString();
            String mineCursor = new RuntimeWire().tree(mineBody).path("nextCursor").textValue();
            when(host.listMyTasks("actor", 17, 50)).thenReturn(new WorkflowTaskPage(List.of(), null));
            mvc.perform(get("/custom/tasks/mine").param("cursor", mineCursor))
                    .andExpect(status().isOk()).andExpect(content().json("{\"items\":[],\"nextCursor\":null}"));
            mvc.perform(post("/custom/commands/complete/replay")).andExpect(status().isNotFound());
            for(String q:List.of("unknown=1","limit=0","limit=2&limit=3","afterId=01"))
                mvc.perform(get("/custom/instances/1/tasks?"+q)).andExpect(status().isBadRequest());
            when(host.task("actor", 5)).thenThrow(new WorkflowRuntimeException(404,"NOT_FOUND"));
            mvc.perform(get("/custom/tasks/5")).andExpect(status().isNotFound());
            mvc.perform(get("/custom/tasks/5/contract")).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.inputs").doesNotExist());
            verify(host, times(2)).task("actor", 5);
            verifyNoInteractions(c.getBean(WorkflowRuntimeQueryService.class));
        }
    }
    @Test void sharedAssignmentProjectionAndExactSubmissionFilterPreserveSourceContinuation() throws Exception {
        try(var c=context(Host.class,Reads.class,WorkflowRuntimeReadConfiguration.class)) {
            var reads=c.getBean(WorkflowRuntimeQueryService.class); var host=c.getBean(WorkflowAuthorizedRuntimeReadService.class); var mvc=mvc(c);
            var task=new io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskView(5,2,1,3,"REVIEW",
                    new io.github.leontecristian.workflow.core.dto.runtime.TaskAssignmentTarget(
                            io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind.GROUP,"finance"),
                    io.github.leontecristian.workflow.core.dto.runtime.TaskStatus.OPEN,null,java.time.Instant.EPOCH,null,null,null,null,null);
            when(host.task("actor", 5)).thenReturn(task);
            var instance = mock(io.github.leontecristian.workflow.core.dto.runtime.WorkflowInstanceView.class);
            var activity = mock(io.github.leontecristian.workflow.core.dto.runtime.WorkflowActivityView.class);
            var version = mock(io.github.leontecristian.workflow.core.dto.definition.DefinitionVersionView.class);
            when(reads.findInstance(1)).thenReturn(Optional.of(instance));
            when(instance.definitionVersionId()).thenReturn(4L);
            when(reads.findActivity(2)).thenReturn(Optional.of(activity));
            when(activity.nodeKey()).thenReturn("COLLECT");
            var definitions = c.getBean(WorkflowDefinitionService.class);
            var catalog = c.getBean(TaskTypeCatalogService.class);
            when(catalog.findById(3)).thenReturn(Optional.of(new io.github.leontecristian.workflow.core.dto.tasktype.TaskTypeView(
                    3, "SANDBOX_REVIEW", "Sandbox review", null, false, 0)));
            when(definitions.findVersion(4)).thenReturn(Optional.of(version));
            when(version.documentJson()).thenReturn("""
                    {"nodes":[{"key":"COLLECT","name":"Collect reviews","taskType":"SANDBOX_REVIEW"}]}
                    """);
            mvc.perform(get("/custom/tasks/5")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.nodeName").value("Collect reviews"))
                    .andExpect(jsonPath("$.taskType").doesNotExist())
                    .andExpect(jsonPath("$.taskTypeName").value("Sandbox review"))
                    .andExpect(jsonPath("$.assignment").doesNotExist())
                    .andExpect(jsonPath("$.assignmentLabel").value("Shared task"))
                    .andExpect(jsonPath("$.id").doesNotExist())
                    .andExpect(jsonPath("$.assignedUserRef").doesNotExist());
            when(host.listInstanceTasks("actor", 1, 0, 50)).thenReturn(new WorkflowTaskPage(List.of(task, task), 42L));
            when(host.listMyTasks("actor", 0, 50)).thenReturn(new WorkflowTaskPage(List.of(task), null));
            mvc.perform(get("/custom/instances/1/tasks")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[1].nodeName").value("Collect reviews"))
                    .andExpect(jsonPath("$.items[1].taskType").doesNotExist())
                    .andExpect(jsonPath("$.items[1].taskTypeName").value("Sandbox review"))
                    .andExpect(jsonPath("$.items[1].taskKey").doesNotExist())
                    .andExpect(jsonPath("$.items[1].id").value("5"))
                    .andExpect(jsonPath("$.nextAfterId").doesNotExist())
                    .andExpect(jsonPath("$.nextCursor").isString());
            // One definition lookup for the detail response and one for the entire page.
            verify(definitions, times(2)).findVersion(4);
            verify(catalog, times(2)).findById(3);
            mvc.perform(get("/custom/tasks/mine")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].nodeName").value("Collect reviews"))
                    .andExpect(jsonPath("$.items[0].taskTypeName").value("Sandbox review"));
            verify(host, org.mockito.Mockito.never()).instance("actor", 1);
            verify(host, org.mockito.Mockito.never()).activity("actor", 2);
            var submission=new io.github.leontecristian.workflow.core.dto.runtime.TaskSubmission(5,2,1,9,
                    java.util.UUID.randomUUID(),io.github.leontecristian.workflow.core.dto.runtime.ActorKind.USER,
                    "other",java.time.Instant.EPOCH,"APPROVE","private",java.util.Map.of());
            when(reads.findTask(5)).thenReturn(Optional.of(task));
            when(reads.listActivitySubmissions(2,0,50)).thenReturn(new io.github.leontecristian.workflow.core.dto.runtime.TaskSubmissionPage(List.of(submission),null));
            when(host.canReadTask("actor", task)).thenReturn(false);
            mvc.perform(get("/custom/activities/2/submissions")).andExpect(status().isOk())
                    .andExpect(content().json("{\"items\":[],\"nextAfterTaskId\":null}"));
        }
    }
    @Test void readOnlyContractExposesBusinessContextWithoutCommandMetadata() throws Exception {
        try (var c = context(Host.class, Reads.class, WorkflowRuntimeReadConfiguration.class)) {
            var host = c.getBean(WorkflowAuthorizedRuntimeReadService.class);
            var reads = c.getBean(WorkflowRuntimeQueryService.class);
            var definitions = c.getBean(WorkflowDefinitionService.class);
            long instanceId = 9007199254740993L;
            var task = new io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskView(5, 2, instanceId, 3, "REVIEW",
                    new io.github.leontecristian.workflow.core.dto.runtime.TaskAssignmentTarget(
                            io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind.GROUP, "finance"),
                    io.github.leontecristian.workflow.core.dto.runtime.TaskStatus.OPEN,
                    null, java.time.Instant.EPOCH, null, null, null, null, "reviewer");
            var instance = mock(io.github.leontecristian.workflow.core.dto.runtime.WorkflowInstanceView.class);
            var activity = mock(io.github.leontecristian.workflow.core.dto.runtime.WorkflowActivityView.class);
            var version = mock(io.github.leontecristian.workflow.core.dto.definition.DefinitionVersionView.class);
            when(host.task("actor", 5)).thenReturn(task);
            when(host.canCompleteTask("actor", task)).thenReturn(true);
            when(host.canReleaseTask("actor", task)).thenReturn(true);
            when(host.canAdministrativelyReleaseTask("actor", task)).thenReturn(true);
            when(reads.findInstance(instanceId)).thenReturn(Optional.of(instance));
            when(instance.id()).thenReturn(instanceId);
            when(instance.definitionVersionId()).thenReturn(4L);
            when(instance.lockVersion()).thenReturn(9L);
            when(reads.findActivity(2)).thenReturn(Optional.of(activity));
            when(activity.nodeKey()).thenReturn("REVIEW");
            when(reads.taskClaimPolicy(instanceId, 5))
                    .thenReturn(io.github.leontecristian.workflow.core.dto.runtime.TaskClaimPolicy.REQUIRED);
            when(reads.previousDecisionsPolicy(5)).thenReturn(
                    io.github.leontecristian.workflow.core.dto.definition.document.PreviousDecisionsPolicy.ALL_PREVIOUS);
            when(definitions.findVersion(4)).thenReturn(Optional.of(version));
            when(version.documentJson()).thenReturn("""
                    {"name":"Pinned booking review","nodes":[{"key":"REVIEW","taskType":"REVIEW","outcomes":[]}]}
                    """);
            mvc(c).perform(get("/custom/tasks/5/contract")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.workflow.name").value("Pinned booking review"))
                    .andExpect(jsonPath("$.workflow.instanceId").value("9007199254740993"))
                    .andExpect(jsonPath("$.claimPolicy").doesNotExist())
                    .andExpect(jsonPath("$.previousDecisions").doesNotExist())
                    .andExpect(jsonPath("$.previousDecisionsAvailable").value(true))
                    .andExpect(jsonPath("$.precondition").doesNotExist())
                    .andExpect(jsonPath("$.inputs").isEmpty())
                    .andExpect(jsonPath("$.resultContract.mode").value("FORBIDDEN"))
                    .andExpect(jsonPath("$.canComplete").value(false))
                    .andExpect(jsonPath("$.canClaim").value(false))
                    .andExpect(jsonPath("$.canRelease").value(false))
                    .andExpect(jsonPath("$.canAdminRelease").doesNotExist());
            var snapshotOrder = org.mockito.Mockito.inOrder(host, reads);
            snapshotOrder.verify(host).task("actor", 5);
            snapshotOrder.verify(reads).findInstance(instanceId);
            snapshotOrder.verify(host).task("actor", 5);
            verify(host, org.mockito.Mockito.never()).canCompleteTask("actor", task);
            when(version.documentJson()).thenReturn("""
                    {"nodes":[{"key":"REVIEW","taskType":"REVIEW","outcomes":[],"resultContract":
                      {"mode":"REQUIRED","schema":{"type":"object","properties":{"amount":{"minimum":9007199254740993.00}}}}}]}
                    """);
            when(reads.taskInputs(instanceId, 5)).thenReturn(Map.of(
                    "booking", new WorkflowValue(VariableValueType.JSON, WorkflowJsonValue.parse("{\"amount\":9007199254740995.00}")),
                    "date", new WorkflowValue(VariableValueType.DATE, new TextLiteral("2026-11-10")),
                    "eligible", new WorkflowValue(VariableValueType.BOOLEAN, new BooleanLiteral(false)),
                    "missing", new WorkflowValue(VariableValueType.LONG, NullLiteral.INSTANCE)));
            mvc(c).perform(get("/custom/tasks/5/contract")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.resultContract.mode").value("REQUIRED"))
                    .andExpect(jsonPath("$.inputs.booking.type").value("JSON"))
                    .andExpect(jsonPath("$.inputs.date.value").value("2026-11-10"))
                    .andExpect(jsonPath("$.inputs.eligible.value").value(false))
                    .andExpect(jsonPath("$.inputs.missing.value").hasJsonPath())
                    .andExpect(jsonPath("$.inputs.missing.value").value(org.hamcrest.Matchers.nullValue()))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("\"amount\":9007199254740995.00")))
                    .andExpect(jsonPath("$.resultContract.schema").doesNotExist())
                    .andExpect(jsonPath("$.resultContract.form").doesNotExist());
            verify(host, org.mockito.Mockito.never()).instance("actor", instanceId);
            verify(host, org.mockito.Mockito.never()).activity("actor", 2);
        }
    }

    @Test void taskContractShowsOnlyThePinnedHumanNodeInstructions() throws Exception {
        try (var c = context(Host.class, Reads.class, WorkflowRuntimeReadConfiguration.class)) {
            var reads = c.getBean(WorkflowRuntimeQueryService.class);
            var access = c.getBean(WorkflowAuthorizedRuntimeReadService.class);
            var definitions = c.getBean(WorkflowDefinitionService.class);
            var closed = task(5);
            var open = new WorkflowTaskView(6, 9, 7, 3, "REVIEW", closed.assignment(), TaskStatus.OPEN,
                    null, Instant.EPOCH, null, null, null, null, null);
            var olderInstance = mock(WorkflowInstanceView.class);
            var newerInstance = mock(WorkflowInstanceView.class);
            var olderActivity = mock(WorkflowActivityView.class);
            var newerActivity = mock(WorkflowActivityView.class);
            when(olderInstance.definitionVersionId()).thenReturn(4L);
            when(newerInstance.definitionVersionId()).thenReturn(8L);
            when(reads.findInstance(1)).thenReturn(Optional.of(olderInstance));
            when(reads.findInstance(7)).thenReturn(Optional.of(newerInstance));
            when(olderActivity.nodeKey()).thenReturn("REVIEW");
            when(newerActivity.nodeKey()).thenReturn("REVIEW");
            when(reads.findActivity(2)).thenReturn(Optional.of(olderActivity));
            when(reads.findActivity(9)).thenReturn(Optional.of(newerActivity));
            when(access.task("actor", 5)).thenReturn(closed);
            when(access.task("actor", 6)).thenReturn(open);
            when(reads.previousDecisionsPolicy(5)).thenReturn(PreviousDecisionsPolicy.NONE);
            when(reads.previousDecisionsPolicy(6)).thenReturn(PreviousDecisionsPolicy.NONE);

            String olderText = "Check the original booking.\nUse <b>literal</b> text.";
            String newerText = "Check the revised booking.";
            var mapper = new ObjectMapper();
            var olderVersion = mock(DefinitionVersionView.class);
            var newerVersion = mock(DefinitionVersionView.class);
            when(definitions.findVersion(4)).thenReturn(Optional.of(olderVersion));
            when(definitions.findVersion(8)).thenReturn(Optional.of(newerVersion));
            var olderDocument = mapper.createObjectNode().put("description", "Family instructions");
            olderDocument.putArray("nodes").addObject().put("key", "REVIEW").put("description", olderText);
            var newerDocument = mapper.createObjectNode().put("description", "Family instructions");
            var newerNode = newerDocument.putArray("nodes").addObject().put("key", "REVIEW").put("description", newerText);
            when(olderVersion.documentJson()).thenReturn(olderDocument.toString());
            when(newerVersion.documentJson()).thenReturn(newerDocument.toString());

            var mvc = mvc(c);
            mvc.perform(get("/custom/tasks/6/contract")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.task.status").value("OPEN"))
                    .andExpect(jsonPath("$.task.description").value(newerText));
            mvc.perform(get("/custom/tasks/5/contract")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.task.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.task.description").value(olderText));
            when(access.listMyTasks("actor", 0, 50)).thenReturn(new WorkflowTaskPage(List.of(open), null));
            mvc.perform(get("/custom/tasks/mine")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].description").doesNotExist());

            for (String description : List.of("", " \n\t ")) {
                newerNode.put("description", description);
                when(newerVersion.documentJson()).thenReturn(newerDocument.toString());
                mvc.perform(get("/custom/tasks/6/contract")).andExpect(status().isOk())
                        .andExpect(jsonPath("$.task.description").doesNotExist());
            }
            newerNode.remove("description");
            when(newerVersion.documentJson()).thenReturn(newerDocument.toString());
            mvc.perform(get("/custom/tasks/6/contract")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.task.description").doesNotExist());
        }
    }

    @Test void previousDecisionsRequireReceivingTaskAccessAndUseAnIndependentlyReadableSourceLabel() throws Exception {
        try (var c = context(Host.class, Reads.class, WorkflowRuntimeReadConfiguration.class)) {
            var host = c.getBean(WorkflowAuthorizedRuntimeReadService.class);
            var reads = c.getBean(WorkflowRuntimeQueryService.class);
            var directory = c.getBean(WorkflowAssignmentDirectory.class);
            var item = decision(9007199254740993L, 9, " <b>Exact</b>\n ");
            org.mockito.Mockito.doReturn(task(5)).when(host).task("actor", 5);
            var source = new WorkflowTaskView(item.taskId(), item.activityId(), 1, 3, "source-key",
                    new TaskAssignmentTarget(AssignmentTargetKind.USER, item.actorRef()), TaskStatus.COMPLETED,
                    item.outcomeKey(), Instant.EPOCH, null, Instant.EPOCH, item.actorRef(), null, item.actorRef());
            when(host.task("actor", item.taskId())).thenReturn(source);
            when(directory.assignmentLabel(source)).thenReturn("Prior reviewer", "Renamed reviewer");
            when(reads.listPreviousDecisions(5, 0, 20)).thenReturn(
                    new io.github.leontecristian.workflow.core.dto.runtime.PreviousDecisionPage(List.of(item), 9L));
            mvc(c).perform(get("/custom/tasks/5/previous-decisions")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].taskId").doesNotExist())
                    .andExpect(jsonPath("$.items[0].historySequence").doesNotExist())
                    .andExpect(jsonPath("$.items[0].actorRef").doesNotExist())
                    .andExpect(jsonPath("$.items[0].actorLabel").value("Prior reviewer"))
                    .andExpect(jsonPath("$.items[0].comment").value(" <b>Exact</b>\n "))
                    .andExpect(jsonPath("$.items[0].result").doesNotExist())
                    .andExpect(jsonPath("$.items[0].inputs").doesNotExist())
                    .andExpect(jsonPath("$.nextCursor").isString());
            verify(host).task("actor", 5);
            // Recheck the exact source independently and leave the previous decision visible after access loss.
            mvc(c).perform(get("/custom/tasks/5/previous-decisions")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].actorLabel").value("Renamed reviewer"));
            org.mockito.Mockito.doThrow(new WorkflowRuntimeException(404, "NOT_FOUND")).when(host).task("actor", item.taskId());
            mvc(c).perform(get("/custom/tasks/5/previous-decisions")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].actorLabel").value("Reviewer"))
                    .andExpect(jsonPath("$.items[0].comment").value(" <b>Exact</b>\n "));
            verify(directory, org.mockito.Mockito.times(2)).assignmentLabel(source);
            when(host.task("actor", 5)).thenThrow(new WorkflowRuntimeException(404, "NOT_FOUND"));
            mvc(c).perform(get("/custom/tasks/5/previous-decisions?cursor=9"))
                    .andExpect(status().isNotFound());
            verify(reads, org.mockito.Mockito.never()).listPreviousDecisions(5, 9, 20);
            org.mockito.Mockito.doReturn(task(5)).when(host).task("actor", 5);
            for (String invalid : List.of("limit=0", "limit=51", "limit=1.5", "limit=9223372036854775808",
                    "cursor=-1", "cursor=01", "cursor=1&cursor=2", "unknown=1"))
                mvc(c).perform(get("/custom/tasks/5/previous-decisions?" + invalid)).andExpect(status().isBadRequest());
        }
    }

    @Test void sharedReadsRestrictReviewerAndAdministratorRepresentationsAndRejectDeniedAccess() throws Exception {
        try (var c = context(Host.class, Reads.class, Commands.class,
                WorkflowRuntimeReadConfiguration.class, WorkflowRuntimeCommandConfiguration.class)) {
            stubPinnedTask(c);
            var host = c.getBean(WorkflowAuthorizedRuntimeReadService.class);
            var reads = c.getBean(WorkflowRuntimeQueryService.class);
            var identity = c.getBean(WorkflowRequestIdentity.class);
            var directory = c.getBean(WorkflowAssignmentDirectory.class);
            var instance = reads.findInstance(1).orElseThrow();
            when(instance.status()).thenReturn(InstanceStatus.ACTIVE);
            when(instance.currentNodeKey()).thenReturn("REVIEW");
            when(instance.lockVersion()).thenReturn(9007199254740994L);
            var version = c.getBean(WorkflowDefinitionService.class).findVersion(4).orElseThrow();
            when(version.documentJson()).thenReturn("""
                    {"nodes":[{"key":"REVIEW","name":"Review amount","taskType":"AMOUNT",
                    "outcomes":[{"key":"APPROVE","label":"Complete","requiresComment":false}],
                    "resultContract":{"mode":"REQUIRED","schema":{"type":"object","required":["approvedAmount"],
                    "properties":{"approvedAmount":{"type":"number","minimum":0}}}}}]}
                    """);
            var task = new WorkflowTaskView(5, 2, 1, 3, "sensitive-task-key",
                    new TaskAssignmentTarget(AssignmentTargetKind.USER, "actor"), TaskStatus.OPEN,
                    null, Instant.EPOCH, null, null, null, null, "actor");
            when(host.task("actor", 5)).thenReturn(task);
            when(host.task("admin", 5)).thenReturn(task);
            when(host.task("denied", 5)).thenThrow(new WorkflowRuntimeException(404, "NOT_FOUND"));
            when(host.canCompleteTask("actor", task)).thenReturn(true);
            when(reads.previousDecisionsPolicy(5)).thenReturn(
                    io.github.leontecristian.workflow.core.dto.definition.document.PreviousDecisionsPolicy.ALL_PREVIOUS);
            var mvc = mvc(c);
            String contractBody = mvc.perform(get("/custom/tasks/5/contract")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.task.ownershipLabel").value("Assigned to you"))
                    .andExpect(jsonPath("$.task.diagnostics").doesNotExist())
                    .andExpect(jsonPath("$.taskType").value("AMOUNT"))
                    .andExpect(jsonPath("$.outcomes[0].key").value("APPROVE"))
                    .andExpect(jsonPath("$.resultContract.schema").doesNotExist())
                    .andExpect(jsonPath("$.resultContract.form.fields[0].label").value("Approved amount"))
                    .andExpect(jsonPath("$.diagnostics").doesNotExist())
                    .andReturn().getResponse().getContentAsString();
            String token = new RuntimeWire().tree(contractBody).path("precondition").textValue();
            var tokens = c.getBean(WorkflowTaskTokens.class);
            assertThat(tokens.expectedVersion(token, "actor", 5)).isEqualTo(9007199254740994L);
            assertThatThrownBy(() -> tokens.expectedVersion(token, "admin", 5)).isInstanceOf(WorkflowRuntimeException.class);
            assertThatThrownBy(() -> tokens.expectedVersion(token, "actor", 6)).isInstanceOf(WorkflowRuntimeException.class);
            assertThatThrownBy(() -> tokens.after(token, "actor", 5)).isInstanceOf(WorkflowRuntimeException.class);
            assertThat(contractBody).doesNotContain("instanceLockVersion", "sensitive-task-key");
            mvc.perform(get("/custom/tasks/5/contract").param("diagnostics", "true"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.diagnostics").doesNotExist());
            var decision = decision(4, 9, "Confirm");
            when(reads.listPreviousDecisions(5, 0, 20)).thenReturn(
                    new io.github.leontecristian.workflow.core.dto.runtime.PreviousDecisionPage(List.of(decision), 9L));
            String previousBody = mvc.perform(get("/custom/tasks/5/previous-decisions")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].actorLabel").value("Reviewer"))
                    .andExpect(jsonPath("$.items[0].actorRef").doesNotExist())
                    .andReturn().getResponse().getContentAsString();
            when(reads.listPreviousDecisions(5, 9, 20)).thenReturn(new io.github.leontecristian.workflow.core.dto.runtime.PreviousDecisionPage(List.of(), null));
            String cursor = new RuntimeWire().tree(previousBody).path("nextCursor").textValue();
            mvc.perform(get("/custom/tasks/5/previous-decisions").param("cursor", cursor))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());

            when(identity.requireActorRef()).thenReturn("admin");
            when(directory.assignmentLabel(task)).thenReturn("Assigned reviewer", "Renamed reviewer", " actor ");
            mvc.perform(get("/custom/tasks/5")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.diagnostics").doesNotExist())
                    .andExpect(jsonPath("$.ownershipLabel").value("Assigned to Assigned reviewer"));
            mvc.perform(get("/custom/tasks/5")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.ownershipLabel").value("Assigned to Renamed reviewer"));
            mvc.perform(get("/custom/tasks/5")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.ownershipLabel").value("Assigned to another user"));
            mvc.perform(get("/custom/tasks/5/contract")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.canComplete").value(false))
                    .andExpect(jsonPath("$.taskType").doesNotExist())
                    .andExpect(jsonPath("$.outcomes[0].key").doesNotExist())
                    .andExpect(jsonPath("$.precondition").doesNotExist())
                    .andExpect(jsonPath("$.diagnostics").doesNotExist())
                    .andExpect(jsonPath("$.task.diagnostics").doesNotExist())
                    .andExpect(jsonPath("$.resultContract.schema").doesNotExist());

            var completed = new WorkflowTaskView(5, 2, 1, 3, "sensitive-task-key", task.assignment(), TaskStatus.COMPLETED,
                    "APPROVE", Instant.EPOCH, null, Instant.EPOCH, "actor",
                    io.github.leontecristian.workflow.core.dto.runtime.TaskClosureReason.TASK_OUTCOME, "actor");
            when(host.task("admin", 5)).thenReturn(completed);
            when(host.task("actor", 5)).thenReturn(completed);
            when(reads.findTaskSubmission(5)).thenReturn(Optional.of(submission(5, WorkflowJsonValue.parse("{\"approvedAmount\":0}"))));
            mvc.perform(get("/custom/tasks/5")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.diagnostics").doesNotExist())
                    .andExpect(jsonPath("$.outcomeLabel").value("Complete"));
            mvc.perform(get("/custom/tasks/5/submission")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.approvedAmount").value(0))
                    .andExpect(jsonPath("$.diagnostics").doesNotExist());
            when(identity.requireActorRef()).thenReturn("actor");
            mvc.perform(get("/custom/tasks/5/submission")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.outcomeLabel").value("Complete"))
                    .andExpect(jsonPath("$.result.approvedAmount").value(0))
                    .andExpect(jsonPath("$.actorRef").doesNotExist())
                    .andExpect(jsonPath("$.commandId").doesNotExist())
                    .andExpect(jsonPath("$.diagnostics").doesNotExist());
            when(identity.requireActorRef()).thenReturn("denied");
            for (String suffix : List.of("", "/contract", "/submission", "/previous-decisions",
                    "/reassignment-availability", "/reassignment-targets")) {
                mvc.perform(get("/custom/tasks/5" + suffix)).andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                        .andExpect(jsonPath("$.diagnostics").isEmpty());
            }
        }
    }

    @Test void previousDecisionWireBoundsEscapedWholeRowsAndRejectsOversizedEntry() {
        var wire = new io.github.leontecristian.workflow.web.service.runtime.RuntimeWire();
        var items = new ArrayList<io.github.leontecristian.workflow.core.dto.runtime.PreviousDecision>();
        for (int i = 1; i <= 50; i++) items.add(decision(i, i, "\u0001".repeat(2000)));
        String encoded = wire.previousDecisionPage(
                new io.github.leontecristian.workflow.core.dto.runtime.PreviousDecisionPage(items, null), item -> Map.of("comment", item.comment()), value -> "cursor-" + value).getBody();
        var tree = wire.tree(encoded);
        assertThat(encoded.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(256 * 1024);
        assertThat(encoded.length() * 2).isLessThanOrEqualTo(256 * 1024);
        int returned = tree.get("items").size();
        assertThat(returned).isBetween(1, 49);
        assertThat(tree.get("nextCursor").textValue()).isEqualTo("cursor-" + returned);
        assertThat(tree.get("items").get(returned - 1).get("comment").textValue()).hasSize(2000);
        assertThatThrownBy(() -> wire.previousDecisionPage(
                new io.github.leontecristian.workflow.core.dto.runtime.PreviousDecisionPage(
                        List.of(decision(1, 1, "x".repeat(256 * 1024))), null), item -> Map.of("comment", item.comment()), value -> "cursor-" + value))
                .isInstanceOf(WorkflowRuntimeException.class)
                .satisfies(failure -> assertThat(((WorkflowRuntimeException) failure).code()).isEqualTo("LIMIT_EXCEEDED"));
    }
    private static io.github.leontecristian.workflow.core.dto.runtime.PreviousDecision decision(long id, long sequence, String comment) {
        return new io.github.leontecristian.workflow.core.dto.runtime.PreviousDecision(id, 2, "MANAGER", "Manager review",
                "APPROVE", "Approve", ActorKind.USER, "actual-actor", Instant.EPOCH, sequence, comment);
    }

    @Test void completionAndReplayPreserveRawResultNumbersAndEnforceActualUtf8Boundary() throws Exception {
        try (var c = context(BootHost.class, Commands.class, Reads.class, WorkflowRuntimeCommandConfiguration.class,
                WorkflowRuntimeReadConfiguration.class)) {
            var host = c.getBean(WorkflowRuntimeCommandCoordinator.class);
            var readHost = c.getBean(WorkflowAuthorizedRuntimeReadService.class);
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
            String complete = "/custom/tasks/5/complete";
            String replay = "/custom/commands/complete/replay";
            String replayBody = base + result + ",\"taskId\":\"5\"}";
            String requestAtLimit = body + " ".repeat(256 * 1024 - body.getBytes(StandardCharsets.UTF_8).length);
            when(host.complete(any(), any())).thenReturn(new WorkflowCommandResult(false, null));
            stubTaskCommands(host, 1L, 0L);
            when(host.replayComplete(any(), any())).thenReturn(Optional.empty());
            mvc.perform(post(complete).contentType("application/json").header("X-Task-Precondition", "0")
                    .content(requestAtLimit.getBytes(StandardCharsets.UTF_8))).andExpect(status().isOk());
            mvc.perform(post(replay).contentType("application/json").header("X-Task-Precondition", "0")
                    .content(replayBody.getBytes(StandardCharsets.UTF_8))).andExpect(status().isOk());
            var command = new CompleteWorkflowTask(UUID.fromString("12345678-1234-1234-1234-123456789abc"),
                    1, 5, 0, "APPROVE", "漢", WorkflowJsonValue.parse(result));
            verify(host).complete(command, null);
            verify(host).replayComplete(command, null);
            mvc.perform(post(complete).contentType("application/json").header("X-Task-Precondition", "0")
                    .content((requestAtLimit + " ").getBytes(StandardCharsets.UTF_8))).andExpect(status().is(413));
            for (String invalid : List.of("null", "[]", "1", "{\"nested\":{\"x\":1,\"x\":2}}")) {
                mvc.perform(post(complete).contentType("application/json").header("X-Task-Precondition", "0")
                        .content(base + invalid + "}")).andExpect(status().isBadRequest());
                mvc.perform(post(replay).contentType("application/json").header("X-Task-Precondition", "0")
                        .content(base + invalid + ",\"taskId\":\"5\"}"))
                        .andExpect(status().isBadRequest());
            }
            verify(host, times(1)).complete(any(), any());
            verify(host, times(1)).replayComplete(any(), any());

            stubPinnedTask(c);
            when(readHost.task("actor", 5)).thenReturn(task(5));
            mvc.perform(get("/custom/tasks/5/submission")).andExpect(content().string("null"));
            when(reads.findTaskSubmission(5)).thenReturn(Optional.of(submission(5, null)));
            mvc.perform(get("/custom/tasks/5/submission")).andExpect(jsonPath("$.result").doesNotHaveJsonPath());
            when(reads.findTaskSubmission(5)).thenReturn(Optional.of(submission(5, command.result())));
            mvc.perform(get("/custom/tasks/5/submission")).andExpect(jsonPath("$.occurredAt").value("1970-01-01T00:00:00Z"))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString(command.result().toJson())));
            when(readHost.task("actor", 5)).thenThrow(new WorkflowRuntimeException(404, "NOT_FOUND"));
            mvc.perform(get("/custom/tasks/5/submission")).andExpect(status().isNotFound());
            verify(reads, times(3)).findTaskSubmission(5);

            var open = mock(WorkflowTaskView.class);
            when(open.status()).thenReturn(TaskStatus.OPEN);
            org.mockito.Mockito.doReturn(open).doThrow(new WorkflowRuntimeException(404, "NOT_FOUND"))
                    .when(readHost).task("actor", 5);
            mvc.perform(get("/custom/tasks/5/submission")).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.result").doesNotExist());
            verify(reads, times(4)).findTaskSubmission(5);
        }
    }

    @Test void resultFailuresExposeBoundedDiagnosticsAndSanitizedCategories() throws Exception {
        try (var c = context(Host.class, Commands.class, WorkflowRuntimeCommandConfiguration.class)) {
            var host = c.getBean(WorkflowRuntimeCommandCoordinator.class);
            var mvc = mvc(c);
            stubTaskCommands(host, 1L, 0L);
            var request = post("/custom/tasks/5/complete").contentType("application/json")
                    .header("X-Task-Precondition", "0").requestAttr("trusted", "safe")
                    .content("{\"commandId\":\"12345678-1234-1234-1234-123456789abc\",\"outcomeKey\":\"APPROVE\",\"result\":{}}");
            doThrow(WorkflowExecutionException.invalidResultSchema("/amount", "/properties/amount/minimum"))
                    .when(host).complete(any(), any());
            mvc.perform(request).andExpect(status().is(422))
                    .andExpect(jsonPath("$.code").value("RESULT_SCHEMA_INVALID"))
                    .andExpect(jsonPath("$.diagnostics[0].path").value("/amount"))
                    .andExpect(jsonPath("$.diagnostics[0].schemaPath").doesNotExist())
                    .andExpect(jsonPath("$.diagnostics[0].message").value("Enter a value at or above the minimum."));

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
            for (var entry : java.util.Map.of(
                    WorkflowExecutionException.Category.COMPLETION_FORBIDDEN, 403,
                    WorkflowExecutionException.Category.BUSINESS_VALIDATION_REJECTED, 422,
                    WorkflowExecutionException.Category.BUSINESS_VALIDATION_FAILED, 503,
                    WorkflowExecutionException.Category.HOST_CONFIGURATION, 500).entrySet()) {
                doThrow(new WorkflowExecutionException(entry.getKey(), new IllegalStateException("private business material")))
                        .when(host).complete(any(), any());
                mvc.perform(request).andExpect(status().is(entry.getValue()))
                        .andExpect(content().json("{\"code\":\"" + entry.getKey().name()
                                + "\",\"message\":\"" + entry.getKey().name()
                                + "\",\"diagnostics\":[],\"correlationId\":\"safe\"}",
                                org.springframework.test.json.JsonCompareMode.STRICT));
            }
        }
    }

    @Test void filteredSubmissionResponseCapsCombinedUtf8AndRetainsUnreturnedVisibleRow() throws Exception {
        try (var c = context(Host.class, Reads.class, WorkflowRuntimeReadConfiguration.class)) {
            var reads = c.getBean(WorkflowRuntimeQueryService.class);
            var host = c.getBean(WorkflowAuthorizedRuntimeReadService.class);
            var result = WorkflowJsonValue.parse("{\"note\":\"" + "漢".repeat(15000) + "\"}");
            stubPinnedTask(c);
            var rows = new ArrayList<TaskSubmission>();
            for (long id = 1; id <= 26; id++) {
                var task = task(id);
                rows.add(submission(id, result));
                when(reads.findTask(id)).thenReturn(Optional.of(task));
                when(host.canReadTask("actor", task)).thenReturn(id != 13 && id != 26);
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

    private static void stubTaskCommands(WorkflowRuntimeCommandCoordinator host, long instanceId, long version) {
        when(host.taskTarget(eq(5L), any())).thenReturn(new WorkflowRuntimeCommandCoordinator.TaskTarget(5L, instanceId, version));
        when(host.taskResult(any())).thenAnswer(call -> Map.of("replayed", call.<WorkflowCommandResult>getArgument(0).replayed()));
    }

    static void stubPinnedTask(AnnotationConfigWebApplicationContext c) {
        var reads = c.getBean(WorkflowRuntimeQueryService.class);
        var instance = mock(io.github.leontecristian.workflow.core.dto.runtime.WorkflowInstanceView.class);
        var activity = mock(io.github.leontecristian.workflow.core.dto.runtime.WorkflowActivityView.class);
        var version = mock(io.github.leontecristian.workflow.core.dto.definition.DefinitionVersionView.class);
        when(reads.findInstance(1)).thenReturn(Optional.of(instance));
        when(instance.definitionVersionId()).thenReturn(4L);
        when(reads.findActivity(2)).thenReturn(Optional.of(activity));
        when(activity.nodeKey()).thenReturn("REVIEW");
        when(c.getBean(WorkflowDefinitionService.class).findVersion(4)).thenReturn(Optional.of(version));
        when(version.documentJson()).thenReturn("""
                {"nodes":[{"key":"REVIEW","name":"Review work","outcomes":[{"key":"APPROVE","label":"Approve"}]}]}
                """);
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
            var reads=c.getBean(WorkflowRuntimeQueryService.class);var host=c.getBean(WorkflowAuthorizedRuntimeReadService.class);var mvc=mvc(c);
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
            verify(host).instance("actor", 1);
            when(reads.listVariables(1,"reviewResult",50,7L)).thenThrow(new WorkflowExecutionException(WorkflowExecutionException.Category.STALE_VERSION));
            mvc.perform(get("/custom/instances/1/variables?afterKey=reviewResult&expectedLockVersion=7"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_VERSION"));

            when(host.task("actor", 5)).thenThrow(new WorkflowRuntimeException(404, "NOT_FOUND"));
            mvc.perform(get("/custom/tasks/5/submission")).andExpect(status().isNotFound());
            verify(reads, times(0)).findTaskSubmission(5);
        }
    }
    @Test void allGroupsCoexistAndCompletionPreservesExactFrozenInputAndLookupOnlyMiss() throws Exception {
        try(var c=context(Host.class,Reads.class,Commands.class,WorkflowRuntimeReadConfiguration.class,
                WorkflowRuntimeCommandConfiguration.class,WorkflowViewerConfiguration.class,WorkflowAdministrationConfiguration.class)) {
            var host=c.getBean(WorkflowRuntimeCommandCoordinator.class);var mvc=mvc(c);
            when(host.complete(any(),eq("corr"))).thenReturn(new WorkflowCommandResult(false,null));
            stubTaskCommands(host, 9007199254740993L, 9007199254740994L);
            String body="{\"commandId\":\"12345678-1234-1234-1234-123456789abc\",\"outcomeKey\":\"APPROVE\",\"comment\":\" exact \"}";
            mvc.perform(post("/custom/tasks/5/complete").contentType("application/json")
                .header("X-Task-Precondition","9007199254740994").requestAttr("trusted","corr").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.replayed").value(false));
            var captured=org.mockito.ArgumentCaptor.forClass(CompleteWorkflowTask.class);
            verify(host).complete(captured.capture(),eq("corr"));
            assertThat(captured.getValue()).isEqualTo(new CompleteWorkflowTask(java.util.UUID.fromString("12345678-1234-1234-1234-123456789abc"),
                9007199254740993L,5,9007199254740994L,"APPROVE"," exact "));
            when(host.replayComplete(any(),any())).thenReturn(Optional.empty());
            mvc.perform(post("/custom/commands/complete/replay").contentType("application/json").header("X-Task-Precondition","0")
                .content(body.substring(0,body.length()-1)+",\"taskId\":\"5\"}"))
                .andExpect(status().isOk()).andExpect(content().json("{\"found\":false,\"result\":null}"));
            verify(host,times(1)).complete(any(),any());
            verifyNoInteractions(c.getBean(WorkflowAuthorizedRuntimeReadService.class));
        }
    }
    @Test void ownershipRoutesPreserveExactInputAndReplayNeverMutates() throws Exception {
        try (var c = context(Host.class, Commands.class, WorkflowRuntimeCommandConfiguration.class)) {
            var host = c.getBean(WorkflowRuntimeCommandCoordinator.class);
            var mvc = mvc(c);
            String uuid = "12345678-1234-1234-1234-123456789abc";
            String body = "{\"commandId\":\"" + uuid + "\",\"reason\":\" exact \"}";
            String token = "9007199254740994";
            String instance = "9007199254740993";
            String release = "/custom/tasks/5/admin-release";
            stubTaskCommands(host, Long.parseLong(instance), Long.parseLong(token));
            when(host.administrativelyRelease(any(), eq("corr")))
                    .thenReturn(new WorkflowCommandResult(false, null));
            mvc.perform(post(release).contentType("application/json")
                    .header("X-Task-Precondition", token).requestAttr("trusted", "corr").content(body))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.replayed").value(false));
            var command = new io.github.leontecristian.workflow.core.dto.runtime.AdministrativelyReleaseWorkflowTask(
                    java.util.UUID.fromString(uuid), Long.parseLong(instance), 5, Long.parseLong(token), " exact ");
            verify(host).administrativelyRelease(command, "corr");
            when(host.replayAdministrativeRelease(command, "corr")).thenReturn(Optional.empty());
            mvc.perform(post("/custom/commands/admin-release/replay").contentType("application/json")
                    .header("X-Task-Precondition", token).requestAttr("trusted", "corr")
                    .content(body.substring(0, body.length() - 1) + ",\"taskId\":\"5\"}"))
                    .andExpect(status().isOk()).andExpect(content().json("{\"found\":false,\"result\":null}"));
            verify(host).replayAdministrativeRelease(command, "corr");

            for (String bad : List.of(body.replace(" exact ", " "), body.replace(" exact ", "x".repeat(1001)),
                    body.replace(" exact ", "x\",\"reason\":\"y"),
                    body.replace(" exact ", "x\",\"assignee\":\"fake"))) {
                mvc.perform(post(release).contentType("application/json")
                        .header("X-Task-Precondition", token).content(bad))
                        .andExpect(status().isBadRequest());
            }
            verify(host, times(1)).administrativelyRelease(any(), any());
            verify(host, times(1)).replayAdministrativeRelease(any(), any());
        }
    }
    @Test void commandParsingAndSafeErrorCategoriesArePreserved() throws Exception {
        try(var c=context(Host.class,Commands.class,WorkflowRuntimeCommandConfiguration.class)) {
            var host=c.getBean(WorkflowRuntimeCommandCoordinator.class);var mvc=mvc(c);
            String body="{\"commandId\":\"12345678-1234-1234-1234-123456789abc\",\"outcomeKey\":\"APPROVE\",\"comment\":null}";
            for(String bad:List.of(body.replace("null","3"),body.replace("null","null,\"actor\":\"fake\""),body.replace("APPROVE","APPROVE\",\"outcomeKey\":\"REJECT"),body.replace("12345678-1234-1234-1234-123456789abc","1-1-1-1-1")))
                mvc.perform(post("/custom/tasks/5/complete").contentType("application/json").header("X-Task-Precondition","0").content(bad))
                    .andExpect(status().isBadRequest());
            verifyNoInteractions(host);
            stubTaskCommands(host, 1L, 0L);
            for(var category:List.of(WorkflowExecutionException.Category.STALE_VERSION,WorkflowExecutionException.Category.LIMIT_EXCEEDED,
                    WorkflowExecutionException.Category.NOT_ASSIGNED,WorkflowExecutionException.Category.ASSIGNMENT_LOOKUP_FAILED,WorkflowExecutionException.Category.HOST_CONFIGURATION)) {
                doThrow(new WorkflowExecutionException(category)).when(host).complete(any(),any());
                int status=category==WorkflowExecutionException.Category.STALE_VERSION?409:category==WorkflowExecutionException.Category.LIMIT_EXCEEDED?422:category==WorkflowExecutionException.Category.NOT_ASSIGNED?403:category==WorkflowExecutionException.Category.ASSIGNMENT_LOOKUP_FAILED?503:500;
                mvc.perform(post("/custom/tasks/5/complete").contentType("application/json").header("X-Task-Precondition","0")
                    .requestAttr("trusted","safe").content(body)).andExpect(status().is(status)).andExpect(jsonPath("$.correlationId").value("safe"));
            }
        }
    }
}
