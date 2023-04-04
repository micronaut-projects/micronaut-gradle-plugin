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

import io.micronaut.gradle.testresources.MicronautTestResourcesPlugin;
import io.micronaut.gradle.testresources.StartTestResourcesService;
import org.graalvm.buildtools.gradle.dsl.GraalVMExtension;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.TaskProvider;

/**
 * Methods for Micronaut GraalVM plugin integration,
 * separated to make sure we don't run into classloading
 * issues.
 */
public final class TestResourcesGraalVM {
    public static final String ENABLED_PROPERTY_NAME = "testresources.native";

    public static void configure(Project project,
                                 Configuration client,
                                 TaskProvider<StartTestResourcesService> internalStart) {
        GraalVMExtension graalVMExtension = project.getExtensions().findByType(GraalVMExtension.class);
        graalVMExtension.getBinaries().all(b -> {
            b.getRuntimeArgs().addAll(internalStart.map(task -> {
                MicronautTestResourcesPlugin.ServerConnectionParametersProvider provider = new MicronautTestResourcesPlugin.ServerConnectionParametersProvider(internalStart);
                return provider.asArguments();
            }));
        });
        ProviderFactory providers = project.getProviders();
        boolean includeClient = Boolean.TRUE.equals(providers
                .systemProperty(ENABLED_PROPERTY_NAME)
                .orElse(providers.gradleProperty(ENABLED_PROPERTY_NAME))
                .map(s -> {
                    if (s.equals("")) {
                        // if the property is set without value, consider it's true
                        return "true";
                    }
                    return "false";
                })
                .orElse("false")
                .map(Boolean::parseBoolean)
                .getOrElse(false));
        if (includeClient) {
            Configuration runtimeClasspath = project.getConfigurations().getByName("runtimeOnly");
            runtimeClasspath.extendsFrom(client);
        }
    }
}
