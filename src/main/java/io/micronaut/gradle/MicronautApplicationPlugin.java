package io.micronaut.gradle;

import com.github.jengelman.gradle.plugins.shadow.ShadowPlugin;
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar;
import io.micronaut.gradle.docker.MicronautDockerPlugin;
import org.apache.tools.ant.taskdefs.condition.Os;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.plugins.*;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetOutput;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.testing.Test;

import java.util.*;

/**
 * A plugin for a Micronaut application. Applies the "application" plugin.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class MicronautApplicationPlugin extends MicronautLibraryPlugin {

    public static final String CONFIGURATION_DEVELOPMENT_ONLY = "developmentOnly";

    @Override
    public void apply(Project project) {
        super.apply(project);

        // create a configuration used for dependencies that are only used for development
        Configuration developmentOnly = project
                .getConfigurations().create(CONFIGURATION_DEVELOPMENT_ONLY);


        // added to ensure file watch works more efficiently on OS X
        if (Os.isFamily(Os.FAMILY_MAC)) {
            project.getDependencies().add(CONFIGURATION_DEVELOPMENT_ONLY, "io.micronaut:micronaut-runtime-osx");
        }

        project.afterEvaluate(p -> {
            final MicronautExtension ext = p.getExtensions().getByType(MicronautExtension.class);
            final String v = getMicronautVersion(p, ext);
            final DependencyHandler dependencyHandler = p.getDependencies();
            Dependency platform = resolveMicronautPlatform(dependencyHandler, v);
            dependencyHandler.add(CONFIGURATION_DEVELOPMENT_ONLY, platform);

            MicronautRuntime micronautRuntime = resolveRuntime(p);
            if (micronautRuntime == MicronautRuntime.ORACLE_FUNCTION) {
                RepositoryHandler repositories = project.getRepositories();
                repositories.add(
                    repositories.maven(mavenArtifactRepository -> mavenArtifactRepository.setUrl("https://dl.bintray.com/fnproject/fnproject"))
                );
            }
            JavaApplication javaApplication = p.getExtensions().getByType(JavaApplication.class);
            micronautRuntime.getDependencies().forEach((scope, dependencies) -> {
                for (String dependency : dependencies) {
                    dependencyHandler.add(scope, dependency);
                }
            });

            if (micronautRuntime == MicronautRuntime.GOOGLE_FUNCTION) {
                String invokerConfig = "invoker";
                Configuration ic = project.getConfigurations().create(invokerConfig);
                dependencyHandler.add(invokerConfig, "com.google.cloud.functions.invoker:java-function-invoker:1.0.0-beta2");

                // reconfigure the run task to use Google cloud invoker
                TaskContainer taskContainer = project.getTasks();
                JavaExec run = (JavaExec) taskContainer.getByName("run");
                run.dependsOn(taskContainer.findByName("processResources"), taskContainer.findByName("classes"));
                run.setMain("com.google.cloud.functions.invoker.runner.Invoker");
                run.setClasspath(ic);
                run.setArgs(Arrays.asList(
                        "--target", "io.micronaut.gcp.function.http.HttpFunction",
                        "--port", 8080
                ));
                run.doFirst(t -> {
                    JavaPluginConvention plugin = project.getConvention().getPlugin(JavaPluginConvention.class);
                    SourceSet sourceSet = plugin.getSourceSets().getByName("main");
                    SourceSetOutput output = sourceSet.getOutput();
                    String runtimeClasspath = project.files(project.getConfigurations().getByName("runtimeClasspath"),
                            output
                    ).getAsPath();
                    ((JavaExec) t).args("--classpath",
                            runtimeClasspath
                    );
                });
                // apply required GCP function dependencies
                dependencyHandler.add(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME, "com.google.cloud.functions:functions-framework-api");
                dependencyHandler.add(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME,  "io.micronaut.gcp:micronaut-gcp-function-http");
                // set the main class name appropriately
                javaApplication.setMainClassName("io.micronaut.gcp.function.http.HttpFunction");
                PluginContainer plugins = project.getPlugins();
                // Google Cloud Function requires shadow packaging
                if (!plugins.hasPlugin(ShadowPlugin.class)) {
                    plugins.apply(ShadowPlugin.class);
                }
            }
        });
        new MicronautDockerPlugin().apply(project);


        final TaskContainer tasks = project.getTasks();
        tasks.withType(JavaExec.class, javaExec -> {
            javaExec.classpath(developmentOnly);

            // If -t (continuous mode) is enabled feed parameters to the JVM
            // that allows it to shutdown on resources changes so a rebuild
            // can apply a restart to the application
            if (project.getGradle().getStartParameter().isContinuous()) {
                Map<String, Object> sysProps = new LinkedHashMap<>();
                sysProps.put("micronaut.io.watch.restart", true);
                sysProps.put("micronaut.io.watch.enabled", true);
                sysProps.put("micronaut.io.watch.paths", "src/main");
                javaExec.systemProperties(
                        sysProps
                );
            }
        });

        // If shadow JAR is enabled it must be configured to merge
        // all META-INF/services file into a single file otherwise this
        // will break the application
        tasks.withType(ShadowJar.class, ShadowJar::mergeServiceFiles);
    }

    @Override
    protected String getBasePluginName() {
        return "application";
    }

    public static MicronautRuntime resolveRuntime(Project p) {
        MicronautExtension ext = p.getExtensions().getByType(MicronautExtension.class);
        Object o = p.findProperty("micronaut.runtime");

        MicronautRuntime micronautRuntime;
        if (o != null) {
            micronautRuntime = MicronautRuntime.valueOf(o.toString().toUpperCase(Locale.ENGLISH));
        } else {
            micronautRuntime = ext.getRuntime().getOrElse(MicronautRuntime.NONE);
        }
        return micronautRuntime;
    }
}
