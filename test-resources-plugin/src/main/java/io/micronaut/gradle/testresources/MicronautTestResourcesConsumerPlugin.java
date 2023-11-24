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
package io.micronaut.gradle.testresources;

import io.micronaut.gradle.MicronautBasePlugin;
import io.micronaut.gradle.PluginsHelper;
import org.gradle.api.DefaultTask;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.plugins.PluginManager;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.testing.Test;

import javax.inject.Inject;

import static io.micronaut.gradle.MicronautComponentPlugin.MICRONAUT_BOMS_CONFIGURATION;

/**
 * A lightweight test resources plugin, which requires
 * that another project of the build actually applies
 * the {@link MicronautTestResourcesPlugin}: this plugin
 * will simply configure builds so that it uses the
 * test resources plugin from another project.
 */
public class MicronautTestResourcesConsumerPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        PluginManager pluginManager = project.getPluginManager();
        pluginManager.apply(MicronautBasePlugin.class);
        Configuration testResourcesConfiguration = createTestResourcesExtension(project);
        pluginManager.withPlugin("io.micronaut.component", unused ->
                project.afterEvaluate(p -> p.getConfigurations().all(cnf -> configureDependencies(testResourcesConfiguration, cnf)))
        );
    }

    private Configuration createTestResourcesExtension(Project project) {
        ConfigurationContainer configurations = project.getConfigurations();
        Configuration boms = configurations.findByName(MICRONAUT_BOMS_CONFIGURATION);
        PluginsHelper.maybeAddMicronautPlaformBom(project, boms);
        var testResourcesConfiguration = project.getConfigurations().create(MicronautTestResourcesPlugin.TESTRESOURCES_CONFIGURATION, conf -> {
            conf.extendsFrom(boms);
            conf.setCanBeConsumed(false);
            conf.setCanBeResolved(false);
            conf.setDescription("Used to declare projects which provide test resources");
            conf.getDependencies().whenObjectAdded(dep -> {
                if (dep instanceof ProjectDependency projectDependency) {
                    projectDependency.attributes(attrs -> configureUsageAttribute(project, attrs));
                } else {
                    throw new InvalidUserDataException("The test resources configuration can only contain project dependencies");
                }
            });
        });
        var testResourcesServerConfig = project.getConfigurations().create("internalTestResourcesServerConfig", conf -> {
            conf.extendsFrom(testResourcesConfiguration);
            conf.setCanBeConsumed(false);
            conf.setCanBeResolved(true);
        });
        var copyServerConfiguration = project.getTasks().register("copyTestResourceServerConfig", CopyServerConfiguration.class, t -> {
            t.getFiles().from(testResourcesServerConfig);
            t.getOutputDirectory().convention(project.getLayout().getBuildDirectory().dir("test-resources-server-config"));
        });
        var jvmArgumentsConfiguration = createJvmArgsProvider(copyServerConfiguration);
        project.getTasks().withType(Test.class).configureEach(t ->
                t.getJvmArgumentProviders().add(jvmArgumentsConfiguration)
        );
        project.getPlugins().withId("java-application", unused ->
                project.getTasks().named("run", JavaExec.class, t ->
                        t.getJvmArgumentProviders().add(jvmArgumentsConfiguration)
                )
        );
        return testResourcesConfiguration;
    }

    private static ServerConnectionParametersProvider createJvmArgsProvider(TaskProvider<CopyServerConfiguration> copyServerConfiguration) {
        return new ServerConnectionParametersProvider(copyServerConfiguration.flatMap(CopyServerConfiguration::getOutputDirectory));
    }

    private static AttributeContainer configureUsageAttribute(Project project, AttributeContainer attrs) {
        return attrs.attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, MicronautTestResourcesPlugin.MICRONAUT_TEST_RESOURCES_USAGE));
    }

    private void configureDependencies(Configuration config, Configuration conf) {
        String name = conf.getName();
        if ("developmentOnly".equals(name) || "testRuntimeOnly".equals(name)) {
            conf.extendsFrom(config);
        }
    }

    public abstract static class CopyServerConfiguration extends DefaultTask {
        @InputFiles
        public abstract ConfigurableFileCollection getFiles();

        @OutputDirectory
        public abstract DirectoryProperty getOutputDirectory();

        @Inject
        public abstract FileSystemOperations getFileSystemOperations();

        @TaskAction
        public void copy() {
            getFileSystemOperations().copy(spec -> {
                spec.into(getOutputDirectory());
                spec.from(getFiles());
                spec.include("test-resources.properties");
            });
        }
    }
}
