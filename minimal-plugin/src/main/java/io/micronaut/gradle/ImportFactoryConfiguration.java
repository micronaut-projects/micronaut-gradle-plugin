package io.micronaut.gradle;

import org.gradle.api.provider.Property;

import javax.inject.Inject;

/**
 * Configuration for generating Micronaut import factories from dependency jars.
 *
 * @since 5.0.0
 */
public abstract class ImportFactoryConfiguration {
    @Inject
    public ImportFactoryConfiguration() {
    }

    /**
     * @return Whether import factory generation is enabled.
     */
    public abstract Property<Boolean> getEnabled();

    /**
     * @return Regex used to include dependencies, matched against {@code group:name}.
     */
    public abstract Property<String> getIncludeDependenciesFilter();

    /**
     * @return Regex used to exclude dependencies, matched against {@code group:name}.
     */
    public abstract Property<String> getExcludeDependenciesFilter();

    /**
     * @return Regex used to include packages.
     */
    public abstract Property<String> getIncludePackagesFilter();

    /**
     * @return Regex used to exclude packages.
     */
    public abstract Property<String> getExcludePackagesFilter();

    /**
     * @return The target package for a single aggregated import factory.
     */
    public abstract Property<String> getTargetPackage();

    public ImportFactoryConfiguration enabled(boolean enabled) {
        getEnabled().set(enabled);
        return this;
    }

    public ImportFactoryConfiguration includeDependenciesFilter(String filter) {
        getIncludeDependenciesFilter().set(filter);
        return this;
    }

    public ImportFactoryConfiguration excludeDependenciesFilter(String filter) {
        getExcludeDependenciesFilter().set(filter);
        return this;
    }

    public ImportFactoryConfiguration includePackagesFilter(String filter) {
        getIncludePackagesFilter().set(filter);
        return this;
    }

    public ImportFactoryConfiguration excludePackagesFilter(String filter) {
        getExcludePackagesFilter().set(filter);
        return this;
    }

    public ImportFactoryConfiguration targetPackage(String targetPackage) {
        getTargetPackage().set(targetPackage);
        return this;
    }
}
