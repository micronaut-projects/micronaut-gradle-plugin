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

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.plugins.PluginManager;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.GroovyCompile;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.testing.Test;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static io.micronaut.gradle.PluginsHelper.applyAdditionalProcessors;
import static io.micronaut.gradle.PluginsHelper.configureAnnotationProcessors;
import static io.micronaut.gradle.PluginsHelper.findGroovySrcDir;
import static org.gradle.api.plugins.JavaPlugin.ANNOTATION_PROCESSOR_CONFIGURATION_NAME;
import static org.gradle.api.plugins.JavaPlugin.API_CONFIGURATION_NAME;
import static org.gradle.api.plugins.JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME;
import static org.gradle.api.plugins.JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME;
import static org.gradle.api.plugins.JavaPlugin.TEST_ANNOTATION_PROCESSOR_CONFIGURATION_NAME;
import static org.gradle.api.plugins.JavaPlugin.TEST_COMPILE_ONLY_CONFIGURATION_NAME;

/**
 * A base plugin which configures Micronaut components, which are either a Micronaut
 * library, or a Micronaut application.
 */
public class MicronautComponentPlugin implements Plugin<Project> {
    private static final List<String> SOURCESETS = List.of(
            SourceSet.MAIN_SOURCE_SET_NAME,
            SourceSet.TEST_SOURCE_SET_NAME
    );
    private static final Set<String> CONFIGURATIONS_TO_APPLY_BOMS = Set.of(
        ANNOTATION_PROCESSOR_CONFIGURATION_NAME,
        TEST_ANNOTATION_PROCESSOR_CONFIGURATION_NAME,
        API_CONFIGURATION_NAME,
        IMPLEMENTATION_CONFIGURATION_NAME,
        COMPILE_ONLY_CONFIGURATION_NAME
    );
    public static final String MICRONAUT_BOMS_CONFIGURATION = "micronautBoms";
    public static final String INSPECT_RUNTIME_CLASSPATH_TASK_NAME = "inspectRuntimeClasspath";

    @Override
    public void apply(Project project) {
        PluginManager plugins = project.getPluginManager();
        plugins.apply(MicronautBasePlugin.class);
        MicronautExtension micronautExtension = project.getExtensions().getByType(MicronautExtension.class);
        TaskContainer tasks = project.getTasks();
        TaskProvider<ApplicationClasspathInspector> inspectRuntimeClasspath = registerInspectRuntimeClasspath(project, tasks);

        configureJava(project, tasks);

        configureGroovy(project, tasks, micronautExtension);

        MicronautKotlinSupport.whenKotlinSupportPresent(project, MicronautKotlinSupport::configureKotlin);

        configureMicronautBom(project, micronautExtension);

        configureTesting(project, micronautExtension, inspectRuntimeClasspath);

        ShadowPluginSupport.withShadowPlugin(project, () -> {
            configureTesting(project, micronautExtension, inspectRuntimeClasspath);
            ShadowPluginSupport.mergeServiceFiles(project);
        });
        PluginsHelper.registerVersionExtensions(PluginsHelper.KNOWN_VERSION_PROPERTIES, project);
    }

    private void configureTesting(Project project, MicronautExtension micronautExtension, TaskProvider<ApplicationClasspathInspector> inspectRuntimeClasspath) {
        project.getTasks().withType(Test.class).configureEach(t -> {
            t.dependsOn(inspectRuntimeClasspath);
            if (micronautExtension.getTestRuntime().get().isUsingJunitPlatform()) {
                t.useJUnitPlatform();
            }
        });
        project.afterEvaluate(p -> {
            DependencyHandler dependencyHandler = project.getDependencies();
            MicronautTestRuntime testRuntime = micronautExtension.getTestRuntime().get();

            testRuntime.getDependencies().forEach((scope, dependencies) -> {
                for (String dependency : dependencies) {
                    dependencyHandler.add(scope, dependency);
                }
            });

            PluginsHelper.applyAdditionalProcessors(
                    p,
                    ANNOTATION_PROCESSOR_CONFIGURATION_NAME,
                    TEST_ANNOTATION_PROCESSOR_CONFIGURATION_NAME
            );
        });


    }

