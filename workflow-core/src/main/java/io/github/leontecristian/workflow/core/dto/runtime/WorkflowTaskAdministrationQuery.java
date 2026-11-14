package io.github.leontecristian.workflow.core.dto.runtime;
/** Exact filters except case-insensitive task-name substring. Null status means ALL; transport defaults to OPEN. */
public record WorkflowTaskAdministrationQuery(TaskStatus status,String taskTypeCode,AssignmentTargetKind assignmentKind,String assignmentRef,String assigneeRef,Long instanceId,long afterId,int limit,String taskName) {
    public WorkflowTaskAdministrationQuery(TaskStatus status,String taskTypeCode,AssignmentTargetKind assignmentKind,String assignmentRef,String assigneeRef,Long instanceId,long afterId,int limit) {
        this(status,taskTypeCode,assignmentKind,assignmentRef,assigneeRef,instanceId,afterId,limit,null);
    }
}
