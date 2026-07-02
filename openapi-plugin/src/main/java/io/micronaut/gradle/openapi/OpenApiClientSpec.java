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
import org.gradle.api.provider.Property;

public interface OpenApiClientSpec extends OpenApiSpec {

    /**
     * The Micronaut HTTP client id to place on generated client declarations.
     *
     * <p>The generator uses this value for the generated {@code @Client}
     * identifier. Leave it unset when the generated client should use the
     * default target derived by the Micronaut OpenAPI generator.</p>
     *
     * @return the generated Micronaut client id
     */
    Property<String> getClientId();

    /**
     * Whether the generated {@code @Client} declaration should use a
     * configurable path expression instead of only the OpenAPI server URL.
     *
     * <p>Defaults to {@code false}. Enable this when the generated client base
     * path should be externalized to Micronaut configuration.</p>
     *
     * @return whether to generate a configurable client path
     */
    Property<Boolean> getClientPath();

    /**
     * Whether to generate client authorization support for OpenAPI security
     * requirements.
     *
     * <p>Defaults to {@code false}. When enabled, generated sources may import
     * Micronaut Security and Reactor types. The Gradle plugin configures code
     * generation, but applications must still add the Micronaut Security modules
     * required by the generated auth classes.</p>
     *
     * @return whether client authorization support is generated
     */
    Property<Boolean> getUseAuth();

    /**
     * Separator used when deriving generated client base-path configuration
     * keys.
     *
     * <p>Defaults to {@code "."}.</p>
     *
     * @return the separator for generated client path property names
     */
    Property<String> getBasePathSeparator();

    /**
     * Additional annotations to place on generated client types.
     *
     * <p>Use fully qualified annotation names when imports would otherwise be
     * ambiguous.</p>
     *
     * @return annotations added to generated client classes or interfaces
     */
    ListProperty<String> getAdditionalClientTypeAnnotations();

    /**
     * Whether to generate reusable authorization support classes.
     *
     * <p>Defaults to {@code true}. This option only has an effect when
     * {@link #getUseAuth()} is enabled.</p>
     *
     * @return whether auth support classes are generated
     */
    Property<Boolean> getGenerateAuthClasses();

    /**
     * Whether to generate a Micronaut client authorization filter.
     *
     * <p>Defaults to {@code true}. This option only has an effect when
     * {@link #getUseAuth()} is enabled. Use the filter pattern and client-id
     * properties to scope the generated filter.</p>
     *
     * @return whether an authorization filter is generated
     */
    Property<Boolean> getAuthFilter();

    /**
     * Whether generated authorization support should include OAuth flows.
     *
     * <p>Defaults to {@code true}. The generated code can require Micronaut
     * Security OAuth dependencies when this remains enabled and the OpenAPI
     * definition declares OAuth security requirements.</p>
     *
     * @return whether OAuth auth support is generated
     */
    Property<Boolean> getUseOauth();

    /**
     * Whether generated authorization support should include HTTP Basic auth.
     *
     * <p>Defaults to {@code true}. This option only has an effect when
     * {@link #getUseAuth()} is enabled.</p>
     *
     * @return whether Basic auth support is generated
     */
    Property<Boolean> getUseBasicAuth();

    /**
     * Whether generated authorization support should include API key auth.
     *
     * <p>Defaults to {@code true}. This option only has an effect when
     * {@link #getUseAuth()} is enabled.</p>
     *
     * @return whether API key auth support is generated
     */
    Property<Boolean> getUseApiKeyAuth();

    /**
     * Request pattern used by the generated authorization filter.
     *
     * <p>Leave unset to use the generator default. Set this when the generated
     * filter must apply only to a subset of requests.</p>
     *
     * @return the generated authorization filter pattern
     */
    Property<String> getAuthorizationFilterPattern();

    /**
     * Pattern style used by the generated authorization filter.
     *
     * <p>Leave unset to use the generator default for the selected generator
     * version.</p>
     *
     * @return the generated authorization filter pattern style
     */
    Property<String> getAuthorizationFilterPatternStyle();

    /**
     * Client ids included by the generated authorization filter.
     *
     * <p>Use this to restrict generated auth filter behavior to specific
     * Micronaut client ids.</p>
     *
     * @return client ids included by the generated auth filter
     */
    ListProperty<String> getAuthFilterClientIds();

    /**
     * Client ids excluded by the generated authorization filter.
     *
     * <p>Use this when the generated auth filter should apply broadly except
     * for specific Micronaut client ids.</p>
     *
     * @return client ids excluded by the generated auth filter
     */
    ListProperty<String> getAuthFilterExcludedClientIds();

    /**
     * Configuration name used by generated authorization support.
     *
     * <p>Leave unset to use the generator default. Set this when generated auth
     * code should read a named Micronaut auth configuration.</p>
     *
     * @return the generated auth configuration name
     */
    Property<String> getAuthConfigName();
}