    private void configureMicronautBom(Project project, MicronautExtension micronautExtension) {
        Configuration micronautBoms = project.getConfigurations().getByName(MICRONAUT_BOMS_CONFIGURATION);
        PluginsHelper.maybeAddMicronautPlaformBom(project, micronautBoms);
        var registry = project.getExtensions().getByType(SourceSetConfigurerRegistry.class);
        var knownSourceSets = new HashSet<SourceSet>();
        registry.register(sourceSet -> {
            configureSourceSet(project, sourceSet, micronautBoms);
            knownSourceSets.add(sourceSet);
        });
        project.afterEvaluate(p -> {
            project.getConfigurations().configureEach(conf -> {
                if (CONFIGURATIONS_TO_APPLY_BOMS.contains(conf.getName())) {
                    conf.extendsFrom(micronautBoms);
                }
            });
            var additionalSourceSets =
                    micronautExtension.getProcessing().getAdditionalSourceSets();
            if (additionalSourceSets.isPresent()) {
                List<SourceSet> configurations = additionalSourceSets.get();
                if (!configurations.isEmpty()) {
                    for (SourceSet sourceSet : configurations) {
                        if (!knownSourceSets.contains(sourceSet)) {
                            AnnotationProcessing.showAdditionalSourceSetDeprecationWarning(sourceSet);
                            configureSourceSet(project, sourceSet, micronautBoms);
                        }
                    }
                }
            }
        });
    }

    private static void configureSourceSet(Project project, SourceSet sourceSet, Configuration micronautBoms) {
        String annotationProcessorConfigurationName = sourceSet
                .getAnnotationProcessorConfigurationName();
        String implementationConfigurationName = sourceSet
                .getImplementationConfigurationName();
        List<String> both = List.of(
                implementationConfigurationName,
                annotationProcessorConfigurationName
        );
        for (String configuration : both) {
            Configuration conf = project.getConfigurations().findByName(configuration);
            if (conf != null) {
                conf.extendsFrom(micronautBoms);
            }
        }
        configureAnnotationProcessors(project,
                implementationConfigurationName,
                annotationProcessorConfigurationName);
    }


    private void configureJava(Project project, TaskContainer tasks) {

        project.afterEvaluate(p -> {
            var sourceSets = PluginsHelper.findSourceSets(p);
            for (String sourceSetName : SOURCESETS) {
                SourceSet sourceSet = sourceSets.findByName(sourceSetName);
                if (sourceSet != null) {
                    String implementationScope;

                    String apiConfigurationName = sourceSet.getApiConfigurationName();
                    Configuration c = p.getConfigurations().findByName(apiConfigurationName);
                    if (c != null) {
                        implementationScope = apiConfigurationName;
                    } else {
                        implementationScope = sourceSet.getImplementationConfigurationName();
                    }

                    String annotationProcessorConfigurationName =
                            sourceSet.getAnnotationProcessorConfigurationName();

                    configureAnnotationProcessors(
                            project,
                            implementationScope,
                            annotationProcessorConfigurationName
                    );
                }
            }

            tasks.withType(JavaCompile.class).configureEach(javaCompile -> {
                final List<String> compilerArgs = javaCompile.getOptions().getCompilerArgs();
                final MicronautExtension micronautExtension = p.getExtensions().getByType(MicronautExtension.class);
                final AnnotationProcessing processing = micronautExtension.getProcessing();
                final boolean isIncremental = processing.getIncremental().getOrElse(true);
                final String group = processing.getGroup().getOrElse(p.getGroup().toString());
                final String module = processing.getModule().getOrElse(p.getName());

                compilerArgs.add("-parameters");
                if (isIncremental) {
                    final List<String> annotations = processing.getAnnotations().getOrElse(Collections.emptyList());
                    compilerArgs.add("-Amicronaut.processing.incremental=true");
                    if (!annotations.isEmpty()) {
                        compilerArgs.add("-Amicronaut.processing.annotations=" + String.join(",", annotations));
                    } else {
                        if (!group.isEmpty()) {
                            compilerArgs.add("-Amicronaut.processing.annotations=" + group + ".*");
                        }
                    }
                }

                if (!group.isEmpty()) {
                    compilerArgs.add("-Amicronaut.processing.group=" + group);
                    compilerArgs.add("-Amicronaut.processing.module=" + module);
                }
            });
        });

    }


