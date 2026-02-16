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
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Cacheable task that runs Micronaut configuration validation and writes reports.
 * <p>
 * This task never fails the build when validation errors are found. Instead, it writes reports and
 * a {@link #getResultFile() result marker} containing the exit code.
 * A separate {@code ConfigurationValidationTask} is responsible for failing the build based on the marker.
 *
 * @since 5.0.0
 */
@CacheableTask
public abstract class ConfigurationValidationReportTask extends DefaultTask {

    /**
     * Default constructor.
     */
    public ConfigurationValidationReportTask() {
    }

    /**
     * Bootstrap main class shipped by {@code micronaut-json-schema-configuration-validator}.
     */
    public static final String BOOTSTRAP_MAIN_CLASS =
        "io.micronaut.jsonschema.configuration.validator.cli.ConfigurationJsonSchemaValidatorCliBootstrap";

    /**
     * Validator tool classpath (should contain only the validator module).
     *
     * @return tool classpath
     */
    @Classpath
    public abstract ConfigurableFileCollection getValidatorClasspath();

    /**
     * Application/runtime classpath used to discover schemas and configuration resources.
     * Passed to the CLI using {@code --classpath}.
     *
     * @return application classpath
     */
    @Classpath
    public abstract ConfigurableFileCollection getApplicationClasspath();

    /**
     * Enabled environments.
     *
     * @return environments
     */
    @Input
    public abstract ListProperty<String> getEnvironments();

    /**
     * Suppression patterns.
     *
     * @return suppressions
     */
    @Input
    public abstract ListProperty<String> getSuppressions();

    /**
     * Whether unknown properties are errors.
     *
     * @return flag
     */
    @Input
    public abstract Property<Boolean> getFailOnNotPresent();

    /**
     * Whether environments should be deduced.
     *
     * @return flag
     */
    @Input
    public abstract Property<Boolean> getDeduceEnvironments();

    /**
     * Report format (json/html/both).
     *
     * @return format
     */
    @Input
    public abstract Property<String> getFormat();

    /**
     * Base directory used for rendering stable, relative origin paths.
     *
     * @return base dir
     */
    @Internal
    public abstract DirectoryProperty getProjectBaseDir();

    /**
     * Base directory path used for origin rewriting.
     * <p>
     * This is an {@link Input} rather than an {@code @InputDirectory} to avoid snapshotting the full project directory,
     * which would prevent build cache reuse due to unrelated file changes.
     *
     * @return base dir path
     */
    @Input
    public abstract Property<String> getProjectBaseDirPath();

    /**
     * Resource directories (relative to {@link #getProjectBaseDir()}) used for origin rewriting.
     *
     * @return resource directories
     */
    @Input
    public abstract ListProperty<String> getResourcesDirs();

    /**
     * Whether the scenario classpath overrides the default computed classpath.
     *
     * @return override flag
     */
    @Input
    public abstract Property<Boolean> getOverrideClasspath();

    /**
     * Output directory where reports will be generated.
     *
     * @return output directory
     */
    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    /**
     * Result marker file containing the CLI exit code and report paths.
     *
     * @return marker file
     */
    @OutputFile
    public abstract RegularFileProperty getResultFile();

    /**
     * Combined enabled flag (global + scenario).
     *
     * @return enabled flag
     */
    @Internal
    public abstract Property<Boolean> getEnabledFlag();

    /**
     * Optional Java launcher.
     *
     * @return java launcher
     */
    @Nested
    @Optional
    public abstract Property<JavaLauncher> getJavaLauncher();

    /**
     * Optional Java executable path.
     *
     * @return executable
     */
    @Input
    @Optional
    public abstract Property<String> getJavaExecutable();

    /**
     * Provides the {@link ExecOperations} used to run the validator.
     *
     * @return exec operations
     */
    @Inject
    protected abstract ExecOperations getExecOperations();

    /**
     * Runs the configuration validator CLI and writes the result marker.
     *
     * @throws IOException if output cannot be written
     */
    @TaskAction
    public final void validateConfiguration() throws IOException {
        if (!getEnabledFlag().getOrElse(true)) {
            getLogger().info("Micronaut configuration validation disabled");
            return;
        }

        Files.createDirectories(getOutputDirectory().getAsFile().get().toPath());
        Path resultFile = getResultFile().getAsFile().get().toPath();
        Files.createDirectories(resultFile.getParent());

        ExecResult execResult = getExecOperations().javaexec(spec -> {
            String explicitExecutable = getJavaExecutable().getOrNull();
            if (explicitExecutable != null && !explicitExecutable.isBlank()) {
                spec.setExecutable(explicitExecutable);
            } else {
                JavaLauncher launcher = getJavaLauncher().getOrNull();
                if (launcher != null) {
                    spec.setExecutable(launcher.getExecutablePath().getAsFile().getAbsolutePath());
                }
            }
            spec.setClasspath(getValidatorClasspath());
            spec.getMainClass().set(BOOTSTRAP_MAIN_CLASS);
            spec.setIgnoreExitValue(true);

            List<String> args = new ArrayList<>();
            args.add("--classpath");
            // The bootstrap creates an isolated classloader from --classpath.
            // Include the validator tool classpath as well so it can run independently of the application dependencies.
            args.add(getValidatorClasspath().getAsPath() + File.pathSeparator + getApplicationClasspath().getAsPath());
            args.add("--out");
            args.add(getOutputDirectory().get().getAsFile().getAbsolutePath());
            args.add("--format");
            args.add(getFormat().get());
            for (String env : getEnvironments().getOrElse(List.of())) {
                if (!env.isBlank()) {
                    args.add("--env");
                    args.add(env);
                }
            }
            for (String sup : getSuppressions().getOrElse(List.of())) {
                if (!sup.isBlank()) {
                    args.add("--suppress");
                    args.add(sup);
                }
            }

            args.add("--fail-on-not-present");
            args.add(String.valueOf(getFailOnNotPresent().getOrElse(true)));
            args.add("--deduce-environments");
            args.add(String.valueOf(getDeduceEnvironments().getOrElse(false)));

            String baseDirPath = getProjectBaseDirPath().getOrNull();
            File baseDir = baseDirPath != null ? new File(baseDirPath) : getProjectBaseDir().getAsFile().get();
            args.add("--project-base-dir");
            args.add(baseDir.getAbsolutePath());

            List<String> resourcesDirs = getResourcesDirs().getOrElse(List.of());
            if (!resourcesDirs.isEmpty()) {
                args.add("--resources-dirs");
                args.add(String.join(",", resourcesDirs));
            }

            spec.args(args);
        });

        int exitCode = execResult.getExitValue();
        writeResultMarker(resultFile, exitCode);

        // Exit codes:
        // 0 = no errors
        // 1 = validation errors
        // 2 = usage/configuration/exception
        if (exitCode == 2) {
            throw new GradleException("Micronaut configuration validation failed to execute. See reports in " + getOutputDirectory().getAsFile().get());
        }
    }

    private void writeResultMarker(Path resultFile, int exitCode) throws IOException {
        Path outDir = getOutputDirectory().getAsFile().get().toPath();

        // NOTE: This file must be reproducible for build caching. Do NOT use java.util.Properties#store,
        // which adds a timestamp comment.
        String content = "exitCode=" + exitCode + "\n" +
            "outputDir=" + outDir.toAbsolutePath() + "\n" +
            "jsonReport=" + outDir.resolve("configuration-errors.json") + "\n" +
            "htmlReport=" + outDir.resolve("configuration-errors.html") + "\n";
        Files.writeString(resultFile, content, StandardCharsets.UTF_8);
    }
}
