package io.micronaut.gradle.jsonschema.tasks;

import org.gradle.api.provider.Property;

public abstract class JsonSchemaUrlWorkAction extends AbstractJsonSchemaWorkAction<JsonSchemaUrlWorkAction.UrlParameters> {
    protected interface UrlParameters extends AbstractJsonSchemaWorkAction.JsonSchemaParameters {
        Property<String> getJsonURL();
    }

    // TODO configure builder
}
