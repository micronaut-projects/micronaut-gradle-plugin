package io.micronaut.gradle;

import com.diffplug.gradle.eclipse.apt.AptEclipsePlugin;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.plugins.GroovyPlugin;
import org.gradle.api.plugins.PluginContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.compile.GroovyCompile;
import org.gradle.api.tasks.compile.GroovyForkOptions;
import org.gradle.api.tasks.compile.JavaCompile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.gradle.api.plugins.JavaPlugin.*;

/**
 * A plugin for creating a Micronaut library. Applies the java-library plugin by default.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class MicronautLibraryPlugin implements Plugin<Project> {
    private boolean isLibrary = false;

    @Override
    public void apply(Project project) {
        final PluginContainer plugins = project.getPlugins();

        plugins.apply(getBasePluginName());
        plugins.apply(AptEclipsePlugin.class);
        ExtensionContainer extensions = project.getExtensions();
        extensions.create("micronaut", MicronautExtension.class);

        final TaskContainer tasks = project.getTasks();

        configureJava(project, tasks);

        configureGroovy(tasks);

        if (MicronautKotlinSupport.isKotlinSupportPresent()) {
            MicronautKotlinSupport.configureKotlin(project);
        }

        project.afterEvaluate(p -> {

            final DependencyHandler dependencyHandler = p.getDependencies();
            final MicronautExtension micronautExtension = p.getExtensions().getByType(MicronautExtension.class);

            String micronautVersion = getMicronautVersion(p, micronautExtension);

            final Dependency platform = dependencyHandler.platform("io.micronaut:micronaut-bom:" + micronautVersion);
            for (String configuration : getBomConfigurations()) {
                dependencyHandler.add(
                        configuration,
                        platform
                );
            }

            for (String configuration : getJavaAnnotationProcessorConfigurations()) {
                dependencyHandler.add(
                        configuration,
                        "io.micronaut:micronaut-inject-java"
                );
            }

            dependencyHandler.add(
                    isLibrary ? API_CONFIGURATION_NAME : IMPLEMENTATION_CONFIGURATION_NAME,
                    "io.micronaut:micronaut-inject"
            );

            boolean hasGroovy = plugins.findPlugin(GroovyPlugin.class) != null;
            if (hasGroovy) {
                for (String configuration : getGroovyAstTransformConfigurations()) {
                    dependencyHandler.add(
                            configuration,
                            "io.micronaut:micronaut-inject-groovy"
                    );
                }
            }
        });
    }

    private void configureJava(Project project, TaskContainer tasks) {
        tasks.withType(JavaCompile.class, javaCompile -> {
            final List<String> compilerArgs = javaCompile.getOptions().getCompilerArgs();
            final MicronautExtension micronautExtension = project.getExtensions().getByType(MicronautExtension.class);
            final MicronautExtension.AnnotationProcessingConfig processingConfig = micronautExtension.getProcessingConfig();
            final boolean isIncremental = processingConfig.isIncremental().getOrElse(true);
            final String group = processingConfig.getGroup().getOrElse(project.getGroup().toString());
            final String module = processingConfig.getModule().getOrElse(project.getName());
            if (isIncremental) {
                final List<String> annotations = processingConfig.getAnnotations().getOrElse(Collections.emptyList());
                compilerArgs.add("-Amicronaut.processing.incremental=true");
                if (!annotations.isEmpty()) {
                    compilerArgs.add("-Amicronaut.processing.annotations=" + String.join(",", annotations));
                } else {
                    if (group.length() > 0) {
                        compilerArgs.add("-Amicronaut.processing.annotations=" + group + ".*");
                    }
                }
            }

            compilerArgs.add("-Amicronaut.processing.group=" + group);
            compilerArgs.add("-Amicronaut.processing.module=" + module);
        });
    }

    private void configureGroovy(TaskContainer tasks) {
        tasks.withType(GroovyCompile.class, groovyCompile -> {
            final GroovyForkOptions forkOptions = groovyCompile.getGroovyOptions().getForkOptions();
            List<String> jvmArgs = forkOptions.getJvmArgs();
            if (jvmArgs != null) {
                jvmArgs.add("-Dgroovy.parameters=true");
            } else {
                jvmArgs = new ArrayList<>();
                jvmArgs.add("-Dgroovy.parameters=true");
                forkOptions.setJvmArgs(jvmArgs);
            }
        });
    }

    private List<String> getJavaAnnotationProcessorConfigurations() {
        return Arrays.asList(
                ANNOTATION_PROCESSOR_CONFIGURATION_NAME,
                TEST_ANNOTATION_PROCESSOR_CONFIGURATION_NAME
        );
    }

    private List<String> getGroovyAstTransformConfigurations() {
        return Arrays.asList(
                COMPILE_ONLY_CONFIGURATION_NAME,
                TEST_COMPILE_ONLY_CONFIGURATION_NAME
        );
    }

    private List<String> getBomConfigurations() {
        return Arrays.asList(
                ANNOTATION_PROCESSOR_CONFIGURATION_NAME,
                TEST_ANNOTATION_PROCESSOR_CONFIGURATION_NAME,
                isLibrary ? API_CONFIGURATION_NAME : IMPLEMENTATION_CONFIGURATION_NAME,
                COMPILE_ONLY_CONFIGURATION_NAME
        );
    }

    protected String getBasePluginName() {
        this.isLibrary = true;
        return "java-library";
    }

    static String getMicronautVersion(Project p, MicronautExtension micronautExtension) {
        String v = micronautExtension.getVersion().getOrNull();
        if (v == null) {
            final Object o = p.getProperties().get("micronautVersion");
            if (o != null) {
                v = o.toString();
            }
        }
        if (v == null || v.length() == 0) {
            throw new InvalidUserCodeException("Micronaut version not set. Use micronaut { version '..'} or 'micronautVersion' in gradle.properties to set the version");
        }
        return v;
    }
}
