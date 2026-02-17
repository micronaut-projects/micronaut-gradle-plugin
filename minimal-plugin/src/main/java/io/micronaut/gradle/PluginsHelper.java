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

import io.micronaut.gradle.internal.AutomaticDependency;
import io.micronaut.gradle.internal.ConfigurableVersionProperty;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.VersionCatalog;
import org.gradle.api.artifacts.VersionCatalogsExtension;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.reflect.TypeOf;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import static org.gradle.api.plugins.JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME;
import static org.gradle.api.plugins.JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME;

public abstract class PluginsHelper {
    public static final String MICRONAUT_PLATFORM_COORDINATES = "io.micronaut.platform:micronaut-platform";
    static final List<String> ANNOTATION_PROCESSOR_MODULES = List.of("inject-java");
    public static final ConfigurableVersionProperty CORE_VERSION_PROPERTY = ConfigurableVersionProperty.of("core");
    public static final ConfigurableVersionProperty DATA_VERSION_PROPERTY = ConfigurableVersionProperty.of("data");
    public static final ConfigurableVersionProperty JAXRS_VERSION_PROPERTY = ConfigurableVersionProperty.of("jaxrs");
    public static final ConfigurableVersionProperty MICROMETER_VERSION_PROPERTY = ConfigurableVersionProperty.of("micrometer");
    public static final ConfigurableVersionProperty MICROSTREAM_VERSION_PROPERTY = ConfigurableVersionProperty.of("microstream");
    public static final ConfigurableVersionProperty OPENAPI_VERSION_PROPERTY = ConfigurableVersionProperty.of("openapi");
    public static final ConfigurableVersionProperty SECURITY_VERSION_PROPERTY = ConfigurableVersionProperty.of("security");
    public static final ConfigurableVersionProperty SERDE_VERSION_PROPERTY = ConfigurableVersionProperty.of("serde");
    public static final ConfigurableVersionProperty SPRING_VERSION_PROPERTY = ConfigurableVersionProperty.of("spring");
    public static final ConfigurableVersionProperty TRACING_VERSION_PROPERTY = ConfigurableVersionProperty.of("tracing");
    public static final ConfigurableVersionProperty VALIDATION_VERSION_PROPERTY = ConfigurableVersionProperty.of("validation");

    private static final Map<String, AutomaticDependency> GROUP_TO_PROCESSOR_MAP = Map.of(
            "io.micronaut.data", new AutomaticDependency(null, "io.micronaut.data:micronaut-data-processor", Optional.of(DATA_VERSION_PROPERTY)),
            "io.micronaut.jaxrs", new AutomaticDependency(null, "io.micronaut.jaxrs:micronaut-jaxrs-processor", Optional.of(JAXRS_VERSION_PROPERTY)),
            "io.micronaut.micrometer", new AutomaticDependency(null, "io.micronaut.micrometer:micronaut-micrometer-annotation", Optional.of(MICROMETER_VERSION_PROPERTY)),
            "io.micronaut.microstream", new AutomaticDependency(null, "io.micronaut.microstream:micronaut-microstream-annotations", Optional.of(MICROSTREAM_VERSION_PROPERTY)),
            "io.micronaut.openapi", new AutomaticDependency(null, "io.micronaut.openapi:micronaut-openapi", Optional.of(OPENAPI_VERSION_PROPERTY)),
            "io.micronaut.security", new AutomaticDependency(null, "io.micronaut.security:micronaut-security-annotations", Optional.of(SECURITY_VERSION_PROPERTY)),
            "io.micronaut.serde", new AutomaticDependency(null, "io.micronaut.serde:micronaut-serde-processor", Optional.of(SERDE_VERSION_PROPERTY)),
            "io.micronaut.spring", new AutomaticDependency(null, "io.micronaut.spring:micronaut-spring-annotation", Optional.of(SPRING_VERSION_PROPERTY)),
            "io.micronaut.tracing", new AutomaticDependency(null, "io.micronaut.tracing:micronaut-tracing-annotation", Optional.of(TRACING_VERSION_PROPERTY)),
            "io.micronaut.validation", new AutomaticDependency(null, "io.micronaut.validation:micronaut-validation-processor", Optional.of(VALIDATION_VERSION_PROPERTY))
    );
    public static final String MICRONAUT_VERSION_PROPERTY = "micronautVersion";
    public static final String MICRONAUT_PLATFORM_ALIAS = "micronaut.platform";
    public static final String MICRONAUT_ALIAS = "micronaut";

    public static final List<ConfigurableVersionProperty> KNOWN_VERSION_PROPERTIES = List.of(
            CORE_VERSION_PROPERTY,
            DATA_VERSION_PROPERTY,
            JAXRS_VERSION_PROPERTY,
            MICROMETER_VERSION_PROPERTY,
            MICROSTREAM_VERSION_PROPERTY,
            OPENAPI_VERSION_PROPERTY,
            SECURITY_VERSION_PROPERTY,
            SERDE_VERSION_PROPERTY,
            SPRING_VERSION_PROPERTY,
            TRACING_VERSION_PROPERTY,
            VALIDATION_VERSION_PROPERTY
    );

    private PluginsHelper() {
    }

