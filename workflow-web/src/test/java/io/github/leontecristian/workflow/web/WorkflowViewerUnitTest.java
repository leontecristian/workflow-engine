package io.github.leontecristian.workflow.web;

import io.github.leontecristian.workflow.core.dto.runtime.ActorKind;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowHistoryEvent;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowHistorySummary;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowHistoryPage;
import io.github.leontecristian.workflow.core.service.definition.WorkflowDefinitionService;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowRuntimeQueryService;
import io.github.leontecristian.workflow.web.configuration.WorkflowViewerConfiguration;
import io.github.leontecristian.workflow.web.configuration.WorkflowViewerOptions;
import io.github.leontecristian.workflow.web.controller.instance.WorkflowInstanceViewerController;
import io.github.leontecristian.workflow.web.dto.instance.ViewerLinkReference;
import io.github.leontecristian.workflow.web.exception.instance.WorkflowViewerException;
import io.github.leontecristian.workflow.web.integration.instance.WorkflowViewerAccess;
import io.github.leontecristian.workflow.web.service.instance.ViewerWire;
import io.github.leontecristian.workflow.web.service.instance.WorkflowInstanceViewerService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Isolated contexts and core doubles only: no Boot, persistence, migrations or HTTP listener. */
class WorkflowViewerUnitTest {
    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    static class Host {
        @Bean WorkflowRuntimeQueryService reads() { return mock(WorkflowRuntimeQueryService.class); }
        @Bean WorkflowDefinitionService definitions() { return mock(WorkflowDefinitionService.class); }
        @Bean WorkflowViewerAccess access() { return mock(WorkflowViewerAccess.class); }
        @Bean UnrelatedController unrelatedController() { return new UnrelatedController(); }
    }
    @org.springframework.web.bind.annotation.RestController
    static class UnrelatedController {
        @org.springframework.web.bind.annotation.GetMapping("/host-error")
        Object error() { throw new IllegalStateException("host failure"); }
        @org.springframework.web.bind.annotation.GetMapping("/host-number")
        Object number() { return java.util.Map.of("id", 9007199254740993L); }
        @org.springframework.web.bind.annotation.PostMapping("/custom/read/instances/start")
        String start() { return "host-start"; }
        @org.springframework.web.bind.annotation.ExceptionHandler(IllegalStateException.class)
        org.springframework.http.ResponseEntity<String> ownError() {
            return org.springframework.http.ResponseEntity.status(418).body("host-owned-error");
        }
    }
    @Configuration(proxyBeanMethods = false)
    @Import(WorkflowViewerConfiguration.class)
    static class Enabled {
        @Bean WorkflowViewerOptions options() { return new WorkflowViewerOptions("/custom/read", "trusted.correlation"); }
    }
    private AnnotationConfigWebApplicationContext context(Class<?>... classes) {
        var context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext()); context.register(classes); context.refresh(); return context;
    }
    @Test void viewerReadPatternDoesNotShadowHostStartPost() throws Exception {
        try (var context = context(Host.class, Enabled.class)) {
            var mvc = MockMvcBuilders.webAppContextSetup(context).build();
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/custom/read/instances/start"))
                    .andExpect(status().isOk()).andExpect(content().string("host-start"));
            verifyNoViewerAccess(context);
        }
    }
    private void verifyNoViewerAccess(AnnotationConfigWebApplicationContext context) {
        org.mockito.Mockito.verifyNoInteractions(context.getBean(WorkflowViewerAccess.class),
                context.getBean(WorkflowRuntimeQueryService.class));
    }
    @Test void packageScanningAndDependencyAloneExposeNothing() throws Exception {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext()); context.register(Host.class);
            context.scan("io.github.leontecristian.workflow.web.configuration", "io.github.leontecristian.workflow.web.controller",
                    "io.github.leontecristian.workflow.web.service", "io.github.leontecristian.workflow.web.exception");
            context.refresh();
            assertThat(context.getBeansOfType(WorkflowInstanceViewerController.class)).isEmpty();
            var mvc = MockMvcBuilders.webAppContextSetup(context).build();
            for (String suffix : List.of("", "/history", "/history/1", "/graph"))
                mvc.perform(get("/custom/read/instances/1" + suffix)).andExpect(status().isNotFound());
        }
    }
    @Test void incompleteActivationFailsAndPathsMustBeLiteral() {
        assertThatThrownBy(() -> context(Host.class, WorkflowViewerConfiguration.class)).hasMessageContaining("WorkflowViewerOptions");
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(WorkflowViewerOptions.class, () -> new WorkflowViewerOptions("/read", null));
            context.registerBean(WorkflowRuntimeQueryService.class, () -> mock(WorkflowRuntimeQueryService.class));
            context.register(WorkflowViewerConfiguration.class);
            assertThatThrownBy(context::refresh).hasMessageContaining("WorkflowViewerAccess");
        }
        for (String path : List.of("", "/", "/read/", "/read/{tenant}", "/read/**"))
            assertThatThrownBy(() -> new WorkflowViewerOptions(path, null)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void explicitActivationMapsOnlySelectedReadsAndRepeatsBothGrants() throws Exception {
        try (var context = context(Host.class, Enabled.class)) {
            var mvc = MockMvcBuilders.webAppContextSetup(context).build();
            var access = context.getBean(WorkflowViewerAccess.class);
            var reads = context.getBean(WorkflowRuntimeQueryService.class);
            when(reads.listHistory(1, 0, 50)).thenReturn(new WorkflowHistoryPage(List.of(), null));
            mvc.perform(get("/custom/read/instances/1/history")).andExpect(status().isOk())
                    .andExpect(content().json("{\"items\":[],\"nextAfterSequence\":null}"));
            verify(access).requireInstanceRead(1); verify(access).requireHistoryRead(1);
            doThrow(new WorkflowViewerException(404, "NOT_FOUND")).when(access).requireHistoryRead(1);
            mvc.perform(get("/custom/read/instances/1/history").param("afterSequence", "50"))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.diagnostics").isEmpty());
            mvc.perform(get("/custom/read/instances/1/history/1")).andExpect(status().isNotFound());
            verify(reads, never()).findHistoryEvent(anyLong(), anyLong(), any());
            doThrow(new WorkflowViewerException(401, "UNAUTHENTICATED")).when(access).requireInstanceRead(1);
            for (String suffix : List.of("", "/history", "/history/1", "/graph"))
                mvc.perform(get("/custom/read/instances/1" + suffix)).andExpect(status().isUnauthorized());
            mvc.perform(get("/custom/read/instances")).andExpect(status().isNotFound());
            mvc.perform(get("/custom/read/tasks/1")).andExpect(status().isNotFound());
        }
    }
    @Test void ownershipHistoryUsesExactTaskGrantEvenWhenHostDetailHookAllowsRead() throws Exception {
        try (var context = context(Host.class, Enabled.class)) {
            var mvc = MockMvcBuilders.webAppContextSetup(context).build();
            var access = context.getBean(WorkflowViewerAccess.class);
            var reads = context.getBean(WorkflowRuntimeQueryService.class);
            var release = new WorkflowHistorySummary(1, 2, "TASK_RELEASED", UUID.randomUUID(), null,
                    ActorKind.USER, "admin", Instant.EPOCH, 3L, 4L, null, null, null, (short) 1);
            when(reads.findHistoryEvent(eq(1L), eq(2L), any())).thenReturn(Optional.of(new WorkflowHistoryEvent(release, "{\"reason\":\"private\"}")));
            when(reads.listHistory(1, 1, 1)).thenReturn(new WorkflowHistoryPage(List.of(release), 2L));
            // The host detail hook accepts the event, while its exact-task policy denies it.
            mvc.perform(get("/custom/read/instances/1/history/2"))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.detailsJson").doesNotExist());
            mvc.perform(get("/custom/read/instances/1/history").param("afterSequence", "1").param("limit", "1"))
                    .andExpect(status().isOk()).andExpect(content().json("{\"items\":[],\"nextAfterSequence\":\"2\"}"));
            when(access.canReadHistorySummary(release)).thenReturn(true);
            mvc.perform(get("/custom/read/instances/1/history/2"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.detailsJson").value("{\"reason\":\"private\"}"));
            doThrow(WorkflowViewerException.missing()).when(access).requireHistoryEventRead(release);
            mvc.perform(get("/custom/read/instances/1/history/2")).andExpect(status().isNotFound());
        }
    }

    @Test void queryValidationAndScopedSafeErrors() throws Exception {
        try (var context = context(Host.class, Enabled.class)) {
            var mvc = MockMvcBuilders.webAppContextSetup(context).build();
            for (String query : List.of("limit=0", "limit=101", "limit=01", "limit=2&limit=3", "unknown=1", "afterSequence=-1", "afterSequence=9223372036854775808"))
                mvc.perform(get("/custom/read/instances/1/history?" + query)).andExpect(status().isBadRequest());
            mvc.perform(get("/custom/read/instances/1/graph?unknown=1")).andExpect(status().isBadRequest());
            var reads = context.getBean(WorkflowRuntimeQueryService.class);
            when(reads.findInstance(1)).thenThrow(new IllegalStateException("private SQL or secret"));
            mvc.perform(get("/custom/read/instances/1?unchangedDetailQuery=1").requestAttr("trusted.correlation", "safe-id"))
                    .andExpect(status().isInternalServerError()).andExpect(content().json(
                            "{\"code\":\"HOST_FAILURE\",\"message\":\"HOST_FAILURE\",\"diagnostics\":[],\"correlationId\":\"safe-id\"}"));
        }
    }
    @Test void structuredCompletionDetailsRequireAnExactTaskGrantEvenWithPermissiveHistoryAccess() throws Exception {
        try (var context = context(Host.class, Enabled.class)) {
            var mvc = MockMvcBuilders.webAppContextSetup(context).build();
            var access = context.getBean(WorkflowViewerAccess.class);
            var reads = context.getBean(WorkflowRuntimeQueryService.class);
            var event = new WorkflowHistorySummary(1, 3, "TASK_COMPLETED", UUID.randomUUID(), null,
                    ActorKind.USER, "reviewer", Instant.EPOCH, 3L, 4L, null, null, null, (short) 3);
            String details = "{\"outcomeKey\":\"APPROVE\",\"comment\":null,\"inputs\":{},\"result\":{\"amount\":123.00}}";
            when(reads.findHistoryEvent(eq(1L), eq(3L), any())).thenReturn(Optional.of(new WorkflowHistoryEvent(event, details)));
            var exactTask = new ViewerLinkReference(3, 4L);
            when(access.readableLinks(1, Set.of(exactTask))).thenReturn(Set.of(new ViewerLinkReference(3, null)));
            mvc.perform(get("/custom/read/instances/1/history/3"))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.detailsJson").doesNotExist());
            when(access.readableLinks(1, Set.of(exactTask))).thenReturn(Set.of(exactTask));
            mvc.perform(get("/custom/read/instances/1/history/3"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.detailsJson").value(details));
            verify(access).requireHistoryEventRead(event);
        }
    }
    @Test void taskInputDetailsRequireReceivingTaskAccess() throws Exception {
        try (var context = context(Host.class, Enabled.class)) {
            var mvc = MockMvcBuilders.webAppContextSetup(context).build();
            var access = context.getBean(WorkflowViewerAccess.class);
            var reads = context.getBean(WorkflowRuntimeQueryService.class);
            var event = new WorkflowHistorySummary(1, 3, "TASK_CREATED", UUID.randomUUID(), null,
                    ActorKind.USER, "reviewer", Instant.EPOCH, 3L, 4L, null, null, null, (short) 5);
            String details = "{\"taskKey\":\"REVIEW\",\"inputs\":{\"booking\":{\"type\":\"JSON\",\"value\":{\"amount\":123.00}}}}";
            when(reads.findHistoryEvent(eq(1L), eq(3L), any())).thenAnswer(call -> {
                Predicate<WorkflowHistorySummary> authorized = call.getArgument(2);
                return authorized.test(event) ? Optional.of(new WorkflowHistoryEvent(event, details)) : Optional.empty();
            });
            var receivingTask = new ViewerLinkReference(3, 4L);
            when(access.readableLinks(1, Set.of(receivingTask))).thenReturn(Set.of(new ViewerLinkReference(3, null)));
            mvc.perform(get("/custom/read/instances/1/history/3"))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.detailsJson").doesNotExist());
            when(access.readableLinks(1, Set.of(receivingTask))).thenReturn(Set.of(receivingTask));
            mvc.perform(get("/custom/read/instances/1/history/3"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.detailsJson").value(details));
            verify(access).requireHistoryEventRead(event);
        }
    }
    @Test void viewerCodecAndErrorsDoNotReplaceHostHandling() throws Exception {
        try (var context = context(Host.class, Enabled.class)) {
            var mvc = MockMvcBuilders.webAppContextSetup(context).build();
            mvc.perform(get("/host-error")).andExpect(status().isIAmATeapot()).andExpect(content().string("host-owned-error"));
            mvc.perform(get("/host-number")).andExpect(content().string("{\"id\":9007199254740993}"));
        }
    }
    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void serverFailuresLogOriginalExceptionsWhileResponsesStaySafe(CapturedOutput output) throws Exception {
        try (var context = context(Host.class, Enabled.class)) {
            var mvc = MockMvcBuilders.webAppContextSetup(context).build();
            var reads = context.getBean(WorkflowRuntimeQueryService.class);
            var unexpected = new IllegalStateException("private-viewer-detail", new IllegalArgumentException("private-viewer-cause"));
            var configuration = new io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException(
                    io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category.HOST_CONFIGURATION,
                    new IllegalArgumentException("private-configuration-cause"));
            var graphFailure = new WorkflowViewerException(500, "HOST_CONFIGURATION");
            graphFailure.initCause(new IllegalStateException("private-graph-cause"));
            for (RuntimeException failure : List.of(unexpected, configuration, graphFailure)) {
                doThrow(failure).when(reads).findInstance(1);
                String code = failure == unexpected ? "HOST_FAILURE" : "HOST_CONFIGURATION";
                mvc.perform(get("/custom/read/instances/1").requestAttr("trusted.correlation", "viewer-log-id"))
                        .andExpect(status().isInternalServerError())
                        .andExpect(content().json("{\"code\":\"" + code + "\",\"message\":\"" + code
                                + "\",\"diagnostics\":[],\"correlationId\":\"viewer-log-id\"}"));
            }
            doThrow(WorkflowViewerException.missing()).when(reads).findInstance(1);
            mvc.perform(get("/custom/read/instances/1").requestAttr("trusted.correlation", "expected-viewer-404"))
                    .andExpect(status().isNotFound());
            assertThat(output.getAll()).contains("Workflow viewer failure viewer-log-id (HOST_FAILURE)",
                    "Workflow viewer failure viewer-log-id (HOST_CONFIGURATION)",
                    "java.lang.IllegalStateException: private-viewer-detail",
                    "Caused by: java.lang.IllegalArgumentException: private-viewer-cause",
                    "Caused by: java.lang.IllegalArgumentException: private-configuration-cause",
                    "Caused by: java.lang.IllegalStateException: private-graph-cause",
                    "at " + WorkflowViewerUnitTest.class.getName())
                    .doesNotContain("Workflow viewer failure expected-viewer-404");
        }
    }
    @Test void exactEventUnicodeStoredBoundsAreIndependentOfEscaping() {
        var reads = mock(WorkflowRuntimeQueryService.class);
        var viewer = new WorkflowInstanceViewerService(reads, mock(WorkflowViewerAccess.class), new ViewerWire());
        String unicode = "{\"v\":\"" + "漢".repeat(1398098) + "\"}";
        assertThat(unicode.getBytes(java.nio.charset.StandardCharsets.UTF_8).length).isLessThanOrEqualTo(4 * 1024 * 1024);
        when(reads.findHistoryEvent(eq(1L), eq(1L), any())).thenReturn(Optional.of(new WorkflowHistoryEvent(summary(1, "actor"), unicode)));
        assertThat(viewer.event(1, 1)).contains("漢");
        when(reads.findHistoryEvent(eq(1L), eq(1L), any())).thenReturn(Optional.of(new WorkflowHistoryEvent(summary(1, "actor"), unicode + "漢")));
        assertThatThrownBy(() -> viewer.event(1, 1)).isInstanceOf(WorkflowViewerException.class);
    }
    private WorkflowHistorySummary summary(long sequence, String actor) {
        return new WorkflowHistorySummary(9007199254740993L, sequence, "FUTURE_EVENT", new UUID(0, 1), null,
                ActorKind.USER, actor, Instant.EPOCH, null, null, null, null, null, (short) 1);
    }
    @Test void exactEventPreservesSourceAndEnforcesFinalEscapedEnvelope() {
        var reads = mock(WorkflowRuntimeQueryService.class); var access = mock(WorkflowViewerAccess.class);
        var wire = new ViewerWire(); var viewer = new WorkflowInstanceViewerService(reads, access, wire);
        String exact = "{\"long\":9007199254740993,\"decimal\":12500.00,\"nil\":null}";
        when(reads.findHistoryEvent(eq(1L), eq(1L), any())).thenReturn(Optional.of(new WorkflowHistoryEvent(summary(1, "actor"), exact)));
        var json = viewer.event(1, 1);
        assertThat(wire.tree(json).path("detailsJson").textValue()).isEqualTo(exact);
        assertThat(json).contains("\"instanceId\":\"9007199254740993\"", "FUTURE_EVENT");
        // Exactly 4 MiB UTF-16 details, well above the ordinary summary cap.
        String maximum = "{\"v\":\"" + "a".repeat(2 * 1024 * 1024 - 8) + "\"}";
        assertThat(maximum.length()).isEqualTo(2 * 1024 * 1024);
        when(reads.findHistoryEvent(eq(1L), eq(1L), any())).thenReturn(Optional.of(new WorkflowHistoryEvent(summary(1, "actor"), maximum)));
        assertThat(viewer.event(1, 1)).contains(maximum.substring(10, 100));
        // Valid stored JSON whose backslashes expand in the final detailsJson envelope.
        String escaping = "{\"v\":\"" + "\\u0001".repeat(349524) + "\"}";
        when(reads.findHistoryEvent(eq(1L), eq(1L), any())).thenReturn(Optional.of(new WorkflowHistoryEvent(summary(1, "actor"), escaping)));
        assertThat(viewer.event(1, 1).getBytes(java.nio.charset.StandardCharsets.UTF_8).length).isLessThan(8 * 1024 * 1024);
        // Oversized final envelope fails, never returning a partial event.
        when(reads.findHistoryEvent(eq(1L), eq(1L), any())).thenReturn(Optional.of(new WorkflowHistoryEvent(summary(1, "\u0001".repeat(1400000)), exact)));
        assertThatThrownBy(() -> viewer.event(1, 1)).isInstanceOfSatisfying(WorkflowViewerException.class, e -> assertThat(e.code()).isEqualTo("LIMIT_EXCEEDED"));
        when(reads.findHistoryEvent(eq(1L), eq(1L), any())).thenReturn(Optional.of(new WorkflowHistoryEvent(summary(1, "actor"), maximum + " ")));
        assertThatThrownBy(() -> viewer.event(1, 1)).isInstanceOf(WorkflowViewerException.class);
    }
    @Test void bytePagingIncludesWholeRowsAndLastReturnedContinuation() {
        var wire = new ViewerWire();
        var rows = List.of(summary(1, "a".repeat(180000)), summary(2, "b".repeat(180000)), summary(3, "c".repeat(180000)));
        var page = wire.page(rows, null, "nextAfterSequence", WorkflowHistorySummary::historySequence);
        assertThat((List<?>) page.get("items")).hasSize(2);
        assertThat(page.get("nextAfterSequence")).isEqualTo(2L);
        assertThat(wire.fits(wire.json(page), 1024 * 1024)).isTrue();
        assertThatThrownBy(() -> wire.page(List.of(summary(1, "a".repeat(600000))), null, "nextAfterSequence", WorkflowHistorySummary::historySequence))
                .isInstanceOf(WorkflowViewerException.class);
    }
}
