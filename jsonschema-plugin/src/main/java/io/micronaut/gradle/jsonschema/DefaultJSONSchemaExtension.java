package io.micronaut.gradle.jsonschema;

import io.micronaut.gradle.jsonschema.tasks.AbstractJsonSchemaGenerator;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.Directory;
import org.gradle.api.provider.Provider;

import javax.inject.Inject;
import java.io.File;
import java.util.List;

public abstract class DefaultJSONSchemaExtension implements JSONSchemaExtension {
    private final Project project;
    private final Configuration classpath;

    @Inject
    public DefaultJSONSchemaExtension(Project project, Configuration classpath) {
        this.project = project;
        this.classpath = classpath;
    }

    @Override
    public void url(String url, Action<JsonSchemaSpec> spec) {
        var regularStringProperty = project.getObjects().property(String.class);
        var urlSpec = project.getObjects().newInstance(JsonSchemaURLSpec.class);
        configureCommonExtensionDefaults(urlSpec);
        urlSpec.getInputUrl().convention("");
        spec.execute(urlSpec);
    }

    @Override
    public void file(File file, Action<JsonSchemaSpec> spec) {
        var regularFileProperty = project.getObjects().fileProperty();
        var fileSpec = project.getObjects().newInstance(JsonSchemaFileSpec.class);
        configureCommonExtensionDefaults(fileSpec);
        fileSpec.getInputFile().convention(new File(""));
        spec.execute(fileSpec);
    }

    @Override
    public void folder(File folder, Action<JsonSchemaSpec> spec) {
        var regularFileProperty = project.getObjects().fileProperty();
        var folderSpec = project.getObjects().newInstance(JsonSchemaFolderSpec.class);
        configureCommonExtensionDefaults(folderSpec);
        folderSpec.getInputFolder().convention(new File(""));
        spec.execute(folderSpec);
    }

    private void configureCommonExtensionDefaults(JsonSchemaSpec spec) {
        spec.getAcceptedUrlPatterns().convention(List.of());
        spec.getLang().convention("JAVA");
        spec.getOutputDirectory().dir("");
        spec.getOutputPackageName().convention("");
        spec.getOutputFileName().convention("");

        withJava(() -> {
            var compileOnlyDeps = project.getConfigurations().getByName("compileOnly").getDependencies();
            compileOnlyDeps.add(project.getDependencies().create("io.micronaut.json-schema:micronaut-json-schema-generator"));
        });
    }

    private void withJava(Runnable runnable) {
        project.getPlugins().withId("java", unused -> runnable.run());
    }

    private static Provider<Directory> mainSrcDir(AbstractJsonSchemaGenerator<?, ?> t, String language) {
        return t.getOutputDirectory().dir("src/main/" + language);
    }
}
