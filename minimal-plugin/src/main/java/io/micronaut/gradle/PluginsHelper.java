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
package io.micronaut.gradle;

import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import static org.gradle.api.plugins.JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME;
import static org.gradle.api.plugins.JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME;

public abstract class PluginsHelper {
    private final static List<String> ANNOTATION_PROCESSOR_MODULES = Arrays.asList("inject-java", "validation");
    private final static Map<String, String> GROUP_TO_PROCESSOR_MAP = Collections.unmodifiableMap(new HashMap<String, String>() {{
        put("io.micronaut.data", "io.micronaut.data:micronaut-data-processor");
        put("io.micronaut.jaxrs", "io.micronaut.jaxrs:micronaut-jaxrs-processor");
        put("io.micronaut.security", "io.micronaut.security:micronaut-security-annotations");
    }});

    public static String findMicronautVersion(Project p, MicronautExtension micronautExtension) {
        String v = micronautExtension.getVersion().getOrNull();
        if (v == null) {
            final Object o = p.getProperties().get("micronautVersion");
            if (o != null) {
                v = o.toString();
            }
        }
        if (v == null || v.length() == 0) {
            throw new InvalidUserCodeException("Micronaut version not set. Use micronaut { version '..'} or 'micronautVersion' in gradle.properties to set the version");
        }
        return v;
    }

    public static Provider<String> findMicronautVersionAsProvider(Project p) {
        return findMicronautExtension(p)
                .getVersion()
                .orElse(p.getProviders().gradleProperty("micronautVersion"))
                .orElse(p.getProviders().provider( () -> {
                    throw new InvalidUserCodeException("Micronaut version not set. Use micronaut { version '..'} or 'micronautVersion' in gradle.properties to set the version");
                }));
    }

    static void configureAnnotationProcessors(
            Project project,
            String implementationScope,
            String annotationProcessorConfiguration) {
        DependencyHandler dependencyHandler = project.getDependencies();
        registerAnnotationProcessors(dependencyHandler, annotationProcessorConfiguration);

        dependencyHandler.add(
                implementationScope,
                "io.micronaut:micronaut-inject"
        );
    }

    static void registerAnnotationProcessors(DependencyHandler dependencyHandler, String... annotationProcessingConfigurations) {
        for (String annotationProcessorModule : ANNOTATION_PROCESSOR_MODULES) {
            for (String annotationProcessingConfiguration : annotationProcessingConfigurations) {
                dependencyHandler.add(
                        annotationProcessingConfiguration,
                        "io.micronaut:micronaut-" + annotationProcessorModule
                );
            }
        }
    }

    static Optional<File> findGroovySrcDir(SourceSet groovySourceSet) {
        Optional<File> groovySrc = groovySourceSet.getAllJava().getSrcDirs()
                .stream().filter(f -> f.getName().endsWith("groovy"))
                .findFirst();
        return groovySrc.flatMap(file -> {
            if (file.exists()) {
                return Optional.of(file);
            }
            return Optional.empty();
        });
    }

    static void applyAdditionalProcessors(Project project, String... configurations) {
        Stream.of(IMPLEMENTATION_CONFIGURATION_NAME, COMPILE_ONLY_CONFIGURATION_NAME).forEach(config -> {
            // Need to do in an afterEvaluate because this will add dependencies only if the user didn't do it
            project.afterEvaluate(p -> {
                final DependencySet allDependencies = project.getConfigurations().getByName(config)
                        .getAllDependencies();
                for (String group : GROUP_TO_PROCESSOR_MAP.keySet()) {
                    boolean hasDep = !allDependencies.matching(dependency -> Objects.equals(dependency.getGroup(), group)).isEmpty();
                    if (hasDep) {
                        final DependencyHandler dependencies = project.getDependencies();
                        for (String configuration : configurations) {
                            dependencies.add(configuration, GROUP_TO_PROCESSOR_MAP.get(group));
                        }
                    }
                }
            });
        });
    }

    public static MicronautRuntime resolveRuntime(Project p) {
        MicronautExtension ext = p.getExtensions().findByType(MicronautExtension.class);
        Object o = p.findProperty("micronaut.runtime");

        MicronautRuntime micronautRuntime;
        if (o != null) {
            micronautRuntime = MicronautRuntime.valueOf(o.toString().toUpperCase(Locale.ENGLISH));
        } else if (ext == null) {
            micronautRuntime = MicronautRuntime.NONE;
        } else {
            micronautRuntime = ext.getRuntime().getOrElse(MicronautRuntime.NONE);
        }
        return micronautRuntime;
    }

    public static MicronautExtension findMicronautExtension(Project project) {
        return project.getExtensions().getByType(MicronautExtension.class);
    }
}
