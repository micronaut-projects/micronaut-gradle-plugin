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
import io.micronaut.gradle.MicronautExtension;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.attributes.Usage;
import org.gradle.api.plugins.PluginManager;

import java.util.Collections;

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
        TestResourcesConsumerConfiguration testResourcesExtension = createTestResourcesExtension(project);
        pluginManager.withPlugin("io.micronaut.component", unused -> {
            project.afterEvaluate(p -> p.getConfigurations().all(cnf -> configureDependencies(p, testResourcesExtension, project.getDependencies(), cnf)));
        });
    }

    private TestResourcesConsumerConfiguration createTestResourcesExtension(Project project) {
        MicronautExtension micronautExtension = project.getExtensions().findByType(MicronautExtension.class);
        return micronautExtension.getExtensions().create("testResources", TestResourcesConsumerConfiguration.class);
    }

    private void configureDependencies(Project project, TestResourcesConsumerConfiguration config, DependencyHandler dependencies, Configuration conf) {
        String name = conf.getName();
        if ("developmentOnly".equals(name) || "testRuntimeOnly".equals(name)) {
            ProjectDependency dependency = (ProjectDependency) dependencies.project(Collections.singletonMap("path", config.getTargetProject().get()));
            dependency.attributes(attrs -> attrs.attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, MicronautTestResourcesPlugin.MICRONAUT_TEST_RESOURCES_USAGE)));
            conf.getDependencies().add(dependency);
        }
    }

}
