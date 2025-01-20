package io.micronaut.gradle.jsonschema;

import org.gradle.api.provider.Property;

import java.io.File;

public interface JsonSchemaFolderSpec extends JsonSchemaSpec {
    Property<File> getInputFolder();
}
