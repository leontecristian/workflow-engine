package io.github.leontecristian.workflow.core.service.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition.Binding;
import io.github.leontecristian.workflow.core.dto.definition.document.MinimalWorkflowDefinition.HumanInput;
import io.github.leontecristian.workflow.core.dto.definition.document.TaskResultContract.Mode;
import io.github.leontecristian.workflow.core.dto.runtime.*;
import io.github.leontecristian.workflow.core.entity.runtime.*;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException;
import io.github.leontecristian.workflow.core.exception.runtime.WorkflowExecutionException.Category;
import io.github.leontecristian.workflow.core.repository.runtime.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Detached evidence and receipt checks; transaction/SQL visibility belongs to integration verification. */
class AllRuntimeEvidenceUnitTest {
    private final RuntimeJson json = new RuntimeJson();
    private final LocalDateTime time = LocalDateTime.of(2026, 1, 1, 0, 0);
    private final UUID command = UUID.fromString("12345678-1234-1234-1234-123456789abc");

    private WorkflowHistoryEntity fact(long sequence, String type, Long task, short version, String details) {
        return fact(sequence,type,task,version,details,command);
    }
    private WorkflowHistoryEntity fact(long sequence, String type, Long task, short version, String details, UUID id) {
        return new WorkflowHistoryEntity(new WorkflowHistorySummary(1,sequence,type,id,null,ActorKind.USER,"reviewer",
                time.toInstant(ZoneOffset.UTC),2L,task,null,null,null,version),details);
    }
    private WorkflowActivityEntity activity(ActivityStatus status, int count) {
        var activity = mock(WorkflowActivityEntity.class);
        when(activity.getId()).thenReturn(2L); when(activity.getInstanceId()).thenReturn(1L);
        when(activity.getNodeKey()).thenReturn("review"); when(activity.getActivationSequence()).thenReturn(2L);
        when(activity.getCompletionPolicy()).thenReturn(CompletionPolicy.ALL); when(activity.getRequiredTaskCount()).thenReturn(count);
        when(activity.getContributionOutcomeKey()).thenReturn("APPROVE");
        when(activity.getStatus()).thenReturn(status); when(activity.getEnteredAt()).thenReturn(time);
        when(activity.getClosureReason()).thenReturn("TASK_OUTCOME"); when(activity.getClosedAt()).thenReturn(time);
        when(activity.getClosedBy()).thenReturn("reviewer"); when(activity.getOutcomeKey()).thenReturn("REJECT");
        return activity;
    }
    private WorkflowTaskEntity task(long id, String key, TaskStatus status, String outcome) {
        var task = mock(WorkflowTaskEntity.class);
        when(task.getId()).thenReturn(id); when(task.getActivityId()).thenReturn(2L); when(task.getTaskKey()).thenReturn(key);
        when(task.getCreatedAt()).thenReturn(time); when(task.getStatus()).thenReturn(status);
        when(task.getAssignment()).thenReturn(new TaskAssignmentTarget(AssignmentTargetKind.GROUP,"finance"));
        when(task.getAssignmentKind()).thenReturn(AssignmentTargetKind.GROUP); when(task.getAssignmentRef()).thenReturn("finance");
        when(task.getOutcomeKey()).thenReturn(outcome); when(task.getClosedAt()).thenReturn(time); when(task.getClosedBy()).thenReturn("reviewer");
        when(task.getClosureReason()).thenReturn(status==TaskStatus.CANCELLED ? TaskClosureReason.ACTIVITY_RESOLVED : TaskClosureReason.TASK_OUTCOME);
        return task;
    }
    private WorkflowHistoryEntity creation(long sequence, long taskId, String key, String snapshot) {
        return fact(sequence,"TASK_CREATED",taskId,(short)6,"{\"taskKey\":\""+key+"\",\"assignment\":{\"kind\":\"GROUP\",\"ref\":\"finance\"},\"assigneeRef\":null,\"inputSnapshotSequence\":\""+snapshot+"\"}");
    }

