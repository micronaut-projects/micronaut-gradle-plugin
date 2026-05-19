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

public interface OpenApiSpec {

    /**
     * Source language generated from the OpenAPI definition.
     *
     * <p>Defaults to {@code "java"}. Set to {@code "kotlin"} for Kotlin
     * sources.</p>
     *
     * @return the generated source language
     */
    Property<String> getLang();

    /**
     * Invoker package used by generated support classes.
     *
     * <p>Defaults to {@code "io.micronaut.openapi"}.</p>
     *
     * @return the generated invoker package
     */
    Property<String> getInvokerPackageName();

    /**
     * Package used by generated API types.
     *
     * <p>Defaults to {@code "io.micronaut.openapi.api"}.</p>
     *
     * @return the generated API package
     */
    Property<String> getApiPackageName();

    /**
     * Package used by generated model types.
     *
     * <p>Defaults to {@code "io.micronaut.openapi.model"}.</p>
     *
     * @return the generated model package
     */
    Property<String> getModelPackageName();

    /**
     * Whether generated models and operation parameters include Bean Validation
     * annotations.
     *
     * <p>Defaults to {@code true}. Generated validation annotations require the
     * usual Micronaut Validation dependencies in the consuming project.</p>
     *
     * @return whether Bean Validation annotations are generated
     */
    Property<Boolean> getUseBeanValidation();

    /**
     * Whether generated schemas that use {@code oneOf} should also generate
     * Java or Kotlin interfaces.
     *
     * <p>Defaults to {@code true}.</p>
     *
     * @return whether oneOf interfaces are generated
     */
    Property<Boolean> getUseOneOfInterfaces();

    /**
     * Whether optional schema properties should use {@code Optional}-style
     * wrappers where supported by the selected generator.
     *
     * <p>Defaults to {@code false}.</p>
     *
     * @return whether optional wrappers are generated
     */
    Property<Boolean> getUseOptional();

    /**
     * Whether generated operations use reactive types.
     *
     * <p>Defaults to {@code true}. When a Java plugin is present, the Gradle
     * plugin adds Reactor Core as a supporting dependency for this option.</p>
     *
     * @return whether reactive types are generated
     */
    Property<Boolean> getUseReactive();

    /**
     * Serialization library used by generated code.
     *
     * <p>Defaults to {@code "MICRONAUT_SERDE_JACKSON"}.</p>
     *
     * @return the serialization framework name
     */
    Property<String> getSerializationFramework();

    /**
     * Whether generated operations always return {@code HttpResponse}.
     *
     * <p>Defaults to {@code false}.</p>
     *
     * @return whether all generated operations return {@code HttpResponse}
     */
    Property<Boolean> getAlwaysUseGenerateHttpResponse();

    /**
     * Whether generated operations return {@code HttpResponse} only when the
     * OpenAPI response metadata requires it.
     *
     * <p>Defaults to {@code true}.</p>
     *
     * @return whether required responses use {@code HttpResponse}
     */
    Property<Boolean> getGenerateHttpResponseWhereRequired();

    /**
     * Whether generated Java sources use Lombok annotations.
     *
     * <p>Defaults to {@code false}. When enabled for Java generation, the Gradle
     * plugin adds Lombok to {@code compileOnly}.</p>
     *
     * @return whether Lombok annotations are generated
     */
    Property<Boolean> getLombok();

    /**
     * Whether generated models include no-argument constructors.
     *
     * <p>Defaults to {@code false}.</p>
     *
     * @return whether no-argument constructors are generated
     */
    Property<Boolean> getNoArgsConstructor();

    /**
     * Whether generated Kotlin sources should be prepared for KSP.
     *
     * <p>Defaults to {@code false}. This option applies to Kotlin generation.</p>
     *
     * @return whether KSP-oriented Kotlin code is generated
     */
    Property<Boolean> getKsp();

