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
package io.micronaut.gradle.openapi.tasks;

import io.micronaut.gradle.openapi.ParameterMappingModel;
import io.micronaut.gradle.openapi.ResponseBodyMappingModel;
import io.micronaut.openapi.generator.MicronautCodeGeneratorBuilder;
import io.micronaut.openapi.generator.MicronautCodeGeneratorEntryPoint;
import io.micronaut.openapi.generator.MicronautCodeGeneratorOptionsBuilder;
import io.micronaut.openapi.generator.MicronautCodeGeneratorOptionsBuilder.GeneratorLanguage;
import io.micronaut.openapi.generator.ParameterMapping;
import io.micronaut.openapi.generator.ResponseBodyMapping;
import io.micronaut.openapi.generator.SerializationLibraryKind;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;

import java.util.Locale;

public abstract class AbstractOpenApiWorkAction<T extends AbstractOpenApiWorkAction.OpenApiParameters> implements WorkAction<T> {

    interface OpenApiParameters extends WorkParameters {

        Property<String> getLang();

        RegularFileProperty getDefinitionFile();

        Property<String> getInvokerPackageName();

        Property<String> getApiPackageName();

        Property<String> getModelPackageName();

        Property<Boolean> getUseBeanValidation();

        Property<Boolean> getUseOneOfInterfaces();

        Property<Boolean> getUseOptional();

        Property<Boolean> getUseReactive();

        ListProperty<String> getOutputKinds();

        Property<String> getSerializationFramework();

        DirectoryProperty getOutputDirectory();

        Property<Boolean> getAlwaysUseGenerateHttpResponse();

        Property<Boolean> getGenerateHttpResponseWhereRequired();

        Property<String> getDateTimeFormat();

        Property<Boolean> getLombok();

        Property<Boolean> getNoArgsConstructor();

        Property<Boolean> getKsp();

        Property<Boolean> getGeneratedAnnotation();

        Property<Boolean> getFluxForArrays();

        ListProperty<ParameterMappingModel> getParameterMappings();

        ListProperty<ResponseBodyMappingModel> getResponseBodyMappings();

        MapProperty<String, String> getSchemaMapping();

        MapProperty<String, String> getImportMapping();

        MapProperty<String, String> getNameMapping();

        MapProperty<String, String> getTypeMapping();

        MapProperty<String, String> getEnumNameMapping();

        MapProperty<String, String> getModelNameMapping();

        MapProperty<String, String> getInlineSchemaNameMapping();

        MapProperty<String, String> getInlineSchemaOption();

        MapProperty<String, String> getOpenapiNormalizer();

        Property<String> getApiNamePrefix();

        Property<String> getApiNameSuffix();

        Property<String> getModelNamePrefix();

        Property<String> getModelNameSuffix();

        Property<Boolean> getUseEnumCaseInsensitive();

        Property<Boolean> getGenerateSwaggerAnnotations();

        Property<Boolean> getImplicitHeaders();

        Property<String> getImplicitHeadersRegex();

        ListProperty<String> getAdditionalEnumTypeAnnotations();

        ListProperty<String> getAdditionalModelTypeAnnotations();

        ListProperty<String> getAdditionalOneOfTypeAnnotations();

        MapProperty<String, Object> getAdditionalProperties();

        Property<Boolean> getUseJakartaEe();

        Property<Boolean> getSortParamsByRequiredFlag();

        Property<Boolean> getSkipOperationExample();

        Property<Boolean> getSkipSortingOperations();

        Property<String> getRemoveOperationIdPrefixDelimiter();

        Property<Integer> getRemoveOperationIdPrefixCount();

        Property<Boolean> getSortModelPropertiesByRequiredFlag();

        Property<Boolean> getEnsureUniqueParams();

        Property<Boolean> getAllowUnicodeIdentifiers();

        Property<Boolean> getPrependFormOrBodyParameters();

        Property<Boolean> getCoroutines();

        Property<Boolean> getUseSealed();

        Property<Boolean> getJsonIncludeAlwaysForRequiredFields();

        Property<Boolean> getRequiredPropertiesInConstructor();

        Property<Boolean> getGenerateControllerAsAbstract();

        Property<Boolean> getUseUrlConnectionCache();

        Property<Boolean> getGenerateEnumConverters();

        Property<Boolean> getUseTags();

        Property<Boolean> getGenerateOperationOnlyForFirstTag();

        Property<Boolean> getJvmOverloads();

        Property<Boolean> getJvmRecord();

        Property<Boolean> getJavaCompatibility();

        Property<String> getUserParameterMode();
    }

    protected abstract void configureBuilder(MicronautCodeGeneratorBuilder builder);

