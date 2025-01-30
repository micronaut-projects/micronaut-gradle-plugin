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

import org.gradle.api.Action;
import org.gradle.api.provider.Property;

import java.io.File;

/**
 * Configures the JSON Schema code generator.
 */
public interface JSONSchemaExtension {
    /**
     * The version of the Micronaut JSON Schema generator.
     * @return the version
     */
    Property<String> getVersion();

    /**
     * Configures generation, given a schema URL.
     * @param url the url string of a json schema
     * @param spec configuration for the server generation
     */
    void fromUrl(String url, Action<JsonSchemaSpec> spec);

    /**
     * Configures generation, given a schema file.
     * @param file the json file
     * @param spec configuration for the server generation
     */
    void fromFile(File file, Action<JsonSchemaSpec> spec);
}