    /**
     * Whether generated sources include {@code @Generated} annotations.
     *
     * <p>Defaults to {@code true}.</p>
     *
     * @return whether generated annotations are emitted
     */
    Property<Boolean> getGeneratedAnnotation();

    /**
     * Whether array responses use Reactor {@code Flux} in generated reactive
     * code.
     *
     * <p>Defaults to {@code false}.</p>
     *
     * @return whether arrays are generated as {@code Flux}
     */
    Property<Boolean> getFluxForArrays();

    /**
     * Date-time representation used by generated code.
     *
     * <p>Defaults to {@code "ZONED_DATETIME"}.</p>
     *
     * @return the date-time format option
     */
    Property<String> getDateTimeFormat();

    /**
     * Parameter mappings applied during generation.
     *
     * <p>Mappings can add, rename, remap, or remove operation parameters before
     * source files are emitted.</p>
     *
     * @return parameter mappings for generated operations
     */
    ListProperty<ParameterMappingModel> getParameterMappings();

    /**
     * Response body mappings applied during generation.
     *
     * <p>Mappings can change how selected response bodies are represented in
     * generated operation signatures.</p>
     *
     * @return response body mappings for generated operations
     */
    ListProperty<ResponseBodyMappingModel> getResponseBodyMappings();

    /**
     * Schema-name mappings passed to the Micronaut OpenAPI generator.
     *
     * @return schema-name mappings
     */
    MapProperty<String, String> getSchemaMapping();

    /**
     * Import mappings passed to the Micronaut OpenAPI generator.
     *
     * @return import mappings
     */
    MapProperty<String, String> getImportMapping();

    /**
     * Name mappings passed to the Micronaut OpenAPI generator.
     *
     * @return name mappings
     */
    MapProperty<String, String> getNameMapping();

    /**
     * Type mappings passed to the Micronaut OpenAPI generator.
     *
     * @return type mappings
     */
    MapProperty<String, String> getTypeMapping();

    /**
     * Enum-name mappings passed to the Micronaut OpenAPI generator.
     *
     * @return enum-name mappings
     */
    MapProperty<String, String> getEnumNameMapping();

    /**
     * Model-name mappings passed to the Micronaut OpenAPI generator.
     *
     * @return model-name mappings
     */
    MapProperty<String, String> getModelNameMapping();

    /**
     * Inline schema name mappings passed to the Micronaut OpenAPI generator.
     *
     * @return inline schema name mappings
     */
    MapProperty<String, String> getInlineSchemaNameMapping();

    /**
     * Inline schema options passed to the Micronaut OpenAPI generator.
     *
     * @return inline schema options
     */
    MapProperty<String, String> getInlineSchemaOption();

    /**
     * OpenAPI normalizer options passed to the Micronaut OpenAPI generator.
     *
     * @return OpenAPI normalizer options
     */
    MapProperty<String, String> getOpenapiNormalizer();

    /**
     * Prefix added to generated API type names.
     *
     * <p>Defaults to the empty string.</p>
     *
     * @return the generated API name prefix
     */
    Property<String> getApiNamePrefix();

    /**
     * Suffix added to generated API type names.
     *
     * <p>Defaults to the empty string.</p>
     *
     * @return the generated API name suffix
     */
    Property<String> getApiNameSuffix();

    /**
     * Prefix added to generated model type names.
     *
     * <p>Defaults to the empty string.</p>
     *
     * @return the generated model name prefix
     */
    Property<String> getModelNamePrefix();

    /**
     * Suffix added to generated model type names.
     *
     * <p>Defaults to the empty string.</p>
     *
     * @return the generated model name suffix
     */
    Property<String> getModelNameSuffix();

    /**
     * Whether generated enum deserialization should be case-insensitive.
     *
     * <p>Defaults to {@code false}.</p>
     *
     * @return whether enums are case-insensitive
     */
    Property<Boolean> getUseEnumCaseInsensitive();

