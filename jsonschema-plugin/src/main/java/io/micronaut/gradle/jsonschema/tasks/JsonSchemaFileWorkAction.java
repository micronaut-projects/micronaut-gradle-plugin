package io.micronaut.gradle.jsonschema.tasks;

import org.gradle.api.file.RegularFileProperty;

import io.micronaut.jsonschema.generator.utils.SourceGeneratorConfigBuilder;

public abstract class JsonSchemaFileWorkAction extends AbstractJsonSchemaWorkAction<JsonSchemaFileWorkAction.FileParameters> {
    protected interface FileParameters extends JsonSchemaParameters {
        RegularFileProperty getJsonFile();
    }

    @Override
    protected void configureBuilder(SourceGeneratorConfigBuilder builder) {
        var parameters = getParameters();
        builder.withJsonFile(parameters.getJsonFile().get().getAsFile());
    }
}