    private void configureGroovy(Project project, TaskContainer tasks, MicronautExtension micronautExtension) {
        project.getPluginManager().withPlugin("groovy", plugin -> {
            tasks.withType(GroovyCompile.class).configureEach(groovyCompile -> groovyCompile.getGroovyOptions().setParameters(true));
            var javaPluginExtension = PluginsHelper.javaPluginExtensionOf(project);
            configureDefaultGroovySourceSet(
                    project,
                    javaPluginExtension,
                    COMPILE_ONLY_CONFIGURATION_NAME,
                    "main"
            );
            configureDefaultGroovySourceSet(
                    project,
                    javaPluginExtension,
                    TEST_COMPILE_ONLY_CONFIGURATION_NAME,
                    "test"
            );
            project.afterEvaluate(p -> {
                DependencyHandler dependencyHandler = project.getDependencies();

                for (String defaultSourceSetName : SOURCESETS) {
                    var sourceSet = PluginsHelper.findSourceSets(p).findByName(defaultSourceSetName);
                    if (sourceSet != null) {
                        String configName = sourceSet.getCompileOnlyConfigurationName();
                        Optional<File> groovySrcDir = findGroovySrcDir(sourceSet);
                        if (groovySrcDir.isPresent()) {
                            dependencyHandler.add(
                                    configName,
                                    "io.micronaut:micronaut-inject-groovy"
                            );
                        }
                    }
                }
                @SuppressWarnings("deprecation")
                var additionalSourceSets = micronautExtension.getProcessing().getAdditionalSourceSets();
                if (additionalSourceSets.isPresent()) {
                    List<SourceSet> sourceSets = additionalSourceSets.get();
                    for (SourceSet sourceSet : sourceSets) {
                        String configName = sourceSet.getCompileOnlyConfigurationName();
                        Optional<File> groovySrcDir = findGroovySrcDir(sourceSet);
                        if (groovySrcDir.isPresent()) {
                            dependencyHandler.add(
                                    configName,
                                    "io.micronaut:micronaut-inject-groovy"
                            );
                            PluginsHelper.applyAdditionalProcessors(project, configName);
                        }
                    }
                }
            });

        });

    }


    private void configureDefaultGroovySourceSet(Project p,
                                                 JavaPluginExtension javaPluginExtension,
                                                 String scope,
                                                 String sourceSetName) {
        SourceSet groovySourceSet = javaPluginExtension.getSourceSets().findByName(sourceSetName);
        if (groovySourceSet != null) {
            Optional<File> groovySrc = findGroovySrcDir(groovySourceSet);
            groovySrc.ifPresent((f -> applyAdditionalProcessors(p, scope)));
        }
    }

    private static TaskProvider<ApplicationClasspathInspector> registerInspectRuntimeClasspath(Project project, TaskContainer tasks) {
        return tasks.register(INSPECT_RUNTIME_CLASSPATH_TASK_NAME, ApplicationClasspathInspector.class, task -> {
            var javaPluginExtension = PluginsHelper.javaPluginExtensionOf(project);
            task.setGroup(BasePlugin.BUILD_GROUP);
            task.setDescription("Performs sanity checks of the runtime classpath to warn about misconfigured builds");
            // Sort the runtimeClasspath before setting it since the order doesn't actually matter for inspection, however it does matter for caching.
            task.getRuntimeClasspath().from(project.getConfigurations()
                    .getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME).resolve()
                    .stream().sorted().toList());
            task.getResources().from(PluginsHelper.findSourceSets(project).getByName(SourceSet.MAIN_SOURCE_SET_NAME).getResources());
            task.getReportFile().set(project.getLayout().getBuildDirectory().file("reports/inspectRuntimeClasspath.txt"));
        });
    }

}