    /**
     * Whether generated sources include Swagger annotations.
     *
     * <p>Defaults to {@code false}.</p>
     *
     * @return whether Swagger annotations are generated
     */
    Property<Boolean> getGenerateSwaggerAnnotations();

    /**
     * Whether implicit headers are generated for operations.
     *
     * <p>Defaults to {@code false}. Use
     * {@link #getImplicitHeadersRegex()} to restrict which headers are treated
     * as implicit.</p>
     *
     * @return whether implicit headers are generated
     */
    Property<Boolean> getImplicitHeaders();

    /**
     * Regular expression used to select implicit headers.
     *
     * <p>Defaults to the empty string.</p>
     *
     * @return the implicit headers regular expression
     */
    Property<String> getImplicitHeadersRegex();

    /**
     * Additional annotations added to generated enum types.
     *
     * @return annotations added to generated enum types
     */
    ListProperty<String> getAdditionalEnumTypeAnnotations();

    /**
     * Additional annotations added to generated model types.
     *
     * @return annotations added to generated model types
     */
    ListProperty<String> getAdditionalModelTypeAnnotations();

    /**
     * Additional annotations added to generated {@code oneOf} interface types.
     *
     * @return annotations added to generated oneOf types
     */
    ListProperty<String> getAdditionalOneOfTypeAnnotations();

    /**
     * Additional generator properties passed through to Micronaut OpenAPI.
     *
     * <p>Use this for generator options that do not yet have a dedicated
     * Gradle DSL property.</p>
     *
     * @return additional generator properties
     */
    MapProperty<String, Object> getAdditionalProperties();

    /**
     * Whether generated code should use Jakarta EE package names.
     *
     * <p>Defaults to {@code true}.</p>
     *
     * @return whether Jakarta EE package names are used
     */
    Property<Boolean> getUseJakartaEe();

    /**
     * Whether generated operation parameters are sorted by required status.
     *
     * <p>Defaults to {@code true}.</p>
     *
     * @return whether parameters are sorted by required status
     */
    Property<Boolean> getSortParamsByRequiredFlag();

    /**
     * Whether generated operation examples are skipped.
     *
     * <p>Defaults to {@code false}.</p>
     *
     * @return whether operation examples are skipped
     */
    Property<Boolean> getSkipOperationExample();

    /**
     * Whether generated operations keep the OpenAPI declaration order instead
     * of applying generator sorting.
     *
     * <p>Defaults to {@code false}.</p>
     *
     * @return whether operation sorting is skipped
     */
    Property<Boolean> getSkipSortingOperations();

    /**
     * Delimiter used when removing prefixes from OpenAPI operation ids.
     *
     * <p>Defaults to {@code "_"}.</p>
     *
     * @return the operation-id prefix delimiter
     */
    Property<String> getRemoveOperationIdPrefixDelimiter();

    /**
     * Number of operation-id prefix segments to remove.
     *
     * <p>Defaults to {@code 1}.</p>
     *
     * @return the number of operation-id prefix segments to remove
     */
    Property<Integer> getRemoveOperationIdPrefixCount();

    /**
     * Whether generated model properties are sorted by required status.
     *
     * <p>Defaults to {@code true}.</p>
     *
     * @return whether model properties are sorted by required status
     */
    Property<Boolean> getSortModelPropertiesByRequiredFlag();

    /**
     * Whether generated operation parameter names are made unique.
     *
     * <p>Defaults to {@code true}.</p>
     *
     * @return whether parameter names are made unique
     */
    Property<Boolean> getEnsureUniqueParams();

    /**
     * Whether generated identifiers may contain Unicode characters.
     *
     * <p>Defaults to {@code false}.</p>
     *
     * @return whether Unicode identifiers are allowed
     */
    Property<Boolean> getAllowUnicodeIdentifiers();

    /**
     * Whether generated form or body parameters are placed before other
     * parameters.
     *
     * <p>Defaults to {@code false}.</p>
     *
     * @return whether form or body parameters are prepended
     */
    Property<Boolean> getPrependFormOrBodyParameters();

