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

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.jvm.toolchain.JavaLauncher;

/**
 * Configuration extension for the {@code io.micronaut.configuration.validation} plugin.
 * <p>
 * This plugin validates Micronaut configuration (typically {@code application.yml},
 * {@code application.properties}, etc.) against JSON Schemas published by Micronaut modules
 * and generated from {@code @ConfigurationProperties}.
 * <p>
 * Validation is executed via the
 * {@code io.micronaut.jsonschema.configuration.validator.cli.ConfigurationJsonSchemaValidatorCliBootstrap}
 * entry point from the {@code micronaut-json-schema-configuration-validator} module.
 *
 * @since 5.0.0
 */
public interface ConfigurationValidationExtension {

    /**
     * Version of {@code micronaut-json-schema-configuration-validator} used by the validation tasks.
     *
     * @return the validator version
     */
    Property<String> getVersion();

    /**
     * Enables or disables configuration validation globally.
     *
     * @return global enabled flag
     */
    Property<Boolean> getEnabled();

    /**
     * Base output directory for generated validation reports.
     * <p>
     * Scenario tasks will write into a scenario subdirectory.
     *
     * @return the base output directory
     */
    DirectoryProperty getOutputDirectory();

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
     * Project base directory used for origin path rewriting in console output.
     * <p>
     * Maps to {@code --project-base-dir}.
     *
     * @return project base directory
     */
    DirectoryProperty getProjectBaseDir();

    /**
     * Optional Java launcher used to execute the validator CLI.
     *
     * @return Java launcher
     */
    Property<JavaLauncher> getJavaLauncher();

    /**
     * Optional explicit Java executable path used to execute the validator CLI.
     *
     * @return Java executable
     */
    Property<String> getJavaExecutable();

    /**
     * Scenario used for {@code run}.
     *
     * @return run scenario configuration
     */
    ConfigurationValidationScenario getRun();

    /**
     * Scenario used for {@code test}.
     *
     * @return test scenario configuration
     */
    ConfigurationValidationScenario getTest();

    /**
     * Scenario used for packaging/build validation.
     *
     * @return production scenario configuration
     */
    ConfigurationValidationScenario getProduction();
}
