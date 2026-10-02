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

public interface OpenApiGenericSpec extends OpenApiSpec {

    /**
     * The fully qualified class name of the custom Micronaut OpenAPI generator.
     *
     * <p>The class must implement {@code io.micronaut.openapi.generator.MicronautCodeGenerator}
     * and be available on the {@code openApiGenerator} classpath.</p>
     *
     * @return the custom generator class name
     */
    Property<String> getGeneratorClassName();

    /**
     * Additional properties to apply to the custom generator options builder.
     *
     * <p>Each entry is matched against a builder method with the property name,
     * {@code with<PropertyName>}, or {@code set<PropertyName>}.</p>
     *
     * @return the custom generator properties
     */
    MapProperty<String, Object> getGeneratorProperties();

    /**
     * The OpenAPI generator output kinds to produce.
     *
     * <p>Common values are {@code apis}, {@code models}, and
     * {@code supporting_files}.</p>
     *
     * @return the requested output kinds
     */
    ListProperty<String> getOutputKinds();
}
