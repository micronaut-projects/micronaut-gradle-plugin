/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.gradle.configuration.validation;

import io.micronaut.gradle.DefaultVersions;
import io.micronaut.gradle.MicronautBasePlugin;
import io.micronaut.gradle.PluginsHelper;
import io.micronaut.gradle.configuration.validation.tasks.ConfigurationValidationReportTask;
import io.micronaut.gradle.configuration.validation.tasks.ConfigurationValidationTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.FileCollection;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.testing.Test;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Registers cacheable Micronaut configuration validation tasks and wires them into common lifecycle tasks.
 *
 * @since 5.0.0
 */
public class MicronautConfigurationValidationPlugin implements Plugin<Project> {

    /**
     * Default constructor.
     */
    public MicronautConfigurationValidationPlugin() {
    }

    /**
     * Declarative configuration used to declare validator dependencies.
     */
    public static final String CONFIGURATION_VALIDATOR = "configurationValidator";

    /**
     * Resolvable configuration used as validator tool classpath.
     */
    public static final String CONFIGURATION_VALIDATOR_CLASSPATH = "configurationValidatorClasspath";

    /**
     * Resolvable configuration used as validator tool classpath for production/run.
     * Extends the project's {@code runtimeClasspath}.
     */
    public static final String CONFIGURATION_VALIDATOR_RUNTIME_CLASSPATH = "configurationValidatorRuntimeClasspath";

    /**
     * Resolvable configuration used as validator tool classpath for test.
     * Extends the project's {@code testRuntimeClasspath}.
     */
    public static final String CONFIGURATION_VALIDATOR_TEST_CLASSPATH = "configurationValidatorTestClasspath";

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(MicronautBasePlugin.class);

        Configuration validatorDependencies = project.getConfigurations().create(CONFIGURATION_VALIDATOR, conf -> {
            conf.setCanBeResolved(false);
            conf.setCanBeConsumed(false);
            conf.setDescription("The Micronaut configuration validator dependencies");
        });
        Configuration validatorClasspath = project.getConfigurations().create(CONFIGURATION_VALIDATOR_CLASSPATH, conf -> {
            conf.setCanBeResolved(true);
            conf.setCanBeConsumed(false);
            conf.setDescription("The Micronaut configuration validator tool classpath");
            conf.extendsFrom(validatorDependencies);
        });

        var micronautExtension = PluginsHelper.findMicronautExtension(project);
        DefaultConfigurationValidationExtension extension = micronautExtension.getExtensions().create(
            "configurationValidation",
            DefaultConfigurationValidationExtension.class,
            project
        );
        extension.getVersion().convention(DefaultVersions.JSONSCHEMA);

        validatorDependencies.getDependencies().addAllLater(extension.getVersion().map(version ->
            List.of(project.getDependencies().create("io.micronaut.jsonschema:micronaut-json-schema-configuration-validator:" + version))
        ));

