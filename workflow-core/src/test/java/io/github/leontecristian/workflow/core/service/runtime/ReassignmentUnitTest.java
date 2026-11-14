package io.github.leontecristian.workflow.core.service.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowCommandContext;
import io.github.leontecristian.workflow.core.dto.runtime.ActorKind;
import io.github.leontecristian.workflow.core.dto.runtime.AssignmentTargetKind;
import io.github.leontecristian.workflow.core.dto.runtime.TaskAssignmentTarget;
import io.github.leontecristian.workflow.core.dto.runtime.ReassignWorkflowTask;
import io.github.leontecristian.workflow.core.dto.runtime.TaskStatus;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowOperation;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowTaskAdministrationQuery;
import io.github.leontecristian.workflow.core.entity.runtime.WorkflowTaskEntity;
import io.github.leontecristian.workflow.core.entity.runtime.WorkflowHistoryEntity;
import io.github.leontecristian.workflow.core.entity.runtime.WorkflowCommandReceiptEntity;
import io.github.leontecristian.workflow.core.repository.runtime.WorkflowTaskRepository;
import io.github.leontecristian.workflow.core.repository.runtime.WorkflowHistoryRepository;
import io.github.leontecristian.workflow.core.repository.runtime.WorkflowInstanceRepository;
import io.github.leontecristian.workflow.core.repository.runtime.WorkflowActivityRepository;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** Database-free tests of the changed evidence, receipt and bounded-query contracts. */
class ReassignmentUnitTest {
    private final RuntimeJson json = new RuntimeJson();
    private final UUID id = UUID.fromString("12345678-1234-1234-1234-123456789abc");
    private final WorkflowCommandContext actor = new WorkflowCommandContext(ActorKind.USER,"admin",Instant.parse("2026-01-03T00:00:00Z"),"attempt");
    private TaskAssignmentTarget target(AssignmentTargetKind kind,String ref) { return new TaskAssignmentTarget(kind,ref); }
    private ReassignWorkflowTask command(AssignmentTargetKind kind,String ref,String reason) { return new ReassignWorkflowTask(id,1,3,3,target(kind,ref),reason); }
    private void category(Category expected,Runnable action) { assertEquals(expected,assertThrows(WorkflowExecutionException.class,action::run).category()); }

    @Test void targetChangesReplaceOwnerWithoutChangingTaskIdentityOrLifecycle() {
        var time = LocalDateTime.parse("2026-01-01T00:00:00");
        var task = new WorkflowTaskEntity(2,"review",target(AssignmentTargetKind.USER,"first"),time);
        for (var kind : AssignmentTargetKind.values()) {
            task.reassign(target(kind,"second"));
            assertEquals(kind,task.getAssignmentKind());
            assertEquals(kind == AssignmentTargetKind.USER ? "second" : null,task.getAssigneeRef());
            assertEquals("review",task.getTaskKey()); assertEquals(2L,task.getActivityId().longValue());
            assertEquals(time,task.getCreatedAt()); assertEquals(TaskStatus.OPEN,task.getStatus());
            if (kind != AssignmentTargetKind.USER) {
                task.claim("claimant"); task.reassign(target(kind,"third")); assertNull(task.getAssigneeRef());
            }
        }
        task.cancelForInstance(time,"admin");
        assertThrows(IllegalStateException.class,() -> task.reassign(target(AssignmentTargetKind.USER,"last")));
    }

