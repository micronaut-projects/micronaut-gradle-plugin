/*
 * Copyright 2003-2021 the original author or authors.
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

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.plugins.PluginManager;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.compile.GroovyCompile;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.testing.Test;

import java.io.File;
import java.util.Arrays;
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
import static org.gradle.api.plugins.JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME;

/**
 * A base plugin which configures Micronaut components, which are either a Micronaut
 * library, or a Micronaut application.
 */
public class MicronautComponentPlugin implements Plugin<Project> {
    private final static List<String> SOURCESETS = Arrays.asList(
            SourceSet.MAIN_SOURCE_SET_NAME,
            SourceSet.TEST_SOURCE_SET_NAME
    );
    private final static Set<String> CONFIGURATIONS_TO_APPLY_BOMS = Collections.unmodifiableSet(new HashSet<String>() {{
        add(ANNOTATION_PROCESSOR_CONFIGURATION_NAME);
        add(TEST_ANNOTATION_PROCESSOR_CONFIGURATION_NAME);
        add(API_CONFIGURATION_NAME);
        add(IMPLEMENTATION_CONFIGURATION_NAME);
        add(COMPILE_ONLY_CONFIGURATION_NAME);
    }});
    public static final String MICRONAUT_BOMS_CONFIGURATION = "micronautBoms";

    @Override
    public void apply(Project project) {
        PluginManager plugins = project.getPluginManager();
        plugins.apply(MicronautBasePlugin.class);
        MicronautExtension micronautExtension = project.getExtensions().getByType(MicronautExtension.class);
        TaskContainer tasks = project.getTasks();

        configureJava(project, tasks);

        configureGroovy(project, tasks, micronautExtension);

        if (MicronautKotlinSupport.isKotlinSupportPresent()) {
            MicronautKotlinSupport.configureKotlin(project);
        }

        configureMicronautBom(project, micronautExtension);

        configureTesting(project, micronautExtension);

        configureShadow(tasks);
    }

    private void configureShadow(TaskContainer tasks) {

        // If shadow JAR is enabled it must be configured to merge
        // all META-INF/services file into a single file otherwise this
        // will break the application
        tasks.withType(ShadowJar.class).configureEach(ShadowJar::mergeServiceFiles);

    }

    private void configureTesting(Project project, MicronautExtension micronautExtension) {
        project.afterEvaluate(p -> {
            DependencyHandler dependencyHandler = project.getDependencies();
            MicronautTestRuntime testRuntime = micronautExtension.getTestRuntime().get();

            testRuntime.getDependencies().forEach((scope, dependencies) -> {
                for (String dependency : dependencies) {
                    dependencyHandler.add(scope, dependency);
                }
            });

            if (testRuntime != MicronautTestRuntime.NONE) {
                // set JUnit 5 platform
                project.getTasks().withType(Test.class, test -> {
                    if (!test.getTestFramework().getClass().getName().contains("JUnitPlatform")) {
                        test.useJUnitPlatform();
                    }
                });
            }

            PluginsHelper.applyAdditionalProcessors(
                    p,
                    ANNOTATION_PROCESSOR_CONFIGURATION_NAME,
                    TEST_ANNOTATION_PROCESSOR_CONFIGURATION_NAME
            );

            Configuration testConfig = p.getConfigurations().getByName(TEST_IMPLEMENTATION_CONFIGURATION_NAME);
            boolean hasJunit5 = !testConfig.getAllDependencies()
                    .matching(dependency -> {
                        String name = dependency.getName();
                        return name.equals("junit-jupiter-engine") || name.equals("micronaut-test-junit5");
                    })
                    .isEmpty();
            if (hasJunit5) {
                project.getTasks().withType(Test.class, test -> {
                    if (!test.getTestFramework().getClass().getName().contains("JUnitPlatform")) {
                        test.useJUnitPlatform();
                    }
                });
            }
        });


    }

