/*
 * Copyright 2003-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.gradle;

import io.micronaut.gradle.graalvm.GraalUtil;
import io.micronaut.gradle.internal.AutomaticDependency;
import org.apache.tools.ant.taskdefs.condition.Os;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.plugins.ApplicationPlugin;
import org.gradle.api.plugins.PluginManager;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.SourceSetOutput;
import org.gradle.api.tasks.TaskContainer;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static io.micronaut.gradle.PluginsHelper.resolveRuntime;

/**
 * A plugin which allows building Micronaut applications, without support
 * for GraalVM or Docker.
 */
@SuppressWarnings("Convert2Lambda")
public class MicronautMinimalApplicationPlugin implements Plugin<Project> {
    public static final String CONFIGURATION_DEVELOPMENT_ONLY = "developmentOnly";
    // This flag is used for testing purposes only
    public static final String INTERNAL_CONTINUOUS_FLAG = "io.micronaut.internal.gradle.continuous";

    private static final Map<String, String> LOGGER_CONFIG_FILE_TO_DEPENDENCY = Map.of(
        "logback.xml", "ch.qos.logback:logback-classic",
        "simplelogger.properties", "org.slf4j:slf4j-simple"
    );

    @Override
    public void apply(Project project) {
        PluginManager plugins = project.getPluginManager();
        plugins.apply(ApplicationPlugin.class);
        plugins.apply(MicronautComponentPlugin.class);
        PluginsHelper.registerVersionExtensions(MicronautRuntimeDependencies.KNOWN_VERSION_PROPERTIES, project);
        Configuration developmentOnly = createDevelopmentOnlyConfiguration(project);
        configureLogging(project);
        configureMicronautRuntime(project);
        configureJavaExecTasks(project, developmentOnly);
    }


