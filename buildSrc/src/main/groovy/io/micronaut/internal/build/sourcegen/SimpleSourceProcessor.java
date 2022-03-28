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
package io.micronaut.internal.build.sourcegen;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

@CacheableTask
public abstract class SimpleSourceProcessor extends DefaultTask {
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract DirectoryProperty getTemplates();

    @OutputDirectory
    abstract DirectoryProperty getOutputDirectory();

    @Input
    abstract MapProperty<String, String> getReplacements();

    @TaskAction
    public void process() throws IOException {
        Path inputDir = getTemplates().getAsFile().get().toPath();
        Path outputPath = getOutputDirectory().getAsFile().get().toPath();
        Map<String, String> replacements = getReplacements().get();
        try (Stream<Path> stream = Files.walk(inputDir)) {
            stream.forEach(path -> {
                String relativePath = inputDir.relativize(path).toString();
                Path target = outputPath.resolve(relativePath);
                try {
                    if (Files.isDirectory(path)) {
                        Files.createDirectories(target);
                    } else if (Files.isRegularFile(path)) {
                        String contents = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                        for (Map.Entry<String, String> entry : replacements.entrySet()) {
                            contents = contents.replace(entry.getKey(), entry.getValue());
                        }
                        Files.write(target, contents.getBytes(StandardCharsets.UTF_8));
                    }
                } catch (IOException e) {
                    throw new GradleException("Unable to create target directory " + target, e);
                }
            });
        }
    }
}
