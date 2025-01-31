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
package io.micronaut.gradle.jsonschema;

import io.micronaut.gradle.DefaultVersions;
import io.micronaut.gradle.MicronautBasePlugin;
import io.micronaut.gradle.PluginsHelper;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

import java.util.List;

public class MicronautJSONSchemaPlugin implements Plugin<Project> {

    public static final String JSON_SCHEMA_GENERATOR_CONFIGURATION = "jsonSchemaGenerator";
    public static final String JSON_SCHEMA_GENERATOR_CLASSPATH_CONFIGURATION = "jsonSchemaGeneratorClasspath";

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(MicronautBasePlugin.class);
        createJSONSchemaExtension(project);
    }

    private static void createJSONSchemaExtension(Project project) {
        var micronautExtension = PluginsHelper.findMicronautExtension(project);
        var generatorDependencies = project.getConfigurations().create(JSON_SCHEMA_GENERATOR_CONFIGURATION, conf -> {
            conf.setCanBeResolved(false);
            conf.setCanBeConsumed(false);
            conf.setDescription("The JSON Schema Generator dependencies");
        });
        var generatorClasspath = project.getConfigurations().create(JSON_SCHEMA_GENERATOR_CLASSPATH_CONFIGURATION, conf -> {
            conf.setCanBeResolved(true);
            conf.setCanBeConsumed(false);
            conf.setDescription("The JSON Schema Generator classpath");
            conf.extendsFrom(generatorDependencies);
        });
        var jsonSchemaExtension = micronautExtension.getExtensions().create(JSONSchemaExtension.class, "jsonschema", DefaultJSONSchemaExtension.class, project, generatorClasspath);
        jsonSchemaExtension.getVersion().convention(DefaultVersions.JSONSCHEMA);
        generatorDependencies.getDependencies().addAllLater(jsonSchemaExtension.getVersion().map(version ->
            List.of(project.getDependencies().create("io.micronaut.jsonschema:micronaut-json-schema-generator:" + version))
        ));
    }
}
