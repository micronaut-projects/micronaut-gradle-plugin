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

import org.gradle.api.Action;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;

import java.io.File;

/**
 * Configures the OpenAPI code generator.
 */
public interface OpenApiExtension {

    /**
     * The version of the Micronaut OpenAPI generator.
     * @return the version
     */
    Property<String> getVersion();

    /**
     * Configures generation of a server, given a definition file.
     * @param file the OpenAPI definition file
     * @param spec configuration for the server generation
     */
    void server(File file, Action<? super OpenApiServerSpec> spec);

    /**
     * Configures generation of a client, given a definition file.
     * @param file the OpenAPI definition file
     * @param spec configuration for the client generation
     */
    void client(File file, Action<? super OpenApiClientSpec> spec);

    /**
     * Configures generation of a server, given a definition file.
     * @param name an identifier used to uniquely refer to the generator, used to derive task names
     * @param definition the OpenAPI definition file provider
     * @param spec configuration for the server generation
     */
    void server(String name, Provider<RegularFile> definition, Action<? super OpenApiServerSpec> spec);

    /**
     * Configures generation of a server, given a definition file.
     * @param name an identifier used to uniquely refer to the generator, used to derive task names
     * @param definition the OpenAPI definition file provider
     * @param spec configuration for the server generation
     */
    void client(String name, Provider<RegularFile> definition, Action<? super OpenApiClientSpec> spec);

    /**
     * Configures generation of a server, given a definition file.
     * @param name an identifier used to uniquely refer to the generator, used to derive task names
     * @param definition the OpenAPI definition file provider
     * @param spec configuration for the server generation
     */
    void server(String name, File definition, Action<? super OpenApiServerSpec> spec);

    /**
     * Configures generation of a server, given a definition file.
     * @param name an identifier used to uniquely refer to the generator, used to derive task names
     * @param definition the OpenAPI definition file provider
     * @param spec configuration for the server generation
     */
    void client(String name, File definition, Action<? super OpenApiClientSpec> spec);
}
