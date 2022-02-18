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
package io.micronaut.gradle.aot;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@CacheableTask
public abstract class MergeServiceFiles extends DefaultTask {

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getInputFiles();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @Inject
    protected abstract FileOperations getFileOperations();

    @TaskAction
    public void execute() {
        Set<File> serviceFiles = getInputFiles().getAsFileTree().matching(f -> f.include("META-INF/services/**")).getFiles();
        File outputDir = getOutputDirectory().dir("META-INF/services").get().getAsFile();
        getFileOperations().delete(outputDir);
        outputDir.mkdirs();
        Map<String, List<File>> perService = serviceFiles.stream()
                .collect(Collectors.groupingBy(File::getName));
        for (Map.Entry<String, List<File>> entry : perService.entrySet()) {
            String serviceType = entry.getKey();
            List<File> files = entry.getValue();
            File mergedServiceFile = new File(outputDir, serviceType);
            try (PrintWriter wrt = new PrintWriter(new OutputStreamWriter(new FileOutputStream(mergedServiceFile), StandardCharsets.UTF_8))) {
                for (File file : files) {
                    Files.readAllLines(file.toPath()).forEach(wrt::println);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
