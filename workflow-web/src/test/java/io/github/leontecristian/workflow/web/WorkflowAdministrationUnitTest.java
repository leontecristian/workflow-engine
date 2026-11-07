package io.github.leontecristian.workflow.web;

import io.github.leontecristian.workflow.core.dto.definition.DefinitionPage;
import io.github.leontecristian.workflow.core.dto.definition.DefinitionVersionPage;
import io.github.leontecristian.workflow.core.dto.tasktype.TaskTypePage;
import io.github.leontecristian.workflow.core.dto.tasktype.CreateTaskType;
import io.github.leontecristian.workflow.core.dto.tasktype.UpdateTaskType;
import io.github.leontecristian.workflow.core.service.definition.WorkflowDefinitionService;
import io.github.leontecristian.workflow.core.service.tasktype.TaskTypeCatalogService;
import io.github.leontecristian.workflow.core.exception.definition.WorkflowDefinitionException;
import io.github.leontecristian.workflow.core.exception.tasktype.TaskTypeCatalogException;
import io.github.leontecristian.workflow.web.configuration.WorkflowAdministrationConfiguration;
import io.github.leontecristian.workflow.web.configuration.WorkflowAdministrationOptions;
import io.github.leontecristian.workflow.web.controller.definition.WorkflowDefinitionController;
import io.github.leontecristian.workflow.web.integration.administration.WorkflowAdministrationHost;
import io.github.leontecristian.workflow.web.exception.administration.WorkflowAdministrationException;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Isolated MVC contexts and doubles; no database, migrations, listener or external service. */
class WorkflowAdministrationUnitTest {
    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    static class Host {
        @Bean WorkflowDefinitionService definitions() { return mock(WorkflowDefinitionService.class); }
        @Bean TaskTypeCatalogService catalog() { return mock(TaskTypeCatalogService.class); }
        @Bean WorkflowAdministrationHost administration() { return mock(WorkflowAdministrationHost.class); }
    }
    @Configuration(proxyBeanMethods = false)
    @Import(WorkflowAdministrationConfiguration.class)
    static class Enabled {
        @Bean WorkflowAdministrationOptions options() {
            return new WorkflowAdministrationOptions("/custom/manage", "trusted.correlation");
        }
    }
    @Configuration(proxyBeanMethods = false)
    @Import(io.github.leontecristian.workflow.web.configuration.WorkflowViewerConfiguration.class)
    static class ViewerOnly {
        @Bean io.github.leontecristian.workflow.web.configuration.WorkflowViewerOptions viewerOptions() {
            return new io.github.leontecristian.workflow.web.configuration.WorkflowViewerOptions("/custom/read", "trusted.correlation");
        }
        @Bean io.github.leontecristian.workflow.core.service.runtime.WorkflowRuntimeQueryService runtimeReads() {
            return mock(io.github.leontecristian.workflow.core.service.runtime.WorkflowRuntimeQueryService.class);
        }
        @Bean io.github.leontecristian.workflow.web.integration.instance.WorkflowViewerAccess viewerAccess() {
            return mock(io.github.leontecristian.workflow.web.integration.instance.WorkflowViewerAccess.class);
        }
    }
    @Test void viewerOnlyActivationDoesNotEnableAdministration() throws Exception {
        try (var context = context(Host.class, ViewerOnly.class)) {
            assertThat(context.getBeansOfType(WorkflowDefinitionController.class)).isEmpty();
            var mvc = MockMvcBuilders.webAppContextSetup(context).build();
            mvc.perform(get("/custom/read/definitions")).andExpect(status().isNotFound());
            mvc.perform(post("/custom/read/versions/import")).andExpect(status().isNotFound());
        }
    }
    private AnnotationConfigWebApplicationContext context(Class<?>... classes) {
        var context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(classes); context.refresh(); return context;
    }
    private MockHttpServletRequestBuilder write(String method, String suffix, String body) {
        return (method.equals("PUT") ? put("/custom/manage" + suffix) : post("/custom/manage" + suffix))
                .header("X-Expected-Lock-Version", "9007199254740993")
                .requestAttr("trusted.correlation", "trusted-id")
                .header("X-Correlation-Id", "untrusted-id")
                .contentType("application/json").content(body);
    }
    @Test void scanningAndDependencyAloneRemainInert() throws Exception {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext()); context.register(Host.class);
            context.scan("io.github.leontecristian.workflow.web.configuration", "io.github.leontecristian.workflow.web.controller",
                    "io.github.leontecristian.workflow.web.service", "io.github.leontecristian.workflow.web.exception");
            context.refresh();
            assertThat(context.getBeansOfType(WorkflowDefinitionController.class)).isEmpty();
            var mvc = MockMvcBuilders.webAppContextSetup(context).build();
            mvc.perform(get("/custom/manage/definitions")).andExpect(status().isNotFound());
            mvc.perform(post("/custom/manage/versions/import")).andExpect(status().isNotFound());
        }
    }
    @Test void explicitActivationRequiresAdapterAndTrustedOptions() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(WorkflowAdministrationOptions.class,
                    () -> new WorkflowAdministrationOptions("/manage", "trusted"));
            context.registerBean(WorkflowDefinitionService.class, () -> mock(WorkflowDefinitionService.class));
            context.registerBean(TaskTypeCatalogService.class, () -> mock(TaskTypeCatalogService.class));
            context.register(WorkflowAdministrationConfiguration.class);
            assertThatThrownBy(context::refresh).hasMessageContaining("WorkflowAdministrationHost");
        }
        assertThatThrownBy(() -> new WorkflowAdministrationOptions("/manage", null)).isInstanceOf(IllegalArgumentException.class);
        for (String path : List.of("", "/", "/manage/", "/{tenant}", "/manage/**"))
            assertThatThrownBy(() -> new WorkflowAdministrationOptions(path, "trusted")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void everyOperationChecksAccessOnceBeforeAnyCoreOrMutationDelegate() throws Exception {
        try (var context = context(Host.class, Enabled.class)) {
            var mvc = MockMvcBuilders.webAppContextSetup(context).build();
            var host = context.getBean(WorkflowAdministrationHost.class);
            var definitions = context.getBean(WorkflowDefinitionService.class);
            var catalog = context.getBean(TaskTypeCatalogService.class);
            for (String route : List.of("/task-types", "/definitions", "/definitions/1", "/definitions/1/versions",
                    "/versions/lookup?key=test&version=1", "/versions/1", "/versions/1/export")) {
                reset(host); doThrow(new WorkflowAdministrationException(403, "FORBIDDEN")).when(host).requireAdministrationAccess();
                mvc.perform(get("/custom/manage" + route)).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
                verify(host).requireAdministrationAccess(); verifyNoMoreInteractions(host);
            }
            for (String route : List.of("/task-types", "/definitions/1", "/versions/import", "/versions/1/document",
                    "/versions/1/validate", "/versions/1/publish", "/versions/1/retire")) {
                reset(host); doThrow(new WorkflowAdministrationException(403, "FORBIDDEN")).when(host).requireAdministrationAccess();
                String method = route.equals("/definitions/1") || route.endsWith("document") ? "PUT" : "POST";
                mvc.perform(write(method, route, "invalid input never read")).andExpect(status().isForbidden());
                verify(host).requireAdministrationAccess(); verifyNoMoreInteractions(host);
            }
            reset(host); doThrow(new WorkflowAdministrationException(403, "FORBIDDEN")).when(host).requireAdministrationAccess();
            mvc.perform(put("/custom/manage/task-types/1")).andExpect(status().isForbidden());
            verify(host).requireAdministrationAccess(); verifyNoMoreInteractions(host);
            verifyNoInteractions(definitions, catalog);
        }
    }
    @Test void writesPreserveExactTokensRawTextTrustedCorrelationAndTypedDelegates() throws Exception {
        try (var context = context(Host.class, Enabled.class)) {
            var mvc = MockMvcBuilders.webAppContextSetup(context).build();
            var host = context.getBean(WorkflowAdministrationHost.class);
            String raw = " {\"number\":900719925474099312345,\"decimal\":1.00}\n";
            mvc.perform(write("POST", "/versions/import", raw)).andExpect(status().isOk());
            verify(host).importDraft(raw, "trusted-id");
            mvc.perform(write("PUT", "/versions/42/document", raw)).andExpect(status().isOk());
            verify(host).replace(42, 9007199254740993L, raw, "trusted-id");
            mvc.perform(write("PUT", "/definitions/42", "{\"name\":\"Name\",\"description\":null}")).andExpect(status().isOk());
            verify(host).metadata(42, 9007199254740993L, "Name", null, "trusted-id");
            mvc.perform(write("POST", "/task-types", "{\"code\":\"TEST\",\"name\":\"Name\",\"selectable\":true}")).andExpect(status().isOk());
            verify(host).createTaskType(new CreateTaskType("TEST", "Name", null, true), "trusted-id");
            mvc.perform(write("PUT", "/task-types/42", "{\"name\":\"Name\",\"selectable\":false}")).andExpect(status().isOk());
            verify(host).updateTaskType(new UpdateTaskType(42, 9007199254740993L, "Name", null, false), "trusted-id");
            for (String action : List.of("validate", "publish", "retire"))
                mvc.perform(write("POST", "/versions/42/" + action, "")).andExpect(status().isOk());
            verify(host).validate(42, 9007199254740993L, "trusted-id");
            verify(host).publish(42, 9007199254740993L, "trusted-id");
            verify(host).retire(42, 9007199254740993L, "trusted-id");
            verify(host, times(8)).requireAdministrationAccess(); verifyNoMoreInteractions(host);
        }
    }
    @Test void coreReadsPagingExportAndExistingExactRouteQueryHandlingSurvive() throws Exception {
        try (var context = context(Host.class, Enabled.class)) {
            var mvc = MockMvcBuilders.webAppContextSetup(context).build();
            var definitions = context.getBean(WorkflowDefinitionService.class);
            var catalog = context.getBean(TaskTypeCatalogService.class);
            when(catalog.list(any())).thenReturn(new TaskTypePage(List.of(), 9007199254740993L));
            when(definitions.listDefinitions(any())).thenReturn(new DefinitionPage(List.of(), null));
            when(definitions.listVersions(any())).thenReturn(new DefinitionVersionPage(List.of(), null));
            when(definitions.exportVersion(42)).thenReturn(" {\"large\":900719925474099312345}\n");
            mvc.perform(get("/custom/manage/task-types?afterId=9007199254740993&limit=100"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.nextAfterId").value("9007199254740993"));
            mvc.perform(get("/custom/manage/definitions")).andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
            verify(definitions).listDefinitions(new io.github.leontecristian.workflow.core.dto.definition.DefinitionQuery(0, 50));
            mvc.perform(get("/custom/manage/definitions?order=DESC&afterId=9007199254740993&limit=2"))
                    .andExpect(status().isOk());
            verify(definitions).listDefinitions(new io.github.leontecristian.workflow.core.dto.definition.DefinitionQuery(
                    9007199254740993L, 2, io.github.leontecristian.workflow.core.dto.definition.DefinitionOrder.DESC));
            mvc.perform(get("/custom/manage/definitions?order=newest")).andExpect(status().isBadRequest());
            mvc.perform(get("/custom/manage/definitions?order=DESC&order=ASC")).andExpect(status().isBadRequest());
            mvc.perform(get("/custom/manage/definitions/42/versions?status=DRAFT")).andExpect(status().isOk());
            mvc.perform(get("/custom/manage/versions/42/export?existingIgnoredQuery=x"))
                    .andExpect(status().isOk()).andExpect(header().string("Content-Disposition", "attachment; filename=\"workflow-version-42.json\""))
                    .andExpect(content().string(" {\"large\":900719925474099312345}\n"));
            mvc.perform(get("/custom/manage/definitions?unknown=x")).andExpect(status().isBadRequest());
            mvc.perform(get("/custom/manage/versions/lookup?key=test&version=2147483648")).andExpect(status().isBadRequest());
            mvc.perform(get("/custom/manage/versions/42?existingIgnoredQuery=x")).andExpect(status().isNotFound());
            verify(definitions).findVersion(42);
        }
    }
    @Test void failedHostMutationNeverReportsSuccessAndErrorsKeepManagementMapping() throws Exception {
        try (var context = context(Host.class, Enabled.class)) {
            var mvc = MockMvcBuilders.webAppContextSetup(context).build();
            var host = context.getBean(WorkflowAdministrationHost.class);
            doThrow(new IllegalStateException("private document and SQL")).when(host).importDraft(anyString(), anyString());
            mvc.perform(write("POST", "/versions/import", "{}"))
                    .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.message").value("HOST_FAILURE"))
                    .andExpect(jsonPath("$.correlationId").value("trusted-id"));
            doThrow(new TaskTypeCatalogException(TaskTypeCatalogException.Category.STALE_VERSION, null)).when(host).updateTaskType(any(), anyString());
            mvc.perform(write("PUT", "/task-types/42", "{\"name\":\"n\",\"selectable\":true}"))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.diagnostics").isEmpty());
            doThrow(new WorkflowDefinitionException(WorkflowDefinitionException.Category.INVALID_INPUT, List.of())).when(host).validate(anyLong(), anyLong(), anyString());
            mvc.perform(write("POST", "/versions/42/validate", "")).andExpect(status().isBadRequest());
            mvc.perform(write("POST", "/versions/42/publish", "{}")).andExpect(status().isBadRequest());
            verify(host, never()).publish(anyLong(), anyLong(), anyString());
        }
    }
}