    @Test void everySiblingReadsTheOneSharedSnapshotAndIndependentExactArray() {
        var activity = activity(ActivityStatus.ACTIVE,2);
        var repository = mock(WorkflowHistoryRepository.class);
        var inputs = Map.of("original",new HumanInput(new Binding("submission","earlier",null),VariableValueType.JSON,false,true));
        var first = creation(4,11,"finance","3"); var second = creation(5,12,"legal","3");
        var snapshot = fact(3,"ACTIVITY_INPUTS_CAPTURED",null,(short)1,
                "{\"inputs\":{\"original\":{\"type\":\"JSON\",\"value\":[{\"amount\":1000.00}]}}}");
        when(repository.findById(new WorkflowHistoryId(1L,2L))).thenReturn(Optional.of(fact(2,"ACTIVITY_ENTERED",null,(short)2,"{}")));
        when(repository.findActivityFacts(eq(2L),eq("ACTIVITY_INPUTS_CAPTURED"),any())).thenReturn(List.of(snapshot));
        when(repository.findActivityFacts(eq(2L),eq("TASK_CREATED"),any())).thenReturn(List.of(first,second));
        var expected = Map.of("original",new WorkflowValue(VariableValueType.JSON,WorkflowJsonValue.parse("[{\"amount\":1000.00}]")));
        assertEquals(expected,RuntimeTaskCreationEvidence.read(first,task(11,"finance",TaskStatus.OPEN,null),activity,inputs,json,repository));
        assertEquals(expected,RuntimeTaskCreationEvidence.read(second,task(12,"legal",TaskStatus.OPEN,null),activity,inputs,json,repository));
    }

    @Test void sharedSnapshotsRejectMissingDuplicateCrossCommandAndInconsistentSiblingReferences() {
        var activity = activity(ActivityStatus.ACTIVE,2);
        var inputs = Map.of("value",new HumanInput(new Binding("variable","value",null),VariableValueType.JSON,false));
        var first = creation(4,11,"finance","3"); var second = creation(5,12,"legal","3");
        var snapshot = fact(3,"ACTIVITY_INPUTS_CAPTURED",null,(short)1,"{\"inputs\":{\"value\":{\"type\":\"JSON\",\"value\":{}}}}");
        for (String corruption : List.of("missing","duplicate","crossCommand","siblingReference","missingSibling","duplicateTask","entryCommand")) {
            var repository = mock(WorkflowHistoryRepository.class);
            var snapshots = switch(corruption) {
                case "missing" -> List.<WorkflowHistoryEntity>of();
                case "duplicate" -> List.of(snapshot,snapshot);
                case "crossCommand" -> List.of(fact(3,"ACTIVITY_INPUTS_CAPTURED",null,(short)1,snapshot.getDetailsJson(),UUID.randomUUID()));
                default -> List.of(snapshot);
            };
            var siblings = switch(corruption) {
                case "siblingReference" -> List.of(first,creation(5,12,"legal","999"));
                case "missingSibling" -> List.of(first);
                case "duplicateTask" -> List.of(first,first);
                default -> List.of(first,second);
            };
            when(repository.findActivityFacts(eq(2L),eq("ACTIVITY_INPUTS_CAPTURED"),any())).thenReturn(snapshots);
            when(repository.findActivityFacts(eq(2L),eq("TASK_CREATED"),any())).thenReturn(siblings);
            when(repository.findById(new WorkflowHistoryId(1L,2L))).thenReturn(Optional.of(
                    fact(2,"ACTIVITY_ENTERED",null,(short)2,"{}",corruption.equals("entryCommand")?UUID.randomUUID():command)));
            assertEquals(Category.HOST_CONFIGURATION,assertThrows(WorkflowExecutionException.class,
                    () -> RuntimeTaskCreationEvidence.read(first,task(11,"finance",TaskStatus.OPEN,null),activity,inputs,json,repository),corruption).category());
        }
    }