    @Test void immutableCreationTargetMayDifferButOriginalOwnerAndSchemaRemainStrict() {
        var task = mock(WorkflowTaskEntity.class);
        var fact = mock(WorkflowHistoryEntity.class);
        when(task.getTaskKey()).thenReturn("review");
        when(task.getAssignment()).thenReturn(target(AssignmentTargetKind.USER,"new"));
        var createdAt = LocalDateTime.parse("2026-01-01T00:00:00");
        when(task.getCreatedAt()).thenReturn(createdAt);
        when(fact.getOccurredAt()).thenReturn(createdAt); when(fact.getDetailsSchemaVersion()).thenReturn((short)4);
        var details = json.object().put("taskKey","review").putNull("assigneeRef");
        details.set("assignment",json.object().put("kind","GROUP").put("ref","original"));
        when(fact.getDetailsJson()).thenAnswer(call -> json.encode(details));
        assertTrue(RuntimeTaskCreationEvidence.matches(fact,task,json));
        details.put("assigneeRef","unexpected"); assertFalse(RuntimeTaskCreationEvidence.matches(fact,task,json));
        details.putNull("assigneeRef"); details.put("taskKey","other"); assertFalse(RuntimeTaskCreationEvidence.matches(fact,task,json));
        verify(task,never()).getAssignment();
    }

