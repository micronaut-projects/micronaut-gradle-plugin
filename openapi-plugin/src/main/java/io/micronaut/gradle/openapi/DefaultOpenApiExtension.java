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
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.openapitools.codegen.CodegenConstants;

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
            serverSpec.getAot().convention(false);
            serverSpec.getGenerateHardNullable().convention(true);
            serverSpec.getGenerateStreamingFileUpload().convention(false);

            spec.execute(serverSpec);
            var controllers = project.getTasks().register(generateApisTaskName(name), OpenApiServerGenerator.class, task -> {
                configureCommonProperties(name, task, serverSpec, definition);
                task.setDescription("Generates OpenAPI controllers from an OpenAPI definition");
                configureServerTask(serverSpec, task);
                task.getOutputKinds().addAll(CodegenConstants.APIS, CodegenConstants.SUPPORTING_FILES);
                task.setEnabled(serverSpec.getGenerateApis().get());
            });
            var models = project.getTasks().register(generateModelsTaskName(name), OpenApiServerGenerator.class, task -> {
                configureCommonProperties(name, task, serverSpec, definition);
                task.setDescription("Generates OpenAPI models from an OpenAPI definition");
                configureServerTask(serverSpec, task);
                task.getOutputKinds().add(CodegenConstants.MODELS);
                task.setEnabled(serverSpec.getGenerateModels().get());
            });
            withJavaSourceSets(sourceSets -> {
                var javaMain = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME).getJava();
                javaMain.srcDir(serverSpec.getGenerateApis().zip(controllers.flatMap(DefaultOpenApiExtension::mainSrcDir), this::ifEnabled));
                javaMain.srcDir(serverSpec.getGenerateModels().zip(models.flatMap(DefaultOpenApiExtension::mainSrcDir), this::ifEnabled));
                project.getPluginManager().withPlugin("org.jetbrains.kotlin.jvm", unused -> {
                    var ext = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME).getExtensions().getByName("kotlin");
                    if (ext instanceof SourceDirectorySet kotlinMain) {
                        kotlinMain.srcDir(serverSpec.getGenerateApis().zip(controllers.flatMap(d -> DefaultOpenApiExtension.mainSrcDir(d, "kotlin")), this::ifEnabled));
                        kotlinMain.srcDir(serverSpec.getGenerateModels().zip(models.flatMap(d -> DefaultOpenApiExtension.mainSrcDir(d, "kotlin")), this::ifEnabled));
                    }
                });
            });
        } else {
            throwDuplicateEntryFor(name);
        }

    }

    private Directory ifEnabled(boolean enabled, Directory dir) {
        // the ignore part is because of a Gradle limitation, it would throw if the provider for `srcDir`
        // returns null
        return enabled ? dir : project.getLayout().getProjectDirectory().dir("__ignore__");
    }

    private void configureCommonExtensionDefaults(OpenApiSpec spec) {
        spec.getApiPackageName().convention("io.micronaut.openapi.api");
        spec.getInvokerPackageName().convention("io.micronaut.openapi");
        spec.getModelPackageName().convention("io.micronaut.openapi.model");
        spec.getUseBeanValidation().convention(true);
        spec.getUseOneOfInterfaces().convention(true);
        spec.getUseOptional().convention(false);
        spec.getUseReactive().convention(true);
        spec.getLombok().convention(false);
        spec.getNoArgsConstructor().convention(false);
        spec.getKsp().convention(false);
        spec.getGeneratedAnnotation().convention(true);
        spec.getFluxForArrays().convention(false);
        spec.getSerializationFramework().convention(DEFAULT_SERIALIZATION_FRAMEWORK);
        spec.getAlwaysUseGenerateHttpResponse().convention(false);
        spec.getGenerateHttpResponseWhereRequired().convention(true);
        spec.getDateTimeFormat().convention("ZONED_DATETIME");
        spec.getLang().convention("java");
        spec.getGenerateSwaggerAnnotations().convention(false);
        spec.getApiNamePrefix().convention("");
        spec.getApiNameSuffix().convention("");
        spec.getModelNamePrefix().convention("");
        spec.getModelNameSuffix().convention("");
        spec.getImplicitHeaders().convention(false);
        spec.getImplicitHeadersRegex().convention("");
        spec.getUseEnumCaseInsensitive().convention(false);

        spec.getUseJakartaEe().convention(true);
        spec.getSortParamsByRequiredFlag().convention(true);
        spec.getSkipOperationExample().convention(false);
        spec.getSkipSortingOperations().convention(false);
        spec.getRemoveOperationIdPrefixDelimiter().convention("_");
        spec.getRemoveOperationIdPrefixCount().convention(1);
        spec.getSortModelPropertiesByRequiredFlag().convention(true);
        spec.getEnsureUniqueParams().convention(true);
        spec.getAllowUnicodeIdentifiers().convention(false);
        spec.getPrependFormOrBodyParameters().convention(false);

        spec.getGenerateApis().convention(true);
        spec.getGenerateModels().convention(true);

        spec.getCoroutines().convention(false);
        spec.getUseSealed().convention(false);
        spec.getJsonIncludeAlwaysForRequiredFields().convention(false);
        spec.getRequiredPropertiesInConstructor().convention(true);
        spec.getGenerateControllerAsAbstract().convention(false);

        spec.getUseUrlConnectionCache().convention(false);
        spec.getGenerateEnumConverters().convention(true);
        spec.getUseTags().convention(true);
        spec.getGenerateOperationOnlyForFirstTag().convention(true);
        spec.getJvmOverloads().convention(false);
        spec.getJvmRecord().convention(false);
        spec.getJavaCompatibility().convention(true);
        spec.getUserParameterMode().convention("NONE");

        withJava(() -> {
                var compileOnlyDeps = project.getConfigurations().getByName("compileOnly").getDependencies();
                if ("java".equalsIgnoreCase(spec.getLang().get())) {
                    compileOnlyDeps.addAllLater(spec.getLombok().map(lombok -> {
                        if (Boolean.TRUE.equals(lombok)) {
                            return List.of(project.getDependencies().create("org.projectlombok:lombok"));
                        }
                        return List.of();
                    }));
                }
                compileOnlyDeps.add(project.getDependencies().create("io.micronaut.openapi:micronaut-openapi"));

                var implDeps = project.getConfigurations().getByName("implementation").getDependencies();
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
        task.getUseOneOfInterfaces().convention(openApiSpec.getUseOneOfInterfaces());
        task.getUseOptional().convention(openApiSpec.getUseOptional());
        task.getUseReactive().convention(openApiSpec.getUseReactive());
        task.getDefinitionFile().convention(definitionFile);
        task.getSerializationFramework().convention(openApiSpec.getSerializationFramework());
        task.getAlwaysUseGenerateHttpResponse().convention(openApiSpec.getAlwaysUseGenerateHttpResponse());
        task.getGenerateHttpResponseWhereRequired().convention(openApiSpec.getGenerateHttpResponseWhereRequired());
        task.getDateTimeFormat().convention(openApiSpec.getDateTimeFormat());
        task.getParameterMappings().convention(openApiSpec.getParameterMappings());
        task.getLang().convention(openApiSpec.getLang());
        task.getLombok().convention(openApiSpec.getLombok());
        task.getNoArgsConstructor().convention(openApiSpec.getNoArgsConstructor());
        task.getKsp().convention(openApiSpec.getKsp());
        task.getGeneratedAnnotation().convention(openApiSpec.getGeneratedAnnotation());
        task.getFluxForArrays().convention(openApiSpec.getFluxForArrays());
        task.getResponseBodyMappings().convention(openApiSpec.getResponseBodyMappings());

        task.getSchemaMapping().convention(openApiSpec.getSchemaMapping());
        task.getImportMapping().convention(openApiSpec.getImportMapping());
        task.getNameMapping().convention(openApiSpec.getNameMapping());
        task.getTypeMapping().convention(openApiSpec.getTypeMapping());
        task.getEnumNameMapping().convention(openApiSpec.getEnumNameMapping());
        task.getModelNameMapping().convention(openApiSpec.getModelNameMapping());
        task.getInlineSchemaNameMapping().convention(openApiSpec.getInlineSchemaNameMapping());
        task.getInlineSchemaOption().convention(openApiSpec.getInlineSchemaOption());
        task.getOpenapiNormalizer().convention(openApiSpec.getOpenapiNormalizer());

        task.getApiNamePrefix().convention(openApiSpec.getApiNamePrefix());
        task.getApiNameSuffix().convention(openApiSpec.getApiNameSuffix());
        task.getModelNamePrefix().convention(openApiSpec.getModelNamePrefix());
        task.getModelNameSuffix().convention(openApiSpec.getModelNameSuffix());

        task.getUseEnumCaseInsensitive().convention(openApiSpec.getUseEnumCaseInsensitive());
        task.getGenerateSwaggerAnnotations().convention(openApiSpec.getGenerateSwaggerAnnotations());
        task.getImplicitHeaders().convention(openApiSpec.getImplicitHeaders());
        task.getImplicitHeadersRegex().convention(openApiSpec.getImplicitHeadersRegex());

        task.getAdditionalEnumTypeAnnotations().convention(openApiSpec.getAdditionalEnumTypeAnnotations());
        task.getAdditionalModelTypeAnnotations().convention(openApiSpec.getAdditionalModelTypeAnnotations());
        task.getAdditionalOneOfTypeAnnotations().convention(openApiSpec.getAdditionalOneOfTypeAnnotations());
        task.getAdditionalProperties().convention(openApiSpec.getAdditionalProperties());

        task.getUseJakartaEe().convention(openApiSpec.getUseJakartaEe());
        task.getSortParamsByRequiredFlag().convention(openApiSpec.getSortParamsByRequiredFlag());
        task.getSkipOperationExample().convention(openApiSpec.getSkipOperationExample());
        task.getSkipSortingOperations().convention(openApiSpec.getSkipSortingOperations());
        task.getRemoveOperationIdPrefixDelimiter().convention(openApiSpec.getRemoveOperationIdPrefixDelimiter());
        task.getRemoveOperationIdPrefixCount().convention(openApiSpec.getRemoveOperationIdPrefixCount());
        task.getSortModelPropertiesByRequiredFlag().convention(openApiSpec.getSortModelPropertiesByRequiredFlag());
        task.getEnsureUniqueParams().convention(openApiSpec.getEnsureUniqueParams());
        task.getAllowUnicodeIdentifiers().convention(openApiSpec.getAllowUnicodeIdentifiers());
        task.getPrependFormOrBodyParameters().convention(openApiSpec.getPrependFormOrBodyParameters());

        task.getCoroutines().convention(openApiSpec.getCoroutines());
        task.getUseSealed().convention(openApiSpec.getUseSealed());
        task.getJsonIncludeAlwaysForRequiredFields().convention(openApiSpec.getJsonIncludeAlwaysForRequiredFields());
        task.getRequiredPropertiesInConstructor().convention(openApiSpec.getRequiredPropertiesInConstructor());
        task.getGenerateControllerAsAbstract().convention(openApiSpec.getGenerateControllerAsAbstract());

        task.getUseUrlConnectionCache().convention(openApiSpec.getUseUrlConnectionCache());
        task.getGenerateEnumConverters().convention(openApiSpec.getGenerateEnumConverters());
        task.getUseTags().convention(openApiSpec.getUseTags());
        task.getGenerateOperationOnlyForFirstTag().convention(openApiSpec.getGenerateOperationOnlyForFirstTag());

        task.getJvmOverloads().convention(openApiSpec.getJvmOverloads());
        task.getJvmRecord().convention(openApiSpec.getJvmRecord());
        task.getJavaCompatibility().convention(openApiSpec.getJavaCompatibility());
        task.getUserParameterMode().convention(openApiSpec.getUserParameterMode());
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

            clientSpec.getClientId();
            clientSpec.getClientPath().convention(false);
            clientSpec.getUseAuth().convention(false);
            clientSpec.getBasePathSeparator().convention(".");
            clientSpec.getAdditionalClientTypeAnnotations();
            clientSpec.getGenerateAuthClasses().convention(true);
            clientSpec.getAuthFilter().convention(true);
            clientSpec.getUseOauth().convention(true);
            clientSpec.getUseBasicAuth().convention(true);
            clientSpec.getUseApiKeyAuth().convention(true);

            spec.execute(clientSpec);
            var client = project.getTasks().register(generateApisTaskName(name), OpenApiClientGenerator.class, task -> {
                configureCommonProperties(name, task, clientSpec, definition);
                task.setDescription("Generates OpenAPI client from an OpenAPI definition");
                configureClientTask(clientSpec, task);
                task.getOutputKinds().addAll(CodegenConstants.APIS, CodegenConstants.SUPPORTING_FILES);
                task.setEnabled(clientSpec.getGenerateApis().get());
            });
            var models = project.getTasks().register(generateModelsTaskName(name), OpenApiClientGenerator.class, task -> {
                configureCommonProperties(name, task, clientSpec, definition);
                task.setDescription("Generates OpenAPI client models from an OpenAPI definition");
                configureClientTask(clientSpec, task);
                task.getOutputKinds().add(CodegenConstants.MODELS);
                task.setEnabled(clientSpec.getGenerateModels().get());
            });
            withJavaSourceSets(sourceSets -> {
                var javaMain = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME).getJava();
                javaMain.srcDir(clientSpec.getGenerateApis().zip(client.flatMap(DefaultOpenApiExtension::mainSrcDir), this::ifEnabled));
                javaMain.srcDir(clientSpec.getGenerateModels().zip(models.flatMap(DefaultOpenApiExtension::mainSrcDir), this::ifEnabled));
                project.getPluginManager().withPlugin("org.jetbrains.kotlin.jvm", unused -> {
                    var ext = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME).getExtensions().getByName("kotlin");
                    if (ext instanceof SourceDirectorySet kotlinMain) {
                        kotlinMain.srcDir(clientSpec.getGenerateApis().zip(client.flatMap(d -> DefaultOpenApiExtension.mainSrcDir(d, "kotlin")), this::ifEnabled));
                        kotlinMain.srcDir(clientSpec.getGenerateModels().zip(models.flatMap(d -> DefaultOpenApiExtension.mainSrcDir(d, "kotlin")), this::ifEnabled));
                    }
                });
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

    @Override
    public void server(String name, File definition, Action<? super OpenApiServerSpec> spec) {
        server(name, project.getObjects().fileProperty().fileValue(definition), spec);
    }

    @Override
    public void client(String name, File definition, Action<? super OpenApiClientSpec> spec) {
        client(name, project.getObjects().fileProperty().fileValue(definition), spec);
    }

    private static Provider<Directory> mainSrcDir(AbstractOpenApiGenerator<?, ?> t, String language) {
        return t.getOutputDirectory().dir("src/main/" + language);
    }

    private static Provider<Directory> mainSrcDir(AbstractOpenApiGenerator<?, ?> t) {
        return mainSrcDir(t, "java");
    }

    private static void configureClientTask(OpenApiClientSpec clientSpec, OpenApiClientGenerator task) {
        task.getClientId().convention(clientSpec.getClientId());
        task.getClientPath().convention(clientSpec.getClientPath());
        task.getAdditionalClientTypeAnnotations().set(clientSpec.getAdditionalClientTypeAnnotations());
        task.getBasePathSeparator().convention(clientSpec.getBasePathSeparator());

        task.getUseAuth().convention(clientSpec.getUseAuth());
        task.getGenerateAuthClasses().convention(clientSpec.getGenerateAuthClasses());
        task.getAuthFilter().convention(clientSpec.getAuthFilter());
        task.getUseOauth().convention(clientSpec.getUseOauth());
        task.getUseBasicAuth().convention(clientSpec.getUseBasicAuth());
        task.getUseApiKeyAuth().convention(clientSpec.getUseApiKeyAuth());
        task.getAuthorizationFilterPattern().convention(clientSpec.getAuthorizationFilterPattern());
        task.getAuthorizationFilterPatternStyle().convention(clientSpec.getAuthorizationFilterPatternStyle());
        task.getAuthFilterClientIds().convention(clientSpec.getAuthFilterClientIds());
        task.getAuthFilterExcludedClientIds().convention(clientSpec.getAuthFilterExcludedClientIds());
        task.getAuthConfigName().convention(clientSpec.getAuthConfigName());
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
        task.getAot().convention(serverSpec.getAot());
        task.getGenerateHardNullable().convention(serverSpec.getGenerateHardNullable());
        task.getGenerateStreamingFileUpload().convention(serverSpec.getGenerateStreamingFileUpload());
        task.getUserParameterMode().convention(serverSpec.getUserParameterMode());
    }

}
