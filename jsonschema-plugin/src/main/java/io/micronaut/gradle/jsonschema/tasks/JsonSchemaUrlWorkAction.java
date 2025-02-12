package io.micronaut.gradle.jsonschema.tasks;

import org.gradle.api.provider.Property;

import io.micronaut.jsonschema.generator.utils.SourceGeneratorConfigBuilder;

public abstract class JsonSchemaUrlWorkAction extends AbstractJsonSchemaWorkAction<JsonSchemaUrlWorkAction.UrlParameters> {
    protected interface UrlParameters extends AbstractJsonSchemaWorkAction.JsonSchemaParameters {
        Property<String> getJsonURL();
    }

    @Override
    protected void configureBuilder(SourceGeneratorConfigBuilder builder) {
        var parameters = getParameters();
        builder.withJsonUrl(parameters.getJsonURL().get());
    }
}
