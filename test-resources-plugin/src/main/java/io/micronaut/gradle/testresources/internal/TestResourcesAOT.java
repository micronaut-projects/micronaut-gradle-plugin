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
package io.micronaut.gradle.testresources.internal;

import io.micronaut.gradle.MicronautComponentPlugin;
import io.micronaut.gradle.aot.AOTExtension;
import io.micronaut.gradle.aot.MicronautAotPlugin;
import io.micronaut.gradle.testresources.MicronautTestResourcesPlugin;
import io.micronaut.gradle.testresources.StartTestResourcesService;
import io.micronaut.gradle.testresources.TestResourcesConfiguration;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;

import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Methods for Micronaut AOT plugin integration,
 * separated to make sure we don't run into classloading
 * issues.
 */
public final class TestResourcesAOT {

    public static void configure(Project project, TestResourcesConfiguration config, DependencyHandler dependencies, TaskContainer tasks, TaskProvider<StartTestResourcesService> internalStart, Configuration testResourcesClasspathConfig) {
        AOTExtension aot = MicronautComponentPlugin.findMicronautExtension(project).getExtensions().getByType(AOTExtension.class);
        Configuration aotAppClasspath = project.getConfigurations().getByName(MicronautAotPlugin.AOT_APPLICATION_CLASSPATH);
        MicronautTestResourcesPlugin.addTestResourcesClientDependencies(project, config, dependencies, internalStart, aotAppClasspath);
        project.afterEvaluate(p -> {
            MapProperty<String, String> props = aot.getConfigurationProperties();
            if (props.get().containsKey("service.types")) {
                props.put("service.types", props.get().get("service.types") + ",io.micronaut.testresources.core.TestResourcesResolver");
            } else {
                props.put("service.types", Stream.concat(
                        Stream.of("io.micronaut.testresources.core.TestResourcesResolver"),
                        MicronautAotPlugin.SERVICE_TYPES.stream()
                ).collect(Collectors.joining(",")));
            }
            tasks.named("optimizedRun", JavaExec.class, javaExec ->
                    javaExec.setClasspath(javaExec.getClasspath().plus(testResourcesClasspathConfig))
            );
        });
    }

}
