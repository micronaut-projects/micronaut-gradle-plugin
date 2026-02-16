/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.gradle.configuration.validation;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

/**
 * Per-scenario configuration for Micronaut configuration validation.
 *
 * @since 5.0.0
 */
public interface ConfigurationValidationScenario {

    /**
     * Enables or disables this scenario.
     *
     * @return enabled flag
     */
    Property<Boolean> getEnabled();

    /**
     * Enabled Micronaut environments.
     * <p>
     * Values map to {@code --env} (repeatable).
     *
     * @return environments
     */
    ListProperty<String> getEnvironments();

    /**
     * Whether unknown configuration properties should be considered errors.
     * <p>
     * Maps to {@code --fail-on-not-present}.
     *
     * @return fail-on-not-present flag
     */
    Property<Boolean> getFailOnNotPresent();

    /**
     * Whether Micronaut environments should be deduced.
     * <p>
     * Maps to {@code --deduce-environments}.
     *
     * @return deduce-environments flag
     */
    Property<Boolean> getDeduceEnvironments();

    /**
     * Report format.
     * <p>
     * Valid values are {@code json}, {@code html}, and {@code both}.
     * Maps to {@code --format}.
     *
     * @return report format
     */
    Property<String> getFormat();

    /**
     * Suppression patterns.
     * <p>
     * Each pattern maps to {@code --suppress}.
     *
     * @return suppression patterns
     */
    ListProperty<String> getSuppressions();

    /**
     * Scenario output directory.
     *
     * @return output directory
     */
    DirectoryProperty getOutputDirectory();

    /**
     * When {@code true}, the scenario classpath fully replaces the default classpath computed by the plugin.
     *
     * @return override classpath flag
     */
    Property<Boolean> getOverrideClasspath();

    /**
     * Extra or overridden classpath entries used to validate configuration.
     * <p>
     * This classpath is passed to the validator CLI via {@code --classpath}.
     *
     * @return classpath entries
     */
    ConfigurableFileCollection getClasspath();

    /**
     * Resource directories under {@link ConfigurationValidationExtension#getProjectBaseDir()}.
     * <p>
     * Used for origin rewriting in console output. Values map to {@code --resources-dirs}.
     *
     * @return resource directory paths (relative to project base dir)
     */
    ListProperty<String> getResourcesDirs();
}
