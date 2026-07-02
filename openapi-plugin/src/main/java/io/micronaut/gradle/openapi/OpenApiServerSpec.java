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

import org.gradle.api.provider.Property;

public interface OpenApiServerSpec extends OpenApiSpec {

    /**
     * Package used for generated server controller types.
     *
     * <p>Defaults to {@code "io.micronaut.openapi.controller"}.</p>
     *
     * @return the generated controller package
     */
    Property<String> getControllerPackage();

    /**
     * Whether to generate server-side authentication annotations and support
     * for OpenAPI security requirements.
     *
     * <p>Defaults to {@code false}. When enabled, the generated server sources
     * may require Micronaut Security dependencies in the consuming
     * application.</p>
     *
     * @return whether server authentication support is generated
     */
    Property<Boolean> getUseAuth();

    /**
     * Whether to generate server code prepared for Micronaut AOT scenarios.
     *
     * <p>Defaults to {@code false}.</p>
     *
     * @return whether AOT-oriented server code is generated
     */
    Property<Boolean> getAot();

    /**
     * Whether Java server models should distinguish required nullable
     * properties more strictly in generated code.
     *
     * <p>Defaults to {@code true}. This option applies to Java server
     * generation.</p>
     *
     * @return whether hard nullable model handling is generated
     */
    Property<Boolean> getGenerateHardNullable();

    /**
     * Whether generated server operations should support streaming file upload
     * types.
     *
     * <p>Defaults to {@code false}.</p>
     *
     * @return whether streaming file upload support is generated
     */
    Property<Boolean> getGenerateStreamingFileUpload();
}
