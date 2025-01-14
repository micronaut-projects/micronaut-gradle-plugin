package io.micronaut.gradle.jsonschema.tasks;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;

import io.micronaut.jsonschema.generator.SourceGenerator;
import io.micronaut.jsonschema.generator.utils.SourceGeneratorConfigBuilder;
import io.micronaut.jsonschema.generator.loaders.UrlLoader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public abstract class AbstractJsonSchemaWorkAction<T extends AbstractJsonSchemaWorkAction.JsonSchemaParameters> implements WorkAction<T> {
    interface JsonSchemaParameters extends WorkParameters {

        Property<String> getLang();

        ListProperty<String> getAcceptedUrlPatterns();

        DirectoryProperty getOutputDirectory();

        Property<String> getPackageName();

        Property<String> getOutputFileName();
    }

    protected abstract void configureBuilder(SourceGeneratorConfigBuilder builder);

    @Override
    public void execute() {
        var parameters = getParameters();
        var lang = parameters.getLang().get();
        var langGenerator = new SourceGenerator(lang.toUpperCase());

        List<String> allowedUrlPatterns = parameters.getAcceptedUrlPatterns().get();
        if (!allowedUrlPatterns.isEmpty()) {
            UrlLoader.setAllowedUrlPatterns(allowedUrlPatterns);
        }

        Path outputPath = parameters.getOutputDirectory().get().getAsFile().toPath();
        String outputPackageName = parameters.getPackageName().get();
        String outputFileName = parameters.getOutputFileName().get();
        var builder = new SourceGeneratorConfigBuilder()
                .withOutputFolder(outputPath)
                .withOutputPackageName(outputPackageName)
                .withOutputFileName(outputFileName);
        configureBuilder(builder);

        try {
            langGenerator.generate(builder.build());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
