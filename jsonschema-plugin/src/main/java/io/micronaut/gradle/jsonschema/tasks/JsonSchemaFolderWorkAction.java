package io.micronaut.gradle.jsonschema.tasks;

import org.gradle.api.file.DirectoryProperty;

public abstract class JsonSchemaFolderWorkAction extends AbstractJsonSchemaWorkAction<JsonSchemaFolderWorkAction.FolderParameters> {
    protected interface FolderParameters extends JsonSchemaParameters {
        DirectoryProperty getInputDirectory();
    }

    // TODO configure builder
}