    @Override
    public void execute() {
        var parameters = getParameters();
        var lang = parameters.getLang().get();
        var builder = MicronautCodeGeneratorEntryPoint.builder()
            .withDefinitionFile(parameters.getDefinitionFile().get().getAsFile().toURI())
            .withOutputDirectory(parameters.getOutputDirectory().getAsFile().get())
            .withOutputs(
                parameters.getOutputKinds().get()
                    .stream()
                    .map(MicronautCodeGeneratorEntryPoint.OutputKind::of)
                    .toArray(MicronautCodeGeneratorEntryPoint.OutputKind[]::new)
            )
            .withOptions(options ->
                options
                    .withLang("kotlin".equalsIgnoreCase(lang) ? GeneratorLanguage.KOTLIN : GeneratorLanguage.JAVA)
                    .withApiPackage(parameters.getApiPackageName().getOrNull())
                    .withInvokerPackage(parameters.getInvokerPackageName().getOrNull())
                    .withModelPackage(parameters.getModelPackageName().getOrNull())
                    .withBeanValidation(parameters.getUseBeanValidation().get())
                    .withUseOneOfInterfaces(parameters.getUseOneOfInterfaces().get())
                    .withOptional(parameters.getUseOptional().get())
                    .withReactive(parameters.getUseReactive().get())
                    .withSerializationLibrary(SerializationLibraryKind.valueOf(parameters.getSerializationFramework().get().toUpperCase(Locale.US)))
                    .withGenerateHttpResponseAlways(parameters.getAlwaysUseGenerateHttpResponse().get())
                    .withGenerateHttpResponseWhereRequired(parameters.getGenerateHttpResponseWhereRequired().get())
                    .withDateTimeFormat(MicronautCodeGeneratorOptionsBuilder.DateTimeFormat.valueOf(parameters.getDateTimeFormat().get().toUpperCase(Locale.US)))
                    .withParameterMappings(parameters.getParameterMappings()
                        .get()
                        .stream()
                        .map(mapping -> new ParameterMapping(
                            mapping.getName(),
                            mapping.getLocation() == null ? null : ParameterMapping.ParameterLocation.valueOf(mapping.getLocation().name()),
                            mapping.getMappedType(),
                            mapping.getMappedName(),
                            mapping.isValidated())
                        )
                        .toList()
                    )
                    .withResponseBodyMappings(parameters.getResponseBodyMappings()
                        .get()
                        .stream()
                        .map(mapping -> new ResponseBodyMapping(
                            mapping.getHeaderName(),
                            mapping.getMappedBodyType(),
                            mapping.isListWrapper(),
                            mapping.isValidated()))
                        .toList()
                    )
                    .withSchemaMapping(parameters.getSchemaMapping().getOrNull())
                    .withImportMapping(parameters.getImportMapping().getOrNull())
                    .withNameMapping(parameters.getNameMapping().getOrNull())
                    .withTypeMapping(parameters.getTypeMapping().getOrNull())
                    .withEnumNameMapping(parameters.getEnumNameMapping().getOrNull())
                    .withModelNameMapping(parameters.getModelNameMapping().getOrNull())
                    .withInlineSchemaNameMapping(parameters.getInlineSchemaNameMapping().getOrNull())
                    .withInlineSchemaOption(parameters.getInlineSchemaOption().getOrNull())
                    .withOpenapiNormalizer(parameters.getOpenapiNormalizer().getOrNull())
                    .withApiNamePrefix(parameters.getApiNamePrefix().getOrNull())
                    .withApiNameSuffix(parameters.getApiNameSuffix().getOrNull())
                    .withModelNamePrefix(parameters.getModelNamePrefix().getOrNull())
                    .withModelNameSuffix(parameters.getModelNameSuffix().getOrNull())
                    .withGenerateSwaggerAnnotations(parameters.getGenerateSwaggerAnnotations().get())
                    .withImplicitHeaders(parameters.getImplicitHeaders().get())
                    .withImplicitHeadersRegex(parameters.getImplicitHeadersRegex().getOrNull())
                    .withUseEnumCaseInsensitive(parameters.getUseEnumCaseInsensitive().get())
                    .withAdditionalEnumTypeAnnotations(parameters.getAdditionalEnumTypeAnnotations().getOrNull())
                    .withAdditionalModelTypeAnnotations(parameters.getAdditionalModelTypeAnnotations().getOrNull())
                    .withAdditionalOneOfTypeAnnotations(parameters.getAdditionalOneOfTypeAnnotations().getOrNull())
                    .withAdditionalProperties(parameters.getAdditionalProperties().getOrNull())

                    .withUseJakartaEe(parameters.getUseJakartaEe().get())
                    .withSortParamsByRequiredFlag(parameters.getSortParamsByRequiredFlag().get())
                    .withSkipOperationExample(parameters.getSkipOperationExample().get())
                    .withSkipSortingOperations(parameters.getSkipSortingOperations().get())
                    .withRemoveOperationIdPrefixDelimiter(parameters.getRemoveOperationIdPrefixDelimiter().getOrNull())
                    .withRemoveOperationIdPrefixCount(parameters.getRemoveOperationIdPrefixCount().get())
                    .withSortModelPropertiesByRequiredFlag(parameters.getSortModelPropertiesByRequiredFlag().get())
                    .withEnsureUniqueParams(parameters.getEnsureUniqueParams().get())
                    .withAllowUnicodeIdentifiers(parameters.getAllowUnicodeIdentifiers().get())
                    .withPrependFormOrBodyParameters(parameters.getPrependFormOrBodyParameters().get())

                    .withUseSealed(parameters.getUseSealed().get())
                    .withJsonIncludeAlwaysForRequiredFields(parameters.getJsonIncludeAlwaysForRequiredFields().get())
                    .withRequiredPropertiesInConstructor(parameters.getRequiredPropertiesInConstructor().get())
                    .withGenerateControllerAsAbstract(parameters.getGenerateControllerAsAbstract().get())

                    .withUseUrlConnectionCache(parameters.getUseUrlConnectionCache().get())
                    .withGenerateEnumConverters(parameters.getGenerateEnumConverters().get())
                    .withUseTags(parameters.getUseTags().get())
                    .withGenerateOperationOnlyForFirstTag(parameters.getGenerateOperationOnlyForFirstTag().get())
            );

        configureBuilder(builder);
        builder.build().generate();
    }
}
