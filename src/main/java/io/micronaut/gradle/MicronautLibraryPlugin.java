package io.micronaut.gradle;

import com.diffplug.gradle.eclipse.apt.AptEclipsePlugin;
import io.micronaut.gradle.graalvm.MicronautGraalPlugin;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.plugins.GroovyPlugin;
import org.gradle.api.plugins.PluginContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.compile.GroovyCompile;
import org.gradle.api.tasks.compile.GroovyForkOptions;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.testing.Test;

import java.util.*;
import java.util.stream.Stream;

import static org.gradle.api.plugins.JavaPlugin.*;

/**
 * A plugin for creating a Micronaut library. Applies the java-library plugin by default.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class MicronautLibraryPlugin implements Plugin<Project> {
    private static final Map<String, String> GROUP_TO_PROCESSOR_MAP = new HashMap<>();

    static {
        GROUP_TO_PROCESSOR_MAP.put(
            "io.swagger.core.v3", "io.micronaut.configuration:micronaut-openapi"
        );
        GROUP_TO_PROCESSOR_MAP.put(
            "io.micronaut.data", "io.micronaut.data:micronaut-data-processor"
        );
        GROUP_TO_PROCESSOR_MAP.put(
            "io.micronaut.jaxrs", "io.micronaut.jaxrs:micronaut-jaxrs-processor"
        );
        GROUP_TO_PROCESSOR_MAP.put(
            "io.micronaut.security", "io.micronaut.security:micronaut-security-annotations"
        );
    }

    private boolean isLibrary = false;

    @Override
    public void apply(Project project) {
        final PluginContainer plugins = project.getPlugins();

        plugins.apply(getBasePluginName());
        plugins.apply(AptEclipsePlugin.class);
        ExtensionContainer extensions = project.getExtensions();
        extensions.create("micronaut", MicronautExtension.class);

        project.getPlugins().apply(MicronautGraalPlugin.class);

        final TaskContainer tasks = project.getTasks();

        configureJava(project, tasks);

        configureGroovy(project, tasks);

        if (MicronautKotlinSupport.isKotlinSupportPresent()) {
            MicronautKotlinSupport.configureKotlin(project);
        }

        project.afterEvaluate(p -> {

            final DependencyHandler dependencyHandler = p.getDependencies();

            final MicronautExtension micronautExtension = p.getExtensions().getByType(MicronautExtension.class);

            String micronautVersion = getMicronautVersion(p, micronautExtension);

            final Dependency platform = resolveMicronautPlatform(dependencyHandler, micronautVersion);
            for (String configuration : getBomConfigurations()) {
                dependencyHandler.add(
                        configuration,
                        platform
                );
            }

            applyAdditionalProcessors(p, ANNOTATION_PROCESSOR_CONFIGURATION_NAME, TEST_ANNOTATION_PROCESSOR_CONFIGURATION_NAME);

            boolean hasGroovy = plugins.findPlugin(GroovyPlugin.class) != null;
            if (hasGroovy) {
                for (String configuration : getGroovyAstTransformConfigurations()) {
                    dependencyHandler.add(
                            configuration,
                            "io.micronaut:micronaut-inject-groovy"
                    );
                }
            }

            Configuration testConfig = p.getConfigurations().getByName(TEST_IMPLEMENTATION_CONFIGURATION_NAME);
            boolean hasJunit5 = !testConfig.getAllDependencies()
                    .matching(dependency -> {
                        String name = dependency.getName();
                        return name.equals("junit-jupiter-engine") || name.equals("micronaut-test-junit5");
                    })
                    .isEmpty();
            if (hasJunit5) {
                project.getTasks().withType(Test.class, Test::useJUnitPlatform);
            }
        });
    }

    static Dependency resolveMicronautPlatform(DependencyHandler dependencyHandler, String micronautVersion) {
        final Dependency platform;
        if (micronautVersion.endsWith("-SNAPSHOT")) {
            // enforced platform has to be used for snapshots to work correctly
            platform = dependencyHandler.enforcedPlatform("io.micronaut:micronaut-bom:" + micronautVersion);
        } else {
            platform = dependencyHandler.platform("io.micronaut:micronaut-bom:" + micronautVersion);
        }
        return platform;
    }

    static void applyAdditionalProcessors(Project project, String ... configurations) {
        Stream.of(IMPLEMENTATION_CONFIGURATION_NAME, COMPILE_ONLY_CONFIGURATION_NAME).forEach(config -> {
            final DependencySet allDependencies = project.getConfigurations().getByName(config)
                    .getAllDependencies();
            for (String group : GROUP_TO_PROCESSOR_MAP.keySet()) {
                boolean hasDep = !allDependencies.matching(dependency -> Objects.equals(dependency.getGroup(), group)).isEmpty();
                if (hasDep) {
                    final DependencyHandler dependencies = project.getDependencies();
                    for (String configuration : configurations) {
                        dependencies.add(configuration, GROUP_TO_PROCESSOR_MAP.get(group));
                    }
                }
            }
        });


    }

    private void configureJava(Project project, TaskContainer tasks) {
        final DependencyHandler dependencyHandler = project.getDependencies();
        for (String configuration : getJavaAnnotationProcessorConfigurations()) {
            List<String> annotationProcessorModules = getAnnotationProcessorModules();
            for (String annotationProcessorModule : annotationProcessorModules) {
                dependencyHandler.add(
                        configuration,
                        "io.micronaut:micronaut-" + annotationProcessorModule
                );
            }

        }

        dependencyHandler.add(
                isLibrary ? API_CONFIGURATION_NAME : IMPLEMENTATION_CONFIGURATION_NAME,
                "io.micronaut:micronaut-inject"
        );

        project.afterEvaluate(p -> tasks.withType(JavaCompile.class, javaCompile -> {
            final List<String> compilerArgs = javaCompile.getOptions().getCompilerArgs();
            final MicronautExtension micronautExtension = p.getExtensions().getByType(MicronautExtension.class);
            final AnnotationProcessing processing = micronautExtension.getProcessing();
            final boolean isIncremental = processing.getIncremental().getOrElse(true);
            final String group = processing.getGroup().getOrElse(p.getGroup().toString());
            final String module = processing.getModule().getOrElse(p.getName());
            if (isIncremental) {
                final List<String> annotations = processing.getAnnotations().getOrElse(Collections.emptyList());
                compilerArgs.add("-Amicronaut.processing.incremental=true");
                if (!annotations.isEmpty()) {
                    compilerArgs.add("-Amicronaut.processing.annotations=" + String.join(",", annotations));
                } else {
                    if (group.length() > 0) {
                        compilerArgs.add("-Amicronaut.processing.annotations=" + group + ".*");
                    }
                }
            }

            if (group.length() > 0) {
                compilerArgs.add("-Amicronaut.processing.group=" + group);
                compilerArgs.add("-Amicronaut.processing.module=" + module);
            }
        }));

    }

    private void configureGroovy(Project project, TaskContainer tasks) {
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

        project.afterEvaluate(p -> {
            PluginContainer plugins = p.getPlugins();
            boolean hasGroovy = plugins.hasPlugin("groovy");
            if (hasGroovy && project.file("src/main/groovy").exists()) {
                applyAdditionalProcessors(p, COMPILE_ONLY_CONFIGURATION_NAME);
            }
            if (hasGroovy && project.file("src/test/groovy").exists()) {
                applyAdditionalProcessors(p, TEST_COMPILE_ONLY_CONFIGURATION_NAME);
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

    static List<String> getAnnotationProcessorModules() {
        return Arrays.asList("inject-java", "validation");
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
