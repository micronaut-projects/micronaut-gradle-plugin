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

import io.micronaut.gradle.PluginsHelper;
import io.micronaut.gradle.openapi.tasks.AbstractOpenApiGenerator;
import io.micronaut.gradle.openapi.tasks.OpenApiClientGenerator;
import io.micronaut.gradle.openapi.tasks.OpenApiServerGenerator;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;

import javax.inject.Inject;
import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.codehaus.groovy.runtime.StringGroovyMethods.capitalize;

public abstract class DefaultOpenApiExtension implements OpenApiExtension {
    public static final String OPENAPI_GROUP = "Micronaut OpenAPI";
    // We use a String here because the type is not available at runtime because of classpath isolation
    private static final String DEFAULT_SERIALIZATION_FRAMEWORK = "MICRONAUT_SERDE_JACKSON";

    private final Set<String> names = new HashSet<>();
    private final Project project;
    private final Configuration classpath;

    @Inject
    public DefaultOpenApiExtension(Project project, Configuration classpath) {
        this.project = project;
        this.classpath = classpath;
    }

    @Override
    public void server(File file, Action<? super OpenApiServerSpec> spec) {
        var regularFileProperty = project.getObjects().fileProperty();
        server("server", regularFileProperty.fileValue(file), spec);
    }

    @Override
    public void client(File file, Action<? super OpenApiClientSpec> spec) {
        var regularFileProperty = project.getObjects().fileProperty();
        client("client", regularFileProperty.fileValue(file), spec);
    }

