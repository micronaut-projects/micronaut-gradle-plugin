package io.micronaut.gradle.jsonschema;

import io.micronaut.gradle.PluginsHelper;
import io.micronaut.gradle.jsonschema.tasks.AbstractJsonSchemaGenerator;
import io.micronaut.gradle.jsonschema.tasks.JsonSchemaFileGenerator;
import io.micronaut.gradle.jsonschema.tasks.JsonSchemaFolderGenerator;
import io.micronaut.gradle.jsonschema.tasks.JsonSchemaUrlGenerator;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.Directory;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;

import javax.inject.Inject;
import java.io.File;
import java.util.List;
import java.util.function.Consumer;

import static org.codehaus.groovy.runtime.StringGroovyMethods.capitalize;

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
        var urlSpec = project.getObjects().newInstance(JsonSchemaURLSpec.class);
        configureCommonExtensionDefaults(urlSpec);
        urlSpec.getInputUrl().convention("");
        spec.execute(urlSpec);
        var generator = project.getTasks().register(generateTaskName(url.substring(url.lastIndexOf("/") + 1)), JsonSchemaUrlGenerator.class, task -> {
            task.setDescription("Generates source files from an URL of a JSON Schema file");
            configureCommonProperties(task, urlSpec);
            task.getJsonURL().convention(url);
        });
        addSourceDir(generator);
    }

    @Override
    public void fromFile(File file, Action<JsonSchemaSpec> spec) {
        var specInstance = file.isDirectory() ? JsonSchemaFolderSpec.class : JsonSchemaFileSpec.class;
        var fileSpec = project.getObjects().newInstance(specInstance);
        configureCommonExtensionDefaults(fileSpec);
        spec.execute(fileSpec);
        if (file.isDirectory()) {
            var regularDirProperty = project.getObjects().directoryProperty();
            var generator = project.getTasks().register(generateTaskName(file.getName()), JsonSchemaFolderGenerator.class, task -> {
                task.setDescription("Generates source files from an URL of a JSON Schema file");
                configureCommonProperties(task, fileSpec);
                task.getInputDirectory().convention(regularDirProperty.fileValue(file));
            });
            addSourceDir(generator);
        } else {
            var regularFileProperty = project.getObjects().fileProperty();
            var generator = project.getTasks().register(generateTaskName(file.getName()), JsonSchemaFileGenerator.class, task -> {
                task.setDescription("Generates source files from an URL of a JSON Schema file");
                configureCommonProperties(task, fileSpec);
                task.getInputFile().convention(regularFileProperty.fileValue(file));
            });
            addSourceDir(generator);
        }
    }

    private void configureCommonExtensionDefaults(JsonSchemaSpec spec) {
        spec.getAcceptedUrlPatterns().convention(List.of());
        spec.getLang().convention("JAVA");
        spec.getOutputPackageName().convention("io.micronaut.jsonschema.generated");
        spec.getOutputFileName().convention("");
        spec.getOutputDirectory().convention(project.getLayout().getBuildDirectory().dir("generated/jsonschema/"));
    }

    private void configureCommonProperties(AbstractJsonSchemaGenerator<?, ?> task, JsonSchemaSpec schemaSpec) {
        task.getClasspath().from(classpath);
        task.getOutputDirectory().convention(schemaSpec.getOutputDirectory().dir(task.getName()));
        task.getPackageName().convention(schemaSpec.getOutputPackageName());
        task.getOutputFileName().convention(schemaSpec.getOutputFileName());
        task.getAcceptedUrlPatterns().convention(schemaSpec.getAcceptedUrlPatterns());
        task.getLanguage().convention(schemaSpec.getLang());
    }

    private void addSourceDir(TaskProvider<? extends AbstractJsonSchemaGenerator<?, ?>> generator) {
        withJavaSourceSets(sourceSets -> {
            var javaMain = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME).getJava();
            javaMain.srcDir(generator.map(DefaultJSONSchemaExtension::mainSrcDir));
            project.getPluginManager().withPlugin("org.jetbrains.kotlin.jvm", unused -> {
                var ext = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME).getExtensions().getByName("kotlin");
                if (ext instanceof SourceDirectorySet kotlinMain) {
                    kotlinMain.srcDir(generator.map(d -> DefaultJSONSchemaExtension.mainSrcDir(d, "kotlin")));
                }
            });
        });
    }

    private void withJava(Runnable runnable) {
        project.getPlugins().withId("java", unused -> runnable.run());
    }

    private void withJavaSourceSets(Consumer<? super SourceSetContainer> consumer) {
        project.getPlugins().withId("java", unused -> consumer.accept(PluginsHelper.findSourceSets(project)));
    }

    private static Provider<Directory> mainSrcDir(AbstractJsonSchemaGenerator<?, ?> t, String language) {
        return t.getOutputDirectory().dir("src/main/" + language);
    }

    private static Provider<Directory> mainSrcDir(AbstractJsonSchemaGenerator<?, ?> t) {
        return mainSrcDir(t, "java");
    }

    private static String generateTaskName(String name) {
        int endIndex = name.contains(".") ? name.indexOf(".") : name.length();
        return "generatingSourcesFrom" + capitalize(name.substring(0, endIndex));
    }
}
