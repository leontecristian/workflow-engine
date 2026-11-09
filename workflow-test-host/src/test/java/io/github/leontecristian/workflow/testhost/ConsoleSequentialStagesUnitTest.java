package io.github.leontecristian.workflow.testhost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.leontecristian.workflow.core.dto.definition.DefinitionVersionView;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowActivityView;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowInstanceView;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskPage;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskView;
import io.github.leontecristian.workflow.core.service.definition.WorkflowDefinitionService;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowRuntimeQueryService;
import io.github.leontecristian.workflow.testhost.exception.console.ConsoleException;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleContext;
import io.github.leontecristian.workflow.testhost.service.console.ConsoleWire;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleRuntimeAccess;
import io.github.leontecristian.workflow.testhost.service.runtime.ConsoleRuntimeReadHost;
import io.github.leontecristian.workflow.web.controller.runtime.WorkflowRuntimeReadController;
import io.github.leontecristian.workflow.web.exception.runtime.WorkflowRuntimeException;
import io.github.leontecristian.workflow.web.service.runtime.RuntimeWire;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/** Exact historical node contract and existing assignment authorization, without a database. */
class ConsoleSequentialStagesUnitTest {
    @Test void historicalContractAndTaskPagesDoNotFollowCurrentPositionOrExposeOtherAssignments() {
        var reads=mock(WorkflowRuntimeQueryService.class); var definitions=mock(WorkflowDefinitionService.class);
        var context=mock(ConsoleContext.class); when(context.actor()).thenReturn("manager");
        var wire=new ConsoleWire(); var access=new ConsoleRuntimeAccess(reads, context, mock(io.github.leontecristian.workflow.core.service.runtime.WorkflowTaskEligibilityService.class), mock(io.github.leontecristian.workflow.testhost.service.runtime.ConsoleTaskOwnershipAuthorization.class));
        var controller=new WorkflowRuntimeReadController(reads,definitions,new ConsoleRuntimeReadHost(reads,access,context),new RuntimeWire(),null,false);
        var instance=mock(WorkflowInstanceView.class); when(instance.id()).thenReturn(1L);
        when(instance.definitionVersionId()).thenReturn(2L); when(instance.currentNodeKey()).thenReturn("PARTNER");
        when(reads.findInstance(1)).thenReturn(Optional.of(instance));
        var manager=mock(WorkflowTaskView.class); when(manager.id()).thenReturn(10L); when(manager.instanceId()).thenReturn(1L);
        when(manager.activityId()).thenReturn(20L); when(manager.assignment()).thenReturn(new io.github.leontecristian.workflow.core.dto.runtime.TaskAssignmentTarget(io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind.USER, "manager"));
        var partner=mock(WorkflowTaskView.class); when(partner.id()).thenReturn(11L); when(partner.instanceId()).thenReturn(1L);
        when(partner.activityId()).thenReturn(21L); when(partner.assignment()).thenReturn(new io.github.leontecristian.workflow.core.dto.runtime.TaskAssignmentTarget(io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind.USER, "partner"));
        when(reads.findTask(10)).thenReturn(Optional.of(manager)); when(reads.findTask(11)).thenReturn(Optional.of(partner));
        when(reads.listTasks(1,0,100)).thenReturn(new WorkflowTaskPage(List.of(manager,partner),null));
        when(reads.listTasks(1,0,50)).thenReturn(new WorkflowTaskPage(List.of(manager,partner),null));
        var activity=mock(WorkflowActivityView.class); when(activity.instanceId()).thenReturn(1L); when(activity.nodeKey()).thenReturn("MANAGER");
        when(reads.findActivity(20)).thenReturn(Optional.of(activity));
        var version=mock(DefinitionVersionView.class);
        when(version.documentJson()).thenReturn("""
            {"nodes":[{"key":"PARTNER","taskType":"REVIEW","outcomes":[{"key":"APPROVE","label":"Partner approval","requiresComment":true}]},
              {"key":"MANAGER","taskType":"REVIEW","outcomes":[{"key":"APPROVE","label":"Manager approval","requiresComment":false}]}]}
            """);
        when(definitions.findVersion(2)).thenReturn(Optional.of(version));
        var contract=wire.tree(controller.contract("10").getBody());
        assertThat(contract.path("outcomes").get(0).path("label").asText()).isEqualTo("Manager approval");
        assertThat(contract.path("outcomes").get(0).path("requiresComment").asBoolean()).isFalse();
        var page=wire.tree(controller.tasks("1",new MockHttpServletRequest()).getBody());
        assertThat(page.path("items")).hasSize(1); assertThat(page.path("items").get(0).path("id").asText()).isEqualTo("10");
        assertThat(page.path("nextAfterId").isNull()).isTrue();
        assertThatThrownBy(()->controller.contract("11")).isInstanceOfSatisfying(WorkflowRuntimeException.class,e->assertThat(e.status()).isEqualTo(404));
        when(context.admin()).thenReturn(true);
        assertThat(wire.tree(controller.tasks("1",new MockHttpServletRequest()).getBody()).path("items")).hasSize(2);
    }
}
