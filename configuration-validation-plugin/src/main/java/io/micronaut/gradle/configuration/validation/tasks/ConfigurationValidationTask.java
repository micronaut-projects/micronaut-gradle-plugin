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
package io.micronaut.gradle.configuration.validation.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Failing task that reads the result marker produced by {@link ConfigurationValidationReportTask}.
 *
 * @since 5.0.0
 */
public abstract class ConfigurationValidationTask extends DefaultTask {

    /**
     * Default constructor.
     */
    public ConfigurationValidationTask() {
    }

    /**
     * Result marker file produced by {@code *ConfigurationValidationReport} tasks.
     *
     * @return marker file
     */
    @InputFile
    public abstract RegularFileProperty getResultFile();

    /**
     * Output directory containing the reports.
     *
     * @return output directory
     */
    @Internal
    public abstract DirectoryProperty getOutputDirectory();

    /**
     * Format used by the report task.
     *
     * @return format
     */
    @Input
    public abstract Property<String> getFormat();

    /**
     * Combined enabled flag (global + scenario).
     *
     * @return enabled flag
     */
    @Internal
    public abstract Property<Boolean> getEnabledFlag();

    /**
     * Reads the result marker and fails the build if validation errors are present.
     *
     * @throws Exception if marker cannot be read or the build should fail
     */
    @TaskAction
    public final void failOnErrors() throws Exception {
        if (!getEnabledFlag().getOrElse(true)) {
            getLogger().info("Micronaut configuration validation disabled");
            return;
        }

        Path marker = getResultFile().getAsFile().get().toPath();
        Properties props = new Properties();
        try (InputStream is = Files.newInputStream(marker)) {
            props.load(is);
        }
        int exitCode;
        try {
            exitCode = Integer.parseInt(props.getProperty("exitCode", "2"));
        } catch (NumberFormatException e) {
            exitCode = 2;
        }

        if (exitCode == 0) {
            return;
        }
        if (exitCode == 2) {
            throw new GradleException("Micronaut configuration validation failed to execute. See reports in " + getOutputDirectory().getAsFile().get());
        }

        String format = getFormat().getOrElse("both");
        StringBuilder message = new StringBuilder("Micronaut configuration validation failed.");
        message.append(System.lineSeparator());
        message.append("Reports: ").append(getOutputDirectory().getAsFile().get());
        message.append(System.lineSeparator());
        if ("json".equalsIgnoreCase(format) || "both".equalsIgnoreCase(format)) {
            message.append("- ").append(getOutputDirectory().file("configuration-errors.json").get().getAsFile());
            message.append(System.lineSeparator());
        }
        if ("html".equalsIgnoreCase(format) || "both".equalsIgnoreCase(format)) {
            message.append("- ").append(getOutputDirectory().file("configuration-errors.html").get().getAsFile());
            message.append(System.lineSeparator());
        }
        throw new GradleException(message.toString());
    }
}