    public static void maybeAddMicronautPlatformBom(Project p, Configuration configuration) {
        MicronautExtension micronautExtension = p.getExtensions().findByType(MicronautExtension.class);
        configuration.getDependencies().addAllLater(
                micronautExtension.getImportMicronautPlatform().zip(PluginsHelper.findMicronautVersion(p), (usePlatform, version) -> {
                    if (Boolean.TRUE.equals(usePlatform)) {
                        return List.of(resolveMicronautPlatform(p.getDependencies(), version));
                    }
                    return Collections.emptyList();
                })
        );
    }

    private static Provider<String> findVersionFromProjectProperties(Project p) {
        return p.getProviders().provider(() -> {
            Object micronautVersion = p.getProperties().get(MICRONAUT_VERSION_PROPERTY);
            if (micronautVersion != null) {
                return micronautVersion.toString();
            }
            return null;
        });
    }

    private static Provider<String> findVersionFromGradleProperties(Project p) {
        return p.getProviders().gradleProperty(MICRONAUT_VERSION_PROPERTY);
    }

    private static Provider<String> findVersionFromVersionCatalog(Project p) {
        return p.provider(() -> {
            VersionCatalogsExtension versionCatalogs = p.getExtensions().findByType(VersionCatalogsExtension.class);
            if (versionCatalogs != null) {
                Optional<VersionCatalog> mn = versionCatalogs.find("mn").or(() -> versionCatalogs.find("libs"));
                if (mn.isPresent()) {
                    VersionCatalog versionCatalog = mn.get();
                    Optional<VersionConstraint> vc = versionCatalog.findVersion(MICRONAUT_PLATFORM_ALIAS)
                            .or(() -> versionCatalog.findVersion(MICRONAUT_ALIAS));
                    if (vc.isPresent()) {
                        return vc.get().getRequiredVersion();
                    }
                }
            }
            return null;
        });
    }

    public static Provider<String> findMicronautVersion(Project p) {
        return findMicronautExtension(p)
                .getVersion()
                .orElse(findVersionFromVersionCatalog(p))
                .orElse(findVersionFromGradleProperties(p))
                .orElse(findVersionFromProjectProperties(p))
                .orElse(failAboutMissingMicronautVersion(p));
    }

    private static Provider<String> failAboutMissingMicronautVersion(Project p) {
        return p.getProviders().provider(() -> {
            throw new InvalidUserCodeException("Micronaut version not set. Use micronaut { version '..'} or 'micronautVersion' in gradle.properties to set the version");
        });
    }

    public static SourceSetContainer findSourceSets(Project p) {
        var javaPluginExtension = javaPluginExtensionOf(p);
        if (javaPluginExtension != null) {
            return javaPluginExtension.getSourceSets();
        }
        return null;
    }

    public static JavaPluginExtension javaPluginExtensionOf(Project p) {
        return p.getExtensions().findByType(JavaPluginExtension.class);
    }

    static void configureAnnotationProcessors(
            Project project,
            String implementationScope,
            String annotationProcessorConfiguration) {
        registerAnnotationProcessors(project, annotationProcessorConfiguration);
        new AutomaticDependency(
                implementationScope,
                "io.micronaut:micronaut-inject",
                Optional.of(CORE_VERSION_PROPERTY)
        ).applyTo(project);
    }

    static void registerAnnotationProcessors(Project p, String... annotationProcessingConfigurations) {
        registerAnnotationProcessors(p, ANNOTATION_PROCESSOR_MODULES, annotationProcessingConfigurations);
    }

    static void registerAnnotationProcessors(Project p, List<String> annotationProcessorModules, String... annotationProcessingConfigurations) {
        for (String annotationProcessorModule : annotationProcessorModules) {
            for (String annotationProcessingConfiguration : annotationProcessingConfigurations) {
                new AutomaticDependency(
                        annotationProcessingConfiguration,
                        "io.micronaut:micronaut-" + annotationProcessorModule,
                        Optional.of(CORE_VERSION_PROPERTY)
                ).applyTo(p);
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
                for (var entry : GROUP_TO_PROCESSOR_MAP.entrySet()) {
                    boolean hasDep = !allDependencies.matching(dependency -> Objects.equals(dependency.getGroup(), entry.getKey())).isEmpty();
                    if (hasDep) {
                        for (String configuration : configurations) {
                            AutomaticDependency automaticDependency = entry.getValue();
                            automaticDependency.withConfiguration(configuration).applyTo(project);
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

    static void registerVersionExtensions(List<ConfigurableVersionProperty> properties, Project project) {
        var micronautExtension = project.getExtensions().findByType(MicronautExtension.class);
        var type = new TypeOf<Property<String>>() {
        };
        properties.forEach(v -> {
            var property = project.getObjects().property(String.class);
            property.convention(project.getProviders().gradleProperty(v.gradlePropertyName()));
            micronautExtension.getExtensions().add(type, v.dslName(), property);
        });
    }

    public static Dependency resolveMicronautPlatform(DependencyHandler dependencyHandler, String micronautVersion) {
        return dependencyHandler.platform(MICRONAUT_PLATFORM_COORDINATES + ":" + micronautVersion);
    }
}
