package io.micronaut.gradle.jsonschema;

import io.micronaut.gradle.Strings;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.tasks.TaskProvider;

import java.io.File;

public abstract class DefaultJSONSchemaExtension extends DefaultAbstractJSONSchemaExtension implements JSONSchemaExtension {

    public DefaultJSONSchemaExtension(Project project, Configuration classpath) {
        super(project, classpath);
    }

    @Override
    public void fromUrl(String url, Action<? super GenerateJSONSchemaSpec> action) {
        String taskName = generateTaskName(url.substring(url.lastIndexOf("/") + 1));
        generate(taskName, spec -> {
            spec.getInputURL().convention(url);
            spec.getOutputDirectory().convention(project.getLayout().getBuildDirectory().dir("generated-sources/jsonschema/" + taskName));
            action.execute(spec);
        });
    }

    @Override
    public void fromFile(File file, Action<? super GenerateJSONSchemaSpec> action) {
        String taskName = generateTaskName(file.getName());
        if (file.isDirectory()) {
            generate(taskName, spec -> {
                spec.getInputDirectory().set(file);
                spec.getOutputDirectory().convention(project.getLayout().getBuildDirectory().dir("generated-sources/jsonschema/" + taskName));
                action.execute(spec);
            });
        } else {
            generate(taskName, spec -> {
                spec.getInputFile().set(file);
                spec.getOutputDirectory().convention(project.getLayout().getBuildDirectory().dir("generated-sources/jsonschema/" + taskName));
                action.execute(spec);
            });
        }
    }

    @Override
    TaskProvider<? extends GenerateJSONSchemaTask> createGenerateJSONSchemaTask(String name, Action<GenerateJSONSchemaTask> configurator) {
        return super.createGenerateJSONSchemaTask(name, task -> {
            configurator.execute(task);
            task.getJavaOutputDirectory().convention(task.getOutputDirectory().dir("src/main/java"));
            task.getGroovyOutputDirectory().convention(task.getOutputDirectory().dir("src/main/groovy"));
            task.getKotlinOutputDirectory().convention(task.getOutputDirectory().dir("src/main/kotlin"));
        });
    }

    @Override
    protected void configureSpec(GenerateJSONSchemaSpec spec) {
        super.configureSpec(spec);
        spec.getAcceptedUrlPatterns().convention((Iterable<String>) null);
    }

    private static String generateTaskName(String name) {
        int endIndex = name.contains(".") ? name.indexOf(".") : name.length();
        return "generatingSourcesFrom" + Strings.capitalize(name.substring(0, endIndex));
    }
}
