package io.micronaut.gradle;

import io.micronaut.gradle.internal.ConfigurableVersionProperty;

import java.util.Locale;
import java.util.Optional;

/**
 * Supported runtime serialization options for Micronaut application builds.
 *
 * @author Álvaro Sánchez-Mariscal
 * @since 5.0.0
 */
public enum MicronautSerialization {
    /**
     * Do not add a runtime serialization dependency automatically.
     */
    NONE(null, null),
    /**
     * Micronaut Serialization with Jackson annotations.
     */
    SERDE_JACKSON("io.micronaut.serde:micronaut-serde-jackson", PluginsHelper.SERDE_VERSION_PROPERTY),
    /**
     * Micronaut Jackson Databind runtime.
     */
    JACKSON("io.micronaut:micronaut-jackson-databind", null);

    private final String runtimeDependency;
    private final ConfigurableVersionProperty versionProperty;

    MicronautSerialization(String runtimeDependency, ConfigurableVersionProperty versionProperty) {
        this.runtimeDependency = runtimeDependency;
        this.versionProperty = versionProperty;
    }

    public String getRuntimeDependency() {
        return runtimeDependency;
    }

    public boolean hasRuntimeDependency() {
        return runtimeDependency != null;
    }

    public Optional<ConfigurableVersionProperty> getVersionProperty() {
        return Optional.ofNullable(versionProperty);
    }

    public boolean matchesArtifact(String artifactId) {
        return runtimeDependency != null && runtimeDependency.endsWith(":" + artifactId);
    }

    public static MicronautSerialization parse(String value) {
        if (value == null) {
            return NONE;
        }
        return switch (value.replace('-', '_').toUpperCase(Locale.ENGLISH)) {
            case "NONE" -> NONE;
            case "SERDE_JACKSON" -> SERDE_JACKSON;
            case "JACKSON" -> JACKSON;
            default -> throw new IllegalArgumentException("Unsupported Micronaut serialization '" + value + "'");
        };
    }
}
