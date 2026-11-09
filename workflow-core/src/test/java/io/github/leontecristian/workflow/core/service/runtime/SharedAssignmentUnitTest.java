package io.github.leontecristian.workflow.core.service.runtime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.github.leontecristian.workflow.core.dto.runtime.*;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.core.repository.runtime.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Pageable;

class SharedAssignmentUnitTest {
    @SuppressWarnings("unchecked")
    private <T> ObjectProvider<T> source(List<T> values) {
        ObjectProvider<T> source=mock(ObjectProvider.class);
        when(source.stream()).thenAnswer(i->values.stream()); return source;
    }
    @Test void declaredResolverKindsAndSharedSupportAreMandatory() {
        var resolver=mock(SingleTaskAssignmentResolver.class);
        when(resolver.identifier()).thenReturn("shared");
        when(resolver.targetKinds()).thenReturn(Set.of(AssignmentTargetKind.GROUP));
        var withoutShared=new RuntimeHostSupport(source(List.of(resolver)),source(List.of()),eligibility(),source(List.of()));
        assertThatThrownBy(()->withoutShared.requireResolver("shared",Set.of(AssignmentTargetKind.USER)))
                .isInstanceOfSatisfying(WorkflowExecutionException.class,e->assertThat(e.category()).isEqualTo(WorkflowExecutionException.Category.HOST_CONFIGURATION));
        assertThatThrownBy(()->withoutShared.requireResolver("shared",Set.of(AssignmentTargetKind.GROUP)))
                .isInstanceOfSatisfying(WorkflowExecutionException.class,e->assertThat(e.category()).isEqualTo(WorkflowExecutionException.Category.HOST_CONFIGURATION));
        var provider=mock(WorkflowSharedAssignmentProvider.class);
        var descriptor=new io.github.leontecristian.workflow.core.dto.definition.SingleAssignmentCapability("shared",Set.of(AssignmentTargetKind.GROUP));
        io.github.leontecristian.workflow.core.service.definition.DefinitionCapabilityProvider capabilities=()->
                new io.github.leontecristian.workflow.core.dto.definition.DefinitionCapabilities(Set.of(descriptor),Set.of());
        var supported=new RuntimeHostSupport(source(List.of(resolver)),source(List.of()),eligibility(provider),source(List.of(capabilities)));
        supported.requireResolver("shared",Set.of(AssignmentTargetKind.GROUP));
        verifyNoInteractions(provider); // Publication checks configuration, never resolves membership/targets.
        when(resolver.resolve(any())).thenReturn(new SingleAssignmentResult(List.of(new ResolvedTask("review",new TaskAssignmentTarget(AssignmentTargetKind.ROLE,"finance"))),null));
        assertThatThrownBy(()->supported.resolve(resolver,mock(AssignmentContext.class)))
                .isInstanceOfSatisfying(WorkflowExecutionException.class,e->assertThat(e.category()).isEqualTo(WorkflowExecutionException.Category.INVALID_ASSIGNMENT));
        when(resolver.resolve(any())).thenReturn(new SingleAssignmentResult(List.of(new ResolvedTask("review",new TaskAssignmentTarget(AssignmentTargetKind.GROUP,"finance"))),null));
        assertThatThrownBy(()->supported.resolve(resolver,mock(AssignmentContext.class)))
                .isInstanceOfSatisfying(WorkflowExecutionException.class,e->assertThat(e.category()).isEqualTo(WorkflowExecutionException.Category.INVALID_ASSIGNMENT));
        when(provider.targetExists(any(),any())).thenThrow(new IllegalStateException("lookup failed"));
        assertThatThrownBy(()->supported.resolve(resolver,mock(AssignmentContext.class)))
                .isInstanceOfSatisfying(WorkflowExecutionException.class,e->assertThat(e.category()).isEqualTo(WorkflowExecutionException.Category.ASSIGNMENT_LOOKUP_FAILED));
    }
    @SuppressWarnings("unchecked")
    private WorkflowTaskEligibilityService eligibility(WorkflowSharedAssignmentProvider... providers) {
        ObjectProvider<WorkflowSharedAssignmentProvider> source=mock(ObjectProvider.class);
        when(source.stream()).thenAnswer(i->Stream.of(providers));
        return new WorkflowTaskEligibilityService(source, source(List.of()));
    }
    private WorkflowTaskView view(AssignmentTargetKind kind,String ref) {
        return new WorkflowTaskView(1,2,3,4,"review",new TaskAssignmentTarget(kind,ref),TaskStatus.OPEN,null,
                Instant.parse("2026-01-01T00:00:00Z"),null,null,null,null,kind == AssignmentTargetKind.USER ? ref : null);
    }
    @Test void userNeedsNoProviderAndUsesExactIdentity() {
        var service=eligibility();
        assertThat(service.isEligible(view(AssignmentTargetKind.USER,"Alice "),"Alice ")).isTrue();
        assertThat(service.isEligible(view(AssignmentTargetKind.USER,"Alice "),"Alice")).isFalse();
        assertThat(service.isEligible(view(AssignmentTargetKind.USER,"Alice"),"alice")).isFalse();
    }
    @Test void sharedMembershipIsFreshAndFailuresFailClosed() {
        var members=new AtomicReference<>(Set.of("alice","bob"));
        var provider=mock(WorkflowSharedAssignmentProvider.class);
        when(provider.isCurrentMember(any())).thenAnswer(i->members.get().contains(((TaskEligibilityContext)i.getArgument(0)).actorRef()));
        var service=eligibility(provider); var task=view(AssignmentTargetKind.GROUP,"finance");
        assertThat(service.isEligible(task,"alice")).isTrue();
        members.set(Set.of("bob"));
        assertThat(service.isEligible(task,"alice")).isFalse();
        members.set(Set.of("alice"));
        assertThat(service.isEligible(task,"alice")).isTrue();
        doThrow(new IllegalStateException("directory offline")).when(provider).isCurrentMember(any());
        assertThatThrownBy(()->service.isEligible(task,"alice")).isInstanceOfSatisfying(WorkflowExecutionException.class,
                e->assertThat(e.category()).isEqualTo(WorkflowExecutionException.Category.ASSIGNMENT_LOOKUP_FAILED));
        for(var missing:List.of(eligibility(),eligibility(provider,provider)))
            assertThatThrownBy(()->missing.isEligible(task,"alice")).isInstanceOfSatisfying(WorkflowExecutionException.class,
                    e->assertThat(e.category()).isEqualTo(WorkflowExecutionException.Category.HOST_CONFIGURATION));
    }
    @Test void deniedCandidatesConsumeBoundedScanAndRetainContinuation() {
        var tasks=mock(WorkflowTaskRepository.class); var eligibility=mock(WorkflowTaskEligibilityService.class);
        when(tasks.findEligibleCandidatesPage(eq("alice"),eq(10),anyLong(),any())).thenAnswer(i->{
            long after=i.getArgument(2); int count=((Pageable)i.getArgument(3)).getPageSize();
            var rows=new ArrayList<RuntimeTaskRow>();
            for(long id=after+1;id<=Math.min(501,after+count);id++) rows.add(row(id));
            return rows;
        });
        var service=new WorkflowRuntimeQueryService(null,null,tasks,null,null,new RuntimeJson(),null,eligibility);
        var first=service.listEligibleTasks("alice",0,50,t->false);
        assertThat(first.items()).isEmpty(); assertThat(first.nextAfterId()).isEqualTo(500L);
        verifyNoInteractions(eligibility);
        var last=service.listEligibleTasks("alice",500,50,t->false);
        assertThat(last.items()).isEmpty(); assertThat(last.nextAfterId()).isNull();
        verify(tasks,times(6)).findEligibleCandidatesPage(eq("alice"),eq(10),anyLong(),any());
    }
    @Test void eligibleRowsAreNeverSkippedAndAccessPrecedesMembership() {
        var tasks=mock(WorkflowTaskRepository.class); var eligibility=mock(WorkflowTaskEligibilityService.class);
        var rows=List.of(row(1),row(2),row(3));
        when(tasks.findEligibleCandidatesPage(eq("alice"),eq(10),eq(0L),any())).thenReturn(rows);
        when(eligibility.isEligible(any(),eq("alice"))).thenReturn(true);
        var service=new WorkflowRuntimeQueryService(null,null,tasks,null,null,new RuntimeJson(),null,eligibility);
        var page=service.listEligibleTasks("alice",0,1,t->t.id()!=1);
        assertThat(page.items()).extracting(WorkflowTaskView::id).containsExactly(2L);
        assertThat(page.nextAfterId()).isEqualTo(2L);
        verify(eligibility,times(1)).isEligible(argThat(t->t.id()==2),eq("alice"));
    }
    private RuntimeTaskRow row(long id) {
        var row=mock(RuntimeTaskRow.class);
        when(row.getId()).thenReturn(id); when(row.getActivityId()).thenReturn(2L);
        when(row.getInstanceId()).thenReturn(3L); when(row.getTaskTypeId()).thenReturn(4L);
        when(row.getTaskKey()).thenReturn("review"); when(row.getAssignmentKind()).thenReturn(AssignmentTargetKind.GROUP);
        when(row.getAssignmentRef()).thenReturn("finance"); when(row.getStatus()).thenReturn(TaskStatus.OPEN);
        when(row.getCreatedAt()).thenReturn(LocalDateTime.of(2026,1,1,0,0)); return row;
    }
}
