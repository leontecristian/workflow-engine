package io.github.leontecristian.workflow.core.dto.definition.document;
import java.util.List;
import java.util.Map;
import io.github.leontecristian.workflow.core.dto.runtime.ActionField;
import io.github.leontecristian.workflow.core.dto.runtime.WorkflowLiteral;
import io.github.leontecristian.workflow.core.dto.runtime.TaskClaimPolicy;
import io.github.leontecristian.workflow.core.dto.runtime.VariableValueType;
/** Internal validated subset; canonical source is retained separately, including all designer values. */
public record MinimalWorkflowDefinition(Node start, Map<String,Human> humans, Node end, List<Transition> transitions,
        Map<String,Input> inputs, Map<String,ActionField> variables, Map<String,Binding> initialize,
        Map<String,Service> services, Map<String,Node> ends, java.util.Set<String> gateways,
        Map<String,String> namedOutputs) {
    public MinimalWorkflowDefinition {
        humans = Map.copyOf(humans);
        transitions = List.copyOf(transitions); inputs=Map.copyOf(inputs); variables=Map.copyOf(variables);
        initialize=Map.copyOf(initialize); services=Map.copyOf(services); ends=Map.copyOf(ends); gateways=java.util.Set.copyOf(gateways);
        namedOutputs = Map.copyOf(namedOutputs);
    }
    public MinimalWorkflowDefinition(Node start, Map<String,Human> humans, Node end, List<Transition> transitions,
            Map<String,Input> inputs, Map<String,ActionField> variables, Map<String,Binding> initialize,
            Map<String,Service> services, Map<String,Node> ends, java.util.Set<String> gateways) {
        this(start,humans,end,transitions,inputs,variables,initialize,services,ends,gateways,Map.of());
    }
    public MinimalWorkflowDefinition(Node start, Human human, Node end, List<Transition> transitions,
            Map<String,Input> inputs, Map<String,ActionField> variables, Map<String,Binding> initialize, Map<String,Service> services) {
        this(start,human == null ? Map.of() : Map.of(human.key(),human),end,transitions,inputs,variables,initialize,services,end==null ? Map.of() : Map.of(end.key(),end),java.util.Set.of());
    }
    public MinimalWorkflowDefinition(Node start, Human human, Node end, List<Transition> transitions) {
        this(start,human,end,transitions,Map.of(),Map.of(),Map.of(),Map.of());
    }
    public boolean extended() { return !inputs.isEmpty() || !variables.isEmpty() || !initialize.isEmpty() || !services.isEmpty() || !gateways.isEmpty(); }
    public record Input(ActionField field, WorkflowLiteral defaultValue) {}
    public record Binding(String source, String key, WorkflowLiteral value, String nodeKey) {
        public Binding(String source, String key, WorkflowLiteral value) {
            this(source, key, value, null);
        }
    }
    public record Service(String key, String action, Map<String,Binding> inputs, Map<String,Binding> outputs, int sourceIndex) {
        public Service { inputs=Map.copyOf(inputs); outputs=Map.copyOf(outputs); }
    }
    public record Node(String key, String type, String name, String result) {}
    public record Assignment(String resolver) {}
    public record Outcome(String key, String label, boolean requiresComment) {}
    public record HumanInput(Binding binding, VariableValueType type, boolean nullable) {}
    public record Human(String key, String taskType, Assignment assignment, List<Outcome> outcomes,
            TaskClaimPolicy claimPolicy, TaskResultContract resultContract, String outputVariable,
            Map<String,HumanInput> inputs) {
        public Human(String key, String taskType, Assignment assignment, List<Outcome> outcomes,
                TaskClaimPolicy claimPolicy, TaskResultContract resultContract, String outputVariable) {
            this(key, taskType, assignment, outcomes, claimPolicy, resultContract, outputVariable, Map.of());
        }
        public Human(String key, String taskType, Assignment assignment, List<Outcome> outcomes,
                TaskClaimPolicy claimPolicy, TaskResultContract resultContract) {
            this(key, taskType, assignment, outcomes, claimPolicy, resultContract, null);
        }
        public Human(String key, String taskType, Assignment assignment, List<Outcome> outcomes,
                TaskClaimPolicy claimPolicy) {
            this(key, taskType, assignment, outcomes, claimPolicy, TaskResultContract.FORBIDDEN);
        }
        public Human(String key, String taskType, Assignment assignment, List<Outcome> outcomes) {
            this(key, taskType, assignment, outcomes, TaskClaimPolicy.OPTIONAL);
        }
        public Human {
            outcomes = List.copyOf(outcomes);
            inputs = Map.copyOf(inputs);
        }
        public java.util.Optional<Outcome> outcome(String key) {
            return outcomes.stream().filter(outcome -> outcome.key().equals(key)).findFirst();
        }
    }
    public record Transition(String key, String from, String to, String outcome, String condition, boolean defaultRoute) {
        public Transition(String key,String from,String to,String outcome) { this(key,from,to,outcome,null,false); }
    }
}
