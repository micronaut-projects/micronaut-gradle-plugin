/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.gradle.internal;

import io.micronaut.aot.bytecode.ClassPathTransform;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.Property;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Runs {@link ClassPathTransform} in a worker, on the JDK of the project's toolchain.
 */
public abstract class ClassPathTransformAction implements WorkAction<ClassPathTransformAction.Parameters> {

    private static final Logger LOGGER = Logging.getLogger(ClassPathTransformAction.class);

    @Override
    public void execute() {
        Parameters parameters = getParameters();
        Path output = parameters.getOutputDirectory().get().getAsFile().toPath();
        List<Path> entries = new ArrayList<>();
        for (File file : parameters.getApplication()) {
            entries.add(file.toPath());
        }
        for (File file : parameters.getDependencies()) {
            entries.add(file.toPath());
        }
        // An entry that does not exist, such as the classes directory of a language without sources, has
        // nothing to rewrite, and the transform only takes entries it can read.
        List<Path> classPath = entries.stream().filter(Files::exists).distinct().toList();
        Set<Path> strip = new LinkedHashSet<>();
        if (Boolean.TRUE.equals(parameters.getStripLocalVariables().get())) {
            for (File file : parameters.getThirdPartyJars()) {
                Path jar = file.toPath();
                if (Files.isRegularFile(jar) && classPath.contains(jar)) {
                    strip.add(jar);
                }
            }
        }
        boolean desugar = Boolean.TRUE.equals(parameters.getDesugarLambdas().get());
        List<String> report = new ArrayList<>();
        List<Path> rewritten = classPath;
        if (desugar || !strip.isEmpty()) {
            try {
                ClassPathTransform.Result result = ClassPathTransform.run(ClassPathTransform.Request.builder()
                    .classPath(classPath)
                    .outputDirectory(output.resolve("copies"))
                    .desugarLambdas(desugar)
                    .stripLocalVariables(strip)
                    .build());
                rewritten = result.classPath();
                for (String line : result.summary().split("\n")) {
                    if (!line.isBlank()) {
                        LOGGER.lifecycle(line);
                        report.add(line);
                    }
                }
                for (String warning : result.warnings()) {
                    LOGGER.warn(warning);
                    report.add("WARNING: " + warning);
                }
                for (ClassPathTransform.Result.Entry entry : result.entries()) {
                    report.add(describe(entry));
                    report.addAll(entry.notes());
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Unable to rewrite the class path", e);
            }
        }
        List<String> mapping = new ArrayList<>(entries.size());
        for (Path entry : entries) {
            int index = classPath.indexOf(entry);
            Path packaged = index < 0 ? entry : rewritten.get(index);
            mapping.add(entry.toAbsolutePath() + "\t" + packaged.toAbsolutePath());
        }
        try {
            Files.createDirectories(output);
            Files.write(output.resolve(ClassPathTransformTask.CLASS_PATH_FILE), mapping, StandardCharsets.UTF_8);
            Files.write(output.resolve(ClassPathTransformTask.REPORT_FILE), report, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to write the class path transform's output", e);
        }
    }

    private static String describe(ClassPathTransform.Result.Entry entry) {
        StringBuilder line = new StringBuilder(entry.path().getFileName().toString());
        line.append(": ").append(entry.sitesRewritten()).append(" lambda sites rewritten, ")
            .append(entry.classesGenerated()).append(" classes generated, ")
            .append(entry.classesStripped()).append(" classes stripped, ")
            .append(entry.bytesSaved()).append(" bytes saved");
        for (Map.Entry<String, Integer> left : entry.sitesLeft().entrySet()) {
            line.append(", ").append(left.getValue()).append(" sites left (").append(left.getKey()).append(')');
        }
        entry.kept().ifPresent(reason -> line.append(", kept: ").append(reason));
        return line.toString();
    }

    /**
     * The parameters of the action.
     */
    public interface Parameters extends WorkParameters {

        ConfigurableFileCollection getApplication();

        ConfigurableFileCollection getDependencies();

        ConfigurableFileCollection getThirdPartyJars();

        Property<Boolean> getDesugarLambdas();

        Property<Boolean> getStripLocalVariables();

        DirectoryProperty getOutputDirectory();
    }
}
