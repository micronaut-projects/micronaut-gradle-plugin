package io.micronaut.gradle.buildpacks;

import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;

/**
 * Configuration for Cloud Native Buildpacks image tasks.
 */
public abstract class BuildpacksExtension {

    public abstract Property<String> getImageName();

    public abstract ListProperty<String> getTags();

    public abstract Property<String> getBuilder();

    public abstract Property<String> getRunImage();

    public abstract ListProperty<String> getBuildpacks();

    public abstract MapProperty<String, String> getEnvironment();

    public abstract Property<Boolean> getPublish();

    public abstract Property<String> getPackExecutable();

    public abstract Property<String> getPullPolicy();

    public abstract Property<String> getNetwork();

    public abstract Property<String> getDockerHost();

    public abstract Property<String> getDockerConfigDirectory();

    public abstract Property<Boolean> getTrustBuilder();
}
