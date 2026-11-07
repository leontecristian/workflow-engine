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

import io.github.leontecristian.workflow.core.dto.runtime.CompleteWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandResult;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskPage;
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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/** Isolated MVC contexts and doubles; no persistence, migrations or listener. */
class WorkflowRuntimeWebUnitTest {
    @Configuration(proxyBeanMethods=false) @EnableWebMvc
    static class Host {
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
                    io.github.leontecristian.workflow.core.dto.runtime.TaskStatus.OPEN,null,java.time.Instant.EPOCH,null,null,null,null);
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
    @Test void variableSnapshotAndExactValuesSurviveAndStalePagesFail() throws Exception {
        try(var c=context(Host.class,Reads.class,WorkflowRuntimeReadConfiguration.class)) {
            var reads=c.getBean(WorkflowRuntimeQueryService.class);var host=c.getBean(WorkflowRuntimeReadHost.class);var mvc=mvc(c);
            var value=new io.github.leontecristian.workflow.core.dto.runtime.WorkflowValue(
                io.github.leontecristian.workflow.core.dto.runtime.VariableValueType.JSON,
                io.github.leontecristian.workflow.core.dto.runtime.WorkflowJsonValue.parse("{\"amount\":9007199254740993.00}"));
            when(reads.listVariables(1,null,50,null)).thenReturn(new io.github.leontecristian.workflow.core.dto.runtime.WorkflowVariablePage(1,7,
                List.of(new io.github.leontecristian.workflow.core.dto.runtime.WorkflowVariableView("data",value.type(),true,value)),"data"));
            mvc.perform(get("/custom/instances/1/variables")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("9007199254740993.00")))
                .andExpect(jsonPath("$.lockVersion").value("7")).andExpect(jsonPath("$.nextAfterKey").value("data"));
            verify(host).instance(1);
            when(reads.listVariables(1,"data",50,7L)).thenThrow(new WorkflowExecutionException(WorkflowExecutionException.Category.STALE_VERSION));
            mvc.perform(get("/custom/instances/1/variables?afterKey=data&expectedLockVersion=7"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_VERSION"));
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
