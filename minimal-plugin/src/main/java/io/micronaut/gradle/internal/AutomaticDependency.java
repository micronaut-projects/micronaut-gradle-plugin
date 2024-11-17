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
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;

import java.util.List;
import java.util.Optional;
import java.util.Set;

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
            p.afterEvaluate(unusedProject -> VersionCatalogLookupCache.get().clear());
            var dependencyHandler = p.getDependencies();
            var micronautExtension = p.getExtensions().getByType(MicronautExtension.class);
            var ignoredDependencies = micronautExtension.getIgnoredAutomaticDependencies();
            p.getConfigurations().getByName(configuration).getDependencies().addAllLater(
                p.getProviders().provider(() -> {
                    var ignored = ignoredDependencies.getOrElse(Set.of());
                    if (ignored.contains(coordinates)) {
                        return List.of();
                    }
                    if (versionProperty.isPresent()) {
                        var provider = (Property<String>) micronautExtension.getExtensions().findByName(versionProperty.get().dslName());
                        if (provider != null && provider.isPresent()) {
                            return List.of(dependencyHandler.create(coordinates + ":" + provider.get()));
                        }
                    }
                    // If the Micronaut version catalog is applied via the settings plugin, we won't use an "empty" version
                    // but fetch it from the catalog if possible
                    VersionCatalogsExtension versionCatalogs = p.getExtensions().findByType(VersionCatalogsExtension.class);
                    if (versionCatalogs != null) {
                        Optional<VersionCatalog> mn = versionCatalogs.find("mn");
                        if (mn.isPresent()) {
                            // The cache should ideally use Gradle build services, but that's currently impossible due to
                            // https://github.com/gradle/gradle/issues/17559
                            var catalogCache = VersionCatalogLookupCache.get();
                            VersionCatalog micronautCatalog = mn.get();
                            Optional<Provider<MinimalExternalModuleDependency>> dependencyProvider = catalogCache.findDependencyFromCatalog(micronautCatalog, coordinates);
                            if (dependencyProvider.isPresent()) {
                                return List.of(dependencyProvider.get().get());
                            }
                        }
                    }
                    return List.of(dependencyHandler.create(coordinates));
                })
            );
        });
    }

    public AutomaticDependency withConfiguration(String configuration) {
        return new AutomaticDependency(configuration, coordinates, versionProperty);
    }
}