    private ObjectNode snapshot(AssignmentTargetKind kind) throws Exception {
        var root = (ObjectNode) json.tree(new String(getClass().getResourceAsStream("/f007/extended-start.json").readAllBytes(),StandardCharsets.UTF_8));
        root.put("operation","REASSIGN_TASK").put("firstHistorySequence","10").put("lastHistorySequence","10");
        ((ObjectNode)root.path("instance")).put("lastHistorySequence","10").put("lockVersion","4");
        var task = (ObjectNode)root.path("task"); task.remove("assignedUserRef");
        task.set("assignment",json.object().put("kind",kind.name()).put("ref","new"));
        if (kind == AssignmentTargetKind.USER) task.put("assigneeRef","new"); else task.putNull("assigneeRef");
        return root;
    }
    private WorkflowCommandReceiptEntity receipt(ObjectNode root,short version) {
        return new WorkflowCommandReceiptEntity(id,1,"REASSIGN_TASK",ActorKind.USER,"admin",new byte[32],RuntimeJson.FINGERPRINT_VERSION,
                LocalDateTime.parse("2026-01-03T00:00:00"),json.encode(root),version);
    }
    @Test void schemaEightReplaysFrozenUserAndSharedSingleAndAllSnapshots() throws Exception {
        for (var kind : AssignmentTargetKind.values()) for (int required : new int[]{1,100}) {
            var root = snapshot(kind);
            if (required > 1) ((ObjectNode)root.path("activity")).put("completionPolicy","ALL").put("requiredTaskCount",required).put("contributionOutcomeKey","APPROVE");
            var receipt = receipt(root,(short)8); String bytes = receipt.getResultJson();
            var result = json.replay(receipt,id,WorkflowOperation.REASSIGN_TASK,actor,new byte[32]).orElseThrow();
            assertEquals(kind,result.task().assignment().kind()); assertEquals(required,result.activity().requiredTaskCount());
            assertEquals(bytes,receipt.getResultJson());
            category(Category.UNSUPPORTED_VERSION,() -> json.replay(receipt(root,(short)6),id,WorkflowOperation.REASSIGN_TASK,actor,new byte[32]));
        }
    }
    @Test void reassignmentReceiptRejectsOwnersClosureAndMultipleFacts() throws Exception {
        for (String mutation : List.of("owner","closed","facts","kind")) {
            var root = snapshot(AssignmentTargetKind.GROUP);
            if (mutation.equals("owner")) ((ObjectNode)root.path("task")).put("assigneeRef","claimant");
            if (mutation.equals("closed")) ((ObjectNode)root.path("task")).put("status","CANCELLED");
            if (mutation.equals("facts")) root.put("firstHistorySequence","9");
            if (mutation.equals("kind")) root.put("operation","CLAIM_TASK");
            category(Category.HOST_CONFIGURATION,() -> json.replay(receipt(root,(short)8),id,WorkflowOperation.REASSIGN_TASK,actor,new byte[32]));
        }
    }
    @Test void schemaEightEntityBoundaryIsReservedForReassignment() {
        for (String operation : List.of("START_WORKFLOW","COMPLETE_TASK","CLAIM_TASK","CANCEL_WORKFLOW")) {
            assertThrows(IllegalArgumentException.class,() -> new WorkflowCommandReceiptEntity(id,1,operation,
                    ActorKind.USER,"admin",new byte[32],RuntimeJson.FINGERPRINT_VERSION,
                    LocalDateTime.parse("2026-01-03T00:00:00"),"{}",(short)8));
        }
    }
    @Test void fingerprintIncludesExactTargetReasonTokenAndActorButNotAttemptMetadata() {
        var original = command(AssignmentTargetKind.USER,"new"," exact ");
        byte[] hash = json.reassignmentFingerprint(original,actor);
        assertArrayEquals(hash,json.reassignmentFingerprint(original,new WorkflowCommandContext(ActorKind.USER,"admin",Instant.EPOCH,"other")));
        for (var changed : List.of(command(AssignmentTargetKind.GROUP,"new"," exact "), command(AssignmentTargetKind.USER,"new "," exact "),
                command(AssignmentTargetKind.USER,"new","exact"),new ReassignWorkflowTask(id,1,3,4,original.assignment(),original.reason()))) {
            assertFalse(Arrays.equals(hash,json.reassignmentFingerprint(changed,actor)));
        }
        assertFalse(Arrays.equals(hash,json.reassignmentFingerprint(original,new WorkflowCommandContext(ActorKind.USER,"other",actor.occurredAt(),null))));
    }
    @Test void administrationValidatesFiltersAndUsesVarcharCodeLengthAndBoundedContinuation() {
        var tasks = mock(WorkflowTaskRepository.class);
        var service = new WorkflowRuntimeQueryService(mock(WorkflowInstanceRepository.class),mock(WorkflowActivityRepository.class),tasks,
                mock(WorkflowHistoryRepository.class),mock(RuntimeDefinitionPins.class),json,mock(RuntimeVariables.class),mock(WorkflowTaskEligibilityService.class));
        when(tasks.findAdministrationPage(any(),any(),anyInt(),any(),any(),anyInt(),any(),anyInt(),any(),anyLong(),any())).thenReturn(List.of());
        var page = service.listAdministrationTasks(new WorkflowTaskAdministrationQuery(TaskStatus.OPEN,"REVIEW",AssignmentTargetKind.GROUP,"Finance","ana",1L,0,20),item -> true);
        assertNull(page.nextAfterId()); assertTrue(page.items().isEmpty());
        verify(tasks).findAdministrationPage(eq(TaskStatus.OPEN),eq("REVIEW"),eq(6),eq(AssignmentTargetKind.GROUP),eq("Finance"),eq(14),eq("ana"),eq(6),eq(1L),eq(0L),argThat(p -> p.getPageSize()==21));
        category(Category.INVALID_INPUT,() -> service.listAdministrationTasks(new WorkflowTaskAdministrationQuery(null,null,null,"Finance",null,null,0,20),item -> true));
    }
    @Test void administrationDeniedCandidateRetainsContinuationAndCachesPinnedMetadata() {
        var tasks = mock(WorkflowTaskRepository.class);
        var pins = mock(RuntimeDefinitionPins.class);
        when(pins.nodeNames(42)).thenReturn(java.util.Map.of("REVIEW","Review"));
        var rows = new java.util.ArrayList<io.github.leontecristian.workflow.core.repository.runtime.RuntimeTaskAdministrationRow>();
        for (long id : new long[]{3,4,5}) {
            var row = mock(io.github.leontecristian.workflow.core.repository.runtime.RuntimeTaskAdministrationRow.class);
            when(row.getId()).thenReturn(id); when(row.getActivityId()).thenReturn(2L); when(row.getInstanceId()).thenReturn(1L);
            when(row.getTaskTypeId()).thenReturn(5L); when(row.getDefinitionVersionId()).thenReturn(42L);
            when(row.getNodeKey()).thenReturn("REVIEW"); when(row.getAssignmentKind()).thenReturn(AssignmentTargetKind.USER);
            when(row.getAssignmentRef()).thenReturn("reviewer"); rows.add(row);
        }
        when(tasks.findAdministrationPage(any(),any(),anyInt(),any(),any(),anyInt(),any(),anyInt(),any(),anyLong(),any())).thenReturn(rows);
        var service = new WorkflowRuntimeQueryService(mock(WorkflowInstanceRepository.class),mock(WorkflowActivityRepository.class),tasks,
                mock(WorkflowHistoryRepository.class),pins,json,mock(RuntimeVariables.class),mock(WorkflowTaskEligibilityService.class));
        var page = service.listAdministrationTasks(new WorkflowTaskAdministrationQuery(TaskStatus.OPEN,null,null,null,null,null,0,2),item -> false);
        assertTrue(page.items().isEmpty()); assertEquals(4L,page.nextAfterId().longValue());
        verify(pins).nodeNames(42);
    }

