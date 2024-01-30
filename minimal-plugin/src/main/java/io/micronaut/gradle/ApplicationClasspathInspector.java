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
package io.micronaut.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Set;
import java.util.stream.Collectors;

@CacheableTask
public abstract class ApplicationClasspathInspector extends DefaultTask {
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getResources();

    @Internal
    public abstract ConfigurableFileCollection getRuntimeClasspath();

    @Input
    public Set<String> getResolvedClasspathNames() {
        return getRuntimeClasspath().getFiles().stream().map(File::getName).collect(Collectors.toSet());
    }

    @OutputFile
    public abstract RegularFileProperty getReportFile();

    @TaskAction
    void inspect() throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(getReportFile().get().getAsFile()))) {
            Set<File> resources = getResources().getFiles();
            if (resources.stream().anyMatch(ApplicationClasspathInspector::isYamlConfigurationFile)) {
                writer.println("YAML configuration file detected");
                if (getResolvedClasspathNames().stream().noneMatch(n -> n.startsWith("snakeyaml"))) {
                    writer.println("Didn't find snakeyaml on classpath. Failing");
                    throw new RuntimeException("YAML configuration file detected but snakeyaml is not on classpath. Make sure to add a runtimeOnly dependency on snakeyaml, e.g 'runtimeOnly(\"org.yaml:snakeyaml\")'");
                }
            }
        }
    }

    private static boolean isYamlConfigurationFile(File f) {
        return f.getName().startsWith("application") && (
                f.getName().endsWith(".yml") ||
                f.getName().endsWith(".yaml"));
    }
}
