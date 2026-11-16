package io.github.leontecristian.workflow.testhost;

import io.github.leontecristian.workflow.core.service.runtime.WorkflowAuthorizedRuntimeReadService;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowCancellationAuthorization;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowRuntimeQueryService;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowRuntimeReadAuthorization;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowTaskEligibilityService;
import io.github.leontecristian.workflow.core.service.runtime.WorkflowTaskReassignmentAuthorization;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

final class RuntimeReadFixtures {
    static WorkflowAuthorizedRuntimeReadService authorized(WorkflowRuntimeQueryService reads,
            WorkflowRuntimeReadAuthorization policy, WorkflowTaskEligibilityService eligibility) {
        var beans = new DefaultListableBeanFactory();
        return new WorkflowAuthorizedRuntimeReadService(reads, policy, eligibility,
                beans.getBeanProvider(WorkflowTaskReassignmentAuthorization.class),
                beans.getBeanProvider(WorkflowCancellationAuthorization.class));
    }
}