    private WorkflowRuntimeQueryService query(WorkflowActivityEntity activity, List<WorkflowTaskEntity> roster, List<WorkflowHistoryEntity> facts) {
        var activities = mock(WorkflowActivityRepository.class); var tasks = mock(WorkflowTaskRepository.class);
        var history = mock(WorkflowHistoryRepository.class);
        when(activities.findByInstanceIdAndNodeKey(eq(1L),eq("review"),any())).thenReturn(List.of(activity));
        when(tasks.findPageByActivityId(eq(2L),eq(0L),any())).thenReturn(roster);
        when(history.findActivityFacts(eq(2L),eq("TASK_COMPLETED"),any())).thenReturn(facts);
        return new WorkflowRuntimeQueryService(mock(WorkflowInstanceRepository.class),activities,tasks,history,
                mock(RuntimeDefinitionPins.class),json,mock(RuntimeVariables.class),mock(WorkflowTaskEligibilityService.class));
    }
    private WorkflowHistoryEntity completion(long sequence, long taskId, String outcome, String result) {
        return fact(sequence,"TASK_COMPLETED",taskId,(short)(result==null?2:3),
                "{\"outcomeKey\":\""+outcome+"\",\"comment\":null,\"inputs\":{}"+(result==null?"":",\"result\":"+result)+"}");
    }

    @Test void originalCollectionSortsTaskKeysKeepsExactNumbersOmitsAbsentResultAndExcludesCancelled() {
        var activity = activity(ActivityStatus.COMPLETED,3);
        var finance = task(9007199254740993L,"finance",TaskStatus.COMPLETED,"APPROVE");
        var legal = task(12,"legal",TaskStatus.COMPLETED,"REJECT");
        var cancelled = task(13,"risk",TaskStatus.CANCELLED,null);
        var query = query(activity,List.of(cancelled,legal,finance),List.of(completion(7,12,"REJECT",null),
                completion(6,9007199254740993L,"APPROVE","{\"amount\":1000.00,\"exact\":9007199254740993}")));
        assertEquals("[{\"comment\":null,\"outcomeKey\":\"APPROVE\",\"result\":{\"amount\":1000.00,\"exact\":9007199254740993},\"taskId\":\"9007199254740993\",\"taskKey\":\"finance\"},{\"comment\":null,\"outcomeKey\":\"REJECT\",\"taskId\":\"12\",\"taskKey\":\"legal\"}]",
                query.resolveSubmission(1,"review",Mode.OPTIONAL).toJson());
    }

    @Test void allOmittedOptionalResultsStillProduceAPresentCollection() {
        var query = query(activity(ActivityStatus.COMPLETED,1),List.of(task(11,"finance",TaskStatus.COMPLETED,"REJECT")),
                List.of(completion(6,11,"REJECT",null)));
        assertEquals("[{\"comment\":null,\"outcomeKey\":\"REJECT\",\"taskId\":\"11\",\"taskKey\":\"finance\"}]",query.resolveSubmission(1,"review",Mode.OPTIONAL).toJson());
    }

    @Test void originalCollectionRejectsMissingDuplicateForeignAndInconsistentEvidence() {
        var accepted = completion(6,11,"REJECT","{}");
        for (String corruption : List.of("missing","duplicate","foreignTask","wrongOutcome","arrayResult","requiredOmission","openTask")) {
            var task = task(11,"finance",corruption.equals("openTask")?TaskStatus.OPEN:TaskStatus.COMPLETED,"REJECT");
            var facts = switch(corruption) {
                case "missing" -> List.<WorkflowHistoryEntity>of();
                case "duplicate" -> List.of(accepted,accepted);
                case "foreignTask" -> List.of(completion(6,99,"REJECT","{}"));
                case "wrongOutcome" -> List.of(completion(6,11,"APPROVE","{}"));
                case "arrayResult" -> List.of(completion(6,11,"REJECT","[]"));
                case "requiredOmission" -> List.of(completion(6,11,"REJECT",null));
                default -> List.of(accepted);
            };
            var query = query(activity(ActivityStatus.COMPLETED,1),List.of(task),facts);
            assertEquals(Category.HOST_CONFIGURATION,assertThrows(WorkflowExecutionException.class,
                    () -> query.resolveSubmission(1,"review",corruption.equals("requiredOmission")?Mode.REQUIRED:Mode.OPTIONAL),corruption).category());
        }
        var active = query(activity(ActivityStatus.ACTIVE,1),List.of(),List.of());
        assertEquals(Category.SUBMISSION_UNAVAILABLE,assertThrows(WorkflowExecutionException.class,
                () -> active.resolveSubmission(1,"review",Mode.OPTIONAL)).category());
    }

