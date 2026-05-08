package io.micronaut.gradle.buildpacks.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds an OCI image using the Cloud Native Buildpacks pack CLI.
 */
public abstract class BuildpackImageTask extends DefaultTask {

    @Input
    public abstract Property<String> getImageName();

    @Input
    public abstract ListProperty<String> getTags();

    @Input
    public abstract Property<String> getBuilder();

    @Input
    @Optional
    public abstract Property<String> getRunImage();

    @Input
    public abstract ListProperty<String> getBuildpacks();

    @Input
    public abstract MapProperty<String, String> getEnvironment();

    @Input
    public abstract Property<Boolean> getPublish();

    @Input
    public abstract Property<String> getPackExecutable();

    @Input
    @Optional
    public abstract Property<String> getPullPolicy();

    @Input
    @Optional
    public abstract Property<String> getNetwork();

    @Input
    @Optional
    public abstract Property<String> getDockerHost();

    @Input
    @Optional
    public abstract Property<String> getDockerConfigDirectory();

    @Input
    public abstract Property<Boolean> getTrustBuilder();

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getApplicationDirectory();

    @Inject
    protected abstract ExecOperations getExecOperations();

    @TaskAction
    public void buildImage() {
        var arguments = buildArguments();
        try {
            getExecOperations().exec(spec -> {
                spec.executable(getPackExecutable().get());
                spec.args(arguments);
                if (getDockerHost().isPresent()) {
                    spec.environment("DOCKER_HOST", getDockerHost().get());
                }
                if (getDockerConfigDirectory().isPresent()) {
                    spec.environment("DOCKER_CONFIG", getDockerConfigDirectory().get());
                }
            });
        } catch (RuntimeException ex) {
            throw new GradleException("Unable to run the Cloud Native Buildpacks pack CLI. Install pack or configure micronaut.buildpacks.packExecutable.", ex);
        }
    }

    private List<String> buildArguments() {
        var args = new ArrayList<String>();
        args.add("build");
        args.add(getImageName().get());
        args.add("--path");
        args.add(getApplicationDirectory().get().getAsFile().getAbsolutePath());
        args.add("--builder");
        args.add(getBuilder().get());
        if (getRunImage().isPresent()) {
            args.add("--run-image");
            args.add(getRunImage().get());
        }
        getBuildpacks().get().forEach(buildpack -> {
            args.add("--buildpack");
            args.add(buildpack);
        });
        getEnvironment().get().forEach((key, value) -> {
            args.add("--env");
            args.add(key + "=" + value);
        });
        getTags().get().forEach(tag -> {
            args.add("--tag");
            args.add(tag);
        });
        if (getPullPolicy().isPresent()) {
            args.add("--pull-policy");
            args.add(getPullPolicy().get());
        }
        if (getNetwork().isPresent()) {
            args.add("--network");
            args.add(getNetwork().get());
        }
        if (Boolean.TRUE.equals(getTrustBuilder().get())) {
            args.add("--trust-builder");
        }
        if (Boolean.TRUE.equals(getPublish().get())) {
            args.add("--publish");
        }
        return args;
    }
}
