package io.github.leontecristian.workflow.core.dto.runtime;
import java.util.List;
public record ServiceActionDescriptor(String identifier, List<ActionField> inputs, List<ActionField> outputs) {
    public ServiceActionDescriptor {
        if (identifier==null || !identifier.matches("[A-Za-z][A-Za-z0-9._-]{0,99}")) throw new IllegalArgumentException("Invalid identifier");
        inputs=fields(inputs); outputs=fields(outputs);
    }
    private static List<ActionField> fields(List<ActionField> values) {
        if (values==null || values.size()>100) throw new IllegalArgumentException("Invalid fields");
        var keys=new java.util.HashSet<String>();
        for (var v:values) if (v==null || !keys.add(v.key())) throw new IllegalArgumentException("Duplicate fields");
        return List.copyOf(values);
    }
}