    @Test void taskNameFilterMatchesPinnedMetadataLiterallyAndRetainsCandidateContinuation() {
        var tasks = mock(WorkflowTaskRepository.class);
        var pins = mock(RuntimeDefinitionPins.class);
        when(pins.nodeNames(42)).thenReturn(java.util.Map.of("REVIEW","Review 100%_ Åmount"));
        var rows = new java.util.ArrayList<io.github.leontecristian.workflow.core.repository.runtime.RuntimeTaskAdministrationRow>();
        for (long id : new long[]{3,4,5}) {
            var row = mock(io.github.leontecristian.workflow.core.repository.runtime.RuntimeTaskAdministrationRow.class);
            when(row.getId()).thenReturn(id); when(row.getActivityId()).thenReturn(2L); when(row.getInstanceId()).thenReturn(1L);
            when(row.getTaskTypeId()).thenReturn(5L); when(row.getDefinitionVersionId()).thenReturn(42L);
            when(row.getNodeKey()).thenReturn("REVIEW"); when(row.getAssignmentKind()).thenReturn(AssignmentTargetKind.USER);
            when(row.getAssignmentRef()).thenReturn("reviewer"); rows.add(row);
        }
        when(tasks.findAdministrationPage(any(),any(),anyInt(),any(),any(),anyInt(),any(),anyInt(),any(),anyLong(),any())).thenReturn(rows);
        var service = new WorkflowRuntimeQueryService(mock(WorkflowInstanceRepository.class),mock(WorkflowActivityRepository.class),tasks,
                mock(WorkflowHistoryRepository.class),pins,json,mock(RuntimeVariables.class),mock(WorkflowTaskEligibilityService.class));
        var matching = new WorkflowTaskAdministrationQuery(TaskStatus.OPEN,null,null,null,null,null,0,2,"100%_ åMOUNT");
        var page = service.listAdministrationTasks(matching,item -> true);
        assertEquals(2,page.items().size()); assertEquals(4L,page.nextAfterId().longValue());
        assertTrue(service.listAdministrationTasks(matching,item -> false).items().isEmpty());
        var empty = service.listAdministrationTasks(new WorkflowTaskAdministrationQuery(TaskStatus.OPEN,null,null,null,null,null,0,2,"100XX"),item -> true);
        assertTrue(empty.items().isEmpty()); assertEquals(4L,empty.nextAfterId().longValue());
        assertTrue(service.listAdministrationTasks(new WorkflowTaskAdministrationQuery(TaskStatus.OPEN,null,null,null,null,null,0,2," Review"),item -> true).items().isEmpty());
        for (String invalid : List.of(" ","x".repeat(201),"\uD800")) {
            category(Category.INVALID_INPUT,() -> service.listAdministrationTasks(new WorkflowTaskAdministrationQuery(null,null,null,null,null,null,0,20,invalid),item -> true));
        }
    }

