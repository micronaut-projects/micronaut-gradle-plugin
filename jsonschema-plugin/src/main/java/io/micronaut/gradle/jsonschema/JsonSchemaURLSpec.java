package io.micronaut.gradle.jsonschema;

import org.gradle.api.provider.Property;

public interface JsonSchemaURLSpec extends JsonSchemaSpec {
    Property<String> getInputUrl();
}
