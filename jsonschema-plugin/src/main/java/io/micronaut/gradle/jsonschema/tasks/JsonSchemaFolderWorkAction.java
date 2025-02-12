package io.micronaut.gradle.jsonschema.tasks;

import org.gradle.api.file.DirectoryProperty;

import io.micronaut.jsonschema.generator.utils.SourceGeneratorConfigBuilder;

public abstract class JsonSchemaFolderWorkAction extends AbstractJsonSchemaWorkAction<JsonSchemaFolderWorkAction.FolderParameters> {
    protected interface FolderParameters extends JsonSchemaParameters {
        DirectoryProperty getInputDirectory();
    }

    @Override
    protected void configureBuilder(SourceGeneratorConfigBuilder builder) {
        var parameters = getParameters();
        builder.withInputFolder(parameters.getInputDirectory().get().getAsFile().toPath());
    }
}
