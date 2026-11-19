package io.github.leontecristian.workflow.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.leontecristian.workflow.core.dto.runtime.ActorKind;
import io.github.leontecristian.workflow.core.dto.runtime.InstanceStatus;
import io.github.leontecristian.workflow.core.dto.runtime.TaskEligibilityContext;
import io.github.leontecristian.workflow.core.dto.runtime.TaskStatus;
import io.github.leontecristian.workflow.core.dto.runtime.TaskSubmission;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowAuthorizationRequest;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCancellationAuthorizationContext;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandContext;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowInstanceView;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowOperation;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowPermission;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowResource;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowScope;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskView;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowVariablePage;
import io.github.leontecristian.workflow.core.service.definition.WorkflowDefinitionService;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowAuthorization;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowAuthorizationSupport;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowRuntimeQueryService;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowRuntimeReadAuthorization;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowTaskEligibilityService;
import io.github.leontecristian.workflow.core.service.tasktype.TaskTypeCatalogService;
import io.github.leontecristian.workflow.web.configuration.WorkflowRuntimeOptions;
import io.github.leontecristian.workflow.web.configuration.WorkflowRuntimeReadConfiguration;
import io.github.leontecristian.workflow.web.controller.runtime.WorkflowRuntimeCommandController;
import io.github.leontecristian.workflow.web.exception.WorkflowUnauthenticatedException;
import io.github.leontecristian.workflow.web.integration.WorkflowRequestIdentity;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

class WorkflowRuntimeReadBoundaryUnitTest {
    private static final String HINT = "/custom/instances/1/cancellation-availability";
    private static final String FALSE_HINT = "{\"allowed\":false,\"lockVersion\":\"9007199254740994\"}";
    private static final Instant TIME = Instant.parse("2026-11-19T08:00:00.123456789Z");

    @Test void unauthenticatedAndInvalidActorsUseTheRuntimeEnvelopeBeforePolicyOrQueries() throws Exception {
        try (var context = readContext()) {
            var identity = context.getBean(WorkflowRequestIdentity.class);
            when(identity.requireActorRef()).thenThrow(new WorkflowUnauthenticatedException("private authentication detail"))
                    .thenReturn(null, " \t");
            var mvc = mvc(context);
            var request = get(HINT).requestAttr("trusted", "request-correlation");
            expectFailure(mvc, request, 401, "UNAUTHENTICATED");
            expectFailure(mvc, request, 500, "HOST_FAILURE");
            expectFailure(mvc, request, 500, "HOST_FAILURE");
            verify(identity, times(3)).requireActorRef();
            verifyNoInteractions(context.getBean(WorkflowRuntimeReadAuthorization.class),
                    context.getBean(WorkflowRuntimeQueryService.class), context.getBean(WorkflowTaskEligibilityService.class),
                    context.getBean(WorkflowDefinitionService.class), context.getBean(TaskTypeCatalogService.class));
        }
    }

