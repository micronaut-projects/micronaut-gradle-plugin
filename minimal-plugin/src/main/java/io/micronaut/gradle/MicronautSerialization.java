package io.micronaut.gradle;

import io.micronaut.gradle.internal.ConfigurableVersionProperty;

import java.util.Locale;
import java.util.Optional;

/**
 * Supported runtime serialization options for Micronaut application builds.
 *
 * @author Sergio del Amo
 * @since 5.0.0
 */
public enum MicronautSerialization {
    /**
     * Micronaut Serialization with Jackson annotations.
     */
    SERDE_JACKSON("io.micronaut.serde:micronaut-serde-jackson", PluginsHelper.SERDE_VERSION_PROPERTY),
    /**
     * Micronaut Jackson Databind runtime.
     */
    JACKSON("io.micronaut.jackson:micronaut-jackson-databind", null);

    private final String runtimeDependency;
    private final ConfigurableVersionProperty versionProperty;

    MicronautSerialization(String runtimeDependency, ConfigurableVersionProperty versionProperty) {
        this.runtimeDependency = runtimeDependency;
        this.versionProperty = versionProperty;
    }

    public String getRuntimeDependency() {
        return runtimeDependency;
    }

    public Optional<ConfigurableVersionProperty> getVersionProperty() {
        return Optional.ofNullable(versionProperty);
    }

    public boolean matchesArtifact(String artifactId) {
        return runtimeDependency.endsWith(":" + artifactId);
    }

    public static MicronautSerialization parse(String value) {
        if (value == null) {
            return SERDE_JACKSON;
        }
        return switch (value.replace('-', '_').toUpperCase(Locale.ENGLISH)) {
            case "SERDE_JACKSON" -> SERDE_JACKSON;
            case "JACKSON" -> JACKSON;
            default -> throw new IllegalArgumentException("Unsupported Micronaut serialization '" + value + "'");
        };
    }
}