    @Test void reassignmentRechecksAssignmentDependentAccessAndPermissionAfterAggregateClaim() {
        for (boolean loseAccess : new boolean[]{true,false}) {
            var tasks = mock(WorkflowTaskRepository.class);
            var activities = mock(WorkflowActivityRepository.class);
            var instances = mock(WorkflowInstanceRepository.class);
            var receipts = mock(io.github.leontecristian.workflow.core.repository.runtime.WorkflowCommandReceiptRepository.class);
            var em = mock(jakarta.persistence.EntityManager.class);
            var adapter = mock(WorkflowTaskReassignmentAuthorization.class);
            var pins = mock(RuntimeDefinitionPins.class);
            var task = mock(WorkflowTaskEntity.class);
            var activity = mock(io.github.leontecristian.workflow.core.entity.runtime.WorkflowActivityEntity.class);
            var instance = mock(io.github.leontecristian.workflow.core.entity.runtime.WorkflowInstanceEntity.class);
            var beans = new org.springframework.beans.factory.support.StaticListableBeanFactory();
            beans.addBean("reassignment",adapter);
            var service = new WorkflowExecutionService(instances,activities,tasks,mock(WorkflowHistoryRepository.class),receipts,em,
                    mock(io.github.leontecristian.workflow.core.service.definition.WorkflowDefinitionRuntimeReader.class),
                    mock(io.github.leontecristian.workflow.core.service.tasktype.TaskTypeCatalogService.class),json,
                    mock(RuntimeHostSupport.class),pins,mock(RuntimeVariables.class),mock(RuntimeServiceProgression.class),
                    mock(WorkflowTaskEligibilityService.class),beans.getBeanProvider(WorkflowTaskSubmissionValidator.class));
            service.setReassignmentAuthorization(beans.getBeanProvider(WorkflowTaskReassignmentAuthorization.class));
            var assignment = new java.util.concurrent.atomic.AtomicReference<>(target(AssignmentTargetKind.USER,"original"));
            when(task.getId()).thenReturn(3L); when(task.getActivityId()).thenReturn(2L);
            when(task.getAssignment()).thenAnswer(call -> assignment.get());
            when(activity.getId()).thenReturn(2L); when(activity.getInstanceId()).thenReturn(1L);
            when(tasks.findById(3L)).thenReturn(java.util.Optional.of(task));
            when(activities.findById(2L)).thenReturn(java.util.Optional.of(activity));
            when(instances.findById(1L)).thenReturn(java.util.Optional.of(instance));
            when(instance.getLockVersion()).thenReturn(3L);
            when(instance.getStatus()).thenReturn(io.github.leontecristian.workflow.core.dto.runtime.InstanceStatus.ACTIVE);
            when(instance.getLastHistorySequence()).thenReturn(4L); when(instance.reserveHistorySequence()).thenReturn(5L);
            when(receipts.findById(id)).thenReturn(java.util.Optional.empty());
            when(adapter.canAccess(any())).thenAnswer(call -> !loseAccess || ((io.github.leontecristian.workflow.core.dto.runtime.TaskEligibilityContext)call.getArgument(0)).task().assignment().ref().equals("original"));
            when(adapter.canReassign(any())).thenAnswer(call -> ((io.github.leontecristian.workflow.core.dto.runtime.TaskEligibilityContext)call.getArgument(0)).task().assignment().ref().equals("original"));
            org.mockito.Mockito.doAnswer(call -> { assignment.set(target(AssignmentTargetKind.USER,"current")); return null; }).when(em).refresh(task);
            category(loseAccess ? Category.NOT_FOUND : Category.REASSIGNMENT_FORBIDDEN,
                    () -> service.reassignTask(command(AssignmentTargetKind.USER,"new","reason"),actor));
            verify(task,never()).reassign(any());
            verify(adapter,never()).isValidTarget(any(),any());
            org.mockito.Mockito.verifyNoInteractions(pins);
        }
    }

}
