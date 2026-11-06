package io.github.leontecristian.workflow.core.service.definition;

import io.github.leontecristian.workflow.core.dto.runtime.WorkflowJsonValue;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class JsonDefinitionUnitTest {
    private final DefinitionDocumentCodec codec=new DefinitionDocumentCodec();
    private final MinimalDefinitionLoader loader=new MinimalDefinitionLoader();
    private String example() throws Exception {
        return Files.readString(Path.of(System.getProperty("basedir"),"../docs/features/F009-json-execution-variables/booking-review.json"));
    }
    @Test void completeConsoleExampleValidatesWithBoundedSchemaFreeDefaultsAndScale() throws Exception {
        var source=example().replace("\"required\":true", "\"required\":false,\"default\":{\"amount\":1.00,\"name\":\"\",\"policy\":\"anything\",\"schemaVersion\":99,\"expressionPolicy\":true}");
        var document=codec.ingest(source); var diagnostics=new DefinitionDiagnostics();
        var model=loader.model(document,diagnostics);
        assertThat(diagnostics.failed()).isFalse();
        assertThat(((WorkflowJsonValue)model.inputs().get("booking").defaultValue()).toJson()).contains("1.00","\"name\":\"\"");
        assertThat(codec.ingest(document.canonical()).canonical()).isEqualTo(document.canonical());
        assertThat(codec.ingest(source.replace("1.00","1.0")).hash()).isNotEqualTo(document.hash());
    }
    @Test void unknownFieldSchemasAreRejectedWhileNestedSnapshotFieldsAreOpen() throws Exception {
        String source=example().replace("\"type\":\"JSON\",\"required\"", "\"type\":\"JSON\",\"schema\":{},\"required\"");
        var diagnostics=new DefinitionDiagnostics(); loader.model(codec.ingest(source),diagnostics);
        assertThat(diagnostics.failed()).isTrue();
    }
}
