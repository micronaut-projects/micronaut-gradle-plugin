package io.micronaut.gradle.jsonschema;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

public interface JsonSchemaSpec {
    Property<String> getLang();

    ListProperty<String> getAcceptedUrlPatterns();

    Property<String> getOutputFileName();

    Property<String> getOutputPackageName();

    DirectoryProperty getOutputDirectory();
}
