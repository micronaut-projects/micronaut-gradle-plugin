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

import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;

public interface OpenApiSpec {

    Property<String> getLang();

    Property<String> getInvokerPackageName();

    Property<String> getApiPackageName();

    Property<String> getModelPackageName();

    Property<Boolean> getUseBeanValidation();

    Property<Boolean> getUseOneOfInterfaces();

    Property<Boolean> getUseOptional();

    Property<Boolean> getUseReactive();

    Property<String> getSerializationFramework();

    Property<Boolean> getAlwaysUseGenerateHttpResponse();

    Property<Boolean> getGenerateHttpResponseWhereRequired();

    Property<Boolean> getLombok();

    Property<Boolean> getKsp();

    Property<Boolean> getGeneratedAnnotation();

    Property<Boolean> getFluxForArrays();

    Property<String> getDateTimeFormat();

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
}
