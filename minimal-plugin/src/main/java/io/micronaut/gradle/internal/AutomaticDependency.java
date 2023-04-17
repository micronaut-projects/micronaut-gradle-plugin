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
package io.micronaut.gradle.internal;

import io.micronaut.gradle.MicronautComponentPlugin;
import io.micronaut.gradle.MicronautExtension;
import org.gradle.api.Project;
import org.gradle.api.artifacts.MinimalExternalModuleDependency;
import org.gradle.api.artifacts.VersionCatalog;
import org.gradle.api.artifacts.VersionCatalogsExtension;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;

import java.util.Optional;

/**
 * Represents a dependency which is automatically
 * added by the Gradle plugin, such as some Micronaut
 * runtime dependencies or 3rd party dependencies.
 * @param configuration the scope (aka configuration) to which the dependency needs to be applied
 * @param coordinates the coordinates of the dependency in the "group:artifact" form
 * @param versionProperty if present, the version property which can be used either in the DSL or gradle properties
 * to override the default version
 */
public record AutomaticDependency(
        String configuration,
        String coordinates,
        Optional<ConfigurableVersionProperty> versionProperty
) {
    public void applyTo(Project p) {
        p.getPlugins().withType(MicronautComponentPlugin.class, unused -> {
            DependencyHandler dependencyHandler = p.getDependencies();
            MicronautExtension micronautExtension = p.getExtensions().getByType(MicronautExtension.class);
            dependencyHandler.addProvider(configuration, p.getProviders().provider(() -> {
                if (versionProperty.isPresent()) {
                    Property<String> provider = (Property<String>) micronautExtension.getExtensions().findByName(versionProperty.get().dslName());
                    if (provider != null && provider.isPresent()) {
                        return dependencyHandler.create(coordinates + ":" + provider.get());
                    }
                }
                // If the Micronaut version catalog is applied via the settings plugin, we won't use an "empty" version
                // but fetch it from the catalog if possible
                VersionCatalogsExtension versionCatalogs = p.getExtensions().findByType(VersionCatalogsExtension.class);
                if (versionCatalogs != null) {
                    Optional<VersionCatalog> mn = versionCatalogs.find("mn");
                    if (mn.isPresent()) {
                        VersionCatalog micronautCatalog = mn.get();
                        Optional<Provider<MinimalExternalModuleDependency>> dependencyProvider = micronautCatalog.getLibraryAliases()
                                .stream()
                                .map(micronautCatalog::findLibrary)
                                .map(Optional::get)
                                .filter(d -> coordinates.equals(d.get().getModule().toString()))
                                .findFirst();
                        if (dependencyProvider.isPresent()) {
                            return dependencyProvider.get().get();
                        }
                    }
                }
                return dependencyHandler.create(coordinates);
            }));

        });
    }

    public AutomaticDependency withConfiguration(String configuration) {
        return new AutomaticDependency(configuration, coordinates, versionProperty);
    }
}