    private void configureMicronautBom(Project project, MicronautExtension micronautExtension) {
        Configuration micronautBoms = project.getConfigurations().create(MICRONAUT_BOMS_CONFIGURATION, conf -> {
            conf.setCanBeResolved(false);
            conf.setCanBeConsumed(false);
            conf.setDescription("BOMs which will be applied by the Micronaut plugins");
        });
        DependencyHandler dependencyHandler = project.getDependencies();
        dependencyHandler.addProvider(micronautBoms.getName(), project.getProviders().provider(() -> {
            String micronautVersion = PluginsHelper.findMicronautVersion(project, micronautExtension);
            return resolveMicronautPlatform(dependencyHandler, micronautVersion);
        }));
        project.getConfigurations().configureEach(conf -> {
            if (CONFIGURATIONS_TO_APPLY_BOMS.contains(conf.getName())) {
                conf.extendsFrom(micronautBoms);
            }
        });
        project.afterEvaluate(p -> {
            ListProperty<SourceSet> additionalSourceSets =
                    micronautExtension.getProcessing().getAdditionalSourceSets();

            if (additionalSourceSets.isPresent()) {
                List<SourceSet> configurations = additionalSourceSets.get();
                if (!configurations.isEmpty()) {
                    for (SourceSet sourceSet : configurations) {
                        String annotationProcessorConfigurationName = sourceSet
                                .getAnnotationProcessorConfigurationName();
                        String implementationConfigurationName = sourceSet
                                .getImplementationConfigurationName();
                        List<String> both = Arrays.asList(
                                implementationConfigurationName,
                                annotationProcessorConfigurationName
                        );
                        for (String configuration : both) {
                            Configuration conf = project.getConfigurations().findByName(configuration);
                            if (conf != null) {
                                conf.extendsFrom(micronautBoms);
                            }
                        }
                        configureAnnotationProcessors(p,
                                implementationConfigurationName,
                                annotationProcessorConfigurationName);
                    }
                }
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

    private void configureJava(Project project, TaskContainer tasks) {

        project.afterEvaluate(p -> {
            SourceSetContainer sourceSets = p.getConvention().getPlugin(JavaPluginConvention.class)
                    .getSourceSets();
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
                        if (group.length() > 0) {
                            compilerArgs.add("-Amicronaut.processing.annotations=" + group + ".*");
                        }
                    }
                }

                if (group.length() > 0) {
                    compilerArgs.add("-Amicronaut.processing.group=" + group);
                    compilerArgs.add("-Amicronaut.processing.module=" + module);
                }
            });
        });

    }


    private void configureGroovy(Project project, TaskContainer tasks, MicronautExtension micronautExtension) {
        project.getPluginManager().withPlugin("groovy", plugin -> {
            tasks.withType(GroovyCompile.class).configureEach(groovyCompile -> groovyCompile.getGroovyOptions().setParameters(true));
            JavaPluginConvention convention = project.getConvention().getPlugin(JavaPluginConvention.class);
            configureDefaultGroovySourceSet(
                    project,
                    convention,
                    COMPILE_ONLY_CONFIGURATION_NAME,
                    "main"
            );
            configureDefaultGroovySourceSet(
                    project,
                    convention,
                    TEST_COMPILE_ONLY_CONFIGURATION_NAME,
                    "test"
            );
            project.afterEvaluate(p -> {
                DependencyHandler dependencyHandler = project.getDependencies();

                for (String defaultSourceSetName : SOURCESETS) {
                    SourceSet sourceSet = p.getConvention().getPlugin(JavaPluginConvention.class)
                            .getSourceSets()
                            .findByName(defaultSourceSetName);
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
                ListProperty<SourceSet> additionalSourceSets = micronautExtension.getProcessing().getAdditionalSourceSets();
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
                                                 JavaPluginConvention plugin,
                                                 String scope,
                                                 String sourceSetName) {
        SourceSet groovySourceSet = plugin.getSourceSets().findByName(sourceSetName);
        if (groovySourceSet != null) {
            Optional<File> groovySrc = findGroovySrcDir(groovySourceSet);
            groovySrc.ifPresent((f -> applyAdditionalProcessors(p, scope)));
        }
    }


}
