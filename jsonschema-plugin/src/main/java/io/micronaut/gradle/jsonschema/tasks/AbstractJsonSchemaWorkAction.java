package io.micronaut.gradle.jsonschema.tasks;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;

public abstract class AbstractJsonSchemaWorkAction<T extends AbstractJsonSchemaWorkAction.JsonSchemaParameters> implements WorkAction<T> {
    interface JsonSchemaParameters extends WorkParameters {
        ConfigurableFileCollection getClasspath();

        Property<String> getLang();

        ListProperty<String> getAcceptedUrlPatterns();

        DirectoryProperty getOutputDirectory();

        Property<String> getPackageName();

        Property<String> getOutputFileName();
    }

    // TODO configure builder

    @Override
    public void execute() {
        // TODO
    }
}