        project.getPluginManager().withPlugin("java", unused -> registerJavaTasks(project, extension, validatorClasspath));
    }

    private static void registerJavaTasks(Project project,
                                          ConfigurationValidationExtension extension,
                                          Configuration validatorClasspath) {
        SourceSetContainer sourceSets = PluginsHelper.findSourceSets(project);
        if (sourceSets == null) {
            return;
        }

        // Build scenario-specific validator classpaths that also include the project's runtime classpaths.
        Configuration runtimeValidatorClasspath = project.getConfigurations().create(CONFIGURATION_VALIDATOR_RUNTIME_CLASSPATH, conf -> {
            conf.setCanBeResolved(true);
            conf.setCanBeConsumed(false);
            conf.setDescription("The Micronaut configuration validator classpath (validator + runtimeClasspath)");
            conf.extendsFrom(validatorClasspath);
            conf.extendsFrom(project.getConfigurations().getByName("runtimeClasspath"));
        });
        Configuration testValidatorClasspath = project.getConfigurations().create(CONFIGURATION_VALIDATOR_TEST_CLASSPATH, conf -> {
            conf.setCanBeResolved(true);
            conf.setCanBeConsumed(false);
            conf.setDescription("The Micronaut configuration validator classpath (validator + testRuntimeClasspath)");
            conf.extendsFrom(validatorClasspath);
            conf.extendsFrom(project.getConfigurations().getByName("testRuntimeClasspath"));
        });
        SourceSet main = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        SourceSet test = sourceSets.findByName(SourceSet.TEST_SOURCE_SET_NAME);

        configureScenarioDefaults(project, extension, main, test);

        var productionReport = registerReportTask(project, "configurationValidationReport", "Validates production configuration and generates reports", extension, extension.getProduction(), runtimeValidatorClasspath, defaultProductionClasspath(project, main));
        var productionCheck = registerCheckTask(project, "configurationValidation", "Fails the build if production configuration is invalid", extension, extension.getProduction(), productionReport);

        productionReport.configure(t -> t.dependsOn(main.getProcessResourcesTaskName(), main.getClassesTaskName()));

        // Wire into lifecycle tasks.
        project.getTasks().named(LifecycleBasePlugin.CHECK_TASK_NAME).configure(t -> t.dependsOn(productionCheck));
        project.getTasks().named(LifecycleBasePlugin.BUILD_TASK_NAME).configure(t -> t.dependsOn(productionCheck));
        project.getTasks().named(LifecycleBasePlugin.ASSEMBLE_TASK_NAME).configure(t -> t.dependsOn(productionCheck));

        var runReport = registerReportTask(project, "runConfigurationValidationReport", "Validates development configuration and generates reports", extension, extension.getRun(), runtimeValidatorClasspath, defaultRunClasspath(project, main));
        var runCheck = registerCheckTask(project, "runConfigurationValidation", "Fails the build if development configuration is invalid", extension, extension.getRun(), runReport);
        runReport.configure(t -> t.dependsOn(main.getProcessResourcesTaskName(), main.getClassesTaskName()));
        project.getPluginManager().withPlugin("application", unused -> project.getTasks().named("run").configure(t -> t.dependsOn(runCheck)));
        project.getPluginManager().withPlugin("java-application", unused -> project.getTasks().named("run").configure(t -> t.dependsOn(runCheck)));

        if (test != null) {
            var testReport = registerReportTask(project, "testConfigurationValidationReport", "Validates test configuration and generates reports", extension, extension.getTest(), testValidatorClasspath, defaultTestClasspath(project, main, test));
            var testCheck = registerCheckTask(project, "testConfigurationValidation", "Fails the build if test configuration is invalid", extension, extension.getTest(), testReport);
            testReport.configure(t -> t.dependsOn(test.getProcessResourcesTaskName(), test.getClassesTaskName()));
            project.getTasks().withType(Test.class).configureEach(t -> t.dependsOn(testCheck));
        }
    }

    private static void configureScenarioDefaults(Project project,
                                                  ConfigurationValidationExtension extension,
                                                  SourceSet main,
                                                  SourceSet test) {
        // Scenario output directories.
        extension.getRun().getOutputDirectory().convention(extension.getOutputDirectory().dir("run"));
        extension.getTest().getOutputDirectory().convention(extension.getOutputDirectory().dir("test"));
        extension.getProduction().getOutputDirectory().convention(extension.getOutputDirectory().dir("production"));

        // Default resource dirs for origin rewriting.
        extension.getRun().getResourcesDirs().convention(relativeResourceDirs(project, main));
        if (test != null) {
            extension.getTest().getResourcesDirs().convention(relativeResourceDirs(project, main, test));
        }
        extension.getProduction().getResourcesDirs().convention(relativeResourceDirs(project, main));
    }

    private static List<String> relativeResourceDirs(Project project, SourceSet... sourceSets) {
        Path baseDir = project.getProjectDir().toPath();
        List<String> dirs = new ArrayList<>();
        for (SourceSet ss : sourceSets) {
            for (File dir : ss.getResources().getSrcDirs()) {
                Path p = dir.toPath();
                if (p.isAbsolute() && p.startsWith(baseDir)) {
                    dirs.add(baseDir.relativize(p).toString().replace('\\', '/'));
                } else {
                    dirs.add(p.toString().replace('\\', '/'));
                }
            }
        }
        return dirs;
    }

    private static TaskProvider<ConfigurationValidationReportTask> registerReportTask(Project project,
                                                                                     String name,
                                                                                     String description,
                                                                                     ConfigurationValidationExtension extension,
                                                                                     ConfigurationValidationScenario scenario,
                                                                                     Configuration validatorClasspath,
                                                                                     FileCollection defaultClasspath) {
        return project.getTasks().register(name, ConfigurationValidationReportTask.class, task -> {
            task.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
            task.setDescription(description);
            task.setEnabled(true);

            task.getEnabledFlag().convention(extension.getEnabled().zip(scenario.getEnabled(), (g, s) -> g && s));

            task.getValidatorClasspath().from(validatorClasspath);
            task.getOutputDirectory().convention(scenario.getOutputDirectory());
            task.getResultFile().convention(task.getOutputDirectory().file("result.properties"));

            task.getEnvironments().convention(scenario.getEnvironments());
            task.getFailOnNotPresent().convention(scenario.getFailOnNotPresent());
            task.getDeduceEnvironments().convention(scenario.getDeduceEnvironments());
            task.getFormat().convention(scenario.getFormat());

            task.getSuppressions().set(extension.getSuppressions().zip(scenario.getSuppressions(), (global, local) -> {
                var out = new ArrayList<>(global);
                out.addAll(local);
                return out;
            }));

            task.getProjectBaseDir().convention(extension.getProjectBaseDir());
            task.getProjectBaseDirPath().convention(extension.getProjectBaseDir().map(d -> d.getAsFile().getAbsolutePath()));
            task.getResourcesDirs().convention(scenario.getResourcesDirs());
            task.getOverrideClasspath().convention(scenario.getOverrideClasspath());

            task.getJavaLauncher().convention(extension.getJavaLauncher());
            task.getJavaExecutable().convention(extension.getJavaExecutable());

            task.getApplicationClasspath().from(project.provider(() -> {
                if (task.getOverrideClasspath().getOrElse(false)) {
                    return scenario.getClasspath();
                }
                return defaultClasspath.plus(scenario.getClasspath());
            }));
        });
    }

    private static TaskProvider<ConfigurationValidationTask> registerCheckTask(Project project,
                                                                              String name,
                                                                              String description,
                                                                              ConfigurationValidationExtension extension,
                                                                              ConfigurationValidationScenario scenario,
                                                                              TaskProvider<ConfigurationValidationReportTask> reportTask) {
        return project.getTasks().register(name, ConfigurationValidationTask.class, task -> {
            task.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
            task.setDescription(description);
            task.dependsOn(reportTask);
            task.getEnabledFlag().convention(extension.getEnabled().zip(scenario.getEnabled(), (g, s) -> g && s));
            task.getResultFile().convention(reportTask.flatMap(ConfigurationValidationReportTask::getResultFile));
            task.getOutputDirectory().convention(reportTask.flatMap(ConfigurationValidationReportTask::getOutputDirectory));
            task.getFormat().convention(reportTask.flatMap(ConfigurationValidationReportTask::getFormat));
        });
    }

    private static FileCollection defaultRunClasspath(Project project, SourceSet main) {
        return project.files(main.getOutput(), main.getResources().getSrcDirs());
    }

    private static FileCollection defaultProductionClasspath(Project project, SourceSet main) {
        return project.files(main.getOutput(), main.getResources().getSrcDirs());
    }

    private static FileCollection defaultTestClasspath(Project project, SourceSet main, SourceSet test) {
        return project.files(main.getOutput(), test.getOutput(), main.getResources().getSrcDirs(), test.getResources().getSrcDirs());
    }
}