    /**
     * Whether API source files are generated and added to the main source set.
     *
     * <p>Defaults to {@code true}.</p>
     *
     * @return whether API sources are generated
     */
    Property<Boolean> getGenerateApis();

    /**
     * Whether model source files are generated and added to the main source set.
     *
     * <p>Defaults to {@code true}.</p>
     *
     * @return whether model sources are generated
     */
    Property<Boolean> getGenerateModels();

    /**
     * Whether Kotlin generation uses coroutines.
     *
     * <p>Defaults to {@code false}. This option applies to Kotlin generation.</p>
     *
     * @return whether coroutine APIs are generated
     */
    Property<Boolean> getCoroutines();

    /**
     * Whether Kotlin generation uses sealed hierarchies where supported.
     *
     * <p>Defaults to {@code false}. This option applies to Kotlin generation.</p>
     *
     * @return whether sealed types are generated
     */
    Property<Boolean> getUseSealed();

    /**
     * Whether required generated model properties should always be included in
     * JSON output.
     *
     * <p>Defaults to {@code false}.</p>
     *
     * @return whether required fields always use JSON include annotations
     */
    Property<Boolean> getJsonIncludeAlwaysForRequiredFields();

    /**
     * Whether required generated model properties are constructor parameters.
     *
     * <p>Defaults to {@code true}.</p>
     *
     * @return whether required properties are constructor parameters
     */
    Property<Boolean> getRequiredPropertiesInConstructor();

    /**
     * Whether generated server controllers are abstract instead of interfaces or
     * concrete types where supported.
     *
     * <p>Defaults to {@code false}. This option is only relevant for server
     * generation.</p>
     *
     * @return whether generated controllers are abstract
     */
    Property<Boolean> getGenerateControllerAsAbstract();

    /**
     * Whether generated clients use URL connection cache support where
     * available.
     *
     * <p>Defaults to {@code false}. This option is only relevant for client
     * generation.</p>
     *
     * @return whether URL connection cache support is generated
     */
    Property<Boolean> getUseUrlConnectionCache();

    /**
     * Whether enum converter classes are generated.
     *
     * <p>Defaults to {@code true}.</p>
     *
     * @return whether enum converters are generated
     */
    Property<Boolean> getGenerateEnumConverters();

    /**
     * Whether OpenAPI tags are used when grouping generated operations.
     *
     * <p>Defaults to {@code true}.</p>
     *
     * @return whether tags are used to group operations
     */
    Property<Boolean> getUseTags();

    /**
     * Whether an operation is generated only for its first tag.
     *
     * <p>Defaults to {@code true}. This option only has an effect when
     * {@link #getUseTags()} is enabled.</p>
     *
     * @return whether only the first operation tag is used
     */
    Property<Boolean> getGenerateOperationOnlyForFirstTag();

    /**
     * Whether Kotlin generated declarations include {@code @JvmOverloads}
     * where supported.
     *
     * <p>Defaults to {@code false}. This option applies to Kotlin generation.</p>
     *
     * @return whether {@code @JvmOverloads} is generated
     */
    Property<Boolean> getJvmOverloads();

    /**
     * Whether generated Java code uses record types where supported.
     *
     * <p>Defaults to {@code false}. This option applies to Java generation.</p>
     *
     * @return whether Java records are generated
     */
    Property<Boolean> getJvmRecord();

    /**
     * Whether Kotlin generated code includes Java interoperability helpers where
     * supported.
     *
     * <p>Defaults to {@code true}. This option applies to Kotlin generation.</p>
     *
     * @return whether Java compatibility helpers are generated
     */
    Property<Boolean> getJavaCompatibility();

    /**
     * How generated operations represent user parameters.
     *
     * <p>Defaults to {@code "NONE"}. Supported values are defined by the
     * selected Micronaut OpenAPI generator version.</p>
     *
     * @return the user parameter mode
     */
    Property<String> getUserParameterMode();
}