    private void configureJavaExecTasks(Project project, Configuration developmentOnlyConfiguration) {
        final TaskContainer tasks = project.getTasks();
        ConfigurationContainer configurations = project.getConfigurations();
        Configuration developmentRuntimeClasspath = configurations.create("developmentRuntimeClasspath", conf -> {
            conf.setCanBeResolved(true);
            conf.setCanBeConsumed(true);
            Configuration runtimeClasspath = configurations.getByName("runtimeClasspath");
            conf.extendsFrom(runtimeClasspath);
            conf.extendsFrom(developmentOnlyConfiguration);
            AttributeUtils.copyAttributes(project.getProviders(), runtimeClasspath, conf);
        });
        tasks.withType(JavaExec.class).configureEach(javaExec -> {
            var sourceSets = PluginsHelper.findSourceSets(project);
            if (javaExec.getName().equals("run")) {
                javaExec.dependsOn(tasks.named(MicronautComponentPlugin.INSPECT_RUNTIME_CLASSPATH_TASK_NAME));
                javaExec.jvmArgs(
                        "-Dcom.sun.management.jmxremote"
                );
                if (!GraalUtil.isGraalJVM()) {
                    // graal doesn't support this
                    javaExec.jvmArgs("-XX:TieredStopAtLevel=1");
                }
                // https://github.com/micronaut-projects/micronaut-gradle-plugin/issues/385
                javaExec.getOutputs().upToDateWhen(t -> false);
                FileCollection classpath = javaExec.getClasspath();
                if (classpath instanceof ConfigurableFileCollection cp) {
                    Set<Object> from = cp.getFrom();
                    from.clear();
                    cp.from(sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME).getOutput());
                    cp.from(developmentRuntimeClasspath);
                }
            }

            // If -t (continuous mode) is enabled feed parameters to the JVM
            // that allows it to shut down on resources changes so a rebuild
            // can apply a restart to the application
            if (project.getGradle().getStartParameter().isContinuous() || Boolean.getBoolean(INTERNAL_CONTINUOUS_FLAG)) {
                SourceSet sourceSet = sourceSets.findByName("main");
                if (sourceSet != null) {
                    var sysProps = new LinkedHashMap<String, Object>();
                    sysProps.put("micronaut.io.watch.restart", true);
                    sysProps.put("micronaut.io.watch.enabled", true);
                    FileCollection sourceDirectories = sourceSet.getAllSource().getSourceDirectories();
                    //noinspection Convert2Lambda
                    javaExec.doFirst(new Action<>() {
                        @Override
                        public void execute(Task workaroundEagerSystemProps) {
                            String watchPaths = sourceDirectories
                                    .getFiles()
                                    .stream()
                                    .map(File::getPath)
                                    .collect(Collectors.joining(","));
                            javaExec.systemProperty("micronaut.io.watch.paths", watchPaths);
                        }
                    });
                    javaExec.systemProperties(
                            sysProps
                    );
                }
            }
        });
    }

    private Configuration createDevelopmentOnlyConfiguration(Project project) {
        ConfigurationContainer configurations = project.getConfigurations();
        Configuration developmentOnly = configurations.create(CONFIGURATION_DEVELOPMENT_ONLY, conf -> {
            conf.setCanBeConsumed(false);
            conf.setCanBeResolved(true);
            conf.extendsFrom(configurations.getByName(MicronautComponentPlugin.MICRONAUT_BOMS_CONFIGURATION));
        });

        // added to ensure file watch works more efficiently on OS X
        if (Os.isFamily(Os.FAMILY_MAC)) {
            developmentOnly.getDependencies().add(project.getDependencies().create("io.micronaut:micronaut-runtime-osx"));
        }

        return developmentOnly;
    }

    private void configureLogging(Project p) {
        DependencyHandler dependencyHandler = p.getDependencies();
        SourceSetContainer sourceSets = PluginsHelper.findSourceSets(p);
        SourceSet sourceSet = sourceSets.findByName(SourceSet.MAIN_SOURCE_SET_NAME);
        if (sourceSet != null) {
            SourceDirectorySet resources = sourceSet.getResources();
            Set<File> srcDirs = resources.getSrcDirs();
            exit:
            for (File srcDir : srcDirs) {
                for (Map.Entry<String, String> entry : LOGGER_CONFIG_FILE_TO_DEPENDENCY.entrySet()) {
                    var loggerConfigFile = new File(srcDir, entry.getKey());
                    if (loggerConfigFile.exists()) {
                        dependencyHandler.add(sourceSet.getRuntimeOnlyConfigurationName(), entry.getValue());
                        break exit;
                    }
                }
            }
        }
    }

    private void configureMicronautRuntime(Project project) {
        project.afterEvaluate(p -> {
            MicronautRuntime micronautRuntime = resolveRuntime(p);
            DependencyHandler dependencyHandler = p.getDependencies();
            MicronautRuntimeDependencies.findApplicationPluginDependenciesByRuntime(micronautRuntime)
                    .toMap()
                    .forEach((scope, dependencies) -> {
                for (AutomaticDependency dependency : dependencies) {
                    dependency.applyTo(project);
                }
            });
            if (micronautRuntime == MicronautRuntime.GOOGLE_FUNCTION) {
                configureGoogleCloudFunctionRuntime(project, p, dependencyHandler);
            }
            ShadowPluginSupport.withShadowPlugin(project, () -> ShadowPluginSupport.mergeServiceFiles(project));

        });
    }

    private void configureGoogleCloudFunctionRuntime(Project project, Project p, DependencyHandler dependencyHandler) {
        String invokerConfig = "invoker";
        Configuration ic = project.getConfigurations().create(invokerConfig);
        dependencyHandler.add(invokerConfig, "com.google.cloud.functions.invoker:java-function-invoker:1.0.0-beta2");

        // reconfigure the run task to use Google cloud invoker
        TaskContainer taskContainer = project.getTasks();
        taskContainer.register("runFunction", JavaExec.class, run -> {
            run.dependsOn(taskContainer.findByName("processResources"), taskContainer.findByName("classes"));
            run.getMainClass().set("com.google.cloud.functions.invoker.runner.Invoker");
            run.setClasspath(ic);
            run.setArgs(List.of(
                    "--target", "io.micronaut.gcp.function.http.HttpFunction",
                    "--port", 8080
            ));
            SourceSet sourceSet = PluginsHelper.findSourceSets(p).getByName("main");
            SourceSetOutput output = sourceSet.getOutput();
            String runtimeClasspath = project.files(project.getConfigurations().getByName("runtimeClasspath"),
                    output
            ).getAsPath();
            run.doFirst(t -> {
                ((JavaExec) t).args("--classpath",
                        runtimeClasspath
                );
            });
        });

        // Google Cloud Function requires shadow packaging
        p.getPluginManager().apply(ShadowPluginSupport.SHADOW_PLUGIN);
    }
}
