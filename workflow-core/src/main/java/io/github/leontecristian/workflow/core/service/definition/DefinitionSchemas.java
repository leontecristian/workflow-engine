package io.github.leontecristian.workflow.core.service.definition;

import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.path.PathType;

/** Engine-private provider configuration for bundled and embedded definition schemas. */
final class DefinitionSchemas {
    private DefinitionSchemas() {}

    static SchemaRegistry offlineRegistry(boolean embedded) {
        return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12, builder -> builder
                .schemaCacheEnabled(false)
                .schemaLoader(loader -> loader.allow(iri -> false))
                .schemaRegistryConfig(SchemaRegistryConfig.builder().failFast(true).typeLoose(false)
                        .losslessNarrowing(embedded).formatAssertionsEnabled(embedded)
                        .pathType(PathType.JSON_POINTER).build()));
    }
}