    @Override
    public void server(String name, Provider<RegularFile> definition, Action<? super OpenApiServerSpec> spec) {
        if (names.add(name)) {
            var serverSpec = project.getObjects().newInstance(OpenApiServerSpec.class);
            configureCommonExtensionDefaults(serverSpec);
            serverSpec.getControllerPackage().convention("io.micronaut.openapi.controller");
            serverSpec.getUseAuth().convention(false);
            spec.execute(serverSpec);
            var controllers = project.getTasks().register(generateApisTaskName(name), OpenApiServerGenerator.class, task -> {
                configureCommonProperties(name, task, serverSpec, definition);
                task.setDescription("Generates OpenAPI controllers from an OpenAPI definition");
                configureServerTask(serverSpec, task);
                task.getOutputKinds().addAll("APIS", "SUPPORTING_FILES");
            });
            var models = project.getTasks().register(generateModelsTaskName(name), OpenApiServerGenerator.class, task -> {
                configureCommonProperties(name, task, serverSpec, definition);
                task.setDescription("Generates OpenAPI models from an OpenAPI definition");
                configureServerTask(serverSpec, task);
                task.getOutputKinds().add("MODELS");
            });
            withJavaSourceSets(sourceSets -> {
                var javaMain = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME).getJava();
                javaMain.srcDir(controllers.map(DefaultOpenApiExtension::mainSrcDir));
                javaMain.srcDir(models.map(DefaultOpenApiExtension::mainSrcDir));
            });
        } else {
            throwDuplicateEntryFor(name);
        }

    }

    private void configureCommonExtensionDefaults(OpenApiSpec spec) {
        spec.getApiPackageName().convention("io.micronaut.openapi.api");
        spec.getInvokerPackageName().convention("io.micronaut.openapi.invoker");
        spec.getModelPackageName().convention("io.micronaut.openapi.model");
        spec.getUseBeanValidation().convention(true);
        spec.getUseOptional().convention(false);
        spec.getUseReactive().convention(true);
        spec.getSerializationFramework().convention(DEFAULT_SERIALIZATION_FRAMEWORK);
        spec.getAlwaysUseGenerateHttpResponse().convention(false);
        spec.getGenerateHttpResponseWhereRequired().convention(false);
        spec.getDateTimeFormat().convention("ZONED_DATETIME");
        withJava(() -> {
                    var implDeps = project.getConfigurations().getByName("implementation").getDependencies();
                    implDeps.add(project.getDependencies().create("io.micronaut.openapi:micronaut-openapi"));
                    implDeps.addAllLater(spec.getUseReactive().map(reactive -> {
                        if (Boolean.TRUE.equals(reactive)) {
                            return List.of(project.getDependencies().create("io.projectreactor:reactor-core"));
                        }
                        return List.of();
                    }));
                }
        );
    }

    private void configureCommonProperties(String name, AbstractOpenApiGenerator<?, ?> task, OpenApiSpec openApiSpec, Provider<RegularFile> definitionFile) {
        task.setGroup(OPENAPI_GROUP);
        task.getClasspath().from(classpath);
        task.getApiPackageName().convention(openApiSpec.getApiPackageName());
        task.getInvokerPackageName().convention(openApiSpec.getInvokerPackageName());
        task.getModelPackageName().convention(openApiSpec.getModelPackageName());
        task.getOutputDirectory().convention(
                project.getLayout().getBuildDirectory().dir("generated/openapi/" + task.getName())
        );
        task.getUseBeanValidation().convention(openApiSpec.getUseBeanValidation());
        task.getUseOptional().convention(openApiSpec.getUseOptional());
        task.getUseReactive().convention(openApiSpec.getUseReactive());
        task.getDefinitionFile().convention(definitionFile);
        task.getSerializationFramework().convention(openApiSpec.getSerializationFramework());
        task.getAlwaysUseGenerateHttpResponse().convention(openApiSpec.getAlwaysUseGenerateHttpResponse());
        task.getGenerateHttpResponseWhereRequired().convention(openApiSpec.getGenerateHttpResponseWhereRequired());
        task.getDateTimeFormat().convention(openApiSpec.getDateTimeFormat());
        task.getParameterMappings().convention(openApiSpec.getParameterMappings());
        task.getResponseBodyMappings().convention(openApiSpec.getResponseBodyMappings());
    }

    private void withJavaSourceSets(Consumer<? super SourceSetContainer> consumer) {
        project.getPlugins().withId("java", unused -> consumer.accept(PluginsHelper.findSourceSets(project)));
    }

    private void withJava(Runnable runnable) {
        project.getPlugins().withId("java", unused -> runnable.run());
    }

    @Override
    public void client(String name, Provider<RegularFile> definition, Action<? super OpenApiClientSpec> spec) {
        if (names.add(name)) {
            var clientSpec = project.getObjects().newInstance(OpenApiClientSpec.class);
            configureCommonExtensionDefaults(clientSpec);
            clientSpec.getUseAuth().convention(false);
            spec.execute(clientSpec);
            var client = project.getTasks().register(generateApisTaskName(name), OpenApiClientGenerator.class, task -> {
                configureCommonProperties(name, task, clientSpec, definition);
                task.setDescription("Generates OpenAPI client from an OpenAPI definition");
                configureClientTask(clientSpec, task);
                task.getOutputKinds().add("APIS");
            });
            var models = project.getTasks().register(generateModelsTaskName(name), OpenApiClientGenerator.class, task -> {
                configureCommonProperties(name, task, clientSpec, definition);
                task.setDescription("Generates OpenAPI client models from an OpenAPI definition");
                configureClientTask(clientSpec, task);
                task.getOutputKinds().add("MODELS");
            });
            withJavaSourceSets(sourceSets -> {
                var javaMain = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME).getJava();
                javaMain.srcDir(client.map(DefaultOpenApiExtension::mainSrcDir));
                javaMain.srcDir(models.map(DefaultOpenApiExtension::mainSrcDir));
            });
            withJava(() -> {
                        var implDeps = project.getConfigurations().getByName("implementation").getDependencies();
                        implDeps.add(project.getDependencies().create("io.micronaut:micronaut-http-client-core"));
                        implDeps.add(project.getDependencies().create("io.micronaut.validation:micronaut-validation"));
                        var annProcessor = project.getConfigurations().getByName("annotationProcessor").getDependencies();
                        annProcessor.add(project.getDependencies().create("io.micronaut.validation:micronaut-validation-processor"));
                    }
            );
        } else {
            throwDuplicateEntryFor(name);
        }
    }

    private static Provider<Directory> mainSrcDir(AbstractOpenApiGenerator<?,?> t) {
        return t.getOutputDirectory().dir("src/main/java");
    }

    private static void configureClientTask(OpenApiClientSpec clientSpec, OpenApiClientGenerator task) {
        task.getClientId().convention(clientSpec.getClientId());
        task.getAdditionalClientTypeAnnotations().set(clientSpec.getAdditionalClientTypeAnnotations());
        task.getBasePathSeparator().convention(clientSpec.getBasePathSeparator());
        task.getAuthorizationFilterPattern().convention(clientSpec.getAuthorizationFilterPattern());
        task.getUseAuth().convention(clientSpec.getUseAuth());
    }

    private static void throwDuplicateEntryFor(String name) {
        throw new GradleException("An OpenAPI definition with name '" + name + "' was already created");
    }

    private static String generateModelsTaskName(String name) {
        return "generate" + capitalize(name) + "OpenApiModels";
    }

    private static String generateApisTaskName(String name) {
        return "generate" + capitalize(name) + "OpenApiApis";
    }

    private static void configureServerTask(OpenApiServerSpec serverSpec, OpenApiServerGenerator task) {
        task.getControllerPackage().convention(serverSpec.getControllerPackage());
        task.getUseAuth().convention(serverSpec.getUseAuth());
    }

}
