/*
 * Copyright 2003-2026 the original author or authors.
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
package io.micronaut.gradle;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

import javax.inject.Inject;
import java.util.List;

/**
 * Configures Micronaut SBOM generation defaults.
 *
 * @since 5.0.0
 */
public abstract class MicronautSbomExtension {
    static final String DEFAULT_OUTPUT_FILE_NAME = "application.cdx.json";
    static final String DEFAULT_PATH_IN_JAR = "META-INF/sbom/" + DEFAULT_OUTPUT_FILE_NAME;

    /**
     * @return Whether Micronaut should configure supported SBOM plugins.
     */
    public abstract Property<Boolean> getEnabled();

    /**
     * @return Whether generated SBOMs should be included in JVM JAR artifacts.
     */
    public abstract Property<Boolean> getIncludeInJar();

    /**
     * @return The Micronaut-owned SBOM reports directory.
     */
    public abstract DirectoryProperty getOutputDirectory();

    /**
     * @return The generated CycloneDX JSON file name.
     */
    public abstract Property<String> getOutputFileName();

    /**
     * @return The Gradle configurations included in the generated SBOM.
     */
    public abstract ListProperty<String> getIncludeConfigurations();

    /**
     * @return The Gradle configurations skipped by the generated SBOM.
     */
    public abstract ListProperty<String> getSkipConfigurations();

    /**
     * @return The SBOM path inside JVM JAR artifacts.
     */
    public abstract Property<String> getSbomPathInJar();

    @Inject
    public MicronautSbomExtension(ObjectFactory objects) {
        getEnabled().convention(true);
        getIncludeInJar().convention(false);
        getOutputFileName().convention(DEFAULT_OUTPUT_FILE_NAME);
        getIncludeConfigurations().convention(List.of("runtimeClasspath"));
        getSkipConfigurations().convention(List.of());
        getSbomPathInJar().convention(DEFAULT_PATH_IN_JAR);
    }
}