    @Test void submissionAccessRechecksCurrentPolicyForTheSameActorAndResolvesIdentityAgainNextRequest() throws Exception {
        try (var context = readContext()) {
            var identity = context.getBean(WorkflowRequestIdentity.class);
            var reads = context.getBean(WorkflowRuntimeQueryService.class);
            var policy = context.getBean(WorkflowRuntimeReadAuthorization.class);
            when(identity.requireActorRef()).thenReturn("first", "second");
            var open = mock(WorkflowTaskView.class);
            when(open.status()).thenReturn(TaskStatus.OPEN);
            var completed = mock(WorkflowTaskView.class);
            when(completed.status()).thenReturn(TaskStatus.COMPLETED);
            when(reads.findTask(5)).thenReturn(Optional.of(open), Optional.of(completed));
            var submission = new TaskSubmission(5, 2, 1, 3, new UUID(0, 5), ActorKind.USER,
                    "second", Instant.EPOCH, "APPROVE", null, Map.of(), null);
            when(reads.findTaskSubmission(5)).thenReturn(Optional.of(submission));
            when(policy.canReadTask(new TaskEligibilityContext(open, "first"))).thenReturn(true);
            when(policy.canReadTask(new TaskEligibilityContext(completed, "first"))).thenReturn(false);
            when(policy.canReadTask(new TaskEligibilityContext(completed, "second"))).thenReturn(true);

            var mvc = mvc(context);
            mvc.perform(get("/custom/tasks/5/submission")).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));
            mvc.perform(get("/custom/tasks/5/submission")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.taskId").value("5"));
            var order = inOrder(identity, policy);
            order.verify(identity).requireActorRef();
            order.verify(policy).canReadTask(new TaskEligibilityContext(open, "first"));
            order.verify(policy).canReadTask(new TaskEligibilityContext(completed, "first"));
            order.verify(identity).requireActorRef();
            order.verify(policy).canReadTask(new TaskEligibilityContext(completed, "second"));
            order.verifyNoMoreInteractions();
        }
    }

    @Test void readOnlyHostWithoutClockReturnsFalseEvenWithCancellationPolicy() throws Exception {
        try (var context = readContext(CancellationHost.class)) {
            assertThat(context.getBeansOfType(Clock.class)).isEmpty();
            assertThat(context.getBeansOfType(WorkflowRuntimeCommandController.class)).isEmpty();
            readableInstance(context);
            mvc(context).perform(get(HINT)).andExpect(status().isOk()).andExpect(content().json(FALSE_HINT));
            verify(context.getBean(WorkflowAuthorization.class), never()).isAllowed(any());
        }
    }

    @Test void absentPolicySkipsClockForAnActiveReadableInstance() throws Exception {
        try (var context = readContext(ClockHost.class)) {
            readableInstance(context);
            mvc(context).perform(get(HINT)).andExpect(status().isOk()).andExpect(content().json(FALSE_HINT));
            verifyNoInteractions(context.getBean(Clock.class));
        }
    }

    @Test void onlyAnActiveReadableHintSamplesTimeAndEvaluatesCancellation() throws Exception {
        try (var context = readContext(ClockHost.class, CancellationHost.class)) {
            var instance = readableInstance(context);
            var reads = context.getBean(WorkflowRuntimeQueryService.class);
            var readPolicy = context.getBean(WorkflowRuntimeReadAuthorization.class);
            var clock = context.getBean(Clock.class);
            var cancellation = context.getBean(WorkflowAuthorization.class);
            var mvc = mvc(context);

            when(reads.listVariables(1, null, 50, null))
                    .thenReturn(new WorkflowVariablePage(1, 9007199254740994L, List.of(), null));
            mvc.perform(get("/custom/instances/1/variables")).andExpect(status().isOk());
            when(instance.status()).thenReturn(InstanceStatus.CANCELLED);
            mvc.perform(get(HINT)).andExpect(status().isOk()).andExpect(content().json(FALSE_HINT));
            when(instance.status()).thenReturn(InstanceStatus.ACTIVE);
            when(readPolicy.canReadInstance("actor", instance)).thenReturn(false);
            mvc.perform(get(HINT)).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
            verifyNoInteractions(clock);
            verify(cancellation, never()).isAllowed(any());

            when(readPolicy.canReadInstance("actor", instance)).thenReturn(true);
            var expected = new WorkflowCancellationAuthorizationContext(1, 2, "request", "7",
                    new WorkflowCommandContext(ActorKind.USER, "actor",
                            Instant.parse("2026-11-19T08:00:00.123456700Z"), null));
            var access = cancellationRequest(WorkflowScope.ACCESS_CANCELLATION, expected);
            var cancel = cancellationRequest(WorkflowScope.CANCEL, expected);
            when(cancellation.isAllowed(access)).thenReturn(true);
            when(cancellation.isAllowed(cancel)).thenReturn(true, false)
                    .thenThrow(new IllegalStateException("private cancellation policy detail"));
            var hintRequest = get(HINT).requestAttr("trusted", "request-correlation");
            mvc.perform(hintRequest).andExpect(status().isOk())
                    .andExpect(content().json("{\"allowed\":true,\"lockVersion\":\"9007199254740994\"}"));
            var order = inOrder(readPolicy, clock, cancellation);
            order.verify(readPolicy, times(4)).canReadInstance("actor", instance);
            order.verify(clock).instant();
            order.verify(cancellation).isAllowed(access);
            order.verify(cancellation).isAllowed(cancel);
            mvc.perform(hintRequest).andExpect(status().isOk()).andExpect(content().json(FALSE_HINT));
            expectFailure(mvc, hintRequest, 503, "AUTHORIZATION_LOOKUP_FAILED");
            verify(clock, times(3)).instant();
            verify(cancellation, times(3)).isAllowed(access);
            verify(cancellation, times(3)).isAllowed(cancel);
        }
    }

    @Test void completionOnlyPolicySkipsCancellationAndClock() throws Exception {
        try (var context = readContext(ClockHost.class, CompletionOnlyHost.class)) {
            readableInstance(context);
            mvc(context).perform(get(HINT)).andExpect(status().isOk()).andExpect(content().json(FALSE_HINT));
            verifyNoInteractions(context.getBean(Clock.class));
            verify(context.getBean(WorkflowAuthorization.class), never()).isAllowed(any());
        }
    }

    private static WorkflowAuthorizationRequest cancellationRequest(WorkflowScope scope,
            WorkflowCancellationAuthorizationContext context) {
        return new WorkflowAuthorizationRequest.Cancellation(new WorkflowPermission(WorkflowResource.INSTANCE, scope), context);
    }

    private static void expectFailure(MockMvc mvc, RequestBuilder request,
            int statusCode, String code) throws Exception {
        mvc.perform(request).andExpect(status().is(statusCode)).andExpect(content().json(
                "{\"code\":\"" + code + "\",\"message\":\"" + code
                        + "\",\"diagnostics\":[],\"correlationId\":\"request-correlation\"}",
                JsonCompareMode.STRICT));
    }

    private static WorkflowInstanceView readableInstance(AnnotationConfigWebApplicationContext context) {
        var instance = mock(WorkflowInstanceView.class);
        when(instance.id()).thenReturn(1L);
        when(instance.definitionVersionId()).thenReturn(2L);
        when(instance.businessType()).thenReturn("request");
        when(instance.businessId()).thenReturn("7");
        when(instance.status()).thenReturn(InstanceStatus.ACTIVE);
        when(instance.lockVersion()).thenReturn(9007199254740994L);
        when(context.getBean(WorkflowRequestIdentity.class).requireActorRef()).thenReturn("actor");
        when(context.getBean(WorkflowRuntimeQueryService.class).findInstance(1)).thenReturn(Optional.of(instance));
        when(context.getBean(WorkflowRuntimeReadAuthorization.class).canReadInstance("actor", instance)).thenReturn(true);
        return instance;
    }

    private static AnnotationConfigWebApplicationContext readContext(Class<?>... optional) {
        var context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(ReadHost.class, WorkflowRuntimeReadConfiguration.class);
        if (optional.length > 0) context.register(optional);
        context.refresh();
        return context;
    }

    private static MockMvc mvc(AnnotationConfigWebApplicationContext context) {
        return MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import(WorkflowAuthorizationSupport.class)
    static class ReadHost {
        @Bean WorkflowRuntimeOptions runtimeOptions() { return new WorkflowRuntimeOptions("/custom", "trusted"); }
        @Bean WorkflowRequestIdentity identity() { return mock(WorkflowRequestIdentity.class); }
        @Bean WorkflowRuntimeReadAuthorization readPolicy() { return mock(WorkflowRuntimeReadAuthorization.class); }
        @Bean WorkflowRuntimeQueryService reads() { return mock(WorkflowRuntimeQueryService.class); }
        @Bean WorkflowTaskEligibilityService eligibility() { return mock(WorkflowTaskEligibilityService.class); }
        @Bean WorkflowDefinitionService definitions() { return mock(WorkflowDefinitionService.class); }
        @Bean TaskTypeCatalogService catalog() { return mock(TaskTypeCatalogService.class); }
    }

    @Configuration(proxyBeanMethods = false)
    static class ClockHost {
        @Bean Clock clock() {
            var clock = mock(Clock.class);
            when(clock.instant()).thenReturn(TIME);
            return clock;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CancellationHost {
        @Bean WorkflowAuthorization cancellation() { return policyFor(WorkflowOperation.CANCEL_WORKFLOW); }
    }

    @Configuration(proxyBeanMethods = false)
    static class CompletionOnlyHost {
        @Bean WorkflowAuthorization completion() { return policyFor(WorkflowOperation.COMPLETE_TASK); }
    }

    private static WorkflowAuthorization policyFor(WorkflowOperation operation) {
        var policy = mock(WorkflowAuthorization.class);
        when(policy.supportedOperations()).thenReturn(Set.of(operation));
        return policy;
    }
}
