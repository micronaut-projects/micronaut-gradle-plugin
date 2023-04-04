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

import io.micronaut.gradle.PluginsHelper;
import io.micronaut.gradle.aot.AOTExtension;
import io.micronaut.gradle.aot.MicronautAotPlugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.provider.MapProperty;

import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Methods for Micronaut AOT plugin integration,
 * separated to make sure we don't run into classloading
 * issues.
 */
public final class TestResourcesAOT {

    public static void configure(Project project, Configuration client) {
        AOTExtension aot = PluginsHelper.findMicronautExtension(project).getExtensions().getByType(AOTExtension.class);
        ConfigurationContainer configurations = project.getConfigurations();
        Configuration aotAppClasspath = configurations.getByName(MicronautAotPlugin.AOT_APPLICATION_CLASSPATH);
        Configuration optimizedRuntimeClasspath = configurations.getByName(MicronautAotPlugin.OPTIMIZED_RUNTIME_CLASSPATH_CONFIGURATION_NAME);
        aotAppClasspath.extendsFrom(client);
        optimizedRuntimeClasspath.extendsFrom(client);
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
        });
    }

}
