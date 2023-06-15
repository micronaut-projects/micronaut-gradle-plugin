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
package io.micronaut.gradle.openapi;

import io.micronaut.gradle.DefaultVersions;
import io.micronaut.gradle.MicronautBasePlugin;
import io.micronaut.gradle.PluginsHelper;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

import java.util.List;

public class MicronautOpenAPIPlugin implements Plugin<Project> {

    public static final String OPEN_API_GENERATOR_CONFIGURATION = "openApiGenerator";
    public static final String OPEN_API_GENERATOR_CLASSPATH_CONFIGURATION = "openApiGeneratorClasspath";

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(MicronautBasePlugin.class);
        createOpenAPIExtension(project);
    }

    private static void createOpenAPIExtension(Project project) {
        var micronautExtension = PluginsHelper.findMicronautExtension(project);
        var generatorDependencies = project.getConfigurations().create(OPEN_API_GENERATOR_CONFIGURATION, conf -> {
            conf.setCanBeResolved(false);
            conf.setCanBeConsumed(false);
            conf.setDescription("The OpenAPI Generator dependencies");
        });
        var generatorClasspath = project.getConfigurations().create(OPEN_API_GENERATOR_CLASSPATH_CONFIGURATION, conf -> {
            conf.setCanBeResolved(true);
            conf.setCanBeConsumed(false);
            conf.setDescription("The OpenAPI Generator classpath");
            conf.extendsFrom(generatorDependencies);
        });
        var openApiExtension = micronautExtension.getExtensions().create(OpenApiExtension.class, "openapi", DefaultOpenApiExtension.class, project, generatorClasspath);
        openApiExtension.getVersion().convention(DefaultVersions.OPENAPI);
        generatorDependencies.getDependencies().addAllLater(openApiExtension.getVersion().map(version ->
            List.of(project.getDependencies().create("io.micronaut.openapi:micronaut-openapi-generator:" + version))
        ));
    }
}