    private ObjectNode receiptRoot(boolean start) throws Exception {
        String name = start?"extended-start.json":"extended-complete.json";
        var root = (ObjectNode)json.tree(new String(getClass().getResourceAsStream("/f007/"+name).readAllBytes(),StandardCharsets.UTF_8));
        var activity = (ObjectNode)root.path("activity"); activity.put("completionPolicy","ALL").put("requiredTaskCount",2).put("contributionOutcomeKey","COMPLETE");
        var task = (ObjectNode)root.path("task"); task.remove("assignedUserRef");
        task.set("assignment",json.object().put("kind","USER").put("ref","reviewer")); task.put("assigneeRef","reviewer");
        return root;
    }
    private WorkflowExecutionResult replay(ObjectNode root, boolean start, short version) {
        var operation = start?WorkflowOperation.START_WORKFLOW:WorkflowOperation.COMPLETE_TASK;
        String actor = start?"f007.admin":"reviewer";
        var context = new WorkflowCommandContext(ActorKind.USER,actor,time.toInstant(ZoneOffset.UTC),"test");
        var receipt = new WorkflowCommandReceiptEntity(command,1,operation.name(),ActorKind.USER,actor,new byte[32],
                RuntimeJson.FINGERPRINT_VERSION,start?time:time.plusDays(1),json.encode(root),version);
        return json.replay(receipt,command,operation,context,new byte[32]).orElseThrow();
    }

    @Test void v6ReceiptsRetainRepresentativeStartFinalAndFrozenNonfinalSnapshots() throws Exception {
        var start = replay(receiptRoot(true),true,(short)6);
        assertEquals(2,start.activity().requiredTaskCount()); assertEquals(TaskStatus.OPEN,start.task().status());
        var complete = replay(receiptRoot(false),false,(short)6);
        assertEquals(ActivityStatus.COMPLETED,complete.activity().status());
        var root = receiptRoot(false); var instance = (ObjectNode)root.path("instance"); var activity = (ObjectNode)root.path("activity");
        instance.put("status","ACTIVE").put("currentNodeKey","REVIEW").putNull("resultKey").putNull("endedAt").putNull("endedBy");
        activity.put("status","ACTIVE").putNull("closedAt").putNull("closedBy").putNull("closureReason").putNull("outcomeKey");
        var waiting = replay(root,false,(short)6);
        assertEquals(ActivityStatus.ACTIVE,waiting.activity().status()); assertEquals(TaskStatus.COMPLETED,waiting.task().status());
        assertEquals("REVIEW",waiting.instance().currentNodeKey());
        for (String corruption : List.of("count","outcome","position")) {
            var invalid = root.deepCopy();
            if (corruption.equals("count")) ((ObjectNode)invalid.path("activity")).put("requiredTaskCount",1);
            if (corruption.equals("outcome")) ((ObjectNode)invalid.path("task")).put("outcomeKey","REJECT");
            if (corruption.equals("position")) ((ObjectNode)invalid.path("instance")).put("currentNodeKey","OTHER");
            assertEquals(Category.HOST_CONFIGURATION,assertThrows(WorkflowExecutionException.class,
                    () -> replay(invalid,false,(short)6),corruption).category());
        }
        assertEquals(Category.HOST_CONFIGURATION,assertThrows(WorkflowExecutionException.class,
                () -> replay(root,false,(short)5)).category());
    }
}
