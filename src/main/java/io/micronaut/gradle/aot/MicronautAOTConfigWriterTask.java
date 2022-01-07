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

import io.micronaut.aot.std.sourcegen.AbstractStaticServiceLoaderSourceGenerator;
import io.micronaut.aot.std.sourcegen.CachedEnvironmentSourceGenerator;
import io.micronaut.aot.std.sourcegen.ConstantPropertySourcesSourceGenerator;
import io.micronaut.aot.std.sourcegen.DeduceEnvironmentSourceGenerator;
import io.micronaut.aot.std.sourcegen.EnvironmentPropertiesSourceGenerator;
import io.micronaut.aot.std.sourcegen.Environments;
import io.micronaut.aot.std.sourcegen.GraalVMOptimizationFeatureSourceGenerator;
import io.micronaut.aot.std.sourcegen.JitStaticServiceLoaderSourceGenerator;
import io.micronaut.aot.std.sourcegen.KnownMissingTypesSourceGenerator;
import io.micronaut.aot.std.sourcegen.LogbackConfigurationSourceGenerator;
import io.micronaut.aot.std.sourcegen.NativeStaticServiceLoaderSourceGenerator;
import io.micronaut.aot.std.sourcegen.PublishersSourceGenerator;
import io.micronaut.aot.std.sourcegen.YamlPropertySourceGenerator;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Properties;

/**
 * A task which is responsible for writing a configuration
 * file for Micronaut AOT.
 */
public abstract class MicronautAOTConfigWriterTask extends DefaultTask {

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    @Optional
    public abstract RegularFileProperty getUserConfiguration();

    @Input
    public abstract Property<Boolean> getForNative();

    @Nested
    public abstract Property<AOTOptimizations> getAOTOptimizations();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    private static void booleanOptimization(Properties props, String optimizationId, Provider<Boolean> provider) {
        if (provider.isPresent()) {
            String key = optimizationId + ".enabled";
            if (!props.containsKey(key)) {
                props.put(key, String.valueOf(provider.get()));
            }
        }
    }

    private static void stringListParameter(Properties props, String parameter, ListProperty<String> provider) {
        if (provider.isPresent()) {
            List<String> elements = provider.get();
            if (!props.containsKey(parameter) && !elements.isEmpty()) {
                props.put(parameter, String.join(",", elements));
            }
        }
    }

    @TaskAction
    void writeConfigFile() {
        Properties props = new Properties();
        if (getUserConfiguration().isPresent()) {
            try (InputStream in = new FileInputStream(getUserConfiguration().getAsFile().get())) {
                props.load(in);
            } catch (IOException e) {
                throw new GradleException("Unable to parse configuration file", e);
            }
        }
        AOTOptimizations optimizations = getAOTOptimizations().get();
        if (optimizations.getConfigurationProperties().isPresent()) {
            props.putAll(optimizations.getConfigurationProperties().get());
        }
        if (!props.containsKey(KnownMissingTypesSourceGenerator.OPTION.key())) {
            props.put(KnownMissingTypesSourceGenerator.OPTION.key(), String.join(",", MicronautAotPlugin.TYPES_TO_CHECK));
        }
        if (!props.containsKey(AbstractStaticServiceLoaderSourceGenerator.SERVICE_TYPES)) {
            props.put(AbstractStaticServiceLoaderSourceGenerator.SERVICE_TYPES, String.join(",", MicronautAotPlugin.SERVICE_TYPES));
        }
        booleanOptimization(props, GraalVMOptimizationFeatureSourceGenerator.ID, getForNative());
        booleanOptimization(props, LogbackConfigurationSourceGenerator.ID, optimizations.getReplaceLogbackXml());
        booleanOptimization(props, CachedEnvironmentSourceGenerator.ID, optimizations.getCacheEnvironment());
        booleanOptimization(props, JitStaticServiceLoaderSourceGenerator.ID, optimizations.getOptimizeServiceLoading());
        booleanOptimization(props, NativeStaticServiceLoaderSourceGenerator.ID, optimizations.getOptimizeServiceLoading());
        booleanOptimization(props, YamlPropertySourceGenerator.ID, optimizations.getConvertYamlToJava());
        booleanOptimization(props, KnownMissingTypesSourceGenerator.ID, optimizations.getOptimizeClassLoading());
        booleanOptimization(props, PublishersSourceGenerator.ID, optimizations.getOptimizeClassLoading());
        if (optimizations.getConvertYamlToJava().isPresent() && optimizations.getConvertYamlToJava().get()) {
            if (!optimizations.getOptimizeServiceLoading().isPresent() || !optimizations.getOptimizeServiceLoading().get()) {
                throw new InvalidUserCodeException("YAML conversion requires service loading optimizations to be enabled too.");
            }
            booleanOptimization(props, ConstantPropertySourcesSourceGenerator.ID, optimizations.getConvertYamlToJava());
        }
        booleanOptimization(props, EnvironmentPropertiesSourceGenerator.ID, optimizations.getPrecomputeOperations());
        booleanOptimization(props, DeduceEnvironmentSourceGenerator.ID, optimizations.getDeduceEnvironment());
        stringListParameter(props, Environments.POSSIBLE_ENVIRONMENTS_NAMES, optimizations.getPossibleEnvironments());
        File outputFile = getOutputFile().getAsFile().get();
        if (outputFile.getParentFile().isDirectory() || outputFile.getParentFile().mkdirs()) {
            try (OutputStream out = new FileOutputStream(outputFile)) {
                props.store(out, "Generated by Gradle");
            } catch (IOException e) {
                throw new GradleException("Unable to write output file: " + outputFile, e);
            }
        } else {
            throw new GradleException("Unable to write output file: " + outputFile);
        }
        getLogger().info("Wrote configuration file {}", outputFile);
    }
}
