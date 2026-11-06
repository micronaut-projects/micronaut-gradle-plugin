/*
 * Copyright 2003-2022 the original author or authors.
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
package io.micronaut.gradle.logback;

import io.micronaut.aot.logback.LogbackPrecompiler;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Incubating;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.IgnoreEmptyDirectories;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Compiles the application's {@code logback.xml} into a Logback {@code Configurator} with Micronaut AOT's Logback
 * precompiler. The output directory holds the generated classes and their service file, or nothing when the
 * precompiler stands down; the report file says what it did.
 *
 * @since 5.1.0
 */
@Incubating
@CacheableTask
public abstract class PrecompileLogback extends DefaultTask {

    /**
     * The application's own output, in class path order: its processed resources and class directories.
     *
     * @return the application roots
     */
    @Internal
    public abstract ConfigurableFileCollection getApplicationOutput();

    /**
     * The entries of {@link #getApplicationOutput()} that the precompiler reads.
     *
     * @return the application inputs
     */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    @IgnoreEmptyDirectories
    public abstract ConfigurableFileCollection getApplicationInputs();

    /**
     * The runtime class path after the application, in order.
     *
     * @return the runtime class path
     */
    @Classpath
    public abstract ConfigurableFileCollection getRuntimeClasspath();

    /**
     * The lowest Java release the application runs on.
     *
     * @return the target release
     */
    @Input
    public abstract Property<Integer> getTargetRelease();

    /**
     * Whether to precompile. When {@code false}, the output directory is left empty.
     *
     * @return the precompile property
     */
    @Input
    public abstract Property<Boolean> getPrecompile();

    /**
     * The directory of the generated classes and service file.
     *
     * @return the output directory
     */
    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    /**
     * What the precompiler did: its message, then its warnings.
     *
     * @return the report file
     */
    @OutputFile
    public abstract RegularFileProperty getReportFile();

    @Inject
    protected abstract FileSystemOperations getFileSystemOperations();

    /**
     * Declares the application inputs from {@link #getApplicationOutput()}.
     */
    public PrecompileLogback() {
        getApplicationInputs().from(getApplicationOutput().getAsFileTree()
            .matching(patterns -> patterns.include(LogbackPrecompiler.applicationInputs())));
    }

    @TaskAction
    void precompile() throws IOException {
        Path output = getOutputDirectory().get().getAsFile().toPath();
        // An earlier configurator must not survive a run that generates nothing
        getFileSystemOperations().delete(spec -> spec.delete(output));
        Files.createDirectories(output);
        List<String> report = new ArrayList<>();
        if (getPrecompile().get()) {
            LogbackPrecompiler.Result result = LogbackPrecompiler.precompile(LogbackPrecompiler.Request.builder()
                .applicationOutput(existing(getApplicationOutput()))
                .runtimeClasspath(existing(getRuntimeClasspath()))
                .targetRelease(getTargetRelease().get())
                .build());
            if (result.status() == LogbackPrecompiler.Status.FAILED) {
                getLogger().warn(result.message());
            } else {
                getLogger().lifecycle(result.message());
            }
            report.add(result.message());
            for (String warning : result.warnings()) {
                getLogger().warn(warning);
                report.add(warning);
            }
            for (Map.Entry<String, byte[]> entry : result.entries().entrySet()) {
                Path file = output.resolve(entry.getKey()).normalize();
                if (!file.startsWith(output)) {
                    throw new GradleException("Unexpected entry generated by the Logback precompiler: " + entry.getKey());
                }
                Files.createDirectories(file.getParent());
                Files.write(file, entry.getValue());
            }
        } else {
            report.add("Logback precompilation is disabled");
        }
        Files.write(getReportFile().get().getAsFile().toPath(), report, StandardCharsets.UTF_8);
    }

    private static List<Path> existing(ConfigurableFileCollection files) {
        return files.getFiles().stream().filter(File::exists).map(File::toPath).toList();
    }
}
