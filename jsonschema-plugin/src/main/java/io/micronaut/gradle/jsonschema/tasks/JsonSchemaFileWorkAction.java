package io.micronaut.gradle.jsonschema.tasks;

import org.gradle.api.file.RegularFileProperty;

public abstract class JsonSchemaFileWorkAction extends AbstractJsonSchemaWorkAction<JsonSchemaFileWorkAction.FileParameters> {
    protected interface FileParameters extends JsonSchemaParameters {
        RegularFileProperty getJsonFile();
    }

    // TODO configure builder
}
